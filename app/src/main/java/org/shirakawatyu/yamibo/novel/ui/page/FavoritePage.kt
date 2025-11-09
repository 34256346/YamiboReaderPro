package org.shirakawatyu.yamibo.novel.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues // 导入 PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme // 导入 MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import org.shirakawatyu.yamibo.novel.R
import org.shirakawatyu.yamibo.novel.bean.Favorite
import org.shirakawatyu.yamibo.novel.item.FavoriteItem
import org.shirakawatyu.yamibo.novel.ui.theme.YamiboColors
import org.shirakawatyu.yamibo.novel.ui.vm.FavoriteVM
import org.shirakawatyu.yamibo.novel.ui.vm.ViewModelFactory
import org.shirakawatyu.yamibo.novel.ui.widget.TopBar
import org.shirakawatyu.yamibo.novel.util.ComposeUtil.Companion.SetStatusBarColor
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 收藏页面，展示用户的收藏列表，支持刷新和拖拽排序。
 *
 * @param favoriteVM 用于管理收藏数据的 ViewModel，默认通过 viewModel() 获取实例。
 * @param navController 导航控制器，用于跳转到其他页面。
 */
@Composable
fun FavoritePage(
    favoriteVM: FavoriteVM = viewModel(
        factory = ViewModelFactory(LocalContext.current.applicationContext)
    ),
    navController: NavController
) {
    val uiState by favoriteVM.uiState.collectAsState()
    // 获取收藏列表 (已根据管理模式过滤)
    val favoriteList = uiState.favoriteList
    // 获取收藏列表的刷新状态
    val isRefreshing = uiState.isRefreshing
    // 获取管理模式状态
    val isInManageMode = uiState.isInManageMode
    // 获取选中项
    val selectedItems = uiState.selectedItems
    // 缓存信息
    var cacheInfoMap = uiState.cacheInfoMap
    var showCacheManagement by remember { mutableStateOf(false) }
    // 加载缓存信息
    LaunchedEffect(Unit) {
        favoriteVM.refreshCacheInfo()
    }
    SetStatusBarColor(YamiboColors.onSurface)

    // 监听生命周期，在onResume时只刷新网络列表
    // 本地列表的更新由FavoriteVM中的Flow collector自动处理
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                favoriteVM.refreshList(showLoading = false)
                favoriteVM.getCacheInfo { info ->
                    cacheInfoMap = info
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    // 长按触觉反馈
    val hapticFeedback = LocalHapticFeedback.current
    // implementation("sh.calvin.reorderable:reorderable:3.0.0")
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            // 仅在非管理模式下允许拖拽排序
            if (!isInManageMode) {
                favoriteVM.moveFavorite(from.index, to.index)
            }
        }
    )
    Column {
        // TopBar
        TopBar(title = if (isInManageMode) "管理收藏 (${selectedItems.size})" else "收藏") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 保持间距 (这是 'if' 分支的)
                Spacer(modifier = Modifier.width(12.dp))
                if (isInManageMode) {
                    // 管理模式 (保持不变)
                    Button(
                        onClick = { favoriteVM.hideSelectedItems() },
                        enabled = selectedItems.isNotEmpty(),
                        // 使用较小的内边距
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.67f
                            )
                        )
                    ) {
                        Text("隐藏")
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    Button(
                        onClick = { favoriteVM.unhideSelectedItems() },
                        enabled = selectedItems.isNotEmpty(),
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.67f
                            )
                        )
                    ) {
                        Text("显示")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(onClick = { favoriteVM.toggleManageMode() }) {
                        Text("完成")
                    }
                } else {
                    // 非管理模式
                    // 1. Spacer将菜单推到最右侧
                    Spacer(modifier = Modifier.weight(1f))

                    // 2. 菜单展开状态
                    var menuExpanded by remember { mutableStateOf(false) }

                    // 3. Box用于锚定菜单
                    Box {
                        // "..." 菜单触发按钮
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_more_horiz),
                                contentDescription = "更多选项",
                                modifier = Modifier.size(24.dp),
                                tint = YamiboColors.primary
                            )
                        }

                        // 4. 下拉菜单本身
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            offset = DpOffset(x = 9.dp, y = 16.dp),
                            modifier = Modifier
                                .background(Color(0xFFFFFCF0))
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            // 菜单项 1: 缓存管理
                            DropdownMenuItem(
                                text = { Text("管理缓存") },
                                onClick = {
                                    showCacheManagement = true
                                    menuExpanded = false
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_download),
                                        contentDescription = "缓存管理",
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    end = 96.dp
                                )
                            )

                            // 菜单项 2: 管理收藏
                            DropdownMenuItem(
                                text = { Text("管理收藏") },
                                onClick = {
                                    favoriteVM.toggleManageMode()
                                    menuExpanded = false
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_visibility),
                                        contentDescription = "管理收藏",
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    end = 96.dp
                                )
                            )

                            // 菜单项 3: 刷新列表
                            DropdownMenuItem(
                                text = { Text("刷新列表") },
                                onClick = {
                                    favoriteVM.refreshList()
                                    menuExpanded = false
                                },
                                enabled = !isRefreshing, // 正在刷新时禁用
                                leadingIcon = {
                                    if (isRefreshing) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    } else {
                                        Icon(
                                            Icons.Filled.Refresh,
                                            contentDescription = "刷新列表",
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                },
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    end = 96.dp
                                )
                            )
                        }
                    }
                }
            }
        }

        // LazyColumn (收藏列表)
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.padding(0.dp, 3.dp)
        ) {

            itemsIndexed(
                items = favoriteList,
                key = { _, item -> item.url }
            ) { index, item ->
                // 使用ReorderableItem包装item以支持拖动排序
                ReorderableItem(
                    state = reorderableState,
                    key = item.url,
                ) { isDragging ->
                    val isSelected = selectedItems.contains(item.url)
                    FavoriteItem(
                        item.title,
                        item.lastView,
                        item.lastPage,
                        item.lastChapter,
                        onClick = {
                            // 点击逻辑
                            if (isInManageMode) {
                                favoriteVM.toggleItemSelection(item.url)
                            } else {
                                favoriteVM.clickHandler(item.url, navController)
                            }
                        },
                        // 拖拽手柄只在非管理模式下启用
                        modifier = Modifier.longPressDraggableHandle(
                            enabled = !isInManageMode,
                            onDragStarted = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        ),
                        isDragging = isDragging,
                        // 传递管理状态
                        isManageMode = isInManageMode,
                        isSelected = isSelected,
                        isHidden = item.isHidden,
                        cacheInfo = cacheInfoMap[item.url],
                        // 拖拽手柄只在非管理模式下显示
                        dragHandle = {
                            if (!isInManageMode) {
                                Icon(
                                    Icons.Filled.Menu,
                                    contentDescription = "Reorder",
                                    tint = YamiboColors.primary
                                )
                            }
                        }
                    )
                }
            }
        }

        // 缓存管理对话框
        if (showCacheManagement) {
            CacheManagementDialog(
                favoriteList = favoriteList,
                cacheInfoMap = cacheInfoMap,
                onDismiss = { showCacheManagement = false },
                onDeleteCache = { url ->
                    favoriteVM.deleteFavoriteCache(url)
                },
                onClearAll = {
                    favoriteVM.clearAllCache()
                }
            )
        }
    }
}

