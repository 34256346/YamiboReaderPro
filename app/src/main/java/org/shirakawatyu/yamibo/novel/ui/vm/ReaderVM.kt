package org.shirakawatyu.yamibo.novel.ui.vm

import android.util.Log
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.shirakawatyu.yamibo.novel.bean.Content
import org.shirakawatyu.yamibo.novel.bean.ContentType
import org.shirakawatyu.yamibo.novel.bean.ReaderSettings
import org.shirakawatyu.yamibo.novel.constant.RequestConfig
import org.shirakawatyu.yamibo.novel.ui.state.ChapterInfo
import org.shirakawatyu.yamibo.novel.ui.state.ReaderState
import org.shirakawatyu.yamibo.novel.util.CacheData
import org.shirakawatyu.yamibo.novel.util.CacheUtil
import org.shirakawatyu.yamibo.novel.util.FavoriteUtil
import org.shirakawatyu.yamibo.novel.util.HTMLUtil
import org.shirakawatyu.yamibo.novel.util.SettingsUtil
import org.shirakawatyu.yamibo.novel.util.TextUtil
import org.shirakawatyu.yamibo.novel.util.ValueUtil
import kotlin.math.floor

class ReaderVM : ViewModel() {
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

    // 转场动画状态，标识是否正在进行页面转场
    var isTransitioning by mutableStateOf(false)

    // 加载遮罩显示状态，控制是否显示加载提示
    var showLoadingScrim by mutableStateOf(false)
        private set

    // 存储未分页的原始数据
    private val rawContentList = ArrayList<Content>()

    // 最新的页面索引
    private var latestPage: Int = 0

    // 当前作者ID
    private var currentAuthorId: String? = null

    // 预加载状态标记，标识是否正在进行预加载操作
    private var isPreloading = false

    // 预加载阈值，当距离页面底部还有多少距离时触发预加载
    private val PRELOAD_THRESHOLD = 50

    // 正在预加载的视图索引
    private var viewBeingPreloaded = 0

    // 下一页的HTML数据
    private var nextHtmlList: List<Content>? = null

    // 下一章的章节信息
    private var nextChapterList: List<ChapterInfo>? = null

    init {
        Log.i(logTag, "VM created.")
    }

