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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch
import org.shirakawatyu.yamibo.novel.R
import org.shirakawatyu.yamibo.novel.ui.state.ChapterInfo
import org.shirakawatyu.yamibo.novel.ui.state.ReaderState
import org.shirakawatyu.yamibo.novel.ui.theme.ReaderTheme
import org.shirakawatyu.yamibo.novel.ui.vm.ReaderVM
import org.shirakawatyu.yamibo.novel.ui.widget.ContentViewer
import org.shirakawatyu.yamibo.novel.ui.widget.PassageWebView
import org.shirakawatyu.yamibo.novel.util.ComposeUtil.Companion.SetStatusBarColor
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
        // 页面数
        val pagerState = rememberPagerState(pageCount = { uiState.htmlList.size })
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
        // 当前章节标题
        val currentChapterTitle =
            if (uiState.htmlList.isNotEmpty() && pagerState.currentPage < uiState.htmlList.size) {
                uiState.htmlList[pagerState.currentPage].chapterTitle
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
        // 在首次加载时调用readerVM的firstLoad方法
        BoxWithConstraints {
            LaunchedEffect(Unit) {
                readerVM.firstLoad(url, maxHeight, maxWidth)
            }
        }
        // 监听HTML列表数据变化
        LaunchedEffect(uiState.htmlList.firstOrNull(), uiState.htmlList.size) {
            // 改变的列表不为空时滚动到初始页面
            if (uiState.htmlList.isNotEmpty()) {
                pagerState.scrollToPage(uiState.initPage)
            }
        }
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
                        onChapterClick = { index ->
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                            readerVM.toggleChapterDrawer(false)
                        }
                    )
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(finalBackground)
                ) {
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
                    } else {
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
                                backgroundColor = finalBackground
                            )

                            SideEffect {
                                readerVM.onPageChange(pagerState, scope)
                            }
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
                                                val currentPage = pagerState.currentPage
                                                readerVM.saveSettings(currentPage)
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
                                pageCount = pagerState.pageCount,
                                currentPage = pagerState.currentPage,
                                onSetView = { readerVM.onSetView(it) },
                                onSetPage = {
                                    scope.launch {
                                        pagerState.scrollToPage(it)
                                    }
                                },
                                onSetFontSize = { readerVM.onSetFontSize(it) },
                                onSetLineHeight = { readerVM.onSetLineHeight(it) },
                                onSetPadding = { readerVM.onSetPadding(it) },
                                onShowChapters = { readerVM.toggleChapterDrawer(true) },
                                onSetBackgroundColor = { readerVM.onSetBackgroundColor(it) }
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
    onSetBackgroundColor: (color: Color?) -> Unit
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
                onSetLineHeight = onSetLineHeight,
                onSetPadding = onSetPadding,
                onBack = { showSpacingMenu = false }
            )
        } else {
            MainSettingsMenu(
                uiState = uiState,
                pageCount = pageCount,
                currentPage = currentPage,
                onSetView = onSetView,
                onSetPage = onSetPage,
                onSetFontSize = onSetFontSize,
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
    onChapterClick: (index: Int) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

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
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text(
                                text = chapter.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "第 ${chapter.startIndex + 1} 页",
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
    onSetFontSize: (fontSize: TextUnit) -> Unit,
    onShowSpacingMenu: () -> Unit,
    onShowChapters: () -> Unit,
    onSetBackgroundColor: (color: Color?) -> Unit
) {
    // 用于平滑拖动 Slider，仅在拖动结束后才触发翻页
    var sliderPage by remember(currentPage) {
        mutableFloatStateOf(currentPage.toFloat())
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

        // 阅读器翻页Slider
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "页数: ${sliderPage.roundToInt() + 1} / $pageCount",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Slider(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .align(Alignment.CenterHorizontally),
                value = sliderPage,
                onValueChange = { sliderPage = it },
                onValueChangeFinished = {
                    onSetPage(sliderPage.roundToInt())
                },
                valueRange = 0f..(pageCount - 1).toFloat().coerceAtLeast(0f),
                steps = (pageCount - 2).coerceAtLeast(0) // 步数
            )
        }


        // 字体和间距
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.weight(0.03f))
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
                modifier = Modifier.weight(1.77f)
            )

            // 右侧：间距按钮
            Button(
                onClick = onShowSpacingMenu,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .weight(1.14f)
            ) {
                Text("间距")
            }
            Spacer(modifier = Modifier.weight(0.06f))
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
 * 二级菜单 - 间距调节 (行高, 页距)
 */
@Composable
private fun SpacingSettingsMenu(
    uiState: ReaderState,
    onSetLineHeight: (lineHeight: TextUnit) -> Unit,
    onSetPadding: (padding: Dp) -> Unit,
    onBack: () -> Unit
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
                text = "间距调节",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        val minLineHeight = (uiState.fontSize.value * 1.6f)
        val maxLineHeight = (uiState.fontSize.value * 3.0f)
        SettingsSlider(
            label = "行距",
            value = uiState.lineHeight.value,
            valueRange = minLineHeight..maxLineHeight,
            steps = 9,
            onValueChange = { onSetLineHeight(it.sp) },
            showValue = false,
            startLabel = "小",
            endLabel = "大"
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsSlider(
            label = "页距",
            value = uiState.padding.value,
            valueRange = 10f..60f, // 0dp 到 40dp
            steps = 9, // 10个档位
            onValueChange = { onSetPadding(it.dp) },
            showValue = false,
            startLabel = "窄",
            endLabel = "宽"
        )
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