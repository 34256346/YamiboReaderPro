// novel/ui/page/ReaderPage.kt

package org.shirakawatyu.yamibo.novel.ui.page

import android.annotation.SuppressLint
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
import org.shirakawatyu.yamibo.novel.ui.widget.ContentViewer
import org.shirakawatyu.yamibo.novel.ui.widget.PassageWebView
import org.shirakawatyu.yamibo.novel.util.ComposeUtil.Companion.SetStatusBarColor
import org.shirakawatyu.yamibo.novel.util.ValueUtil
import kotlin.math.roundToInt

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
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ReaderPage(
    readerVM: ReaderVM = viewModel(),
    url: String = "",
    navController: NavController
) {
    val uiState by readerVM.uiState.collectAsState()

    // 将所有内容包裹在 ReaderTheme 中
    ReaderTheme(nightMode = uiState.nightMode) {
        // 背景颜色
        val themeBackground = MaterialTheme.colorScheme.background
        val finalBackground = uiState.backgroundColor ?: themeBackground

        // [MODIFIED] 撤销上一版修改：改回简单的 state 创建
        val pagerState = rememberPagerState(pageCount = { uiState.htmlList.size })
        // 滚动列表 (竖屏)
        val lazyListState = rememberLazyListState()

        // 显示设置菜单
        var showSettings by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        // 抽屉状态
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
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
        // [修改] 当前页/行 索引
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

        // [REMOVED] 移除上一版错误的 LaunchedEffect
        // ...

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
                // [修复] 同时监听索引和滚动状态
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
                        // [修改] 传递 pageCount 和 isVerticalMode
                        pageCount = uiState.htmlList.size,
                        isVerticalMode = uiState.isVerticalMode,
                        onChapterClick = { index ->
                            scope.launch {
                                // --- 修改这里的逻辑 ---
                                if (uiState.isVerticalMode) {
                                    // 如果是竖屏模式，滚动 LazyList
                                    lazyListState.scrollToItem(index)
                                } else {
                                    // 否则，滚动 Pager
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

                        // [MODIFIED]
                        // 1. 定义一个key，当数据(列表)和目标页(initPage)都准备好时，这个key会更新
                        val dataKey =
                            "${uiState.htmlList.hashCode()}_${uiState.initPage}_${uiState.isVerticalMode}"
                        // 2. `remember(dataKey)`: 当key变化时，此状态会重置为false
                        var isInitialScrollDone by remember(dataKey) { mutableStateOf(false) }

                        // [MODIFIED]
                        // 仅在 hasLoaded 且 htmlList *非空* 时才渲染内容
                        if (uiState.htmlList.isNotEmpty()) {

                            // [MODIFIED]
                            // 3. 此 LaunchedEffect 仅在 dataKey 变化时运行一次
                            LaunchedEffect(dataKey) {
                                if (uiState.isVerticalMode) {
                                    if (lazyListState.firstVisibleItemIndex != uiState.initPage) {
                                        // 立即滚动，不带动画
                                        lazyListState.scrollToItem(uiState.initPage)
                                    }
                                } else {
                                    if (pagerState.currentPage != uiState.initPage) {
                                        // 立即滚动，不带动画
                                        pagerState.scrollToPage(uiState.initPage)
                                    }
                                }
                                // 4. 滚动完成后，标记为完成
                                isInitialScrollDone = true
                            }

                            // [MODIFIED]
                            // 5. 使用 Box 和 graphicsLayer 将内容隐藏 (alpha=0f)
                            //    直到 isInitialScrollDone 变为 true
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(alpha = if (isInitialScrollDone) 1f else 0f)
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
                                            // [修改] LazyColumn 控制左右内边距
                                            .padding(horizontal = uiState.padding),
                                        state = lazyListState
                                    ) {
                                        itemsIndexed(
                                            items = uiState.htmlList,
                                            // [修改] Key 现在基于行内容
                                            key = { index, item -> "${item.type}_${item.chapterTitle}_${item.data.hashCode()}_${index}" }
                                        ) { index, content ->
                                            // [修改] 移除 Box 和 Modifier.height(maxHeight)
                                            // [修改] 直接调用 ContentViewer
                                            ContentViewer(
                                                data = content,
                                                padding = uiState.padding, // 传递以便 ContentViewer (横屏) 使用
                                                lineHeight = uiState.lineHeight,
                                                letterSpacing = uiState.letterSpacing,
                                                fontSize = uiState.fontSize,
                                                currentPage = index + 1, // [修改] 这是 *行* 索引
                                                pageCount = uiState.htmlList.size, // [修改] 这是 *总行数*
                                                nightMode = uiState.nightMode,
                                                backgroundColor = finalBackground, // 传递以便 ContentViewer (横屏) 使用
                                                isVerticalMode = true // [重要] 告知 ContentViewer 处于竖屏模式
                                            )
                                        }
                                    }
                                } else {
                                    // [旧逻辑] 横屏翻页模式
                                    HorizontalPager(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable(
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() },
                                                onClick = { showSettings = true }
                                            )
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
                                            isVerticalMode = false // [旧逻辑]
                                        )

                                        SideEffect {
                                            readerVM.onPageChange(pagerState, scope)
                                        }
                                    }
                                }
                            } // [MODIFIED] 结束 `Box(alpha = ...)`
                        } // [MODIFIED] 结束 `if (uiState.htmlList.isNotEmpty())`

                        // --- 添加固定的顶部栏 ---
                        if (uiState.isVerticalMode && !showSettings && uiState.htmlList.isNotEmpty()) {
                            VerticalModeHeader(
                                chapterTitle = currentChapterTitle,
                                // [修改] currentPageIndex 是 0-based
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
                                                // [修改] 使用正确的 currentPageIndex (行或页)
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
                                    }
                                }
                            }
                            // 底部栏
                            ReaderSettingsBar(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter),
                                uiState = uiState,
                                // [修改] pageCount (总行数或总页数)
                                pageCount = uiState.htmlList.size,
                                // [修改] currentPage (当前行或当前页)
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
                                }
                            )
                        }
                    }
                    // [修改] 如果 !hasLoaded，则此处为空白，等待 isLoading 遮罩
                    // [修改] 如果 hasLoaded 但 htmlList 为空，也显示空白，等待数据
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
    }
}

