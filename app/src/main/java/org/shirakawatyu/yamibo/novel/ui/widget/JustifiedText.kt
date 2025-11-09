package org.shirakawatyu.yamibo.novel.ui.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.shirakawatyu.yamibo.novel.util.ValueUtil

// 两端对齐算法
@Composable
fun JustifiedText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    lineHeight: TextUnit = 24.sp,
    letterSpacing: TextUnit = 0.sp,
    color: Color = Color.Black,
    isVerticalMode: Boolean = false // [新增]
) {
    // 创建Paint对象
    val textPaint = remember {
        android.graphics.Paint().apply {
            this.isAntiAlias = true
        }
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1. 获取画布和参数
            // 可用宽度即是画布的宽度（父 Composable 已经处理了边距）
            val availableWidth = size.width
            val lineHeightPx = ValueUtil.spToPx(lineHeight)
            val fontSizePx = ValueUtil.spToPx(fontSize)
            val letterSpacingPx = ValueUtil.spToPx(letterSpacing)

            // 2. 配置 Paint
            textPaint.color = color.toArgb()
            textPaint.textSize = fontSizePx
            // 计算并设置基础字间距（Paint 的 letterSpacing 是一个乘数）
            val baseLetterSpacingMultiplier =
                if (fontSizePx > 0) letterSpacingPx / fontSizePx else 0f
            textPaint.letterSpacing = baseLetterSpacingMultiplier


            // [新增] 模式切换
            if (isVerticalMode) {
                // --- 竖屏模式 ---
                // text 是单行，Y 坐标固定
                // 我们在 ContentViewer 中已经将 Composable 高度设为 lineHeightPx，
                // 计算基线 (baseline) 使其在行高内垂直居中
                val baselineY = (size.height / 2) + (fontSizePx / 2.8f) // 粗略估算

                // 竖屏模式下，我们假设传来的 text 已经是被 TextUtil 分割好的一行
                // 我们需要判断是否需要对齐

                // 重置为基础间距，用于测量
                textPaint.letterSpacing = baseLetterSpacingMultiplier
                val originalWidth = textPaint.measureText(text)
                val remainingSpace = availableWidth - originalWidth

                // [竖屏对齐逻辑]
                // 如果剩余空间大于一个字符宽度（估算），说明这是个短行（如段落末尾），不对齐。
                val oneCharWidth = fontSizePx
                if (remainingSpace > oneCharWidth && text.length > 1) {
                    // 这是个短行（段落末尾），不要对齐
                    textPaint.letterSpacing = baseLetterSpacingMultiplier
                } else if (remainingSpace > 0 && text.length > 1) {
                    // 这是个几乎满的行（被分行或避头尾），需要对齐
                    val gaps = text.length - 1 // <--- [已修正]
                    // 计算需要分配到每个间隙的额外像素
                    val justificationSpacingPx = remainingSpace / gaps
                    // 转换为 Paint 的乘数
                    val justificationMultiplier =
                        if (fontSizePx > 0) justificationSpacingPx / fontSizePx else 0f

                    // 应用总间距（基础间距 + 对齐间距）
                    textPaint.letterSpacing = baseLetterSpacingMultiplier + justificationMultiplier
                }
                // else: 保持基础间距 (已设置)

                // 绘制文本
                drawContext.canvas.nativeCanvas.drawText(
                    text,
                    0f, // X=0
                    baselineY, // Y=居中基线
                    textPaint
                )

            } else {
                // --- 横屏模式 (旧逻辑) ---
                // 3. 按 TextUtil 的换行符 \n 拆分
                val lines = text.split('\n')
                var currentY = lineHeightPx // Y 坐标从第一行的基线开始

                // 4. 逐行绘制
                lines.forEachIndexed { index, line ->
                    // 如果是空行（段落分隔），只增加 Y 坐标
                    if (line.isEmpty()) {
                        return@forEachIndexed
                    }

                    // 判断是否为段落或页面的最后一行
                    val isLastLine =
                        (index < lines.size - 1 && lines[index + 1].isEmpty())
                    // 重置为基础间距，用于测量
                    textPaint.letterSpacing = baseLetterSpacingMultiplier
                    val originalWidth = textPaint.measureText(line)
                    val remainingSpace = availableWidth - originalWidth

                    // 5. 实现两端对齐（Justification）
                    // 如果不是最后一行，且有剩余空间，且行内多于1个字
                    if (!isLastLine && remainingSpace > 0 && line.length > 1) {
                        val gaps = line.length - 1
                        // 计算需要分配到每个间隙的额外像素
                        val justificationSpacingPx = remainingSpace / gaps
                        // 转换为 Paint 的乘数
                        val justificationMultiplier =
                            if (fontSizePx > 0) justificationSpacingPx / fontSizePx else 0f

                        // 应用总间距（基础间距 + 对齐间距）
                        textPaint.letterSpacing =
                            baseLetterSpacingMultiplier + justificationMultiplier
                    }
                    // else: 保持基础间距（已在测量前设置）

                    // 6. 绘制文本
                    drawContext.canvas.nativeCanvas.drawText(
                        line,
                        0f, // X=0，因为父 Composable 已处理了左边距
                        currentY,
                        textPaint
                    )

                    // 移至下一行
                    currentY += lineHeightPx
                }
            }

            // 清理 Paint 状态
            textPaint.letterSpacing = 0f
        }
    }
}