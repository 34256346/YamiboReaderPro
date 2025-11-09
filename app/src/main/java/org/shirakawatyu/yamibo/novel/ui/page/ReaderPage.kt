package org.shirakawatyu.yamibo.novel.ui.page

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.shirakawatyu.yamibo.novel.R
import org.shirakawatyu.yamibo.novel.ui.state.ChapterInfo
import org.shirakawatyu.yamibo.novel.ui.state.ReaderState
import org.shirakawatyu.yamibo.novel.ui.theme.ReaderTheme
import org.shirakawatyu.yamibo.novel.ui.vm.ReaderVM
import org.shirakawatyu.yamibo.novel.ui.vm.ViewModelFactory
import org.shirakawatyu.yamibo.novel.ui.widget.CacheDialog
import org.shirakawatyu.yamibo.novel.ui.widget.CacheProgressDialog
import org.shirakawatyu.yamibo.novel.ui.widget.ContentViewer
import org.shirakawatyu.yamibo.novel.ui.widget.PassageWebView
import org.shirakawatyu.yamibo.novel.util.ComposeUtil.Companion.SetStatusBarColor
import org.shirakawatyu.yamibo.novel.util.ValueUtil
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectTapGestures

private val backgroundColors = listOf(
    null, // 代表 "原背景"
    Color(0xFFf5f1e8),
    Color(0xFFf5f5dc),
    Color(0xFFd9e0e8),
    Color(0xFFdddddd)
)

/**
 * 阅读器页面，用于格式化显示原论坛内容
 *
 * @param readerVM 默认为新实例
 * @param url 要显示的网页的 URL
 * @param navController 用于顶部栏的返回按钮的导航
 */
