package com.opencode.multilensipcam

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

class PixelFontOverlayRenderer {
    private val pixelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun draw(
        canvas: Canvas,
        background: Bitmap,
        timestamp: String,
        status: VideoOverlayStatus
    ) {
        val batteryPercentText = status.batteryPercent?.coerceIn(0, 100)?.let { "$it%" } ?: "--%"
        val chargingText = if (status.charging) " ~" else ""
        val text = "$timestamp $batteryPercentText$chargingText"
        val glyphCount = text.length
        val textCells = glyphCount * GLYPH_ADVANCE_CELLS
        val totalCells = textCells + BATTERY_ADVANCE_CELLS
        if (totalCells <= 0) return

        val cellSize = (status.targetWidthPx / totalCells).coerceIn(MIN_CELL_SIZE_PX, MAX_CELL_SIZE_PX)
        val margin = max(8f, cellSize)
        val top = margin
        var x = margin

        for (char in timestamp) {
            x = drawGlyphChar(canvas, background, char, x, top, cellSize)
        }
        x = drawGlyphChar(canvas, background, ' ', x, top, cellSize)
        x = drawBatteryIcon(canvas, background, x, top, cellSize, status.batteryPercent)
        for (char in " $batteryPercentText$chargingText") {
            x = drawGlyphChar(canvas, background, char, x, top, cellSize)
        }
    }

    private fun drawGlyphChar(
        canvas: Canvas,
        background: Bitmap,
        rawChar: Char,
        x: Float,
        y: Float,
        cellSize: Float
    ): Float {
        val char = if (rawChar in 'a'..'z') rawChar.uppercaseChar() else rawChar
        if (char == ' ') {
            return x + GLYPH_ADVANCE_CELLS * cellSize
        }
        val glyph = GLYPHS[char] ?: GLYPHS['?'] ?: EMPTY_GLYPH
        val width = GLYPH_WIDTH * cellSize
        val height = GLYPH_HEIGHT * cellSize
        val color = pickForegroundColor(background, x, y, width, height)
        drawGlyph(canvas, glyph, x, y, cellSize, color)
        return x + GLYPH_ADVANCE_CELLS * cellSize
    }

    private fun drawBatteryIcon(
        canvas: Canvas,
        background: Bitmap,
        x: Float,
        y: Float,
        cellSize: Float,
        batteryPercent: Int?
    ): Float {
        val width = BATTERY_WIDTH_CELLS * cellSize
        val height = GLYPH_HEIGHT * cellSize
        val color = pickForegroundColor(background, x, y, width, height)
        val shadowColor = if (color == Color.WHITE) Color.argb(110, 0, 0, 0) else Color.argb(110, 255, 255, 255)
        val shadowOffset = max(1f, cellSize * 0.33f)
        val level = batteryPercent?.coerceIn(0, 100)
        val fillBars = when {
            level == null -> 0
            level <= 0 -> 0
            else -> ceil(level / 25f).toInt().coerceIn(1, 4)
        }

        drawBatteryBody(canvas, x + shadowOffset, y + shadowOffset, cellSize, shadowColor, fillBars)
        drawBatteryBody(canvas, x, y, cellSize, color, fillBars)
        return x + BATTERY_ADVANCE_CELLS * cellSize
    }

    private fun drawBatteryBody(
        canvas: Canvas,
        x: Float,
        y: Float,
        cellSize: Float,
        color: Int,
        fillBars: Int
    ) {
        pixelPaint.color = color
        drawCellRect(canvas, x, y, BATTERY_BODY_WIDTH_CELLS, 1, cellSize)
        drawCellRect(canvas, x, y + (GLYPH_HEIGHT - 1) * cellSize, BATTERY_BODY_WIDTH_CELLS, 1, cellSize)
        drawCellRect(canvas, x, y, 1, GLYPH_HEIGHT, cellSize)
        drawCellRect(canvas, x + (BATTERY_BODY_WIDTH_CELLS - 1) * cellSize, y, 1, GLYPH_HEIGHT, cellSize)

        val terminalX = x + BATTERY_BODY_WIDTH_CELLS * cellSize
        drawCellRect(canvas, terminalX, y + 2f * cellSize, BATTERY_TERMINAL_WIDTH_CELLS, 3, cellSize)

        val innerStartX = x + cellSize
        val innerY = y + cellSize
        val barGap = 1
        for (bar in 0 until fillBars) {
            val barX = innerStartX + bar * (1 + barGap) * cellSize
            drawCellRect(canvas, barX, innerY, 1, GLYPH_HEIGHT - 2, cellSize)
        }
    }

    private fun drawGlyph(
        canvas: Canvas,
        glyph: IntArray,
        x: Float,
        y: Float,
        cellSize: Float,
        color: Int
    ) {
        val shadowColor = if (color == Color.WHITE) Color.argb(110, 0, 0, 0) else Color.argb(110, 255, 255, 255)
        val shadowOffset = max(1f, cellSize * 0.33f)
        drawGlyphPixels(canvas, glyph, x + shadowOffset, y + shadowOffset, cellSize, shadowColor)
        drawGlyphPixels(canvas, glyph, x, y, cellSize, color)
    }

    private fun drawGlyphPixels(
        canvas: Canvas,
        glyph: IntArray,
        x: Float,
        y: Float,
        cellSize: Float,
        color: Int
    ) {
        pixelPaint.color = color
        glyph.forEachIndexed { rowIndex, rowBits ->
            for (col in 0 until GLYPH_WIDTH) {
                val bit = (rowBits shr (GLYPH_WIDTH - 1 - col)) and 0x1
                if (bit == 0) continue
                val left = x + (col * cellSize)
                val top = y + (rowIndex * cellSize)
                canvas.drawRect(left, top, left + cellSize, top + cellSize, pixelPaint)
            }
        }
    }

