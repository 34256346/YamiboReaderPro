package org.shirakawatyu.yamibo.novel.ui.widget

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
    isVerticalMode: Boolean = false
) {
    // 顶部章节标题的固定高度（标题 + 间距）
    val chapterTitleHeight = 24.dp
    val cookie by GlobalData.cookieFlow.collectAsState(initial = "")

    // [新增] 竖屏模式的特殊处理
    if (isVerticalMode) {
        // 在竖屏模式下，padding(horizontal) 是由 ReaderPage 的 LazyColumn 的 modifier 提供的
        // 我们只需要处理顶部和底部的间距（即行高/图片高度）

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
                    .fillMaxWidth() // 宽度占满 (padding 已由 LazyColumn 处理)
                    .padding(vertical = 4.dp), // 图片上下加一点间距
                model = imageRequestBuilder.build(),
                onError = {
                    it.result.throwable.printStackTrace()
                    Log.e("SubcomposeAsyncImage", data.data)
                },
                error = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp), // 失败提示需要自己的 padding
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

            // [修改] 如果 data.data 为空，我们将其渲染为一个空行（段落间距）
            if (data.data.isEmpty()) {
                // 这是一个段落分隔符。渲染一个固定高度的空行 (例如 1/3 行高)
                // [FIX] Convert TextUnit (sp) to Dp
                Spacer(modifier = Modifier.height(ValueUtil.spToDp(lineHeight) / 3))
            } else {
                // 这是一个正常的文本行
                JustifiedText(
                    modifier = Modifier
                        .fillMaxWidth() // 宽度占满 (padding 已由 LazyColumn 处理)
                        .height(ValueUtil.spToDp(lineHeight)), // [FIX] Convert TextUnit (sp) to Dp
                    text = data.data,
                    lineHeight = lineHeight,
                    fontSize = fontSize,
                    letterSpacing = letterSpacing,
                    color = MaterialTheme.colorScheme.onBackground,
                    isVerticalMode = true // [新增] 告知 JustifiedText
                )
            }
        }
        // [竖屏模式] 不渲染章节标题和页码 (由 ReaderPage.VerticalModeHeader 处理)

    } else {
        // --- [旧逻辑] 横屏翻页模式 ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(horizontal = padding) // 横屏模式自己控制 padding
        ) {

            // [旧逻辑] 章节标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chapterTitleHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 章节标题（左）
                data.chapterTitle?.takeIf { it.isNotBlank() }?.let { title ->
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
            }

            // [旧逻辑] 正文内容
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
                JustifiedText(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    text = data.data,
                    lineHeight = lineHeight,
                    fontSize = fontSize,
                    letterSpacing = letterSpacing,
                    color = MaterialTheme.colorScheme.onBackground,
                    isVerticalMode = false // [旧逻辑]
                )
            }

            // [旧逻辑] 页码
            if (!isVerticalMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 4.dp, top = 1.dp, bottom = 4.dp)
                        .height(50.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
}