@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ReaderPage(
    readerVM: ReaderVM = viewModel(
        factory = ViewModelFactory(LocalContext.current.applicationContext)
    ),
    url: String = "",
    navController: NavController
) {
    val uiState by readerVM.uiState.collectAsState()

    // 缓存相关状态
    val cachedPages by readerVM.cachedPages.collectAsState()
    val cacheProgress by readerVM.cacheProgress.collectAsState()
    val isDiskCaching by readerVM.isDiskCaching.collectAsState()
    var showCacheDialog by remember { mutableStateOf(false) }

    // 将所有内容包裹在 ReaderTheme 中
    ReaderTheme(nightMode = uiState.nightMode) {
        // 背景颜色
        val themeBackground = MaterialTheme.colorScheme.background
        val finalBackground = uiState.backgroundColor ?: themeBackground

        val pagerState = rememberPagerState(pageCount = { uiState.htmlList.size })
        // 滚动列表 (竖屏)
        val lazyListState = rememberLazyListState()

        // 显示设置菜单
        var showSettings by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        // 抽屉状态
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val smoothScrollAnimation = remember {
            tween<Float>(durationMillis = 432, easing = EaseOut)
        }
        // 监听VM状态来打开/关闭抽屉
        LaunchedEffect(uiState.showChapterDrawer) {
            if (uiState.showChapterDrawer) {
                drawerState.open()
            } else {
                drawerState.close()
            }
        }
        // 监听抽屉手势关闭，同步回VM
        LaunchedEffect(drawerState.isOpen) {
            if (!drawerState.isOpen) {
                readerVM.toggleChapterDrawer(false)
            }
        }
        // 当前页/行索引
        val currentPageIndex = if (uiState.isVerticalMode) {
            // 使用快照，确保在重组时获取最新的稳定值
            remember(
                lazyListState.firstVisibleItemIndex,
                lazyListState.isScrollInProgress
            ) {
                if (lazyListState.isScrollInProgress) {
                    // 如果在滚动中，报告我们正前往的页面（或当前页）
                    lazyListState.firstVisibleItemIndex
                } else {
                    // 稳定在的页面
                    lazyListState.firstVisibleItemIndex
                }
            }.coerceIn(0, (uiState.htmlList.size - 1).coerceAtLeast(0))
        } else {
            pagerState.currentPage
        }

        // 使用这个正确的 currentPageIndex 来获取标题
        val currentChapterTitle =
            if (uiState.htmlList.isNotEmpty() && currentPageIndex < uiState.htmlList.size) {
                uiState.htmlList[currentPageIndex].chapterTitle
            } else {
                null
            }
        // 存储fontSize,lineHeight,padding
        var settingsOnOpen by remember {
            mutableStateOf<Pair<Triple<TextUnit, TextUnit, Dp>, Color?>?>(
                null
            )
        }
        // 是否显示加载遮罩
        val isLoading = readerVM.showLoadingScrim
        // 是否显示图片加载警告对话框
        var showImageWarning by remember { mutableStateOf(false) }
        if (showImageWarning) {
            AlertDialog(
                onDismissRequest = { showImageWarning = false },
                title = { Text("确认加载图片") },
                text = { Text("开启后将加载帖子中的图片，这会显著增加加载时间，并可能导致应用卡顿。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            readerVM.toggleLoadImages(true)
                            showImageWarning = false
                        }
                    ) {
                        Text("确认开启")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImageWarning = false }) { Text("取消") }
                }
            )
        }
        // 根据设置页面显示状态设置状态栏颜色
        val statusBarColor = if (showSettings) {
            Color.Black
        } else {
            finalBackground
        }
        SetStatusBarColor(statusBarColor)

        // 监听设置页面显示状态变化
        LaunchedEffect(showSettings) {
            // 保存打开时的设置页面参数作为是否改变的判断基准
            if (showSettings) {
                settingsOnOpen = Pair(
                    Triple(uiState.fontSize, uiState.lineHeight, uiState.padding),
                    uiState.backgroundColor
                )
            }
        }
        // 监听竖屏滚动状态
        LaunchedEffect(uiState.isVerticalMode, lazyListState) {
            if (uiState.isVerticalMode) {
                // 同时监听索引和滚动状态
                snapshotFlow {
                    // 只有当isScrollInProgress为false时，我们才认为状态是“稳定”的
                    Pair(lazyListState.firstVisibleItemIndex, !lazyListState.isScrollInProgress)
                }
                    .distinctUntilChanged() // 确保只有在稳定状态或索引变化时才触发
                    .collect { (visibleIndex, isSettled) ->
                        // 当列表“稳定”在新的索引时，才调用VM
                        if (isSettled) {
                            if (visibleIndex >= 0 && visibleIndex < uiState.htmlList.size) {
                                readerVM.onVerticalPageSettled(visibleIndex)
                            }
                        }
                    }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(finalBackground)
        ) {
            PassageWebView(
                url = uiState.urlToLoad,
                loadImages = uiState.loadImages
            ) { success, html, loadedUrl, maxPage ->
                readerVM.loadFinished(success, html, loadedUrl, maxPage)
            }
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen,
                drawerContent = {
                    ChapterDrawerContent(
                        drawerState = drawerState,
                        chapterList = uiState.chapterList,
                        currentChapterTitle = currentChapterTitle,
                        // 传递 pageCount 和 isVerticalMode
                        pageCount = uiState.htmlList.size,
                        isVerticalMode = uiState.isVerticalMode,
                        onChapterClick = { index ->
                            scope.launch {
                                if (uiState.isVerticalMode) {
                                    // 如果是竖屏模式，滚动 LazyList
                                    lazyListState.scrollToItem(index)
                                } else {
                                    // 否则，滚动Pager
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                            readerVM.toggleChapterDrawer(false)
                        }
                    )
                }
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(finalBackground)
                ) {
                    var hasLoaded by remember { mutableStateOf(false) }

                    if (maxHeight > 0.dp && maxWidth > 0.dp && !hasLoaded) {
                        LaunchedEffect(maxHeight, maxWidth, url) {
                            readerVM.firstLoad(url, maxHeight, maxWidth)
                            hasLoaded = true
                        }
                    }

                    if (uiState.isError) {
                        // 显示错误和重试界面
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painterResource(id = R.drawable.ic_error),
                                contentDescription = "加载失败",
                                modifier = Modifier.size(48.dp),
                                tint = if (uiState.nightMode) Color.White else Color.Gray
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "页面加载失败",
                                fontSize = 18.sp,
                                color = if (uiState.nightMode) Color.White else Color.DarkGray
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = { readerVM.retryLoad() }) {
                                Text("重试")
                            }
                        }
                    } else if (hasLoaded) {

                        // 定义一个key，当数据(列表)和目标页(initPage)都准备好时，这个key会更新
                        val dataKey =
                            "${uiState.htmlList.hashCode()}_${uiState.initPage}_${uiState.isVerticalMode}"

                        // 这个flag用于跟踪这个currentView的初始滚动是否已完成。
                        var isInitialScrollDone by remember(uiState.currentView) {
                            mutableStateOf(
                                false
                            )
                        }


                        // 仅在hasLoaded且htmlList非空时才渲染内容
                        if (uiState.htmlList.isNotEmpty()) {

                            // 此LaunchedEffect仅在dataKey变化时运行一次
                            LaunchedEffect(dataKey) {
                                // 1. 仅在 "初始滚动" 未完成时执行
                                if (!isInitialScrollDone) {
                                    if (uiState.isVerticalMode) {
                                        // 2. 检查用户是否已在滚动
                                        if (lazyListState.firstVisibleItemIndex != uiState.initPage && !lazyListState.isScrollInProgress) {
                                            lazyListState.scrollToItem(uiState.initPage)
                                        }
                                    } else {
                                        // 2. 检查用户是否已在滚动
                                        if (pagerState.pageCount > uiState.initPage && // 确保 Pager 已更新
                                            pagerState.currentPage != uiState.initPage &&
                                            !pagerState.isScrollInProgress
                                        ) {
                                            pagerState.scrollToPage(uiState.initPage)
                                        }
                                    }
                                    // 3. 无论是否真的执行了滚动（可能被 isScrollInProgress 阻止），
                                    // 都将此flag设为 true，以防止 "跳回" Bug。
                                    isInitialScrollDone = true
                                }
                            }


                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                // 最好是等待 Pager/LazyList 稳定
                                // .graphicsLayer(alpha = if (isInitialScrollDone) 1f else 0f)
                            ) {
                                if (uiState.isVerticalMode) {
                                    // 竖屏滚动模式
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable(
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() },
                                                onClick = { showSettings = true }
                                            )
                                            .padding(horizontal = uiState.padding),
                                        state = lazyListState
                                    ) {
                                        itemsIndexed(
                                            items = uiState.htmlList,
                                            key = { index, item -> "${item.type}_${item.chapterTitle}_${item.data.hashCode()}_${index}" }
                                        ) { index, content ->
                                            ContentViewer(
                                                data = content,
                                                padding = uiState.padding, // 传递以便 ContentViewer (横屏) 使用
                                                lineHeight = uiState.lineHeight,
                                                letterSpacing = uiState.letterSpacing,
                                                fontSize = uiState.fontSize,
                                                currentPage = index + 1,
                                                pageCount = uiState.htmlList.size,
                                                nightMode = uiState.nightMode,
                                                backgroundColor = finalBackground, // 传递以便 ContentViewer (横屏) 使用
                                                isVerticalMode = true
                                            )
                                        }
                                    }
                                } else {
                                    HorizontalPager(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onTap = { offset ->
                                                        val screenWidth = size.width.toFloat()
                                                        val leftZoneEnd = screenWidth * 0.25f
                                                        val rightZoneStart = screenWidth * 0.75f

                                                        if (offset.x < leftZoneEnd) {
                                                            // 点击左侧25%：上一页
                                                            scope.launch {
                                                                val newPage =
                                                                    (pagerState.currentPage - 1).coerceAtLeast(
                                                                        0
                                                                    )
                                                                pagerState.animateScrollToPage(
                                                                    page = newPage,
                                                                    animationSpec = smoothScrollAnimation
                                                                )
                                                            }
                                                        } else if (offset.x > rightZoneStart) {
                                                            // 点击右侧25%：下一页
                                                            scope.launch {
                                                                val newPage =
                                                                    (pagerState.currentPage + 1)
                                                                        .coerceAtMost(pagerState.pageCount - 1)
                                                                pagerState.animateScrollToPage(
                                                                    page = newPage,
                                                                    animationSpec = smoothScrollAnimation
                                                                )
                                                            }
                                                        } else {
                                                            // 点击中间50%：显示设置
                                                            showSettings = true
                                                        }
                                                    }
                                                )
                                            }
                                            .pointerInput(Unit) {
                                                detectTransformGestures { _, pan, zoom, _ ->
                                                    readerVM.onTransform(pan, zoom)
                                                }
                                            }
                                            .graphicsLayer(
                                                scaleX = uiState.scale,
                                                scaleY = uiState.scale,
                                                translationX = uiState.offset.x,
                                                translationY = uiState.offset.y
                                            ),
                                        state = pagerState,
                                    ) { page ->
                                        ContentViewer(
                                            data = uiState.htmlList[page],
                                            padding = uiState.padding,
                                            lineHeight = uiState.lineHeight,
                                            letterSpacing = uiState.letterSpacing,
                                            fontSize = uiState.fontSize,
                                            currentPage = pagerState.currentPage + 1,
                                            pageCount = pagerState.pageCount,
                                            nightMode = uiState.nightMode,
                                            backgroundColor = finalBackground,
                                            isVerticalMode = false
                                        )

                                        SideEffect {
                                            readerVM.onPageChange(pagerState, scope)
                                        }
                                    }
                                }
                            }
                        }

                        // 添加固定的顶部栏
                        if (uiState.isVerticalMode && !showSettings && uiState.htmlList.isNotEmpty()) {
                            VerticalModeHeader(
                                chapterTitle = currentChapterTitle,
                                currentPage = currentPageIndex + 1,
                                pageCount = uiState.htmlList.size,
                                backgroundColor = finalBackground,
                                padding = uiState.padding // 传入内边距以对齐
                            )
                        }
                        // 设置页面
                        if (showSettings) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.2f))
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        onClick = {
                                            val settingsNow = Pair(
                                                Triple(
                                                    uiState.fontSize,
                                                    uiState.lineHeight,
                                                    uiState.padding
                                                ),
                                                uiState.backgroundColor
                                            )
                                            if (settingsOnOpen != settingsNow) {
                                                readerVM.saveSettings(currentPageIndex)
                                            }
                                            showSettings = false
                                        }
                                    )
                            )
                            // 顶部栏
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shadowElevation = 8.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween

                                ) {
                                    IconButton(onClick = {
                                        navController.navigateUp()
                                    }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "返回"
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // 加载图片开关
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "加载图片",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Switch(
                                                checked = uiState.loadImages,
                                                onCheckedChange = { isChecked ->
                                                    if (isChecked) {
                                                        showImageWarning = true
                                                    } else {
                                                        readerVM.toggleLoadImages(false)
                                                    }
                                                }
                                            )
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        // 夜间模式开关
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "夜间模式",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Switch(
                                                checked = uiState.nightMode,
                                                onCheckedChange = { isChecked ->
                                                    readerVM.toggleNightMode(isChecked)
                                                }
                                            )
                                        }

                                        Spacer(Modifier.width(16.dp))
                                        // 刷新按钮
                                        IconButton(onClick = {
                                            readerVM.forceRefreshCurrentPage()
                                            // 立即隐藏设置菜单，以显示加载动画
                                            showSettings = false
                                        }) {
                                            Icon(
                                                Icons.Default.Refresh,
                                                contentDescription = "刷新页面"
                                            )
                                        }
                                    }
                                }
                            }
                            // 底部栏
                            ReaderSettingsBar(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter),
                                uiState = uiState,
                                pageCount = uiState.htmlList.size,
                                currentPage = currentPageIndex,
                                onSetView = { readerVM.onSetView(it) },
                                onSetPage = { pageIndex ->
                                    scope.launch {
                                        if (uiState.isVerticalMode) {
                                            lazyListState.scrollToItem(pageIndex)
                                        } else {
                                            pagerState.scrollToPage(pageIndex)
                                        }
                                    }
                                },
                                onSetFontSize = { readerVM.onSetFontSize(it) },
                                onSetLineHeight = { readerVM.onSetLineHeight(it) },
                                onSetPadding = { readerVM.onSetPadding(it) },
                                onShowChapters = { readerVM.toggleChapterDrawer(true) },
                                onSetBackgroundColor = { readerVM.onSetBackgroundColor(it) },
                                onSetReadingMode = { isVertical ->
                                    readerVM.setReadingMode(isVertical, currentPageIndex)
                                },
                                onShowCacheDialog = {
                                    if (isDiskCaching) {
                                        // 如果正在缓存（即使在后台），直接显示进度
                                        readerVM.showCacheProgress()
                                    } else {
                                        // 否则，显示缓存选择页
                                        showCacheDialog = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        // 加载遮罩
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {}
                    ),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        if (showCacheDialog) {
            CacheDialog(
                maxWebView = uiState.maxWebView,
                cachedPages = cachedPages,
                onDismiss = { showCacheDialog = false },
                onStartCache = { pages, includeImages ->
                    readerVM.startCaching(pages, includeImages)
                    showCacheDialog = false
                },
                onDeleteCache = { pages ->
                    readerVM.deleteCachedPages(pages)
                },
                onUpdateCache = { pages, includeImages ->
                    readerVM.updateCachedPages(pages, includeImages)
                    showCacheDialog = false
                }
            )
        }
        // 缓存进度对话框
        cacheProgress?.let { progress ->
            CacheProgressDialog(
                totalPages = progress.totalPages,
                currentPage = progress.currentPage,
                currentPageNum = progress.currentPageNum,
                onDismiss = {
                    readerVM.resetCacheProgress()
                },
                onStopCache = {
                    readerVM.stopCaching()
                }
            )
        }
    }
}

