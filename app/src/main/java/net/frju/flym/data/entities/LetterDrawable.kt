package net.frju.flym.data.entities

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.min

/** A small self-contained replacement for TextDrawable used for feed initials. */
class LetterDrawable(
        private val text: String,
        private val color: Int,
        private val rounded: Boolean
) : Drawable() {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        backgroundPaint.color = color
        val radius = if (rounded) min(bounds.width(), bounds.height()) / 2f else 0f
        canvas.drawRoundRect(RectF(bounds), radius, radius, backgroundPaint)

        if (text.isNotEmpty() && bounds.width() > 0 && bounds.height() > 0) {
            textPaint.textSize = bounds.height() * if (text.length > 1) 0.42f else 0.50f
            val metrics = textPaint.fontMetrics
            val baseline = bounds.centerY() - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(text, bounds.centerX().toFloat(), baseline, textPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        backgroundPaint.alpha = alpha
        textPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        backgroundPaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android framework")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
