package org.shirakawatyu.yamibo.novel.ui.widget

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import org.shirakawatyu.yamibo.novel.bean.Content
import org.shirakawatyu.yamibo.novel.bean.ContentType
import org.shirakawatyu.yamibo.novel.constant.RequestConfig
import org.shirakawatyu.yamibo.novel.global.GlobalData
import org.shirakawatyu.yamibo.novel.util.ValueUtil // [NEW] Import ValueUtil

@Composable
fun ContentViewer(
    data: Content,
    padding: Dp = 16.dp,
    lineHeight: TextUnit = 43.sp,
    fontSize: TextUnit = 24.sp,
    letterSpacing: TextUnit = 2.sp,
    currentPage: Int,
    pageCount: Int,
    nightMode: Boolean,
    backgroundColor: Color,
    isVerticalMode: Boolean = false,
    onRefresh: () -> Unit = {},
    bookTitle: String = ""
) {
    // 顶部章节标题的固定高度（标题 + 间距）
    val chapterTitleHeight = 24.dp
    val cookie by GlobalData.cookieFlow.collectAsState(initial = "")

    // 竖屏模式的特殊处理
    if (isVerticalMode) {
        if (data.type == ContentType.IMG) {
            // [竖屏图片]
            val imageRequestBuilder = ImageRequest.Builder(LocalContext.current)
                .data(data.data)
                .crossfade(true)
                .addHeader("User-Agent", RequestConfig.UA)
                .bitmapConfig(Bitmap.Config.RGB_565)

            if (cookie.isNotBlank()) {
                imageRequestBuilder.addHeader("Cookie", cookie)
            }

            SubcomposeAsyncImage(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                model = imageRequestBuilder.build(),
                onError = {
                    it.result.throwable.printStackTrace()
                    Log.e("SubcomposeAsyncImage", data.data)
                },
                error = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            "Image failed to load",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "图片加载失败",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                contentDescription = "Image Content",
                loading = { CircularProgressIndicator() })
        } else if (data.type == ContentType.TEXT) {
            // [竖屏文本行]
            if (data.chapterTitle == "footer" && data.data.contains("正在加载下一页") || data.data == "刷新本页内容") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("刷新本页内容")
                    }
                }
            } else if (data.data.isEmpty()) {
                Spacer(modifier = Modifier.height(ValueUtil.spToDp(lineHeight) / 3))
            } else {
                // 正常的文本行
                JustifiedText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ValueUtil.spToDp(lineHeight)),
                    text = data.data,
                    lineHeight = lineHeight,
                    fontSize = fontSize,
                    letterSpacing = letterSpacing,
                    color = MaterialTheme.colorScheme.onBackground,
                    isVerticalMode = true
                )
            }
        }
    } else {
        // --- 横屏翻页模式 ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(horizontal = padding)
        ) {

            // 章节标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chapterTitleHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 书籍标题（左）
                if (bookTitle.isNotBlank()) {
                    Text(
                        text = bookTitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 1.dp, top = 4.dp, end = 4.dp),
                        textAlign = TextAlign.Start
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // 章节标题（右）
                data.chapterTitle?.takeIf { it.isNotBlank() && it != "footer" }?.let { title ->
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 7.dp, top = 4.dp),
                        textAlign = TextAlign.End
                    )
                }
            }


            // 正文内容
            if (data.type == ContentType.IMG) {
                val imageRequestBuilder = ImageRequest.Builder(LocalContext.current)
                    .data(data.data)
                    .crossfade(true)
                    .addHeader("User-Agent", RequestConfig.UA)
                    .bitmapConfig(Bitmap.Config.RGB_565)

                if (cookie.isNotBlank()) {
                    imageRequestBuilder.addHeader("Cookie", cookie)
                }

                SubcomposeAsyncImage(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    model = imageRequestBuilder.build(),
                    onError = {
                        it.result.throwable.printStackTrace()
                        Log.e("SubcomposeAsyncImage", data.data)
                    },
                    error = {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                "Image failed to load",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "图片加载失败",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    contentDescription = "Image Content",
                    loading = { CircularProgressIndicator() })
            } else if (data.type == ContentType.TEXT) {
                if (data.chapterTitle == "footer" && data.data.contains("正在加载下一页") || data.data == "刷新本页内容") {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("刷新本页内容")
                        }
                    }
                } else {
                    JustifiedText(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        text = data.data,
                        lineHeight = lineHeight,
                        fontSize = fontSize,
                        letterSpacing = letterSpacing,
                        color = MaterialTheme.colorScheme.onBackground,
                        isVerticalMode = false
                    )
                }
            }

            // 页码
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 4.dp, top = 1.dp, bottom = 4.dp)
                    .height(50.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 右侧显示页码
                Text(
                    text = "${currentPage}/${pageCount}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}