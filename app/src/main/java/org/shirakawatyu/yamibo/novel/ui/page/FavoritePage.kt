// novel/ui/page/FavoritePage.kt

package org.shirakawatyu.yamibo.novel.ui.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues // 导入 PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme // 导入 MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import org.shirakawatyu.yamibo.novel.item.FavoriteItem
import org.shirakawatyu.yamibo.novel.ui.theme.YamiboColors
import org.shirakawatyu.yamibo.novel.ui.vm.FavoriteVM
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
    favoriteVM: FavoriteVM = viewModel(),
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

    SetStatusBarColor(YamiboColors.onSurface)

    // [MODIFIED]
    // 监听生命周期，在 onResume 时 *只* 刷新网络列表
    // 本地列表的更新由 FavoriteVM 中的 Flow collector 自动处理
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // [MODIFIED] favoriteVM.loadFavorites() // 不再需要
                favoriteVM.refreshList(showLoading = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    // 长按触觉反馈
    val hapticFeedback = LocalHapticFeedback.current
    // ... (此 composable 的其余部分保持不变) ...
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
                // 保持间距
                Spacer(modifier = Modifier.width(24.dp))
                if (isInManageMode) {
                    // 管理模式
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
                    // 保持间距
                    Spacer(modifier = Modifier.width(24.dp))
                    Button(
                        onClick = { favoriteVM.unhideSelectedItems() },
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
                        Text("显示")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = { favoriteVM.toggleManageMode() }) {
                        Text("完成")
                    }
                } else {
                    // 非管理模式
                    Button(
                        onClick = { favoriteVM.toggleManageMode() },
                        contentPadding = PaddingValues(all = 8.dp)
                    ) {
                        Icon(Icons.Filled.Edit, "管理")
                    }
                    Spacer(modifier = Modifier.width(24.dp))

                    // 刷新按钮
                    if (isRefreshing) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = YamiboColors.primary,
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        Button(
                            onClick = { favoriteVM.refreshList() },
                            contentPadding = PaddingValues(all = 8.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, "刷新")
                        }
                    }
                }
            }
        }
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
    }
}