// ... [ ReaderSettingsBar, ChapterDrawerContent, 和所有其他 Composable 保持不变 ] ...

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
    onSetReadingMode: (isVertical: Boolean) -> Unit
) {
    // 状态：用于控制显示主菜单还是二级“间距”菜单
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
        // 根据状态显示不同的菜单
        if (showSpacingMenu) {
            SpacingSettingsMenu(
                uiState = uiState,
                onSetFontSize = onSetFontSize,
                onSetLineHeight = onSetLineHeight,
                onSetPadding = onSetPadding,
                onBack = { showSpacingMenu = false },
                onSetReadingMode = onSetReadingMode
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
                onSetBackgroundColor = onSetBackgroundColor
            )
        }
    }
}

@Composable
fun ChapterDrawerContent(
    drawerState: DrawerState,
    chapterList: List<ChapterInfo>,
    currentChapterTitle: String?,
    pageCount: Int, // [新增]
    isVerticalMode: Boolean, // [新增]
    onChapterClick: (index: Int) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // [修改] pageCount (总行/页数)
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

                // [新增] 计算百分比或页码
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
                                text = pageLabel, // [修改]
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
    pageCount: Int, // [修改] 这是总行数或总页数
    currentPage: Int, // [修改] 这是当前行索引或页索引 (0-based)
    onSetView: (view: Int) -> Unit,
    onSetPage: (page: Int) -> Unit,
    onShowSpacingMenu: () -> Unit,
    onShowChapters: () -> Unit,
    onSetBackgroundColor: (color: Color?) -> Unit
) {
    // [修改] 模式判断
    val isVerticalMode = uiState.isVerticalMode

    // [修改] 滑块逻辑
    // 1. 确定滑块的 "值" (百分比或页索引)
    val sliderValue = if (isVerticalMode) uiState.currentPercentage else currentPage.toFloat()
    // 2. 确定滑块的 "范围"
    val sliderRange =
        if (isVerticalMode) 0f..100f else 0f..(pageCount - 1).toFloat().coerceAtLeast(0f)
    // 3. 确定滑块的 "步数" (100% / 100 步, N 页 / (N-1) 步)
    val sliderSteps =
        if (isVerticalMode) 98 else (pageCount - 2).coerceAtLeast(0) // 98 steps = 99 intervals

    // 4. 滑块的瞬时状态
    var sliderPos by remember(sliderValue) { // [修改] Keyed on sliderValue
        mutableFloatStateOf(sliderValue)
    }

    // 控制网页跳转弹窗的显示
    var showWebViewPageSelector by remember { mutableStateOf(false) }
    // 协程作用域
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
            Spacer(modifier = Modifier.weight(0.2f))
            // 右侧网页翻页控制区域
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .weight(1.5f)
                    .clickable(
                        // 只有大于1页时才允许点击
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
                    // 显示最大页数
                    text = "网页: ${uiState.currentView} / ${uiState.maxWebView}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(
                    onClick = { onSetView(uiState.currentView + 1) },
                    // 最后一页时禁用
                    enabled = uiState.currentView < uiState.maxWebView
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一页(网页)")
                }
            }
        }

        // [修改] 统一的阅读器进度Slider
        Column(modifier = Modifier.fillMaxWidth()) {
            // 5. 确定滑块 "显示的文本"
            val displayText = if (isVerticalMode) {
                "${sliderPos.roundToInt()}%"
            } else {
                "${sliderPos.roundToInt() + 1} / $pageCount"
            }

            Text(
                text = displayText, // [修改]
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Slider(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .align(Alignment.CenterHorizontally),
                value = sliderPos, // [修改]
                onValueChange = { sliderPos = it }, // [修改]
                onValueChangeFinished = {
                    // [修改] 6. 结束拖动时，计算目标 *索引*
                    val targetIndex = if (isVerticalMode) {
                        (sliderPos / 100f * pageCount.coerceAtLeast(1).toFloat())
                            .toInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                    } else {
                        sliderPos.roundToInt()
                    }
                    onSetPage(targetIndex)
                },
                valueRange = sliderRange, // [修改]
                steps = sliderSteps // [修改]
            )
        }


        // 缓存和间距
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
                onClick = { /* TODO: 占位，实现缓存逻辑 */ },
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                modifier = Modifier.weight(1f)
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
                modifier = Modifier.weight(1f)
            ) {
                Text("页面设置")
            }
        }

        // 背景颜色选择
        Spacer(modifier = Modifier.height(8.dp))
        ColorSwatchRow(
            selectedColor = uiState.backgroundColor,
            onColorSelected = onSetBackgroundColor
        )
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
                                            MaterialTheme.colorScheme.primary // 选中
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) // 未选中
                                    )
                                }
                                if (page < uiState.maxWebView) {
                                    HorizontalDivider(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) // 分割线
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showWebViewPageSelector = false }) {
                        Text(
                            "取消",
                            fontSize = 22.sp
                        )

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
    onSetReadingMode: (isVertical: Boolean) -> Unit
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
                modifier = Modifier.weight(1f) // 占据左侧空间
            )

            // 右侧：阅读模式选项
            Text(
                text = "模式",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                // 占位符状态，默认为竖屏 (false)
                val isVerticalMode = uiState.isVerticalMode
                // 横屏按钮
                IconToggleButton(
                    checked = !isVerticalMode,
                    onCheckedChange = { onSetReadingMode(false) },
                    colors = IconButtonDefaults.iconToggleButtonColors(
                        // 选中时的颜色
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
                        // 选中时的颜色
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

        Spacer(modifier = Modifier.height(8.dp)) // 保留原有的间距

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
            valueRange = 15f..65f, // 15dp 到 65dp
            steps = 9, // 10个档位
            onValueChange = { onSetPadding(it.dp) },
            showValue = false,
            startLabel = "窄",
            endLabel = "宽"
        )
    }
}

@Composable
private fun VerticalModeHeader(
    chapterTitle: String?,
    currentPage: Int, // [修改] 这是 1-based 索引
    pageCount: Int, // [修改] 这是总行数
    backgroundColor: Color,
    padding: Dp
) {
    val chapterTitleHeight = 24.dp

    // [修改] 计算百分比
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
                text = "${percent.roundToInt()}%", // [修改]
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