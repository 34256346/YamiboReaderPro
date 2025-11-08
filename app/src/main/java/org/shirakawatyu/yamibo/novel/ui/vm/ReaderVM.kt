// novel/ui/vm/ReaderVM.kt

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
    // [修改] 竖屏用 1000 (行), 横屏用 100 (页)
    private val PRELOAD_THRESHOLD_VERTICAL = 1000
    private val PRELOAD_THRESHOLD_HORIZONTAL = 100


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
                // 背景颜色加载
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
        }
    }

    // [修改] 增加一个辅助函数来计算横屏页的平均行数
    private fun getAvgItemsPerHorizontalPage(): Int {
        val state = _uiState.value
        val topPadding = 24.dp
        val footerHeight = 50.dp
        val pageContentHeight = maxHeight - topPadding - footerHeight
        val pageContentHeightPx = ValueUtil.dpToPx(pageContentHeight)
        val lineHeightPx = ValueUtil.spToPx(state.lineHeight)
        return (pageContentHeightPx / lineHeightPx).toInt().coerceAtLeast(1)
    }

    // 加载页面数据
    private fun loadWithSettings() {
        viewModelScope.launch {
            // 获取收藏栏保存的数据
            FavoriteUtil.getFavoriteMap { favMap ->
                val favorite = favMap[url]
                val targetView = favorite?.lastView ?: 1
                // [MODIFICATION] This is now a "page number", not necessarily an index
                val targetPageNum = favorite?.lastPage ?: 0
                currentAuthorId = favorite?.authorId

                // [MODIFICATION] Calculate the target *index* based on the loaded mode
                val targetIndex: Int
                if (_uiState.value.isVerticalMode) {
                    // Convert page number back to an estimated row index
                    val avgItemsPerPage = getAvgItemsPerHorizontalPage()
                    targetIndex = (targetPageNum * avgItemsPerPage)
                } else {
                    // In horizontal mode, page number *is* the index
                    targetIndex = targetPageNum
                }

                // 从缓存中获取页面数据
                CacheUtil.getCache(url, targetView) { cacheData ->
                    if (cacheData != null) {
                        // 缓存命中：使用缓存数据更新UI状态并完成加载
                        Log.i(logTag, "Cache hit for page $targetView")
                        if (currentAuthorId == null && cacheData.authorId != null) {
                            Log.i(
                                logTag,
                                "Updating local authorId from cache: ${cacheData.authorId}"
                            )
                            currentAuthorId = cacheData.authorId
                        }
                        // [MODIFICATION] 从这里移除 initPage 的设置
                        _uiState.value = _uiState.value.copy(
                            currentView = targetView,
                            // initPage = targetIndex, // <-- [REMOVED]
                            maxWebView = cacheData.maxPageNum
                        )
                        currentAuthorId = cacheData.authorId

                        // [MODIFICATION] 将 targetIndex 传递给 loadFinished
                        loadFinished(
                            success = true,
                            cacheData.htmlContent,
                            null,
                            cacheData.maxPageNum,
                            isFromCache = true,
                            cacheTargetIndex = targetIndex // <-- [ADDED]
                        )
                    } else {
                        // 缓存未命中：从网络加载数据并更新UI状态
                        Log.i(logTag, "Cache miss. Loading page $targetView from network.")

                        // [MODIFICATION] 缓存未命中时，initPage 保持在这里设置
                        _uiState.value = _uiState.value.copy(
                            currentView = targetView,
                            initPage = targetIndex // [MODIFIED] Use targetIndex
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
     * [MODIFICATION] 增加 cacheTargetIndex 参数
     */
    fun loadFinished(
        success: Boolean,
        html: String,
        loadedUrl: String?,
        maxPage: Int,
        isFromCache: Boolean = false,
        cacheTargetIndex: Int = 0 // <-- [ADDED]
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

                // [修改] 计算初始展示页码(索引)和百分比
                val newInitPage = if (isFromCache) {
                    // _uiState.value.initPage // <-- [OLD]
                    cacheTargetIndex // <-- [NEW] 使用传递过来的 cacheTargetIndex
                } else if (initialized) {
                    0 // 已初始化 (例如换页)，从 0 开始
                } else {
                    _uiState.value.initPage // 首次加载，使用 vm 已有的 targetIndex
                }

                val totalItems = passages.size.coerceAtLeast(1)
                // [修改] 确保 newInitPage 在范围内
                val safeInitPage = newInitPage.coerceIn(0, (totalItems - 1).coerceAtLeast(0))

                val newPercent = (safeInitPage.toFloat() / totalItems) * 100f

                // [MODIFICATION] 这是关键：htmlList 和 initPage 在同一次更新中被设置
                _uiState.value = _uiState.value.copy(
                    htmlList = passages,
                    chapterList = chapters,
                    initPage = safeInitPage,
                    currentPercentage = newPercent, // [新增]
                    maxWebView = maxPage,
                    isError = false
                )
                // 标记已初始化并记录最新页面
                if (!initialized) {
                    initialized = true
                }
                latestPage = safeInitPage
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
        // rawContentList的快照
        val contentSnapshot = rawContentList.toList()
        val state = _uiState.value

        val passages: List<Content>

        // [修改] 根据模式选择分页算法
        if (state.isVerticalMode) {
            // --- 竖屏滚动模式 ---
            // 1. 计算可用宽度
            val pageContentWidth = maxWidth - (state.padding + state.padding)

            // 2. 调用新的分行算法
            val lines = TextUtil.pagingTextVertical(
                rawContentList = contentSnapshot,
                width = pageContentWidth,
                fontSize = state.fontSize,
                letterSpacing = state.letterSpacing
            ).toMutableList() // 转换为 MutableList 以添加页脚

            // 3. 添加页脚
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
            // --- 横屏翻页模式 (旧逻辑) ---
            val passagesList = ArrayList<Content>()
            // [修改] 使用 getAvgItemsPerHorizontalPage 内部的逻辑来计算
            val topPadding = 24.dp
            val footerHeight = 50.dp
            val pageContentHeight = maxHeight - topPadding - footerHeight
            val pageContentWidth = maxWidth - (state.padding + state.padding)
            // 遍历原始内容列表，对文本内容进行分页处理，图片内容直接添加
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

            // [修改] 根据当前状态显示不同的提示信息
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

        // --- 章节列表构建 (通用逻辑) ---
        // 构建章节信息列表，记录每个章节标题在内容列表中的起始索引
        val chapterList = mutableListOf<ChapterInfo>()
        var lastTitle: String? = null
        passages.forEachIndexed { index, content ->
            // [修改] 章节索引现在指向 *行索引* (竖屏) 或 *页索引* (横屏)
            if (content.chapterTitle != null && content.chapterTitle != lastTitle && content.chapterTitle != "footer") {
                chapterList.add(ChapterInfo(title = content.chapterTitle, startIndex = index))
                lastTitle = content.chapterTitle
            }
        }

        return Pair(passages, chapterList)
    }

    /**
     * 处理页面变化的共享逻辑（保存历史、预加载、切换到预加载的页面）
     */
    private fun processPageChange(newPage: Int) {
        val oldPage = latestPage
        val state = _uiState.value
        val list = state.htmlList

        // 保存历史
        if (list.isNotEmpty() && newPage < list.size && oldPage >= 0 && oldPage < list.size) {
            val oldChapter = list[oldPage].chapterTitle
            val newChapter = list[newPage].chapterTitle

            // [修改] 仅在章节变化时保存 (竖屏模式下)
            // (横屏模式下，每次翻页都会保存)
            if (newChapter != null && newChapter != oldChapter) {
                saveHistory(newPage)
            } else if (!state.isVerticalMode) {
                // 如果是横屏，且不在转场中，每次都保存
                if (!isTransitioning) {
                    saveHistory(newPage)
                }
            }
        }

        val listSize = list.size
        // 检查是否需要切换到已预加载的下一页
        if (listSize > 0 &&
            newPage == listSize - 1 && // (检查是否是最后 [加载更多] 页面)
            nextHtmlList != null &&
            !isTransitioning // 再次检查
        ) {
            isTransitioning = true
            val newCurrentView = state.currentView + 1

            Log.i(logTag, "Switching to preloaded page $newCurrentView")

            // [修改] 切换时重置百分比
            _uiState.value = state.copy(
                htmlList = nextHtmlList!!,
                chapterList = nextChapterList ?: listOf(),
                initPage = 0,
                currentPercentage = 0f, // [新增]
                currentView = newCurrentView
            )

            nextHtmlList = null
            nextChapterList = null
            latestPage = 0 // 重置

            return // 完成切换，不需要执行后续逻辑
        }

        // 检查是否需要触发预加载
        val lastContentPageIndex = (listSize - 2).coerceAtLeast(0)
        // [修改] 竖屏模式下，阈值应该基于行数，横屏模式下基于页数
        val threshold =
            if (state.isVerticalMode) PRELOAD_THRESHOLD_VERTICAL else PRELOAD_THRESHOLD_HORIZONTAL
        val triggerPageIndex = (lastContentPageIndex - threshold).coerceAtLeast(0)

        if (listSize > 0 &&
            !isPreloading &&
            nextHtmlList == null &&
            state.currentView < state.maxWebView &&
            !isTransitioning && // 再次检查
            newPage >= triggerPageIndex // 关键：当滚动到触发点
        ) {
            val viewToPreload = state.currentView + 1
            Log.i(logTag, "newPage $newPage triggerPageIndex $triggerPageIndex")
            Log.i(logTag, "Preloading view $viewToPreload")
            triggerPreload(viewToPreload, state.maxWebView)
        }

        // 更新最新页面
        latestPage = newPage
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

        // 检查
        val list = _uiState.value.htmlList
        // 如果页面没变 (latestPage)，或者列表无效，则不处理
        if (newPage == latestPage || list.isEmpty() || newPage >= list.size) {
            // 但要处理缩放重置
            if (curPagerState.settledPage != curPagerState.targetPage && _uiState.value.scale != 1f) {
                _uiState.value = _uiState.value.copy(scale = 1f, offset = Offset(0f, 0f))
            }
            return
        }

        // --- 页面已变化，且不在转场中 ---

        // [新增] 更新百分比
        val totalPages = curPagerState.pageCount.coerceAtLeast(1)
        val percent = (newPage.toFloat() / totalPages) * 100f
        _uiState.value = _uiState.value.copy(currentPercentage = percent)

        processPageChange(newPage)

        if (curPagerState.settledPage != curPagerState.targetPage && _uiState.value.scale != 1f) {
            _uiState.value = _uiState.value.copy(scale = 1f, offset = Offset(0f, 0f))
        }
    }

    /**
     * (新函数) 当竖屏滚动时调用
     * 仅当页面 *稳定* 在新索引时才应调用
     */
    fun onVerticalPageSettled(newPage: Int) {
        if (isTransitioning) {
            // 如果是转场到新网页，我们只关心转场是否在 initPage 结束
            if (newPage == _uiState.value.initPage) {
                Log.i(
                    logTag,
                    "Transition complete (Vertical). Settled at page ${_uiState.value.initPage}"
                )
                isTransitioning = false
                latestPage = _uiState.value.initPage
            }
            return // 在转场期间，忽略其他页面变化
        }

        if (newPage == latestPage) return // 索引未变化

        // [新增] 更新百分比
        val totalRows = _uiState.value.htmlList.size.coerceAtLeast(1)
        val percent = (newPage.toFloat() / totalRows) * 100f
        _uiState.value = _uiState.value.copy(currentPercentage = percent)

        processPageChange(newPage)
    }

    // 保存阅读历史记录
    private fun saveHistory(pageToSave: Int) { // pageToSave is the index (row/page)
        val currentList = _uiState.value.htmlList
        var currentChapter: String? = null
        // 获取当前页面的章节标题
        if (pageToSave >= 0 && pageToSave < currentList.size) {
            currentChapter = currentList[pageToSave].chapterTitle
        }

        // [MODIFICATION] Calculate the value to save based on mode
        val state = _uiState.value
        val valueToSave: Int

        if (state.isVerticalMode) {
            // In vertical mode, convert row index to an equivalent page number
            val avgItemsPerPage = getAvgItemsPerHorizontalPage()
            valueToSave = (pageToSave.toFloat() / avgItemsPerPage.toFloat()).toInt()
        } else {
            // In horizontal mode, the index *is* the page number
            valueToSave = pageToSave
        }

        // 更新收藏夹中的历史记录信息
        FavoriteUtil.getFavoriteMap {
            it[url]?.let { it1 ->
                FavoriteUtil.updateFavorite(
                    it1.copy(
                        lastPage = valueToSave, // [MODIFIED] Save the calculated value
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
            state.isVerticalMode // [重要] 保存当前模式
        )
        SettingsUtil.saveSettings(settings)
    }

    // 保存阅读器设置并重新分页内容
    fun saveSettings(currentPage: Int) {
        saveCurrentSettings()

        viewModelScope.launch {
            showLoadingScrim = true

            // [修改] 计算旧的百分比，用于模式切换
            val oldPageCount = _uiState.value.htmlList.size.coerceAtLeast(1)
            val oldPercent = currentPage.toFloat() / oldPageCount

            // [修改] 查找旧章节信息 (基于索引，依然有效)
            val (oldChapterTitle, oldItemInChapter) = if (currentPage >= 0 && currentPage < oldPageCount) {
                val oldChapterTitle = _uiState.value.htmlList[currentPage].chapterTitle
                val oldChapterStartIndex =
                    _uiState.value.chapterList.find { it.title == oldChapterTitle }?.startIndex ?: 0
                val oldItemInChapter = (currentPage - oldChapterStartIndex).coerceAtLeast(0)
                Pair(oldChapterTitle, oldItemInChapter)
            } else {
                Pair(null, 0)
            }

            // 在后台线程中重新分页内容 (将使用 state 中 *新* 的 isVerticalMode)
            val (newPages, newChapters) = withContext(Dispatchers.Default) {
                paginateContent()
            }

            val newPageCount = newPages.size.coerceAtLeast(1)

            // [修改] 计算新的滚动位置
            val pageToScrollTo = if (oldChapterTitle != null) {
                // 1. 尝试找到新章节列表中的同一个章节
                val newChapterStartIndex =
                    newChapters.find { it.title == oldChapterTitle }?.startIndex ?: 0

                // 2. 滚动到该章节的开头 + 旧的章内页码 (或行号)
                (newChapterStartIndex + oldItemInChapter).coerceIn(
                    0,
                    (newPageCount - 1).coerceAtLeast(0)
                )
            } else {
                // 如果找不到旧章节（例如列表为空），回退到百分比逻辑
                (oldPercent * newPageCount).toInt().coerceIn(
                    0,
                    (newPageCount - 1).coerceAtLeast(0)
                )
            }

            // [新增] 计算新的百分比
            val newPercent = (pageToScrollTo.toFloat() / newPageCount) * 100f

            // 确保在滚动回第一页时也清空偏移
            if (pageToScrollTo == 0) {
                _uiState.value = _uiState.value.copy(scale = 1f, offset = Offset(0f, 0f))
            }

            _uiState.value = _uiState.value.copy(
                htmlList = newPages,
                chapterList = newChapters,
                initPage = pageToScrollTo, // 使用新计算出的页面索引
                currentPercentage = newPercent, // [新增]
                isError = false
            )
            showLoadingScrim = false
        }
    }

    fun setReadingMode(isVertical: Boolean, currentPage: Int) {
        if (isVertical == _uiState.value.isVerticalMode) return
        _uiState.value = _uiState.value.copy(
            isVerticalMode = isVertical,
            initPage = currentPage // [修改] 暂存 currentPage (旧索引)
        )
        // [修改] saveSettings 会使用新的 isVerticalMode 重新分页
        // 并使用 currentPage (old 索引) 来计算新的 initPage (new 索引)
        saveSettings(currentPage)
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
                currentPercentage = 0f, // [修改]
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

                        // [MODIFICATION] 从这里移除 initPage 的设置
                        _uiState.value = _uiState.value.copy(
                            currentView = view,
                            initPage = 0, // [修改] 假设缓存的网页总是从 0 索引开始
                            currentPercentage = 0f, // [修改]
                            maxWebView = cacheData.maxPageNum
                        )
                        // 加载缓存的HTML
                        loadFinished(
                            success = true,
                            cacheData.htmlContent,
                            null,
                            cacheData.maxPageNum,
                            isFromCache = true,
                            cacheTargetIndex = 0 // <-- [ADDED] 缓存的网页总是从 0 开始
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

    // 设置行高
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
        saveCurrentSettings()
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
        // [MODIFICATION]
        // 只有在VM成功初始化 (即至少成功加载过一次页面) 之后，
        // 才在退出时保存历史记录。
        // 这可以防止在加载失败或卡住时 (initialized=false)，
        // 退出页面导致 latestPage(0) 覆盖掉
        // 已有的收藏记录。
        if (initialized) {
            saveHistory(latestPage)
        }
        nextHtmlList = null
        nextChapterList = null
        isPreloading = false
        super.onCleared()
    }
}