@Composable
fun ReaderSettingsBar(
    modifier: Modifier = Modifier,
    uiState: ReaderState,
    pageCount: Int,
    currentPage: Int,
    onSetView: (view: Int) -> Unit,
    onSetPage: (page: Int) -> Unit,
    onSetFontSize: (fontSize: TextUnit) -> Unit,
    onSetLineHeight: (lineHeight: TextUnit) -> Unit,
    onSetPadding: (padding: Dp) -> Unit,
    onShowChapters: () -> Unit,
    onSetBackgroundColor: (color: Color?) -> Unit,
    onSetReadingMode: (isVertical: Boolean) -> Unit,
    onShowCacheDialog: () -> Unit
) {
    var showSpacingMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {}),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 8.dp
    ) {
        if (showSpacingMenu) {
            SpacingSettingsMenu(
                uiState = uiState,
                onSetFontSize = onSetFontSize,
                onSetLineHeight = onSetLineHeight,
                onSetPadding = onSetPadding,
                onBack = { showSpacingMenu = false },
                onSetReadingMode = onSetReadingMode,
                onSetBackgroundColor = onSetBackgroundColor
            )
        } else {
            MainSettingsMenu(
                uiState = uiState,
                pageCount = pageCount,
                currentPage = currentPage,
                onSetView = onSetView,
                onSetPage = onSetPage,
                onShowSpacingMenu = { showSpacingMenu = true },
                onShowChapters = onShowChapters,
                onSetBackgroundColor = onSetBackgroundColor,
                onShowCacheDialog = onShowCacheDialog
            )
        }
    }
}