// 在文件末尾添加缓存管理对话框
@Composable
fun CacheManagementDialog(
    favoriteList: List<Favorite>,
    cacheInfoMap: Map<String, FavoriteVM.CacheInfo>,
    onDismiss: () -> Unit,
    onDeleteCache: (String) -> Unit,
    onClearAll: () -> Unit
) {
    var showClearAllConfirm by remember { mutableStateOf(false) }

    // 格式化文件大小的辅助函数
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "缓存管理",
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                // 统计信息
                val totalCached = cacheInfoMap.values.sumOf { it.totalPages }
                val totalSize = cacheInfoMap.values.sumOf { it.totalSize }

                if (cacheInfoMap.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                "总计: ${cacheInfoMap.size} 部作品",
                                fontSize = 14.sp
                            )
                            Text(
                                "缓存页数: $totalCached 页",
                                fontSize = 14.sp
                            )
                            Text(
                                "占用空间: ${formatFileSize(totalSize)}",
                                fontSize = 14.sp
                            )
                        }
                    }

                    HorizontalDivider()

                    // 作品列表
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 分离数据
                        val favoriteUrls = favoriteList.map { it.url }.toSet()

                        // 仍在收藏中的缓存 (有标题)
                        val favoriteCaches =
                            favoriteList.filter { cacheInfoMap.containsKey(it.url) }

                        // 已移除的缓存 (孤立的)
                        val orphanedCaches =
                            cacheInfoMap.values.filter { !favoriteUrls.contains(it.url) }

                        items(
                            items = favoriteCaches,
                            key = { "favorite_${it.url}" }
                        ) { favorite ->
                            val info = cacheInfoMap[favorite.url]!!
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        favorite.title, // 使用收藏标题
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${info.totalPages} 页 · ${formatFileSize(info.totalSize)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteCache(favorite.url) }) {
                                    Icon(
                                        Icons.Default.Delete, "删除缓存",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                        // 3. 渲染孤立的缓存
                        if (orphanedCaches.isNotEmpty()) {
                            item(key = "orphan_header") {
                                Text(
                                    "已取消收藏的帖子的缓存",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            items(
                                items = orphanedCaches,
                                key = { "orphan_${it.url}" }
                            ) { info ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            info.url, // 只能显示 URL
                                            fontSize = 12.sp, // 用小号字体显示 URL
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "${info.totalPages} 页 · ${formatFileSize(info.totalSize)}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onDeleteCache(info.url) }) {
                                        Icon(
                                            Icons.Default.Delete, "删除缓存",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                } else {
                    // 无缓存提示
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_cache),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "暂无缓存",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "在阅读页面点击缓存按钮\n即可开始缓存小说",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (cacheInfoMap.isNotEmpty()) {
                TextButton(
                    onClick = { showClearAllConfirm = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("清空所有缓存")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )

    // 清空确认对话框
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("确认清空") },
            text = { Text("确定要清空所有缓存吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showClearAllConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}