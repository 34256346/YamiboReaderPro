package org.shirakawatyu.yamibo.novel.util

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import org.shirakawatyu.yamibo.novel.bean.Content
import org.shirakawatyu.yamibo.novel.bean.ContentType

// 分页算法
class TextUtil {
    companion object {
        private val PUNCTUATION_LINE_START_DENY_SET = BooleanArray(0x10000).apply {
            "，。,、.!？?）」)]}”\"'".forEach { char ->
                if (char.code < size) this[char.code] = true
            }
        }

        private val PUNCTUATION_LINE_END_DENY_SET = BooleanArray(0x10000).apply {
            "（(「[{“\"'".forEach { char ->
                if (char.code < size) this[char.code] = true
            }
        }

        // ASCII 可打印字符范围
        private const val ASCII_START = 0x0020
        private const val ASCII_END = 0x007e

        fun pagingText(
            text: String,
            height: Dp,
            width: Dp,
            fontSize: TextUnit,
            letterSpacing: TextUnit,
            lineHeight: TextUnit
        ): List<String> {
            // 转换为像素值
            val targetPixelWidth = ValueUtil.dpToPx(width)
            val pageContentHeight = ValueUtil.dpToPx(height)
            val lineHeightPx = ValueUtil.spToPx(lineHeight)
            // 使用安全区域计算最大行数
            val maxLine = calculateMaxLines(pageContentHeight, lineHeightPx)

            // 边界保护：无法显示任何行
            if (maxLine <= 0 || text.isEmpty()) {
                return emptyList()
            }

            val fontSizePx = ValueUtil.spToPx(fontSize)
            val letterSpacingPx = ValueUtil.spToPx(letterSpacing)
            val halfWidthPx = (0.5f * fontSizePx) + letterSpacingPx
            val fullWidthPx = fontSizePx + letterSpacingPx

            return performPaging(
                text = text,
                targetPixelWidth = targetPixelWidth,
                maxLine = maxLine,
                halfWidthPx = halfWidthPx,
                fullWidthPx = fullWidthPx
            )
        }

        private fun calculateMaxLines(
            totalHeightPx: Float,
            lineHeightPx: Float,
            safeAreaRatio: Float = 1.0f // 为了竖直滑动时无空白，不设置安全区了
        ): Int {
            val safeHeight = totalHeightPx * safeAreaRatio
            val calculatedLines = (safeHeight / lineHeightPx).toInt()

            // 确保至少有1行，且不会超出安全区域
            return calculatedLines.coerceAtLeast(1)
        }

        private fun performPaging(
            text: String,
            targetPixelWidth: Float,
            maxLine: Int,
            halfWidthPx: Float,
            fullWidthPx: Float
        ): List<String> {
            // 预估行数
            val avgCharsPerLine = (targetPixelWidth / fullWidthPx).toInt().coerceAtLeast(1)
            val estimatedTotalLines = (text.length / avgCharsPerLine).coerceAtLeast(1)
            val resultLines = ArrayList<String>(estimatedTotalLines)
            var isStartOfParagraph = true // 标记是否为段落首行
            // 1. 将源文本拆分为 "内容行" 和 "段落标记行" ("")
            text.lineSequence()
                .forEach { line ->
                    if (line.isBlank()) {
                        // 这是一个段落分隔符
                        if (resultLines.isNotEmpty() && resultLines.last().isNotEmpty()) {
                            resultLines.add("") // 添加标记行
                        }
                        isStartOfParagraph = true
                    } else {
                        // 这是一个内容行，对其进行分行
                        val lineToChunk: String
                        if (isStartOfParagraph) {
                            // 1. 移除所有行首的空白符 (包括全角和半角)
                            val trimmedLine = line.trimStart(' ', '　')
                            // 2. 统一添加两个全角空格
                            lineToChunk = "　　$trimmedLine"
                            isStartOfParagraph = false // 重置标记
                        } else {
                            lineToChunk = line // 不是段落首行，保持原样
                        }
                        chunkLineOptimized(
                            line = lineToChunk,
                            targetPixelWidth = targetPixelWidth,
                            halfWidthPx = halfWidthPx,
                            fullWidthPx = fullWidthPx,
                            output = resultLines
                        )
                    }
                }

            // 2. [关键修复] 按 "内容行" 数量 (maxLine) 组装页面
            val estimatedPages = (resultLines.size / maxLine).coerceAtLeast(1)
            val pages = ArrayList<String>(estimatedPages)

            var lineIndex = 0 // resultLines 的总游标
            while (lineIndex < resultLines.size) {
                val pageBuilder = StringBuilder()
                var contentLinesOnThisPage = 0 // 当前页已添加的 *内容行* 数量
                var lastIndexForThisPage = lineIndex // 当前页的结束游标

                // 循环查找当前页的结束位置
                while (lastIndexForThisPage < resultLines.size) {
                    val currentLine = resultLines[lastIndexForThisPage]

                    if (currentLine.isNotEmpty()) {
                        // 这是一个内容行
                        if (contentLinesOnThisPage >= maxLine) {
                            // 内容行已满, 这行属于下一页
                            break // 停止, [lastIndexForThisPage] 将是下一页的开头
                        }
                        contentLinesOnThisPage++ // 计入内容行
                    }
                    // else: 这是一个 "" 标记行, 我们总是把它包含在当前页, 且不计入 maxLine

                    lastIndexForThisPage++ // 将这行 (内容或标记) 包含在当前页
                }

                // 3. 根据计算好的起止索引, 构建页面字符串
                for (k in lineIndex until lastIndexForThisPage) {
                    pageBuilder.append(resultLines[k])
                    if (k < lastIndexForThisPage - 1) {
                        pageBuilder.append('\n')
                    }
                }

                // 4. 添加页面 (确保非空)
                if (pageBuilder.isNotEmpty()) {
                    pages.add(pageBuilder.toString())
                }

                // 5. 设置下一页的起始索引
                lineIndex = lastIndexForThisPage
            }

            return pages
        }

        private fun chunkLineOptimized(
            line: String,
            targetPixelWidth: Float,
            halfWidthPx: Float,
            fullWidthPx: Float,
            output: MutableList<String>
        ) {
            val lineLength = line.length
            val charWidths = FloatArray(lineLength) { i ->
                if (line[i].code in ASCII_START..ASCII_END) halfWidthPx else fullWidthPx
            }

            val lineBuilder = StringBuilder()
            var currentWidth = 0.0f
            var i = 0

            while (i < lineLength) {
                val c = line[i]
                val charWidth = charWidths[i]

                if (currentWidth + charWidth > targetPixelWidth && lineBuilder.isNotEmpty()) {
                    val newWidth = handlePunctuationOptimized(
                        lineBuilder = lineBuilder,
                        currentChar = c,
                        chunks = output,
                        halfWidthPx = halfWidthPx,
                        fullWidthPx = fullWidthPx
                    )

                    if (newWidth != null) {
                        currentWidth = newWidth
                        i++
                        continue
                    }

                    // 正常换行
                    output.add(lineBuilder.toString())
                    lineBuilder.clear()
                    currentWidth = 0.0f
                }

                currentWidth += charWidth
                lineBuilder.append(c)
                i++
            }

            if (lineBuilder.isNotEmpty()) {
                output.add(lineBuilder.toString())
            }
        }

        private fun getCharWidth(c: Char, halfWidthPx: Float, fullWidthPx: Float): Float {
            return if (c.code in ASCII_START..ASCII_END) halfWidthPx else fullWidthPx
        }

        private fun handlePunctuationOptimized(
            lineBuilder: StringBuilder,
            currentChar: Char,
            chunks: MutableList<String>,
            halfWidthPx: Float,
            fullWidthPx: Float
        ): Float? {
            if (lineBuilder.isEmpty()) return null

            val lastChar = lineBuilder[lineBuilder.length - 1]

            // 避头：当前字符不能在行首
            if (currentChar.code < PUNCTUATION_LINE_START_DENY_SET.size &&
                PUNCTUATION_LINE_START_DENY_SET[currentChar.code]
            ) {
                lineBuilder.append(currentChar)
                chunks.add(lineBuilder.toString())
                lineBuilder.clear()
                return 0.0f
            }

            // 避尾：上一字符不能在行尾
            if (lastChar.code < PUNCTUATION_LINE_END_DENY_SET.size &&
                PUNCTUATION_LINE_END_DENY_SET[lastChar.code]
            ) {
                val newLineLength = lineBuilder.length - 1
                if (newLineLength > 0) {
                    chunks.add(lineBuilder.substring(0, newLineLength))
                    lineBuilder.clear()
                    lineBuilder.append(lastChar).append(currentChar)

                    val lastCharWidth = getCharWidth(lastChar, halfWidthPx, fullWidthPx)
                    val currentCharWidth = getCharWidth(currentChar, halfWidthPx, fullWidthPx)
                    return lastCharWidth + currentCharWidth
                }
            }

            return null
        }

        fun pagingTextVertical(
            rawContentList: List<Content>,
            width: Dp,
            fontSize: TextUnit,
            letterSpacing: TextUnit
        ): List<Content> {

            val targetPixelWidth = ValueUtil.dpToPx(width)
            val fontSizePx = ValueUtil.spToPx(fontSize)
            val letterSpacingPx = ValueUtil.spToPx(letterSpacing)
            val halfWidthPx = (0.5f * fontSizePx) + letterSpacingPx
            val fullWidthPx = fontSizePx + letterSpacingPx

            // 结果列表
            val resultLines = ArrayList<Content>()
            var isStartOfParagraph = true // 标记是否为段落首行

            for (content in rawContentList) {
                if (content.type == ContentType.IMG) {
                    resultLines.add(content) // 图片保持原样
                    isStartOfParagraph = true // 图片后面强制认为是新段落
                    continue
                }

                if (content.type == ContentType.TEXT) {
                    val text = content.data
                    val chapterTitle = content.chapterTitle // 保留章节标题

                    // 按换行符分割
                    text.lineSequence().forEach { line ->
                        if (line.isBlank()) {
                            // 这是一个段落分隔符
                            if (resultLines.isNotEmpty()) {
                                // 添加一个空的 TEXT Content 作为段落标记
                                val lastContent = resultLines.last()
                                // 避免连续添加空行
                                if (lastContent.type != ContentType.TEXT || lastContent.data.isNotEmpty()) {
                                    resultLines.add(Content("", ContentType.TEXT, chapterTitle))
                                }
                            }
                            isStartOfParagraph = true
                        } else {
                            // 这是一个内容行
                            val lineToChunk: String
                            if (isStartOfParagraph) {
                                val trimmedLine = line.trimStart(' ', '　')
                                lineToChunk = "　　$trimmedLine"
                                isStartOfParagraph = false
                            } else {
                                lineToChunk = line
                            }

                            // 调用 chunkLineOptimized
                            chunkLineOptimizedVertical(
                                line = lineToChunk,
                                targetPixelWidth = targetPixelWidth,
                                halfWidthPx = halfWidthPx,
                                fullWidthPx = fullWidthPx,
                                chapterTitle = chapterTitle, // 传递标题
                                output = resultLines // 输出到 Content 列表
                            )
                        }
                    }
                }
            }
            return resultLines
        }

        private fun chunkLineOptimizedVertical(
            line: String,
            targetPixelWidth: Float,
            halfWidthPx: Float,
            fullWidthPx: Float,
            chapterTitle: String?,
            output: MutableList<Content>
        ) {
            val lineLength = line.length
            val charWidths = FloatArray(lineLength) { i ->
                if (line[i].code in ASCII_START..ASCII_END) halfWidthPx else fullWidthPx
            }

            val lineBuilder = StringBuilder()
            var currentWidth = 0.0f
            var i = 0

            while (i < lineLength) {
                val c = line[i]
                val charWidth = charWidths[i]

                if (currentWidth + charWidth > targetPixelWidth && lineBuilder.isNotEmpty()) {
                    val newWidth = handlePunctuationOptimizedVertical(
                        lineBuilder = lineBuilder,
                        currentChar = c,
                        chapterTitle = chapterTitle,
                        chunks = output,
                        halfWidthPx = halfWidthPx,
                        fullWidthPx = fullWidthPx
                    )

                    if (newWidth != null) {
                        currentWidth = newWidth
                        i++
                        continue
                    }

                    // 正常换行
                    output.add(
                        Content(
                            lineBuilder.toString(),
                            ContentType.TEXT,
                            chapterTitle
                        )
                    )
                    lineBuilder.clear()
                    currentWidth = 0.0f
                }

                currentWidth += charWidth
                lineBuilder.append(c)
                i++
            }

            if (lineBuilder.isNotEmpty()) {
                output.add(Content(lineBuilder.toString(), ContentType.TEXT, chapterTitle))
            }
        }

        private fun handlePunctuationOptimizedVertical(
            lineBuilder: StringBuilder,
            currentChar: Char,
            chapterTitle: String?,
            chunks: MutableList<Content>,
            halfWidthPx: Float,
            fullWidthPx: Float
        ): Float? {
            if (lineBuilder.isEmpty()) return null

            val lastChar = lineBuilder[lineBuilder.length - 1]

            // 避头
            if (currentChar.code < PUNCTUATION_LINE_START_DENY_SET.size &&
                PUNCTUATION_LINE_START_DENY_SET[currentChar.code]
            ) {
                lineBuilder.append(currentChar)
                chunks.add(Content(lineBuilder.toString(), ContentType.TEXT, chapterTitle))
                lineBuilder.clear()
                return 0.0f
            }

            // 避尾
            if (lastChar.code < PUNCTUATION_LINE_END_DENY_SET.size &&
                PUNCTUATION_LINE_END_DENY_SET[lastChar.code]
            ) {
                val newLineLength = lineBuilder.length - 1
                if (newLineLength > 0) {
                    chunks.add(
                        Content(
                            lineBuilder.substring(0, newLineLength),
                            ContentType.TEXT,
                            chapterTitle
                        )
                    )
                    lineBuilder.clear()
                    lineBuilder.append(lastChar).append(currentChar)

                    val lastCharWidth = getCharWidth(lastChar, halfWidthPx, fullWidthPx)
                    val currentCharWidth = getCharWidth(currentChar, halfWidthPx, fullWidthPx)
                    return lastCharWidth + currentCharWidth
                }
            }

            return null
        }
    }
}