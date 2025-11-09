package org.shirakawatyu.yamibo.novel.ui.vm

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.WebView
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.shirakawatyu.yamibo.novel.bean.Content
import org.shirakawatyu.yamibo.novel.bean.ContentType
import org.shirakawatyu.yamibo.novel.bean.ReaderSettings
import org.shirakawatyu.yamibo.novel.constant.RequestConfig
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.module.PassageWebViewClient
import org.shirakawatyu.yamibo.novel.ui.state.ChapterInfo
import org.shirakawatyu.yamibo.novel.ui.state.ReaderState
import org.shirakawatyu.yamibo.novel.util.CacheData
import org.shirakawatyu.yamibo.novel.util.CacheUtil
import org.shirakawatyu.yamibo.novel.util.FavoriteUtil
import org.shirakawatyu.yamibo.novel.util.HTMLUtil
import org.shirakawatyu.yamibo.novel.util.LocalCacheUtil
import org.shirakawatyu.yamibo.novel.util.SettingsUtil
import org.shirakawatyu.yamibo.novel.util.TextUtil
import org.shirakawatyu.yamibo.novel.util.ValueUtil
import androidx.core.graphics.toColorInt

@SuppressLint("SetJavaScriptEnabled")
class ReaderVM(private val applicationContext: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderState())
    val uiState = _uiState.asStateFlow()

    private var pagerState: PagerState? = null
    private var maxHeight = 0.dp
    private var maxWidth = 0.dp
    private var initialized = false
    private val logTag = "ReaderVM"
    private var compositionScope: CoroutineScope? = null
    var url by mutableStateOf("")
        private set

    var isTransitioning by mutableStateOf(false)
    var showLoadingScrim by mutableStateOf(false)
        private set

    private val rawContentList = ArrayList<Content>()
    private var latestPage: Int = 0
    private var currentAuthorId: String? = null
    private var isPreloading = false
    private val PRELOAD_THRESHOLD_VERTICAL = 1000
    private val PRELOAD_THRESHOLD_HORIZONTAL = 100
    private var viewBeingPreloaded = 0
    private var nextHtmlList: List<Content>? = null
    private var nextChapterList: List<ChapterInfo>? = null

    // ==================== 缓存相关 ====================
    // 缓存重试机制
    private val diskCacheRetries = mutableMapOf<Int, Int>() // 跟踪 <PageNum, RetryCount>
    private val MAX_CACHE_RETRIES = 2 // 每次加载允许失败2次（总共尝试3次）
    private val CACHE_RETRY_DELAY_MS = 5000L // 失败后等待5秒重试

    // 本地缓存工具
    private val localCache by lazy { LocalCacheUtil.getInstance(applicationContext) }

    // 缓存状态
    private val _cachedPages = MutableStateFlow<Set<Int>>(emptySet())
    val cachedPages: StateFlow<Set<Int>> = _cachedPages

    // 缓存进度
    private val _cacheProgress = MutableStateFlow<CacheProgress?>(null)
    val cacheProgress: StateFlow<CacheProgress?> = _cacheProgress

    // 专用于缓存的后台WebView
    @SuppressLint("StaticFieldLeak")
    private var cacheWebView: WebView? = null
    private var cacheWebViewClient: PassageWebViewClient? = null

    // 暴露isDiskCaching状态
    private val _isDiskCaching = MutableStateFlow(false)
    val isDiskCaching: StateFlow<Boolean> = _isDiskCaching.asStateFlow()

    private var diskCacheQueue: MutableSet<Int> = mutableSetOf()
    private var diskCacheIncludeImages: Boolean = false

    // 进度条总数
    private var diskCacheTotalPages: Int = 0

    // 进度条当前
    private var diskCacheCurrentPage: Int = 0

    // 跟踪当前缓存会话是否应显示进度对话框
    private var currentCacheSessionShowsProgress: Boolean = true

    data class CacheProgress(
        val totalPages: Int,
        val currentPage: Int, // 1-based index of processing (e..g., 1/20)
        val currentPageNum: Int, // The actual page number being cached (e.g., Page 5)
        val isComplete: Boolean = false
    )

    init {
        Log.i(logTag, "VM created.")
        // 在ViewModel创建时立即开始监听缓存索引变化
        viewModelScope.launch {
            localCache.index.collect { index ->
                // 当缓存索引更新时，如果已经有URL，则更新缓存状态
                if (url.isNotEmpty()) {
                    updateCachedPagesFromIndex(index)
                }
            }
        }
    }

    private fun extractPageNumFromLoadedUrl(url: String?): Int? {
        if (url == null) return null
        return try {
            url.substringAfter("page=", "").substringBefore("&").toIntOrNull()
        } catch (e: Exception) {
            Log.e(logTag, "Could not extract page number from URL: $url", e)
            null
        }
    }

    // 从索引更新缓存页面状态的方法
    private fun updateCachedPagesFromIndex(index: Map<String, LocalCacheUtil.CacheIndex>) {
        val novelCache = index[url]
        if (novelCache != null && novelCache.pages.isNotEmpty()) {
            _cachedPages.value = novelCache.pages.keys
            Log.i(logTag, "缓存状态已更新: $url - ${_cachedPages.value.size} 页")
        } else {
            _cachedPages.value = emptySet()
            Log.i(logTag, "缓存状态已清空: $url - 无缓存")
        }
    }

    // 启动磁盘缓存
    fun startCaching(
        pagesToCache: Set<Int>,
        includeImages: Boolean = false,
        showProgressDialog: Boolean = true
    ) {
        if (_isDiskCaching.value) {
            Log.w(logTag, "Already caching. New request ignored.")
            return
        }
        if (pagesToCache.isEmpty()) return

        Log.i(
            logTag,
            "Starting disk cache for ${pagesToCache.size} pages, includeImages=$includeImages"
        )

        _isDiskCaching.value = true
        diskCacheQueue = pagesToCache.toMutableSet()
        diskCacheIncludeImages = false // includeImages
        diskCacheTotalPages = pagesToCache.size
        diskCacheCurrentPage = 0 // 尚未开始处理
        diskCacheRetries.clear() // 开始新任务时，清空重试计数器
        // 记录这个缓存会话是否应该显示进度条
        currentCacheSessionShowsProgress = showProgressDialog
        // 初始化后台 WebView
        viewModelScope.launch(Dispatchers.Main) {
            // WebView 必须在主线程创建
            if (cacheWebView == null) {
                Log.i(logTag, "Creating background cache WebView...")
                cacheWebView = WebView(applicationContext).apply {
                    // 基本设置
                    settings.javaScriptEnabled = true
                    settings.useWideViewPort = true
                    // 应用缓存时选择的图片设置
//                    if (includeImages) {
//                        settings.loadsImagesAutomatically = true
//                        settings.blockNetworkImage = false
//                    } else {
                    settings.loadsImagesAutomatically = false
                    settings.blockNetworkImage = true
//                    }
                    webChromeClient = GlobalData.webChromeClient
                }
                // 设置专用的 Client 和回调
                cacheWebViewClient = PassageWebViewClient(::handleCacheLoadFinished)
                cacheWebView?.webViewClient = cacheWebViewClient!!
            }
            // 启动缓存队列
            loadNextPageForDiskCache(showProgressDialog)
        }
    }

    /**
     * 验证用于磁盘缓存的 HTML 内容。
     */
    private fun isDiskCacheHtmlValid(htmlContent: String?): Boolean {
        if (htmlContent.isNullOrBlank()) {
            Log.w(logTag, "DiskCache Validation FAILED: HTML content is null or blank.")
            return false
        }
        if (htmlContent.length < 100) {
            Log.w(
                logTag,
                "DiskCache Validation FAILED: HTML content is too short (${htmlContent.length} chars)."
            )
            return false
        }
        if (htmlContent.contains("[Error] Content element not found") ||
            htmlContent.contains("[Error] 页面加载失败")
        ) {
            Log.w(logTag, "DiskCache Validation FAILED: HTML content contains known error string.")
            return false
        }
        if (!htmlContent.contains("class=\"message\"") && !htmlContent.contains("class='message'")) {
            Log.w(logTag, "DiskCache Validation FAILED: HTML content missing 'message' class.")
            return false
        }
        return true
    }

    // 后台 WebView 加载完成的回调 (不变)
    private fun handleCacheLoadFinished(
        success: Boolean,
        html: String,
        loadedUrl: String?,
        maxPage: Int
    ) {
        // 从URL中解析出我们刚刚加载的页码
        val pageNum = extractPageNumFromLoadedUrl(loadedUrl)

        // 检查这个页码是否在我们期望的队列中
        if (pageNum == null || !diskCacheQueue.contains(pageNum)) {
            Log.w(
                logTag,
                "DiskCache: WebView loaded $loadedUrl (page $pageNum), which is not the expected page in queue. Ignoring."
            )

            // 检查这是否是一个我们正在等待的重试页面的（可能已超时的）失败回调
            if (diskCacheQueue.isNotEmpty() && pageNum == diskCacheQueue.first()) {
                Log.w(
                    logTag,
                    "DiskCache: ...but it *is* the current page. We must process this failure."
                )
                // 允许失败逻辑继续
            } else {
                return // 否则，安全地忽略
            }
        }

        // 检查并存储AuthorId
        checkAndStoreAuthorId(loadedUrl)

        val isContentValid = success && isDiskCacheHtmlValid(html)

        // 检查加载是否成功
        if (isContentValid) {
            Log.i(
                logTag,
                "DiskCache: Successfully fetched page $pageNum. Saving to disk & memory..."
            )
            diskCacheRetries.remove(pageNum)
            // 启动一个IO协程来保存
            viewModelScope.launch(Dispatchers.IO) {
                // 准备数据
                val cacheData = CacheData(
                    cachedPageNum = pageNum,
                    htmlContent = html,
                    maxPageNum = maxPage,
                    authorId = currentAuthorId
                )
                // 保存到磁盘
                localCache.savePage(url, pageNum, cacheData, diskCacheIncludeImages)
                // 同时保存到内存 (以便下次读取时更快)
                CacheUtil.saveCache(url, cacheData)

                // 回到主线程更新状态
                withContext(Dispatchers.Main) {
                    _cachedPages.value += pageNum // 更新UI状态
                    diskCacheQueue.remove(pageNum) // 从队列中移除
                    loadNextPageForDiskCache(false) // 触发下一页缓存
                }
            }
        } else {
            // 加载失败
            val currentRetries = diskCacheRetries.getOrDefault(pageNum, 0)
            if (currentRetries < MAX_CACHE_RETRIES) {
                // 还可以重试
                diskCacheRetries[pageNum] = currentRetries + 1
                Log.w(
                    logTag,
                    "DiskCache: Failed to fetch page $pageNum. Retrying in ${CACHE_RETRY_DELAY_MS}ms... (Attempt ${currentRetries + 1}/${MAX_CACHE_RETRIES + 1})"
                )

                // 重新构建失败的 URL
                var urlToReload = loadedUrl
                if (urlToReload == null || !urlToReload.contains("page=")) {
                    // 如果 URL 损坏或丢失，从队列重建
                    urlToReload = "${RequestConfig.BASE_URL}/${this.url}&page=$pageNum"
                    if (currentAuthorId != null) {
                        urlToReload += "&authorid=$currentAuthorId"
                    }
                }
                // 启动一个带延迟的新协程来触发 WebView 重新加载
                viewModelScope.launch(Dispatchers.Main) {
                    delay(CACHE_RETRY_DELAY_MS)
                    // 检查在延迟期间缓存任务是否已被用户取消
                    if (_isDiskCaching.value && diskCacheQueue.contains(pageNum)) {
                        Log.i(logTag, "DiskCache: Retrying load for $urlToReload")
                        cacheWebView?.loadUrl(urlToReload)
                    } else {
                        Log.i(
                            logTag,
                            "DiskCache: Retry for page $pageNum aborted (caching stopped or page removed)."
                        )
                    }
                }
            } else {
                // 达到最大重试次数
                Log.e(
                    logTag,
                    "DiskCache: Failed to fetch page $pageNum after ${MAX_CACHE_RETRIES + 1} attempts. Giving up on this page."
                )

                // 清理状态
                diskCacheRetries.remove(pageNum)
                _cachedPages.value -= pageNum // 确保UI上不显示为已缓存
                diskCacheQueue.remove(pageNum) // 从队列中移除

                // 触发下一页缓存 (跳过失败的)
                loadNextPageForDiskCache(false)
            }
        }
    }

    // 缓存队列状态机
    private fun loadNextPageForDiskCache(showInitialProgress: Boolean = true) {
        if (!_isDiskCaching.value || diskCacheQueue.isEmpty()) {
            _isDiskCaching.value = false
            // 标记进度为完成 (如果进度条还显示的话)
            if (currentCacheSessionShowsProgress && _cacheProgress.value != null && _cacheProgress.value?.isComplete == false) {
                _cacheProgress.value = _cacheProgress.value?.copy(
                    // 确保进度条显示 100%
                    currentPage = _cacheProgress.value?.totalPages ?: diskCacheTotalPages,
                    isComplete = true
                )
            } else if (!currentCacheSessionShowsProgress) {
                // 如果是静默会话，就在日志中记录完成
                Log.i(logTag, "Silent disk caching complete.")
            } else {
                Log.i(logTag, "Disk caching complete.")
            }
            // 此时可以考虑销毁 cacheWebView 以释放资源，或者保留它以便下次使用
            // 暂时保留
            return
        }

        val pageNum = diskCacheQueue.first() // 获取下一个要缓存的页码
        diskCacheCurrentPage++ // 标记我们开始处理这一页

        // 更新进度条
        if (currentCacheSessionShowsProgress) {
            // 检查是否应该显示
            val shouldShowDialogNow = if (diskCacheCurrentPage == 1) {
                showInitialProgress
            } else {
                true
            }
            if (shouldShowDialogNow) {
                _cacheProgress.value = CacheProgress(
                    totalPages = diskCacheTotalPages,
                    currentPage = diskCacheCurrentPage,
                    currentPageNum = pageNum
                )
            }
        }

        // 检查内存缓存
        CacheUtil.getCache(url, pageNum) { memoryCacheData ->
            if (memoryCacheData != null) {
                // 内存缓存命中：直接保存到磁盘
                Log.i(logTag, "Page $pageNum found in memory. Saving to disk.")
                viewModelScope.launch(Dispatchers.IO) {
                    localCache.savePage(url, pageNum, memoryCacheData, diskCacheIncludeImages)
                    withContext(Dispatchers.Main) {
                        _cachedPages.value += pageNum
                        diskCacheQueue.remove(pageNum) // 从队列中移除
                        loadNextPageForDiskCache(false) // 递归处理下一页
                    }
                }
            } else {
                // 内存缓存未命中：触发 [cacheWebView] 加载
                Log.i(logTag, "Page $pageNum not in memory. Triggering background WebView load.")
                var urlToLoad = "${RequestConfig.BASE_URL}/${this.url}&page=${pageNum}"
                if (currentAuthorId != null) {
                    urlToLoad += "&authorid=$currentAuthorId"
                }
                // 命令后台WebView加载
                viewModelScope.launch(Dispatchers.Main) {
                    cacheWebView?.loadUrl(urlToLoad)
                }
            }
        }
    }

    // 删除缓存页面
    fun deleteCachedPages(pagesToDelete: Set<Int>) {
        viewModelScope.launch {
            try {
                Log.i(logTag, "Deleting ${pagesToDelete.size} cached pages")
                pagesToDelete.forEach { pageNum ->
                    localCache.deletePage(url, pageNum)
                }
                _cachedPages.value -= pagesToDelete
                Log.i(logTag, "Successfully deleted ${pagesToDelete.size} cached pages")
            } catch (e: Exception) {
                Log.e(logTag, "Failed to delete cached pages", e)
            }
        }
    }

    // 更新缓存页面
    fun updateCachedPages(
        pagesToUpdate: Set<Int>,
        includeImages: Boolean,
        showProgressDialog: Boolean = true
    ) {
        viewModelScope.launch {
            try {
                Log.i(logTag, "Updating ${pagesToUpdate.size} cached pages")
                // 先删除旧缓存
                pagesToUpdate.forEach { pageNum ->
                    localCache.deletePage(url, pageNum)
                }
                _cachedPages.value -= pagesToUpdate
                // 重新缓存
                startCaching(pagesToUpdate, includeImages, showProgressDialog)
            } catch (e: Exception) {
                Log.e(logTag, "Failed to update cached pages", e)
            }
        }
    }

    // 重置缓存进度
    fun resetCacheProgress() {
        // 任何时候调用都隐藏对话框
        _cacheProgress.value = null
        currentCacheSessionShowsProgress = false
        Log.i(logTag, "Cache progress hidden by user. Switching to silent caching.")
    }

    // 重新显示缓存进度
    fun showCacheProgress() {
        // 仅当缓存正在运行且进度条被隐藏时
        if (_isDiskCaching.value && _cacheProgress.value == null) {
            currentCacheSessionShowsProgress = true
            Log.i(logTag, "Cache progress re-enabled by user.")

            // 重新构建进度条状态
            _cacheProgress.value = CacheProgress(
                totalPages = diskCacheTotalPages,
                currentPage = diskCacheCurrentPage,
                currentPageNum = diskCacheQueue.firstOrNull() ?: 0, // 显示当前页或0
                isComplete = false
            )
        }
    }

    // 终止缓存
    fun stopCaching() {
        if (!_isDiskCaching.value) return
        Log.i(logTag, "User requested to stop caching.")
        _isDiskCaching.value = false
        diskCacheQueue.clear()
        // 停止后台 WebView
        viewModelScope.launch(Dispatchers.Main) {
            cacheWebView?.stopLoading()
        }
        // 隐藏进度对话框
        _cacheProgress.value = null
        // 重置进度计数器
        diskCacheTotalPages = 0
        diskCacheCurrentPage = 0
    }

    // ====================上方为缓存功能====================

    fun firstLoad(initUrl: String, initHeight: Dp, initWidth: Dp) {
        viewModelScope.launch {
            url = initUrl
            maxWidth = initWidth
            maxHeight = initHeight

            // 加载缓存状态
            updateCachedPagesFromIndex(localCache.index.value)

            val applySettingsAndLoad = { settings: ReaderSettings? ->
                val bgColor = settings?.backgroundColor?.let {
                    try {
                        Color(it.toColorInt())
                    } catch (_: Exception) {
                        null
                    }
                }

                _uiState.value = _uiState.value.copy(
                    fontSize = settings?.fontSizePx?.let { ValueUtil.pxToSp(it) } ?: 24.sp,
                    lineHeight = settings?.lineHeightPx?.let { ValueUtil.pxToSp(it) } ?: 43.sp,
                    padding = (settings?.paddingDp ?: 16f).dp,
                    nightMode = settings?.nightMode ?: false,
                    backgroundColor = bgColor,
                    loadImages = settings?.loadImages ?: false,
                    isVerticalMode = settings?.isVerticalMode ?: false
                )
                loadWithSettings()
            }

            SettingsUtil.getSettings(
                callback = { settings ->
                    applySettingsAndLoad(settings)
                },
                onNull = {
                    applySettingsAndLoad(null)
                }
            )
            // 监听收藏变化，并在URL变化时检查
            viewModelScope.launch {
                FavoriteUtil.getFavoriteFlow().collect { favorites ->
                    val isFavorited = favorites.any { it.url == url }  // 检查URL是否在收藏列表中
                    _uiState.value = _uiState.value.copy(isFavorited = isFavorited)
                    Log.i(logTag, "当前页面 $url 是否收藏: $isFavorited")
                }
            }
        }
    }

    private fun loadWithSettings() {
        viewModelScope.launch {
            FavoriteUtil.getFavoriteMap { favMap ->
                val favorite = favMap[url]
                val targetView = favorite?.lastView ?: 1
                val targetPageNum = favorite?.lastPage ?: 0
                currentAuthorId = favorite?.authorId

                val targetIndex: Int
                if (_uiState.value.isVerticalMode) {
                    val avgItemsPerPage = getAvgItemsPerHorizontalPage()
                    targetIndex = (targetPageNum * avgItemsPerPage)
                } else {
                    targetIndex = targetPageNum
                }

                // 优先检查本地缓存
                viewModelScope.launch {
                    val localCacheData = localCache.loadPage(url, targetView)

                    if (localCacheData != null) {
                        // 本地缓存命中
                        Log.i(logTag, "Local cache hit for page $targetView")

                        if (currentAuthorId == null && localCacheData.authorId != null) {
                            currentAuthorId = localCacheData.authorId
                        }

                        _uiState.value = _uiState.value.copy(
                            currentView = targetView,
                            maxWebView = localCacheData.maxPageNum
                        )

                        loadFinished(
                            success = true,
                            localCacheData.htmlContent,
                            null,
                            localCacheData.maxPageNum,
                            isFromCache = true,
                            cacheTargetIndex = targetIndex
                        )
                        return@launch
                    }

                    // 本地缓存未命中，检查内存缓存
                    CacheUtil.getCache(url, targetView) { cacheData ->
                        if (cacheData != null) {
                            Log.i(logTag, "Memory cache hit for page $targetView")

                            if (currentAuthorId == null && cacheData.authorId != null) {
                                currentAuthorId = cacheData.authorId
                            }

                            _uiState.value = _uiState.value.copy(
                                currentView = targetView,
                                maxWebView = cacheData.maxPageNum
                            )

                            loadFinished(
                                success = true,
                                cacheData.htmlContent,
                                null,
                                cacheData.maxPageNum,
                                isFromCache = true,
                                cacheTargetIndex = targetIndex
                            )
                        } else {
                            // 内存缓存也未命中，从网络加载
                            Log.i(logTag, "Cache miss. Loading page $targetView from network.")

                            _uiState.value = _uiState.value.copy(
                                currentView = targetView,
                                initPage = targetIndex
                            )

                            loadFromNetwork(targetView)
                        }
                    }
                }
            }
        }
    }

    fun onSetView(view: Int, forceReload: Boolean = false) {
        if (view == _uiState.value.currentView && !isTransitioning && !forceReload) {
            Log.i(logTag, "Already on view $view. Ignoring.")
            return
        }

        if (view == _uiState.value.currentView + 1 && nextHtmlList != null && !forceReload) {
            Log.i(logTag, "Using preloaded content for view $view")
            isTransitioning = true

            _uiState.value = _uiState.value.copy(
                htmlList = nextHtmlList!!,
                chapterList = nextChapterList ?: listOf(),
                initPage = 0,
                currentPercentage = 0f,
                currentView = view
            )

            nextHtmlList = null
            nextChapterList = null
            latestPage = 0

        } else {
            Log.i(logTag, "Page $view not preloaded. Checking cache...")
            nextHtmlList = null
            nextChapterList = null
            isPreloading = false

            if (forceReload) {
                CacheUtil.clearCacheEntry(url, view)
            }

            viewModelScope.launch {
                if (!forceReload) {
                    // 先检查本地缓存
                    val localCacheData = localCache.loadPage(url, view)

                    if (localCacheData != null && localCacheData.authorId == currentAuthorId) {
                        // 本地缓存命中
                        Log.i(
                            logTag,
                            "Local cache hit for page $view. Loading from local cache."
                        )
                        isTransitioning = true

                        _uiState.value = _uiState.value.copy(
                            currentView = view,
                            initPage = 0,
                            currentPercentage = 0f,
                            maxWebView = localCacheData.maxPageNum
                        )

                        loadFinished(
                            success = true,
                            localCacheData.htmlContent,
                            null,
                            localCacheData.maxPageNum,
                            isFromCache = true,
                            cacheTargetIndex = 0
                        )
                        return@launch
                    }

                    // 本地缓存未命中，检查内存缓存
                    CacheUtil.getCache(url, view) { cacheData ->
                        viewModelScope.launch {
                            if (cacheData != null && cacheData.authorId == currentAuthorId) {
                                // 内存缓存命中
                                Log.i(
                                    logTag,
                                    "Memory cache hit for page $view. Loading from memory cache."
                                )
                                isTransitioning = true

                                _uiState.value = _uiState.value.copy(
                                    currentView = view,
                                    initPage = 0,
                                    currentPercentage = 0f,
                                    maxWebView = cacheData.maxPageNum
                                )

                                loadFinished(
                                    success = true,
                                    cacheData.htmlContent,
                                    null,
                                    cacheData.maxPageNum,
                                    isFromCache = true,
                                    cacheTargetIndex = 0
                                )
                            } else {
                                // 缓存未命中
                                Log.i(
                                    logTag,
                                    "Cache miss for page $view. Loading from network."
                                )
                                loadFromNetwork(view)
                                isTransitioning = true
                            }
                        }
                    }
                } else {
                    Log.i(logTag, "Force reloading page $view from network.")
                    loadFromNetwork(view)
                    isTransitioning = true
                }
            }
        }
    }

    private fun getAvgItemsPerHorizontalPage(): Int {
        val state = _uiState.value
        val topPadding = 24.dp
        val footerHeight = 50.dp
        val pageContentHeight = maxHeight - topPadding - footerHeight
        val pageContentHeightPx = ValueUtil.dpToPx(pageContentHeight)
        val lineHeightPx = ValueUtil.spToPx(state.lineHeight)
        return (pageContentHeightPx / lineHeightPx).toInt().coerceAtLeast(1)
    }

    // loadFromNetwork
    private fun loadFromNetwork(view: Int) {
        var urlToLoad = "${RequestConfig.BASE_URL}/${this.url}&page=${view}"
        if (currentAuthorId != null) {
            urlToLoad += "&authorid=$currentAuthorId"
        }

        _uiState.value = _uiState.value.copy(
            currentView = view,
            urlToLoad = "about:blank"
        )

        viewModelScope.launch {
            delay(10)
            _uiState.value = _uiState.value.copy(
                urlToLoad = urlToLoad
            )
        }
        showLoadingScrim = true
        isTransitioning = true
    }

    // triggerPreload (只用于UI预加载)
    private fun triggerPreload(targetView: Int, maxView: Int) {
        if (isPreloading) return
        if (targetView > maxView) return

        // 如果正在磁盘缓存，则不要进行UI预加载
        if (_isDiskCaching.value) return

        isPreloading = true
        viewBeingPreloaded = targetView

        var urlToLoad = "${RequestConfig.BASE_URL}/${this.url}&page=${targetView}"
        if (currentAuthorId != null) {
            urlToLoad += "&authorid=$currentAuthorId"
        }

        Log.i(logTag, "Triggering UI preload for page $targetView")
        _uiState.value = _uiState.value.copy(
            urlToLoad = "about:blank"
        )

        viewModelScope.launch {
            delay(10)
            _uiState.value = _uiState.value.copy(
                urlToLoad = urlToLoad
            )
        }
    }

    // loadFinished
    fun loadFinished(
        success: Boolean,
        html: String,
        loadedUrl: String?,
        maxPage: Int,
        isFromCache: Boolean = false,
        cacheTargetIndex: Int = 0
    ) {
        viewModelScope.launch {
            val loadedPageNum = if (isFromCache) {
                _uiState.value.currentView
            } else {
                extractPageNumFromLoadedUrl(loadedUrl)
            }

            if (!success) {
                _uiState.value = _uiState.value.copy(
                    isError = true,
                    htmlList = emptyList(),
                    maxWebView = maxPage
                )
                showLoadingScrim = false
                isTransitioning = false
                return@launch
            }

            if (!isFromCache) {
                checkAndStoreAuthorId(loadedUrl)
                _uiState.value = _uiState.value.copy(maxWebView = maxPage)
            }

            val (passages, chapters) = withContext(Dispatchers.Default) {
                parseHtmlToContent(html)
                paginateContent(isFromCache)
            }

            if (isPreloading) {
                if (loadedPageNum != viewBeingPreloaded) {
                    Log.w(
                        logTag,
                        "loadFinished: Received stale preload for $loadedPageNum, expecting $viewBeingPreloaded. Ignoring."
                    )
                    if (loadedPageNum != _uiState.value.currentView) {
                        isPreloading = false
                    }
                    return@launch
                }

                isPreloading = false

                val pageNumToCache = viewBeingPreloaded
                Log.i(logTag, "Caching page $pageNumToCache to *memory* for $url")
                val dataToCache = CacheData(
                    cachedPageNum = pageNumToCache,
                    htmlContent = html,
                    maxPageNum = maxPage,
                    authorId = currentAuthorId
                )
                // 只保存到内存
                CacheUtil.saveCache(url, dataToCache)

                nextHtmlList = passages
                nextChapterList = chapters

                val currentList = _uiState.value.htmlList
                if (currentList.isNotEmpty()) {
                    val modifiedList = currentList.dropLast(1).toMutableList()
                    modifiedList.add(Content("...下一页 (网页)", ContentType.TEXT, "footer"))
                    _uiState.value = _uiState.value.copy(htmlList = modifiedList)
                }

            } else {
                // 正常加载 (非预加载，非磁盘缓存)
                if (!isFromCache && loadedPageNum != null && loadedPageNum != _uiState.value.currentView) {
                    Log.w(
                        logTag,
                        "loadFinished: Received stale content for $loadedPageNum, but currentView is ${_uiState.value.currentView}. Ignoring."
                    )
                    return@launch
                }
                if (!isFromCache) {
                    val pageNumToCache = _uiState.value.currentView
                    Log.i(logTag, "Caching current page $pageNumToCache to *memory* for ${url}")
                    val dataToCache = CacheData(
                        cachedPageNum = pageNumToCache,
                        htmlContent = html,
                        maxPageNum = maxPage,
                        authorId = currentAuthorId
                    )
                    // 只保存到内存
                    CacheUtil.saveCache(url, dataToCache)
                }

                val newInitPage = if (isFromCache) {
                    cacheTargetIndex
                } else if (initialized) {
                    0
                } else {
                    _uiState.value.initPage
                }

                val totalItems = passages.size.coerceAtLeast(1)
                val safeInitPage = newInitPage.coerceIn(0, (totalItems - 1).coerceAtLeast(0))
                val newPercent = (safeInitPage.toFloat() / totalItems) * 100f

                _uiState.value = _uiState.value.copy(
                    htmlList = passages,
                    chapterList = chapters,
                    initPage = safeInitPage,
                    currentPercentage = newPercent,
                    maxWebView = maxPage,
                    isError = false
                )

                if (!initialized) {
                    initialized = true
                }
                latestPage = safeInitPage
                if (!isFromCache) {
                    showLoadingScrim = false
                }
                // 无论是否来自缓存，只要是 '正常加载' 完成，就应该结束 'Transition' 状态
                isTransitioning = false
            }
        }
    }

    fun retryLoad() {
        Log.i(logTag, "Retrying load for view ${uiState.value.currentView}")
        viewModelScope.launch {
            showLoadingScrim = true
            _uiState.value = _uiState.value.copy(
                isError = false,
                urlToLoad = "about:blank"
            )
            delay(10)
            loadFromNetwork(uiState.value.currentView)
        }
    }

    private fun checkAndStoreAuthorId(loadedUrl: String?) {
        if (loadedUrl == null) return
        if (currentAuthorId != null) return

        val extractedAuthorId = loadedUrl.substringAfter("authorid=", "").substringBefore("&")

        if (extractedAuthorId.isNotBlank()) {
            Log.i(logTag, "Discovered and storing new authorId $extractedAuthorId for ${this.url}")
            currentAuthorId = extractedAuthorId
            val baseUrl = this.url

            viewModelScope.launch {
                FavoriteUtil.getFavoriteMap { map ->
                    map[baseUrl]?.let { favorite ->
                        if (favorite.authorId == null) {
                            FavoriteUtil.updateFavorite(favorite.copy(authorId = extractedAuthorId))
                        }
                    }
                }
            }
        }
    }

    private fun parseHtmlToContent(html: String) {
        rawContentList.clear()
        val doc = Jsoup.parse(html)
        doc.getElementsByTag("i").forEach { it.remove() }

        for (node in doc.getElementsByClass("message")) {
            val rawText = HTMLUtil.toText(node.html())
            val chapterTitle: String? = rawText.lines()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.take(30)

            if (rawText.isNotBlank()) {
                rawContentList.add(Content(rawText, ContentType.TEXT, chapterTitle))
            }

            if (_uiState.value.loadImages) {
                for (element in node.getElementsByTag("img")) {
                    val src = element.attribute("src").value
                    if (!src.startsWith("http://") && !src.startsWith("httpsH")) {
                        rawContentList.add(
                            Content(
                                "${RequestConfig.BASE_URL}/${src}",
                                ContentType.IMG,
                                chapterTitle
                            )
                        )
                    } else {
                        rawContentList.add(Content(src, ContentType.IMG, chapterTitle))
                    }
                }
            }
        }
    }

    private fun paginateContent(isFromCache: Boolean = false): Pair<List<Content>, List<ChapterInfo>> {
        val contentSnapshot = rawContentList.toList()
        val state = _uiState.value

        val passages: List<Content>

        if (state.isVerticalMode) {
            val pageContentWidth = maxWidth - (state.padding + state.padding)
            val lines = TextUtil.pagingTextVertical(
                rawContentList = contentSnapshot,
                width = pageContentWidth,
                fontSize = state.fontSize,
                letterSpacing = state.letterSpacing
            ).toMutableList()

            if (isTransitioning) {
                // 正在转场中
            } else if (isFromCache) {
                lines.add(Content("正在加载下一页...", ContentType.TEXT, "footer"))
            } else if (nextHtmlList != null) {
                lines.add(Content("...下一页", ContentType.TEXT, "footer"))
            } else if (uiState.value.currentView < uiState.value.maxWebView) {
                lines.add(Content("正在加载下一页...", ContentType.TEXT, "footer"))
            } else {
                lines.add(Content("...没有更多了", ContentType.TEXT, "footer"))
            }
            passages = lines

        } else {
            val passagesList = ArrayList<Content>()
            val topPadding = 24.dp
            val footerHeight = 50.dp
            val pageContentHeight = maxHeight - topPadding - footerHeight
            val pageContentWidth = maxWidth - (state.padding + state.padding)

            for (content in contentSnapshot) {
                if (content.type == ContentType.TEXT) {
                    val pagedText = TextUtil.pagingText(
                        content.data,
                        pageContentHeight,
                        pageContentWidth,
                        state.fontSize,
                        state.letterSpacing,
                        state.lineHeight,
                    )
                    for (t in pagedText) {
                        passagesList.add(Content(t, ContentType.TEXT, content.chapterTitle))
                    }
                } else if (content.type == ContentType.IMG) {
                    passagesList.add(content)
                }
            }

            if (isTransitioning) {
                // 正在转场中
            } else if (isFromCache) {
                passagesList.add(Content("正在加载下一页...", ContentType.TEXT, "footer"))
            } else if (nextHtmlList != null) {
                passagesList.add(Content("...下一页", ContentType.TEXT, "footer"))
            } else if (uiState.value.currentView < uiState.value.maxWebView) {
                passagesList.add(Content("正在加载下一页...", ContentType.TEXT, "footer"))
            } else {
                passagesList.add(Content("...没有更多了", ContentType.TEXT, "footer"))
            }
            passages = passagesList
        }

        val chapterList = mutableListOf<ChapterInfo>()
        var lastTitle: String? = null
        passages.forEachIndexed { index, content ->
            if (content.chapterTitle != null && content.chapterTitle != lastTitle && content.chapterTitle != "footer") {
                chapterList.add(ChapterInfo(title = content.chapterTitle, startIndex = index))
                lastTitle = content.chapterTitle
            }
        }

        return Pair(passages, chapterList)
    }

    private fun processPageChange(newPage: Int) {
        val oldPage = latestPage
        val state = _uiState.value
        val list = state.htmlList

        if (list.isNotEmpty() && newPage < list.size && oldPage >= 0 && oldPage < list.size) {
            val oldChapter = list[oldPage].chapterTitle
            val newChapter = list[newPage].chapterTitle

            if (newChapter != null && newChapter != oldChapter) {
                saveHistory(newPage)
            } else if (!state.isVerticalMode) {
                if (!isTransitioning) {
                    saveHistory(newPage)
                }
            }
        }

        val listSize = list.size

        if (listSize > 0 &&
            newPage == listSize - 1 &&
            nextHtmlList != null &&
            !isTransitioning
        ) {
            isTransitioning = true
            val newCurrentView = state.currentView + 1

            Log.i(logTag, "Switching to preloaded page $newCurrentView")

            _uiState.value = state.copy(
                htmlList = nextHtmlList!!,
                chapterList = nextChapterList ?: listOf(),
                initPage = 0,
                currentPercentage = 0f,
                currentView = newCurrentView
            )

            nextHtmlList = null
            nextChapterList = null
            latestPage = 0

            return
        }

        val lastContentPageIndex = (listSize - 2).coerceAtLeast(0)
        val threshold =
            if (state.isVerticalMode) PRELOAD_THRESHOLD_VERTICAL else PRELOAD_THRESHOLD_HORIZONTAL
        val triggerPageIndex = (lastContentPageIndex - threshold).coerceAtLeast(0)

        if (listSize > 0 &&
            !isPreloading &&
            nextHtmlList == null &&
            state.currentView < state.maxWebView &&
            !isTransitioning &&
            newPage >= triggerPageIndex
        ) {
            val viewToPreload = state.currentView + 1
            Log.i(logTag, "newPage $newPage triggerPageIndex $triggerPageIndex")
            Log.i(logTag, "Preloading view $viewToPreload")
            triggerPreload(viewToPreload, state.maxWebView)
        }

        latestPage = newPage
    }

    fun onPageChange(curPagerState: PagerState, scope: CoroutineScope) {
        if (pagerState == null) {
            pagerState = curPagerState
        }
        if (compositionScope == null) {
            compositionScope = scope
        }

        val newPage = curPagerState.targetPage

        if (isTransitioning) {
            val isSettledAtInit =
                curPagerState.settledPage == _uiState.value.initPage && newPage == _uiState.value.initPage

            // 检查用户是否中断了滚动：
            // 1. 滚动已停止 (!isScrollInProgress)
            // 2. 停止的页面 *不是* 我们期望的 initPage
            val userInterrupted = !curPagerState.isScrollInProgress &&
                    curPagerState.settledPage != _uiState.value.initPage &&
                    curPagerState.settledPage == newPage // 确保已稳定

            if (isSettledAtInit) {
                // 这是 "成功" 的转场
                Log.i(logTag, "Transition complete. Settled at page ${_uiState.value.initPage}")
                isTransitioning = false
                latestPage = _uiState.value.initPage
                // 此处不能 return，需要让下面的逻辑（processPageChange）执行
            } else if (userInterrupted) {
                // 这是 "被用户中断" 的转场
                Log.w(
                    logTag,
                    "User interrupted transition. Settled at page $newPage. Ending transition."
                )
                isTransitioning = false
                latestPage = newPage // 将 'latestPage' 更新为用户选择的页面
            } else {
                // 转场仍在进行中
                // 此时不应处理页面变更逻辑（如预加载）
                // 仅重置缩放
                if (curPagerState.settledPage != curPagerState.targetPage && _uiState.value.scale != 1f) {
                    _uiState.value = _uiState.value.copy(scale = 1f, offset = Offset(0f, 0f))
                }
                return
            }
        }


        val list = _uiState.value.htmlList

        if (list.isEmpty() || newPage >= list.size) {
            if (curPagerState.settledPage != curPagerState.targetPage && _uiState.value.scale != 1f) {
                _uiState.value = _uiState.value.copy(scale = 1f, offset = Offset(0f, 0f))
            }
            return
        }

        // 仅在页面真正改变时才处理 (修复了 isTransitioning 逻辑后，需要在这里加一个检查)
        if (newPage == latestPage) {
            if (curPagerState.settledPage != curPagerState.targetPage && _uiState.value.scale != 1f) {
                _uiState.value = _uiState.value.copy(scale = 1f, offset = Offset(0f, 0f))
            }
            return
        }

        val totalPages = curPagerState.pageCount.coerceAtLeast(1)
        val percent = (newPage.toFloat() / totalPages) * 100f
        _uiState.value = _uiState.value.copy(currentPercentage = percent)

        processPageChange(newPage)

        if (curPagerState.settledPage != curPagerState.targetPage && _uiState.value.scale != 1f) {
            _uiState.value = _uiState.value.copy(scale = 1f, offset = Offset(0f, 0f))
        }
    }

    fun onVerticalPageSettled(newPage: Int) {
        if (isTransitioning) {
            // 只要它在转场期间稳定下来，就认为转场结束
            Log.i(
                logTag,
                "Transition complete (Vertical). Settled at page $newPage"
            )
            isTransitioning = false
            latestPage = newPage
        }

        if (newPage == latestPage) return

        val totalRows = _uiState.value.htmlList.size.coerceAtLeast(1)
        val percent = (newPage.toFloat() / totalRows) * 100f
        _uiState.value = _uiState.value.copy(currentPercentage = percent)

        processPageChange(newPage)
    }

    /**
     * 强制刷新当前正在查看的网页页面。
     * 1. 清除内存缓存。
     * 2. 触发UI刷新（将从网络重新加载并存入内存）。
     * 3. 检查该页面是否已存在于 *磁盘* 缓存中：
     * - 如果是，则启动后台任务以替换磁盘缓存。
     * - 如果否，则不执行任何磁盘操作。
     */
    fun forceRefreshCurrentPage() {
        val pageToRefresh = _uiState.value.currentView
        val novelUrl = this.url

        if (novelUrl.isBlank()) {
            Log.w(logTag, "Cannot refresh, URL is blank.")
            return
        }

        Log.i(logTag, "Force refreshing... URL: $novelUrl, Page: $pageToRefresh")

        // 1. 清除内存缓存
        CacheUtil.clearCacheEntry(novelUrl, pageToRefresh)

        // 2. 触发UI重载
        viewModelScope.launch {
            // 清理预加载状态 (从 onSetView(forceReload=true) 中提取)
            nextHtmlList = null
            nextChapterList = null
            isPreloading = false

            showLoadingScrim = true
            _uiState.value = _uiState.value.copy(
                isError = false, // 清除错误状态
                urlToLoad = "about:blank"
            )
            delay(10) // 等待 recomposition
            loadFromNetwork(pageToRefresh) // 加载真实 URL, 这会设置 isTransitioning = true
        }

        // 根据用户请求，检查是否需要更新本地缓存
        if (_cachedPages.value.contains(pageToRefresh)) {
            Log.i(
                logTag,
                "Page $pageToRefresh is locally cached. Triggering disk cache update."
            )
            // 触发磁盘缓存的“更新”（删除旧的，然后重新下载并保存）
            updateCachedPages(setOf(pageToRefresh), false, showProgressDialog = false)
        } else {
            Log.i(
                logTag,
                "Page $pageToRefresh is not locally cached. Skipping disk cache update."
            )
        }
    }

    private fun saveHistory(pageToSave: Int) {
        val currentList = _uiState.value.htmlList
        var currentChapter: String? = null

        if (pageToSave >= 0 && pageToSave < currentList.size) {
            currentChapter = currentList[pageToSave].chapterTitle
        }

        val state = _uiState.value
        val valueToSave: Int

        if (state.isVerticalMode) {
            val avgItemsPerPage = getAvgItemsPerHorizontalPage()
            valueToSave = (pageToSave.toFloat() / avgItemsPerPage.toFloat()).toInt()
        } else {
            valueToSave = pageToSave
        }

        FavoriteUtil.getFavoriteMap {
            it[url]?.let { it1 ->
                FavoriteUtil.updateFavorite(
                    it1.copy(
                        lastPage = valueToSave,
                        lastView = uiState.value.currentView,
                        lastChapter = currentChapter,
                        authorId = this.currentAuthorId
                    )
                )
            }
        }
    }

    private fun saveCurrentSettings() {
        val state = _uiState.value
        val backgroundColorString = state.backgroundColor?.let {
            String.format("#%08X", it.toArgb())
        }
        val settings = ReaderSettings(
            ValueUtil.spToPx(state.fontSize),
            ValueUtil.spToPx(state.lineHeight),
            state.padding.value,
            state.nightMode,
            backgroundColorString,
            state.loadImages,
            state.isVerticalMode
        )
        SettingsUtil.saveSettings(settings)
    }

    fun saveSettings(currentPage: Int) {
        saveCurrentSettings()

        viewModelScope.launch {
            showLoadingScrim = true

            val oldPageCount = _uiState.value.htmlList.size.coerceAtLeast(1)
            val oldPercent = currentPage.toFloat() / oldPageCount

            val (oldChapterTitle, oldItemInChapter) = if (currentPage >= 0 && currentPage < oldPageCount) {
                val oldChapterTitle = _uiState.value.htmlList[currentPage].chapterTitle
                val oldChapterStartIndex =
                    _uiState.value.chapterList.find { it.title == oldChapterTitle }?.startIndex ?: 0
                val oldItemInChapter = (currentPage - oldChapterStartIndex).coerceAtLeast(0)
                Pair(oldChapterTitle, oldItemInChapter)
            } else {
                Pair(null, 0)
            }

            val (newPages, newChapters) = withContext(Dispatchers.Default) {
                paginateContent()
            }

            val newPageCount = newPages.size.coerceAtLeast(1)

            val pageToScrollTo = if (oldChapterTitle != null) {
                val newChapterStartIndex =
                    newChapters.find { it.title == oldChapterTitle }?.startIndex ?: 0

                (newChapterStartIndex + oldItemInChapter).coerceIn(
                    0,
                    (newPageCount - 1).coerceAtLeast(0)
                )
            } else {
                (oldPercent * newPageCount).toInt().coerceIn(
                    0,
                    (newPageCount - 1).coerceAtLeast(0)
                )
            }

            val newPercent = (pageToScrollTo.toFloat() / newPageCount) * 100f

            if (pageToScrollTo == 0) {
                _uiState.value = _uiState.value.copy(scale = 1f, offset = Offset(0f, 0f))
            }

            _uiState.value = _uiState.value.copy(
                htmlList = newPages,
                chapterList = newChapters,
                initPage = pageToScrollTo,
                currentPercentage = newPercent,
                isError = false
            )
            showLoadingScrim = false
            isTransitioning = false
        }
    }

    fun setReadingMode(isVertical: Boolean, currentPage: Int) {
        if (isVertical == _uiState.value.isVerticalMode) return
        _uiState.value = _uiState.value.copy(
            isVerticalMode = isVertical,
            initPage = currentPage
        )
        saveSettings(currentPage)
    }

    fun onTransform(pan: Offset, zoom: Float) {
        val scale = (_uiState.value.scale * zoom).coerceIn(0.5f, 3f)
        val offset = if (scale == 1f) Offset(0f, 0f) else _uiState.value.offset + pan
        _uiState.value = _uiState.value.copy(scale = scale, offset = offset)
    }

    fun toggleChapterDrawer(show: Boolean) {
        _uiState.value = _uiState.value.copy(showChapterDrawer = show)
    }

    fun onSetFontSize(fontSize: TextUnit) {
        val newMinLineHeight = (fontSize.value * 1.5f).sp
        val currentLineHeight = _uiState.value.lineHeight

        if (currentLineHeight < newMinLineHeight) {
            _uiState.value = _uiState.value.copy(
                fontSize = fontSize,
                lineHeight = newMinLineHeight
            )
        } else {
            _uiState.value = _uiState.value.copy(fontSize = fontSize)
        }
    }

    fun onSetLineHeight(lineHeight: TextUnit) {
        val currentFontSizeValue = _uiState.value.fontSize.value
        val newMinLineHeightValue = currentFontSizeValue * 1.5f
        val coercedLineHeightValue = lineHeight.value.coerceIn(
            minimumValue = newMinLineHeightValue,
            maximumValue = 100.0f
        )
        _uiState.value = _uiState.value.copy(
            lineHeight = coercedLineHeightValue.sp
        )
    }

    fun onSetPadding(padding: Dp) {
        _uiState.value = _uiState.value.copy(padding = padding)
    }

    fun toggleNightMode(isNight: Boolean) {
        _uiState.value = _uiState.value.copy(
            nightMode = isNight,
            backgroundColor = null
        )
        saveCurrentSettings()
    }

    fun toggleLoadImages(load: Boolean) {
        _uiState.value = _uiState.value.copy(loadImages = load)
        saveCurrentSettings()
        val currentPage = latestPage
        _uiState.value = _uiState.value.copy(initPage = currentPage)
        onSetView(uiState.value.currentView, forceReload = true)
    }

    fun onSetBackgroundColor(color: Color?) {
        _uiState.value = _uiState.value.copy(
            backgroundColor = color,
            nightMode = false
        )
    }

    override fun onCleared() {
        if (initialized) {
            saveHistory(latestPage)
        }
        nextHtmlList = null
        nextChapterList = null
        isPreloading = false

        // 销毁后台 WebView
        viewModelScope.launch(Dispatchers.Main) {
            cacheWebView?.stopLoading()
            cacheWebView?.destroy()
            cacheWebView = null
            cacheWebViewClient = null
            Log.i(logTag, "Background cache WebView destroyed in onCleared.")
        }

        super.onCleared()
    }
}