    private fun drawCellRect(
        canvas: Canvas,
        x: Float,
        y: Float,
        widthCells: Int,
        heightCells: Int,
        cellSize: Float
    ) {
        canvas.drawRect(
            x,
            y,
            x + widthCells * cellSize,
            y + heightCells * cellSize,
            pixelPaint
        )
    }

    private fun pickForegroundColor(
        bitmap: Bitmap,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ): Int {
        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return Color.WHITE

        val l = left.roundToInt().coerceIn(0, bitmapWidth - 1)
        val t = top.roundToInt().coerceIn(0, bitmapHeight - 1)
        val r = (left + width).roundToInt().coerceIn(l + 1, bitmapWidth)
        val b = (top + height).roundToInt().coerceIn(t + 1, bitmapHeight)
        val sampleStepX = max(1, (r - l) / 4)
        val sampleStepY = max(1, (b - t) / 4)

        var luminanceSum = 0.0
        var samples = 0
        var yCursor = t
        while (yCursor < b) {
            var xCursor = l
            while (xCursor < r) {
                val color = bitmap.getPixel(xCursor, yCursor)
                val rValue = Color.red(color)
                val gValue = Color.green(color)
                val bValue = Color.blue(color)
                luminanceSum += (0.299 * rValue) + (0.587 * gValue) + (0.114 * bValue)
                samples += 1
                xCursor += sampleStepX
            }
            yCursor += sampleStepY
        }
        val avgLuminance = if (samples == 0) 0.0 else luminanceSum / samples
        return if (avgLuminance >= LUMA_BRIGHT_THRESHOLD) Color.BLACK else Color.WHITE
    }

    private companion object {
        private const val GLYPH_WIDTH = 5
        private const val GLYPH_HEIGHT = 7
        private const val GLYPH_ADVANCE_CELLS = 6
        private const val BATTERY_BODY_WIDTH_CELLS = 6
        private const val BATTERY_TERMINAL_WIDTH_CELLS = 1
        private const val BATTERY_WIDTH_CELLS = BATTERY_BODY_WIDTH_CELLS + BATTERY_TERMINAL_WIDTH_CELLS
        private const val BATTERY_ADVANCE_CELLS = BATTERY_WIDTH_CELLS + 2
        private const val MIN_CELL_SIZE_PX = 2f
        private const val MAX_CELL_SIZE_PX = 45f
        private const val LUMA_BRIGHT_THRESHOLD = 160.0
        private val EMPTY_GLYPH = intArrayOf(0, 0, 0, 0, 0, 0, 0)
        private val GLYPHS: Map<Char, IntArray> = mapOf(
            '0' to rows("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
            '1' to rows("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
            '2' to rows("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
            '3' to rows("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
            '4' to rows("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
            '5' to rows("11111", "10000", "10000", "11110", "00001", "00001", "11110"),
            '6' to rows("01110", "10000", "10000", "11110", "10001", "10001", "01110"),
            '7' to rows("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
            '8' to rows("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
            '9' to rows("01110", "10001", "10001", "01111", "00001", "00001", "01110"),
            'A' to rows("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
            'B' to rows("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
            'C' to rows("01110", "10001", "10000", "10000", "10000", "10001", "01110"),
            'D' to rows("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
            'E' to rows("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
            'F' to rows("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
            'G' to rows("01110", "10001", "10000", "10111", "10001", "10001", "01110"),
            'H' to rows("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
            'I' to rows("01110", "00100", "00100", "00100", "00100", "00100", "01110"),
            'J' to rows("00001", "00001", "00001", "00001", "10001", "10001", "01110"),
            'K' to rows("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
            'L' to rows("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
            'M' to rows("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
            'N' to rows("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
            'O' to rows("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
            'P' to rows("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
            'Q' to rows("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
            'R' to rows("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
            'S' to rows("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
            'T' to rows("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
            'U' to rows("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
            'V' to rows("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
            'W' to rows("10001", "10001", "10001", "10101", "10101", "10101", "01010"),
            'X' to rows("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
            'Y' to rows("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
            'Z' to rows("11111", "00001", "00010", "00100", "01000", "10000", "11111"),
            '/' to rows("00001", "00010", "00100", "01000", "10000", "00000", "00000"),
            ':' to rows("00000", "00100", "00100", "00000", "00100", "00100", "00000"),
            '%' to rows("11001", "11010", "00100", "01000", "10110", "00110", "00000"),
            '~' to rows("00000", "00000", "01001", "10110", "00000", "00000", "00000"),
            '-' to rows("00000", "00000", "00000", "01110", "00000", "00000", "00000"),
            '?' to rows("01110", "10001", "00010", "00100", "00100", "00000", "00100")
        )

        private fun rows(
            row0: String,
            row1: String,
            row2: String,
            row3: String,
            row4: String,
            row5: String,
            row6: String
        ): IntArray {
            return intArrayOf(
                rowToBits(row0),
                rowToBits(row1),
                rowToBits(row2),
                rowToBits(row3),
                rowToBits(row4),
                rowToBits(row5),
                rowToBits(row6)
            )
        }

        private fun rowToBits(row: String): Int {
            var bits = 0
            for (char in row) {
                bits = bits shl 1
                if (char == '1') {
                    bits = bits or 1
                }
            }
            return bits
        }
    }
}