@Composable
fun ChapterDrawerContent(
    drawerState: DrawerState,
    chapterList: List<ChapterInfo>,
    currentChapterTitle: String?,
    pageCount: Int,
    isVerticalMode: Boolean,
    onChapterClick: (index: Int) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // pageCount (总行/页数)
    val totalItems = pageCount.coerceAtLeast(1)

    val currentChapterIndex = remember(currentChapterTitle, chapterList) {
        chapterList.indexOfFirst { it.title == currentChapterTitle }.coerceAtLeast(0)
    }
    // 当抽屉打开时，自动滚动到当前章节位置
    LaunchedEffect(drawerState.isOpen, currentChapterIndex) {
        if (drawerState.isOpen) {
            val scrollOffsetItems = 4
            val targetIndex = (currentChapterIndex - scrollOffsetItems).coerceAtLeast(0)
            scope.launch {
                lazyListState.animateScrollToItem(index = targetIndex)
            }
        }
    }
    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(0.75f)
    ) {
        Text(
            "章节目录",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)

        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState
        ) {
            itemsIndexed(
                items = chapterList,
                key = { _, chapter -> "${chapter.title}_${chapter.startIndex}" }
            ) { index, chapter ->
                val isSelected = index == currentChapterIndex

                // 计算百分比或页码
                val percent = (chapter.startIndex.toFloat() / totalItems) * 100f
                val pageLabel = if (isVerticalMode) {
                    "${percent.roundToInt()}%"
                } else {
                    "第 ${chapter.startIndex + 1} 页"
                }

                NavigationDrawerItem(
                    label = {
                        Column {
                            Text(
                                text = chapter.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = pageLabel,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    selected = isSelected,
                    onClick = { onChapterClick(chapter.startIndex) }
                )
            }
        }
    }
}

/**
 * 主设置菜单
 */
@Composable
private fun MainSettingsMenu(
    uiState: ReaderState,
    pageCount: Int,
    currentPage: Int,
    onSetView: (view: Int) -> Unit,
    onSetPage: (page: Int) -> Unit,
    onShowSpacingMenu: () -> Unit,
    onShowChapters: () -> Unit,
    onSetBackgroundColor: (color: Color?) -> Unit,
    onShowCacheDialog: () -> Unit
) {
    // 模式判断
    val isVerticalMode = uiState.isVerticalMode

    // 滑块逻辑
    val sliderValue = if (isVerticalMode) uiState.currentPercentage else currentPage.toFloat()
    val sliderRange =
        if (isVerticalMode) 0f..100f else 0f..(pageCount - 1).toFloat().coerceAtLeast(0f)
    val sliderSteps =
        if (isVerticalMode) 98 else (pageCount - 2).coerceAtLeast(0)

    var sliderPos by remember(sliderValue) {
        mutableFloatStateOf(sliderValue)
    }

    var showWebViewPageSelector by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 章节和网页
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧章节按钮
            TextButton(
                onClick = onShowChapters,
                modifier = Modifier.weight(1.3f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Menu, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("章节", fontSize = 18.sp)
                }
            }
            Spacer(modifier = Modifier.weight(0.1f))
            // 右侧网页翻页控制区域
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .weight(1.6f)
                    .clickable(
                        enabled = uiState.maxWebView > 1,
                        onClick = { showWebViewPageSelector = true }
                    )
            ) {
                IconButton(
                    onClick = { onSetView(uiState.currentView - 1) },
                    enabled = uiState.currentView > 1
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一页(网页)")
                }
                Text(
                    text = "网页: ${uiState.currentView} / ${uiState.maxWebView}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(
                    onClick = { onSetView(uiState.currentView + 1) },
                    enabled = uiState.currentView < uiState.maxWebView
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一页(网页)")
                }
            }
        }

        // 统一的阅读器进度Slider
        Column(modifier = Modifier.fillMaxWidth()) {
            val displayText = if (isVerticalMode) {
                "${sliderPos.roundToInt()}%"
            } else {
                "${sliderPos.roundToInt() + 1} / $pageCount"
            }

            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Slider(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .align(Alignment.CenterHorizontally),
                value = sliderPos,
                onValueChange = { sliderPos = it },
                onValueChangeFinished = {
                    val targetIndex = if (isVerticalMode) {
                        (sliderPos / 100f * pageCount.coerceAtLeast(1).toFloat())
                            .toInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                    } else {
                        sliderPos.roundToInt()
                    }
                    onSetPage(targetIndex)
                },
                valueRange = sliderRange,
                steps = sliderSteps
            )
        }

        // 缓存和页面设置按钮（移除了背景色选择）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 左侧：缓存按钮
            Button(
                onClick = onShowCacheDialog,
                enabled = uiState.isFavorited,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .weight(1f)
                    .size(40.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_cache),
                    contentDescription = "缓存",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("缓存")
            }

            Spacer(Modifier.width(24.dp))

            // 右侧：页面设置按钮
            Button(
                onClick = onShowSpacingMenu,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .weight(1f)
                    .size(40.dp),
            ) {
                Text("页面设置")
            }
        }
        // 网页跳转页面
        if (showWebViewPageSelector) {
            val lazyListState = rememberLazyListState()
            val currentPageIndex = (uiState.currentView - 1).coerceAtLeast(0)

            LaunchedEffect(uiState.currentView) {
                val targetIndex = (currentPageIndex - 3).coerceAtLeast(0)
                scope.launch {
                    lazyListState.animateScrollToItem(index = targetIndex)
                }
            }
            AlertDialog(
                onDismissRequest = { showWebViewPageSelector = false },
                title = {
                    Text(
                        "跳转网页",
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                text = {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        state = lazyListState
                    ) {
                        items(uiState.maxWebView) { pageNum ->
                            val page = pageNum + 1
                            Column {
                                TextButton(
                                    onClick = {
                                        onSetView(page)
                                        showWebViewPageSelector = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "第 $page 页",
                                        fontSize = 18.sp,
                                        color = if (page == uiState.currentView)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                }
                                if (page < uiState.maxWebView) {
                                    HorizontalDivider(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showWebViewPageSelector = false }) {
                        Text("取消", fontSize = 22.sp)
                    }
                }
            )
        }
    }
}

/**
 * 用于显示背景颜色选项的行
 */
@Composable
private fun ColorSwatchRow(
    selectedColor: Color?,
    onColorSelected: (Color?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 遍历所有背景颜色选项
        backgroundColors.forEach { color ->
            ColorSwatch(
                color = color,
                isSelected = selectedColor == color,
                onClick = { onColorSelected(color) }
            )
        }
    }
}

/**
 * 单个颜色样本 Composable
 */
@Composable
private fun RowScope.ColorSwatch(
    color: Color?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // null 代表"原背景"，我们用主题的背景色来显示它
    val displayColor = color ?: MaterialTheme.colorScheme.background
    // 边框颜色
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)

    Box(
        modifier = Modifier
            .weight(1f)
            .height(32.dp)
            .padding(horizontal = 4.dp)
            // 使用 graphicsLayer 来裁剪为圆形
            .graphicsLayer {
                shape = CircleShape
                clip = true
            }
            .background(displayColor)
            // 默认的细边框
            .border(
                width = 1.dp,
                color = borderColor,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    ) {
        if (isSelected) {
            // 选中状态：显示一个更粗的、高亮的边框
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
            )
        }
        if (color == null) {
            // "原背景" 选项的特殊标记 (使用你添加的 drawable)
            Icon(
                painter = painterResource(id = R.drawable.ic_original_background),
                contentDescription = "原背景",
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(16.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 二级菜单 - 页面设置 (行高, 页距)
 * 添加了字体设置和阅读模式
 */
@Composable
private fun SpacingSettingsMenu(
    uiState: ReaderState,
    onSetFontSize: (fontSize: TextUnit) -> Unit,
    onSetLineHeight: (lineHeight: TextUnit) -> Unit,
    onSetPadding: (padding: Dp) -> Unit,
    onBack: () -> Unit,
    onSetReadingMode: (isVertical: Boolean) -> Unit,
    onSetBackgroundColor: (color: Color?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Text(
                text = "页面设置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 字体和阅读模式行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧：字体调节
            SettingAdjuster(
                label = "字体",
                value = "${uiState.fontSize.value.roundToInt()}",
                onDecrease = {
                    val newSize = (uiState.fontSize.value - 1f).coerceAtLeast(12f)
                    onSetFontSize(newSize.sp)
                },
                onIncrease = {
                    val newSize = (uiState.fontSize.value + 1f).coerceAtMost(40f)
                    onSetFontSize(newSize.sp)
                },
                modifier = Modifier.weight(1f)
            )

            // 右侧：阅读模式选项
            Text(
                text = "阅读模式",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                val isVerticalMode = uiState.isVerticalMode
                // 横屏按钮
                IconToggleButton(
                    checked = !isVerticalMode,
                    onCheckedChange = { onSetReadingMode(false) },
                    colors = IconButtonDefaults.iconToggleButtonColors(
                        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = 0.5f
                        ),
                        checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_swap_horiz),
                        contentDescription = "横屏"
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                // 竖屏按钮
                IconToggleButton(
                    checked = isVerticalMode,
                    onCheckedChange = { onSetReadingMode(true) },
                    colors = IconButtonDefaults.iconToggleButtonColors(
                        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = 0.5f
                        ),
                        checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_swap_vert),
                        contentDescription = "竖屏"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val minLineHeight = (uiState.fontSize.value * 1.5f).coerceAtLeast(18f)
        val maxLineHeight = 100f
        SettingsSlider(
            label = "行距",
            value = uiState.lineHeight.value,
            valueRange = minLineHeight..maxLineHeight,
            steps = 14,
            onValueChange = { onSetLineHeight(it.sp) },
            showValue = false,
            startLabel = "小",
            endLabel = "大"
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsSlider(
            label = "页距",
            value = uiState.padding.value,
            valueRange = 15f..65f,
            steps = 9,
            onValueChange = { onSetPadding(it.dp) },
            showValue = false,
            startLabel = "窄",
            endLabel = "宽"
        )

        // 背景颜色选择
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "背景颜色",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        ColorSwatchRow(
            selectedColor = uiState.backgroundColor,
            onColorSelected = onSetBackgroundColor
        )
    }
}

@Composable
private fun VerticalModeHeader(
    chapterTitle: String?,
    currentPage: Int,
    pageCount: Int,
    backgroundColor: Color,
    padding: Dp
) {
    val chapterTitleHeight = 24.dp

    val totalItems = pageCount.coerceAtLeast(1)
    // currentPage 是 1-based, 索引是 (currentPage - 1)
    val percent = ((currentPage.toFloat() - 1) / totalItems) * 100f

    // 使用 Surface 来实现背景遮挡和阴影
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(chapterTitleHeight)
            // 裁切掉阴影，避免在透明背景时看起来奇怪
            .clip(RectangleShape),
        // 使用带一点透明度的背景色，使其在内容滚动时有轻微区分
        color = backgroundColor.copy(alpha = 0.9f),
        shadowElevation = 2.dp // 添加一点阴影
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 关键：使用从 uiState 传入的 padding 来对齐 ContentViewer 的内边距
                .padding(horizontal = padding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 章节标题（左）
            chapterTitle?.takeIf { it.isNotBlank() && it != "footer" }?.let { title ->
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 1.dp, top = 4.dp, end = 7.dp),
                    textAlign = TextAlign.Start
                )
            } ?: Spacer(modifier = Modifier.weight(1f))

            // 页码（右）
            Text(
                text = "${percent.roundToInt()}%",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 1.dp, top = 4.dp, end = 4.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

/**
 * 通用组件：带标签的 Slider，用于页距、行距
 */
@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    showValue: Boolean = true,
    startLabel: String? = null,
    endLabel: String? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            if (showValue) {
                Text(
                    text = "${value.roundToInt()}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.widthIn(min = 40.dp),
                    textAlign = TextAlign.End
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (startLabel != null) {
                Text(
                    text = startLabel,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Slider(
                modifier = Modifier.weight(1f),
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps
            )
            if (endLabel != null) {
                Text(
                    text = endLabel,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}


/**
 * 通用组件：带 + - 按钮的调节器，现仅用于字体大小
 */
@Composable
private fun SettingAdjuster(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onDecrease) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_remove),
                    contentDescription = "Decrease $label"
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.widthIn(min = 24.dp),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = onIncrease) {
                Icon(Icons.Default.Add, contentDescription = "Increase $label")
            }
        }
    }
}