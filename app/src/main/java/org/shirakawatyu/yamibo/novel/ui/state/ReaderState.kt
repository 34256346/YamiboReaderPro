package org.shirakawatyu.yamibo.novel.ui.state

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shirakawatyu.yamibo.novel.bean.Content

/**
 * 章节信息
 * @param title 章节标题
 * @param startIndex 章节在 htmlList 中的起始页索引
 */
data class ChapterInfo(
    val title: String,
    val startIndex: Int
)

/**
 * 数据类，表示阅读器的状态信息
 *
 * @property htmlList HTML内容列表，默认为空列表
 * @property currentView 当前视图索引，默认为1
 * @property initPage 初始页面索引，默认为0
 * @property lineHeight 文本行高，默认为43.sp
 * @property padding 内边距，默认为16.dp
 * @property fontSize 字体大小，默认为24.sp
 * @property letterSpacing 字符间距，默认为2.sp
 * @property scale 缩放比例，可变，默认为1f
 * @property offset 偏移量，可变，默认为Offset(0f, 0f)
 * @property isLoading 是否正在加载，默认为false
 * @property chapterList 章节信息列表，默认为空列表
 * @property showChapterDrawer 是否显示章节抽屉，默认为false
 * @property maxWebView 最大帖子页面数量，默认为1
 * @property urlToLoad 需要加载的URL，默认为空字符串
 * @property loadImages 是否加载图片，默认为false
 * @property nightMode 是否为夜间模式，默认为false
 * @property isError 是否发生错误，默认为false
 * @property backgroundColor 自定义背景颜色，null表示使用主题默认
 * @property isVerticalMode 是否为竖屏滚动模式，默认为false
 * @property currentPercentage 当前阅读百分比（竖直模式），默认为0f
 */
data class ReaderState(
    val htmlList: List<Content> = listOf(),
    val currentView: Int = 1,
    val initPage: Int = 0,
    val lineHeight: TextUnit = 43.sp,
    val padding: Dp = 16.dp,
    val fontSize: TextUnit = 24.sp,
    val letterSpacing: TextUnit = 2.sp,
    var scale: Float = 1f,
    var offset: Offset = Offset(0f, 0f),
    val isLoading: Boolean = false,
    val chapterList: List<ChapterInfo> = listOf(),
    val showChapterDrawer: Boolean = false,
    val maxWebView: Int = 1,
    val urlToLoad: String = "",
    val loadImages: Boolean = false,
    val nightMode: Boolean = false,
    val isError: Boolean = false,
    val backgroundColor: Color? = null,
    val isVerticalMode: Boolean = false,
    val currentPercentage: Float = 0f,
)