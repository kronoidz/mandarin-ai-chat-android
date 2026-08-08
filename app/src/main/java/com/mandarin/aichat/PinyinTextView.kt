package com.mandarin.aichat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView

/**
 * A [TextView] that can overlay pinyin annotations above CJK characters.
 *
 * When [pinyinEnabled] is true, each Hanzi character in the text will have its pinyin reading drawn
 * above it in a smaller, muted typeface — provided the character has an entry in [PinyinDict].
 *
 * Enable via the [pinyinEnabled] property. The view automatically adds top padding and extra line
 * spacing to make room for the annotations, and invalidates on toggle.
 */
class PinyinTextView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        AppCompatTextView(context, attrs, defStyleAttr) {

    var pinyinEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            applyPinyinSpacing()
            invalidate()
        }

    private val pinyinPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize =
                        TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_SP,
                                9f,
                                resources.displayMetrics
                        )
                color = Color.parseColor("#9E9E9E")
            }

    /** Total height needed for the pinyin line: ascent (negative) → descent. */
    private val pinyinLineHeight: Float
        get() = -pinyinPaint.ascent() + pinyinPaint.descent()

    private val gapPx =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics)

    /** The extra top inset needed when pinyin is enabled. */
    private val pinyinTopInset: Int
        get() = (pinyinLineHeight + gapPx * 2).toInt()

    // Saved from XML to restore when toggling off.
    private var savedPaddingLeft = 0
    private var savedPaddingTop = 0
    private var savedPaddingRight = 0
    private var savedPaddingBottom = 0
    private var savedTextSize = 0f
    private var layoutsSaved = false

    override fun onFinishInflate() {
        super.onFinishInflate()
        savedPaddingLeft = paddingLeft
        savedPaddingTop = paddingTop
        savedPaddingRight = paddingRight
        savedPaddingBottom = paddingBottom
        savedTextSize = textSize
        layoutsSaved = true
    }

    private fun applyPinyinSpacing() {
        if (!layoutsSaved) return
        if (pinyinEnabled) {
            setPadding(
                    savedPaddingLeft,
                    savedPaddingTop + pinyinTopInset,
                    savedPaddingRight,
                    savedPaddingBottom
            )
            setLineSpacing(pinyinLineHeight + gapPx * 2, 1f)
            // Enlarge Hanzi + add spacing so pinyin fits between characters.
            setTextSize(TypedValue.COMPLEX_UNIT_PX, savedTextSize * 1.5f)
            letterSpacing = 0.06f
        } else {
            setPadding(savedPaddingLeft, savedPaddingTop, savedPaddingRight, savedPaddingBottom)
            setLineSpacing(0f, 1f)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, savedTextSize)
            letterSpacing = 0f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!pinyinEnabled) return

        val layout = layout ?: return
        val str = text?.toString() ?: return

        val padLeft = compoundPaddingLeft.toFloat()
        val padTop = compoundPaddingTop.toFloat()

        canvas.save()
        canvas.translate(padLeft, padTop)

        for (line in 0 until layout.lineCount) {
            val lineStart = layout.getLineStart(line)
            val lineEnd = layout.getLineEnd(line)
            val baseline = layout.getLineBaseline(line)

            var i = lineStart
            while (i < lineEnd) {
                val cp = Character.codePointAt(str, i)
                val charCount = Character.charCount(cp)

                if (cp in PinyinDict.cjkRange) {
                    val pinyin = PinyinDict.get(cp)
                    if (pinyin != null) {
                        val charStartX = layout.getPrimaryHorizontal(i)
                        val charEndX =
                                if (i + charCount < lineEnd) {
                                    layout.getPrimaryHorizontal(i + charCount)
                                } else {
                                    layout.getLineRight(line)
                                }
                        val charWidth = (charEndX - charStartX).coerceAtLeast(1f)

                        val pinyinWidth = pinyinPaint.measureText(pinyin)
                        val pinyinX = charStartX + (charWidth - pinyinWidth) / 2f
                        // Draw pinyin just above the character ascent, with a small gap.
                        val pinyinY = baseline + paint.ascent() - gapPx - pinyinPaint.descent()

                        canvas.drawText(pinyin, pinyinX, pinyinY, pinyinPaint)
                    }
                }
                i += charCount
            }
        }
        canvas.restore()
    }
}
