package org.shirakawatyu.yamibo.novel.item

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shirakawatyu.yamibo.novel.ui.theme.YamiboColors

/**
 * 收藏项组件，用于展示收藏的小说条目信息。
 *
 * @param title 小说标题
 * @param lastView 上次阅读的网页页码
 * @param lastPage 上次阅读的阅读器页码
 * @param lastChapter 上次阅读的章节名称，可能为空
 * @param onClick 点击该项时触发的回调函数
 * @param modifier 组件修饰符，默认为Modifier
 * @param isDragging 是否正在拖拽状态，影响动画效果，默认为false
 * @param dragHandle 拖拽手柄的可组合项
 */
@Composable
fun FavoriteItem(
    title: String,
    lastView: Int,
    lastPage: Int,
    lastChapter: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    dragHandle: @Composable (() -> Unit) = {},
    isManageMode: Boolean = false,
    isSelected: Boolean = false,
    isHidden: Boolean = false
) {
    // 拖拽动画：根据是否处于拖拽状态动态调整卡片的阴影、缩放和颜色
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 1.dp,
        label = "elevation_animation"
    )
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1.0f,
        label = "scale_animation"
    )
    val color by animateColorAsState(
        targetValue = when {
            isDragging -> YamiboColors.onSurface // 拖拽时
            isManageMode && isSelected -> YamiboColors.secondary // 管理模式 + 选中
            isManageMode && isHidden -> Color.LightGray // 管理模式+已隐藏(未选中)
            else -> YamiboColors.tertiary // 默认
        },
        label = "color_animation"
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(5.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(15.dp, 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    modifier = Modifier.padding(0.dp, 5.dp),
                    fontSize = 16.sp,
                    color = Color.Black,
                    maxLines = 2,
                    text = title
                )
                // 显示最近阅读章节名（如果存在且非空）
                if (lastChapter != null && lastChapter.isNotBlank()) {
                    Text(
                        modifier = Modifier.padding(0.dp, 2.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        text = lastChapter,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    color = Color.Black,
                    fontSize = 12.sp,
                    text = "上次读到第${lastPage + 1}页, 对应网页第${lastView}页"
                )
                if (isManageMode && isHidden) {
                Text(
                    modifier = Modifier.padding(top = 2.dp),
                    color = Color.Gray,
                    fontSize = 12.sp,
                    text = "[已隐藏]"
                )
            }
            }
            // 拖拽手柄
            Row(verticalAlignment = Alignment.CenterVertically) {
                dragHandle()
            }
        }
    }
}

@Preview
@Composable
fun FavoriteItemPreview() {
    FavoriteItem(
        title = "一周一次买下同班同学的那些事",
        lastView = 1,
        lastPage = 2,
        lastChapter = "episode 397 宫城志绪理",
        onClick = {}
    )
}

@Preview
@Composable
fun FavoriteItemManageModePreview() {
    Column {
        FavoriteItem(
            title = "正常项目",
            lastView = 1, lastPage = 2, lastChapter = "Chapter 1",
            onClick = {}, isManageMode = true, isSelected = false, isHidden = false
        )
        FavoriteItem(
            title = "选中项目",
            lastView = 1, lastPage = 2, lastChapter = "Chapter 2",
            onClick = {}, isManageMode = true, isSelected = true, isHidden = false
        )
        FavoriteItem(
            title = "隐藏项目",
            lastView = 1, lastPage = 2, lastChapter = "Chapter 3",
            onClick = {}, isManageMode = true, isSelected = false, isHidden = true
        )
        FavoriteItem(
            title = "选中的隐藏项目",
            lastView = 1, lastPage = 2, lastChapter = "Chapter 4",
            onClick = {}, isManageMode = true, isSelected = true, isHidden = true
        )
    }
}