    fun firstLoad(initUrl: String, initHeight: Dp, initWidth: Dp) {
        viewModelScope.launch {
            url = initUrl
            maxWidth = initWidth
            maxHeight = initHeight

            val applySettingsAndLoad = { settings: ReaderSettings? ->
                // v-- 新增背景颜色加载逻辑 --v
                val bgColor = settings?.backgroundColor?.let {
                    try {
                        Color(android.graphics.Color.parseColor(it))
                    } catch (e: Exception) {
                        null // 解析失败则使用默认
                    }
                }

                _uiState.value = _uiState.value.copy(
                    fontSize = settings?.fontSizePx?.let { ValueUtil.pxToSp(it) } ?: 24.sp,
                    lineHeight = settings?.lineHeightPx?.let { ValueUtil.pxToSp(it) } ?: 43.sp,
                    padding = (settings?.paddingDp ?: 16f).dp,
                    nightMode = settings?.nightMode ?: false,
                    backgroundColor = bgColor // <-- 新增
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
        }
    }

    // 加载页面数据
    private fun loadWithSettings() {
        viewModelScope.launch {
            // 获取收藏栏保存的数据
            FavoriteUtil.getFavoriteMap { favMap ->
                val favorite = favMap[url]
                val targetView = favorite?.lastView ?: 1
                val targetPage = favorite?.lastPage ?: 0
                currentAuthorId = favorite?.authorId
                // 从缓存中获取页面数据
                CacheUtil.getCache(url, targetView) { cacheData ->
                    if (cacheData != null &&
                        cacheData.authorId == currentAuthorId
                    ) {
                        // 缓存命中：使用缓存数据更新UI状态并完成加载
                        Log.i(logTag, "Cache hit for page $targetView")

                        _uiState.value = _uiState.value.copy(
                            currentView = targetView,
                            initPage = targetPage,
                            maxWebView = cacheData.maxPageNum
                        )
                        currentAuthorId = cacheData.authorId

                        loadFinished(
                            success = true,
                            cacheData.htmlContent,
                            null,
                            cacheData.maxPageNum,
                            isFromCache = true
                        )
                    } else {
                        // 缓存未命中：从网络加载数据并更新UI状态
                        Log.i(logTag, "Cache miss. Loading page $targetView from network.")

                        _uiState.value = _uiState.value.copy(
                            currentView = targetView,
                            initPage = targetPage
                        )

                        loadFromNetwork(targetView)
                    }
                }
            }
        }
    }

    // 从网络加载数据
    private fun loadFromNetwork(view: Int) {
        var urlToLoad = "${RequestConfig.BASE_URL}/${this.url}&page=${view}"
        // 如果本地有保存数据，则会获取到作者ID，添加到URL中，直接加载“只看楼主”界面
        if (currentAuthorId != null) {
            urlToLoad += "&authorid=$currentAuthorId"
        }

        _uiState.value = _uiState.value.copy(
            currentView = view,
            urlToLoad = urlToLoad
        )
        showLoadingScrim = true
        isTransitioning = true
    }

    // 触发预加载
    private fun triggerPreload(targetView: Int, maxView: Int) {
        // 检查是否正在预加载或目标页码超过最大页码
        if (isPreloading) return
        if (targetView > maxView) return
        // 设置预加载状态和预加载的页码
        isPreloading = true
        viewBeingPreloaded = targetView
        // 构建预加载的URL
        var urlToLoad = "${RequestConfig.BASE_URL}/${this.url}&page=${targetView}"
        if (currentAuthorId != null) {
            urlToLoad += "&authorid=$currentAuthorId"
        }

        Log.i(logTag, "Triggering preload for page $targetView")

        _uiState.value = _uiState.value.copy(
            urlToLoad = urlToLoad
        )
    }

    /**
     * 处理网页加载完成后的逻辑，包括
     * 解析HTML内容
     * 分页处理
     * 缓存管理
     * UI状态更新
     */
    fun loadFinished(
        success: Boolean,
        html: String,
        loadedUrl: String?,
        maxPage: Int,
        isFromCache: Boolean = false
    ) {
        viewModelScope.launch {
            // 如果加载失败
            if (!success) {
                _uiState.value = _uiState.value.copy(
                    isError = true,
                    htmlList = emptyList(),
                    maxWebView = maxPage
                )
                showLoadingScrim = false // 隐藏加载圈
                isTransitioning = false // 确保停止转场
                return@launch
            }
            // 如果不是从缓存加载的内容，更新最大页面数到UI状态中
            if (!isFromCache) {
                checkAndStoreAuthorId(loadedUrl)
                _uiState.value = _uiState.value.copy(maxWebView = maxPage)
            }
            // 解析HTML并进行分页处理
            val (passages, chapters) = withContext(Dispatchers.Default) {
                parseHtmlToContent(html)
                paginateContent(isFromCache)
            }
            // 判断是否处于预加载状态
            if (isPreloading) {
                isPreloading = false

                val pageNumToCache = viewBeingPreloaded
                Log.i(logTag, "Caching page $pageNumToCache for $url")
                val dataToCache = CacheData(
                    cachedPageNum = pageNumToCache,
                    htmlContent = html,
                    maxPageNum = maxPage,
                    authorId = currentAuthorId
                )
                CacheUtil.saveCache(url, dataToCache)
                // 更新下一页的数据列表
                nextHtmlList = passages
                nextChapterList = chapters

                val currentList = _uiState.value.htmlList
                if (currentList.isNotEmpty()) {
                    val modifiedList = currentList.dropLast(1).toMutableList()
                    modifiedList.add(Content("...下一页 (网页)", ContentType.TEXT, "footer"))
                    _uiState.value = _uiState.value.copy(htmlList = modifiedList)
                }

            } else {
                // 非预加载状态下，如果不是从缓存读取，则将当前页缓存
                if (!isFromCache) {
                    val pageNumToCache = _uiState.value.currentView
                    Log.i(logTag, "Caching current page $pageNumToCache for ${url}")
                    val dataToCache = CacheData(
                        cachedPageNum = pageNumToCache,
                        htmlContent = html,
                        maxPageNum = maxPage,
                        authorId = currentAuthorId
                    )
                    CacheUtil.saveCache(url, dataToCache)
                }
                // 计算初始展示页码
                val newInitPage = if (isFromCache) {
                    // 如果是从缓存加载，则使用当前UI状态中的初始页面
                    _uiState.value.initPage
                } else if (initialized) {
                    // 如果已经初始化完成，则从第0页开始
                    0
                } else {
                    // 其他情况下使用当前UI状态中的初始页面
                    _uiState.value.initPage
                }
                // 更新UI状态
                _uiState.value = _uiState.value.copy(
                    htmlList = passages,
                    chapterList = chapters,
                    initPage = newInitPage,
                    maxWebView = maxPage,
                    isError = false
                )
                // 标记已初始化并记录最新页面
                if (!initialized) {
                    initialized = true
                }
                latestPage = newInitPage
                if (!isFromCache) {
                    showLoadingScrim = false
                }
            }
        }
    }

    // 用于重试的方法
    fun retryLoad() {
        Log.i(logTag, "Retrying load for view ${uiState.value.currentView}")
        // 重置错误状态并从网络重新加载
        viewModelScope.launch {
            showLoadingScrim = true
            // 设置URL为about:blank，确保重新加载，因为相同时会触发保护
            _uiState.value = _uiState.value.copy(
                isError = false,
                urlToLoad = "about:blank"
            )
            kotlinx.coroutines.delay(10)
            loadFromNetwork(uiState.value.currentView)
        }
    }

    // 该函数从加载的URL中提取作者ID，如果本地当前没有储存则储存
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

    // 解析HTML字符串并将其转换为内容列表
    private fun parseHtmlToContent(html: String) {
        rawContentList.clear()
        val doc = Jsoup.parse(html)
        doc.getElementsByTag("i").forEach { it.remove() }

        for (node in doc.getElementsByClass("message")) {
            val rawText = HTMLUtil.toText(node.html())
            // 提取章节标题，取第一行非空文本的前30个字符
            val chapterTitle: String? = rawText.lines()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.take(30)

            if (rawText.isNotBlank()) {
                rawContentList.add(Content(rawText, ContentType.TEXT, chapterTitle))
            }
            // 如果需要加载图片，则处理图片元素
            if (_uiState.value.loadImages) {
                for (element in node.getElementsByTag("img")) {
                    val src = element.attribute("src").value
                    if (!src.startsWith("http://") && !src.startsWith("https")) {
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

    // 对原始内容列表进行分页处理，生成可用于页面显示的内容列表和章节信息。
    private fun paginateContent(isFromCache: Boolean = false): Pair<List<Content>, List<ChapterInfo>> {
        val passages = ArrayList<Content>()
        val state = _uiState.value
        // 计算页面内容区域的可用高度和宽度，用于文本分页
        val topPadding = 16.dp
        val footerHeight = 30.dp
        val pageContentHeight = maxHeight - topPadding - footerHeight
        val pageContentWidth = maxWidth - (state.padding + state.padding)
        // 遍历原始内容列表，对文本内容进行分页处理，图片内容直接添加
        for (content in rawContentList) {
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
                    passages.add(Content(t, ContentType.TEXT, content.chapterTitle))
                }
            } else if (content.type == ContentType.IMG) {
                passages.add(content)
            }
        }
        // 根据当前状态显示不同的提示信息
        if (isTransitioning) {
            // 正在转场中
        } else if (isFromCache) {
            passages.add(Content("正在加载下一页...", ContentType.TEXT, "footer"))
        } else if (nextHtmlList != null) {
            passages.add(Content("...下一页", ContentType.TEXT, "footer"))
        } else if (uiState.value.currentView < uiState.value.maxWebView) {
            passages.add(Content("正在加载下一页...", ContentType.TEXT, "footer"))
        } else {
            passages.add(Content("...没有更多了", ContentType.TEXT, "footer"))
        }
        // 构建章节信息列表，记录每个章节标题在内容列表中的起始索引
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

    /**
     * 当页面发生改变时调用，用于处理
     * 页面切换逻辑
     * 章节保存
     * 预加载
     * 页面过渡状态管理
     * */
    fun onPageChange(curPagerState: PagerState, scope: CoroutineScope) {
        if (pagerState == null) {
            pagerState = curPagerState
        }
        if (compositionScope == null) {
            compositionScope = scope
        }

        val newPage = curPagerState.targetPage
        // 如果正处于页面转场状态，判断是否已经到达目标页面
        if (isTransitioning) {
            if (curPagerState.settledPage == _uiState.value.initPage && newPage == _uiState.value.initPage) {
                Log.i(logTag, "Transition complete. Settled at page ${_uiState.value.initPage}")
                isTransitioning = false
                latestPage = _uiState.value.initPage
            } else {
                return
            }
        }
        val oldPage = latestPage
        val list = _uiState.value.htmlList

        if (newPage != oldPage && list.isNotEmpty() && newPage < list.size && oldPage >= 0 && oldPage < list.size) {
            val oldChapter = list[oldPage].chapterTitle
            val newChapter = list[newPage].chapterTitle

            if (newChapter != null && newChapter != oldChapter) {
                saveHistory(newPage)
            }
        }
        val listSize = uiState.value.htmlList.size
        // 判断是否需要切换到预加载的下一页内容
        if (listSize > 0 &&
            newPage == listSize - 1 &&
            curPagerState.currentPage == listSize - 1 &&
            nextHtmlList != null &&
            !isTransitioning
        ) {
            isTransitioning = true
            val newCurrentView = uiState.value.currentView + 1

            Log.i(logTag, "Switching to preloaded page $newCurrentView")

            _uiState.value = _uiState.value.copy(
                htmlList = nextHtmlList!!,
                chapterList = nextChapterList ?: listOf(),
                initPage = 0,
                currentView = newCurrentView
            )

            nextHtmlList = null
            nextChapterList = null
            latestPage = 0

            return
        }
        // 计算触发预加载的页面索引阈值
        val lastContentPageIndex = (listSize - 2).coerceAtLeast(0)
        val triggerPageIndex = (lastContentPageIndex - PRELOAD_THRESHOLD).coerceAtLeast(0)
        // 判断是否满足预加载条件并触发预加载
        if (listSize > 0 &&
            !isPreloading &&
            nextHtmlList == null &&
            uiState.value.currentView < uiState.value.maxWebView &&
            !isTransitioning &&
            newPage >= triggerPageIndex
        ) {

            val viewToPreload = uiState.value.currentView + 1
            Log.i(logTag, "newPage$newPage triggerPageIndex$triggerPageIndex")
            Log.i(logTag, "Preloading view $viewToPreload")
            triggerPreload(viewToPreload, uiState.value.maxWebView)
        }

        latestPage = newPage

        if (curPagerState.settledPage != curPagerState.targetPage && _uiState.value.scale != 1f) {
            _uiState.value = _uiState.value.copy(scale = 1f, offset = Offset(0f, 0f))
        }
    }

    // 保存阅读历史记录
    private fun saveHistory(pageToSave: Int) {
        val currentList = _uiState.value.htmlList
        var currentChapter: String? = null
        // 获取当前页面的章节标题
        if (pageToSave >= 0 && pageToSave < currentList.size) {
            currentChapter = currentList[pageToSave].chapterTitle
        }
        // 更新收藏夹中的历史记录信息
        FavoriteUtil.getFavoriteMap {
            it[url]?.let { it1 ->
                FavoriteUtil.updateFavorite(
                    it1.copy(
                        lastPage = pageToSave,
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
            backgroundColorString
        )
        SettingsUtil.saveSettings(settings)
    }

    // 保存阅读器设置并重新分页内容
    fun saveSettings(currentPage: Int) {
        saveCurrentSettings()

        viewModelScope.launch {
            showLoadingScrim = true
            // 获取旧的总页数
            val oldPageCount = _uiState.value.htmlList.size
            // 在后台线程中重新分页内容
            val (newPages, newChapters) = withContext(Dispatchers.Default) {
                paginateContent()
            }
            // 获取新的页面数量
            val newPageCount = newPages.size
            // 使用百分比逻辑和向下取整来计算新的滚动位置
            val pageToScrollTo = if (oldPageCount > 0 && newPageCount > 0) {
                // 计算出阅读进度百分比 (当前的页码 / 旧的总页数 = 0.1)
                val progressPercentage = (currentPage + 1).toDouble() / oldPageCount.toDouble()

                // 用百分比乘以新的总页数，得到新的页码
                val newPageNumber = floor(progressPercentage * newPageCount).toInt()
                // 确保索引在新页数的有效范围内
                (newPageNumber - 1).coerceIn(0, (newPageCount - 1).coerceAtLeast(0))
            } else {
                currentPage.coerceIn(0, (newPageCount - 1).coerceAtLeast(0))
            }

            // 确保在滚动回第一页时也清空偏移
            if (pageToScrollTo == 0) {
                _uiState.value = _uiState.value.copy(scale = 1f, offset = Offset(0f, 0f))
            }

            _uiState.value = _uiState.value.copy(
                htmlList = newPages,
                chapterList = newChapters,
                initPage = pageToScrollTo, // 使用新计算出的页面索引
                isError = false
            )
            showLoadingScrim = false
        }
    }

    fun onTransform(pan: Offset, zoom: Float) {
        val scale = (_uiState.value.scale * zoom).coerceIn(0.5f, 3f)
        val offset = if (scale == 1f) Offset(0f, 0f) else _uiState.value.offset + pan
        _uiState.value = _uiState.value.copy(scale = scale, offset = offset)
    }

    fun onSetView(view: Int, forceReload: Boolean = false) {
        // 检查是否是当前页，如果是，则不执行任何操作
        if (view == _uiState.value.currentView && !isTransitioning && !forceReload) {
            Log.i(logTag, "Already on view $view. Ignoring.")
            return
        }
        // 检查是否是请求下一页 (view == _uiState.value.currentView + 1)
        // 并且下一页已经预加载 (nextHtmlList != null)
        if (view == _uiState.value.currentView + 1 && nextHtmlList != null) {
            Log.i(logTag, "Using preloaded content for view $view")
            isTransitioning = true // 开始转场
            // 应用预加载的内容
            _uiState.value = _uiState.value.copy(
                htmlList = nextHtmlList!!,
                chapterList = nextChapterList ?: listOf(),
                initPage = 0,
                currentView = view
            )
            // 清理
            nextHtmlList = null
            nextChapterList = null
            latestPage = 0

        } else {
            // 否则 (跳转到非下一页，或没有预加载)，先检查缓存
            Log.i(logTag, "Page $view not preloaded. Checking cache...")
            // 清理掉可能过时的预加载数据
            nextHtmlList = null
            nextChapterList = null
            isPreloading = false

            CacheUtil.getCache(url, view) { cacheData ->
                viewModelScope.launch {
                    if (cacheData != null && cacheData.authorId == currentAuthorId) {
                        // Case:缓存命中
                        Log.i(logTag, "Cache hit for page $view. Loading from cache.")
                        isTransitioning = true

                        _uiState.value = _uiState.value.copy(
                            currentView = view,
                            initPage = 0,
                            maxWebView = cacheData.maxPageNum
                        )
                        // 加载缓存的HTML
                        loadFinished(
                            success = true,
                            cacheData.htmlContent,
                            null,
                            cacheData.maxPageNum,
                            isFromCache = true
                        )

                    } else {
                        // Case:缓存未命中
                        Log.i(logTag, "Cache miss for page $view. Loading from network.")
                        loadFromNetwork(view)
                        isTransitioning = true
                    }
                }
            }
        }
    }

    // 切换章节抽屉的显示状态
    fun toggleChapterDrawer(show: Boolean) {
        _uiState.value = _uiState.value.copy(showChapterDrawer = show)
    }

    // 设置字体大小
    fun onSetFontSize(fontSize: TextUnit) {
        val newMinLineHeight = (fontSize.value * 1.6f).sp
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

    // 设置行高
    fun onSetLineHeight(lineHeight: TextUnit) {
        val currentFontSizeValue = _uiState.value.fontSize.value
        val newMinLineHeightValue = currentFontSizeValue * 1.6f
        val coercedLineHeightValue = lineHeight.value.coerceIn(
            minimumValue = newMinLineHeightValue,
            maximumValue = 100.0f
        )
        _uiState.value = _uiState.value.copy(
            lineHeight = coercedLineHeightValue.sp
        )
    }

    // 设置内边距
    fun onSetPadding(padding: Dp) {
        _uiState.value = _uiState.value.copy(padding = padding)
    }

    // 切换夜间模式
    fun toggleNightMode(isNight: Boolean) {
        _uiState.value = _uiState.value.copy(
            nightMode = isNight,
            backgroundColor = null
        )
        saveCurrentSettings()
    }

    // 切换图片加载
    fun toggleLoadImages(load: Boolean) {
        _uiState.value = _uiState.value.copy(loadImages = load)
        val currentPage = latestPage
        _uiState.value = _uiState.value.copy(initPage = currentPage)
        onSetView(uiState.value.currentView, forceReload = true)
    }

    // 设置背景颜色
    fun onSetBackgroundColor(color: Color?) {
        _uiState.value = _uiState.value.copy(
            backgroundColor = color,
            nightMode = false
        )
    }

    // 退出时，保存当前页面的历史记录，清理预加载相关的数据列表
    override fun onCleared() {
        saveHistory(latestPage)
        nextHtmlList = null
        nextChapterList = null
        isPreloading = false
        super.onCleared()
    }
}

