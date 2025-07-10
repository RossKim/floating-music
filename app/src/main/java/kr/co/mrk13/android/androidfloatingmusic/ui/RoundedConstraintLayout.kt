package kr.co.mrk13.android.androidfloatingmusic.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.withClip
import kr.co.mrk13.android.androidfloatingmusic.R

/**
 * @author ross.
 */
class RoundedConstraintLayout(context: Context, attrs: AttributeSet) :
    ConstraintLayout(context, attrs) {

    private lateinit var rectF: RectF
    private val path = Path()
    private var cornerRadius = 0f

    init {
        getContext().withStyledAttributes(attrs, R.styleable.RoundedConstraintLayout) {
            cornerRadius = getFloat(R.styleable.RoundedConstraintLayout_appCornerRadius, 0f)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rectF = RectF(0f, 0f, w.toFloat(), h.toFloat())
        resetPath()
    }

    override fun draw(canvas: Canvas) {
        canvas.withClip(path) {
            super.draw(canvas)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.withClip(path) {
            super.dispatchDraw(canvas)
        }
    }

    private fun resetPath() {
        path.reset()
        path.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)
        path.close()
    }
}