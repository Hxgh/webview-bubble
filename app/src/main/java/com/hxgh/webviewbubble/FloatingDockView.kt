package com.hxgh.webviewbubble

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 极简高端原生悬浮控制器 (Apple AssistiveTouch / 触觉光环质感设计)
 * - 贴边形态 (Docked)：34dp x 4dp 极简磨砂胶囊小条，带 36dp x 56dp 隐形触控热区
 * - 悬浮形态 (Floating)：44dp 圆形微光悬浮球，内置同心触感光环，支持按下弹性缩放
 * - 单次点击平滑弹性外弹并呼出控制面板
 * - 可配置倒计时自动吸附边缘
 */
class FloatingDockView(context: Context) : FrameLayout(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())

    var isDarkTheme: Boolean = true
        set(value) {
            field = value
            applyThemeColors()
        }

    var autoDockSeconds: Int = 5
        set(value) {
            field = value
            resetAutoDockCountdown()
        }

    var onOpenPanelListener: (() -> Unit)? = null
    var onPositionChanged: ((isDocked: Boolean, isLeft: Boolean, yPx: Float) -> Unit)? = null

    var safeTopPx: Int = 0
    var safeBottomPx: Int = 0
    var safeLeftPx: Int = 0
    var safeRightPx: Int = 0

    var isDocked: Boolean = true
        private set
    var isSnappedLeft: Boolean = true
        private set
    private var isDragging: Boolean = false

    private val dockBarView = View(context)
    private val bubbleView = FrameLayout(context)
    private val bubbleIcon = ImageView(context)

    private var breatheAnimator: ValueAnimator? = null
    private var autoDockRunnable = Runnable {
        if (!isDocked && !isDragging) {
            dockToEdge()
        }
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var startTranslationX = 0f
    private var startTranslationY = 0f
    private var velocityTracker: VelocityTracker? = null

    init {
        clipChildren = false
        clipToPadding = false

        buildViews()
        applyThemeColors()
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun buildViews() {
        // 1. 贴边极简小条 (4dp 宽 x 34dp 高胶囊)
        val barLp = LayoutParams(dp(4f), dp(34f)).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
        }
        dockBarView.layoutParams = barLp
        addView(dockBarView)

        // 2. 悬浮球 (44dp 纯圆，精致触感)
        val bubbleSize = dp(44f)
        val bubbleLp = LayoutParams(bubbleSize, bubbleSize).apply {
            gravity = Gravity.CENTER
        }
        bubbleView.layoutParams = bubbleLp
        bubbleView.elevation = dp(7f).toFloat()

        // 悬浮球内部图标 (Apple 质感同心光环光圈)
        val iconSize = dp(24f)
        val iconLp = LayoutParams(iconSize, iconSize).apply {
            gravity = Gravity.CENTER
        }
        bubbleIcon.layoutParams = iconLp
        bubbleIcon.setImageDrawable(HaloLensDrawable(isDarkTheme))
        bubbleView.addView(bubbleIcon)

        addView(bubbleView)

        // 默认显示贴边形态
        switchToDockedState(isSnappedLeft)
    }

    fun applyThemeColors() {
        val barBg = if (isDarkTheme) Color.argb(180, 255, 255, 255) else Color.argb(160, 0, 0, 0)
        val barRadius = dp(2f).toFloat()

        dockBarView.background = GradientDrawable().apply {
            setColor(barBg)
            cornerRadius = barRadius
        }

        // 高级黑曜石深色 / 纯净陶瓷白浅色
        val bubbleBg = if (isDarkTheme) Color.rgb(28, 28, 32) else Color.WHITE
        val bubbleBorder = if (isDarkTheme) Color.argb(45, 255, 255, 255) else Color.argb(22, 0, 0, 0)

        bubbleView.background = GradientDrawable().apply {
            setColor(bubbleBg)
            cornerRadius = dp(22f).toFloat() // 44dp 圆形
            setStroke(dp(1.2f), bubbleBorder)
        }

        bubbleIcon.setImageDrawable(HaloLensDrawable(isDarkTheme))
    }

    fun setLoading(loading: Boolean) {
        breatheAnimator?.cancel()
        if (loading) {
            breatheAnimator = ValueAnimator.ofFloat(0.35f, 1.0f).apply {
                duration = 750
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { anim ->
                    dockBarView.alpha = anim.animatedValue as Float
                }
                start()
            }
        } else {
            dockBarView.alpha = 1.0f
        }
    }

    private fun switchToDockedState(left: Boolean) {
        isDocked = true
        isSnappedLeft = left
        cancelAutoDockCountdown()

        layoutParams = (layoutParams ?: LayoutParams(dp(36f), dp(56f))).apply {
            width = dp(36f)
            height = dp(56f)
        }

        val barLp = dockBarView.layoutParams as LayoutParams
        barLp.gravity = Gravity.CENTER_VERTICAL or (if (left) Gravity.START else Gravity.END)
        dockBarView.layoutParams = barLp

        dockBarView.visibility = View.VISIBLE
        bubbleView.visibility = View.GONE
        applyThemeColors()
    }

    private fun switchToFloatingState() {
        isDocked = false

        layoutParams = (layoutParams ?: LayoutParams(dp(44f), dp(44f))).apply {
            width = dp(44f)
            height = dp(44f)
        }

        dockBarView.visibility = View.GONE
        bubbleView.visibility = View.VISIBLE
        applyThemeColors()
    }

    private fun clampY(y: Float): Float {
        val parentH = (parent as? View)?.height ?: resources.displayMetrics.heightPixels
        val minY = safeTopPx + dp(12f)
        val maxY = max(minY.toFloat(), (parentH - safeBottomPx - height - dp(12f)).toFloat())
        return max(minY.toFloat(), min(maxY, y))
    }

    /**
     * 单次轻触贴边小条：平滑弹性弹至外侧悬浮位置
     */
    fun popToFloating(targetY: Float = translationY, onFinished: (() -> Unit)? = null) {
        cancelAutoDockCountdown()
        switchToFloatingState()

        val parentW = (parent as? View)?.width ?: resources.displayMetrics.widthPixels
        val clampedY = clampY(targetY)
        val targetX = if (isSnappedLeft) {
            max(safeLeftPx + dp(14f).toFloat(), dp(14f).toFloat())
        } else {
            min((parentW - safeRightPx - dp(44f) - dp(14f)).toFloat(), (parentW - dp(58f)).toFloat())
        }

        bubbleView.scaleX = 0.8f
        bubbleView.scaleY = 0.8f
        bubbleView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(220).setInterpolator(OvershootInterpolator(1.4f)).start()

        animate()
            .translationX(targetX)
            .translationY(clampedY)
            .setDuration(240)
            .setInterpolator(OvershootInterpolator(1.25f))
            .withEndAction {
                resetAutoDockCountdown()
                onPositionChanged?.invoke(false, isSnappedLeft, clampedY)
                onFinished?.invoke()
            }
            .start()
    }

    /**
     * 吸附回侧边极简小条
     */
    fun dockToEdge(targetY: Float = translationY, preferLeft: Boolean? = null) {
        cancelAutoDockCountdown()
        val parentW = (parent as? View)?.width ?: resources.displayMetrics.widthPixels
        val isLeft = preferLeft ?: (translationX < parentW / 2)
        val clampedY = clampY(targetY)
        val targetX = if (isLeft) safeLeftPx.toFloat() else (parentW - safeRightPx - dp(36f)).toFloat()

        animate()
            .translationX(targetX)
            .translationY(clampedY)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                switchToDockedState(isLeft)
                translationX = targetX
                translationY = clampedY
                onPositionChanged?.invoke(true, isLeft, clampedY)
            }
            .start()
    }

    fun resetAutoDockCountdown() {
        cancelAutoDockCountdown()
        if (autoDockSeconds > 0 && !isDocked) {
            handler.postDelayed(autoDockRunnable, autoDockSeconds * 1000L)
        }
    }

    fun cancelAutoDockCountdown() {
        handler.removeCallbacks(autoDockRunnable)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelAutoDockCountdown()
                downRawX = event.rawX
                downRawY = event.rawY
                startTranslationX = translationX
                startTranslationY = translationY
                isDragging = false

                // 按下时轻微内缩反馈 (触觉按压质感)
                if (!isDocked) {
                    bubbleView.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                if (!isDragging && dist > touchSlop) {
                    isDragging = true
                    if (isDocked) {
                        switchToFloatingState()
                    }
                }

                if (isDragging) {
                    translationX = startTranslationX + dx
                    translationY = clampY(startTranslationY + dy)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                // 抬起恢复尺寸
                if (!isDocked) {
                    bubbleView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140).setInterpolator(OvershootInterpolator(1.4f)).start()
                }

                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                if (isDragging && dist > touchSlop) {
                    val parentW = (parent as? View)?.width ?: resources.displayMetrics.widthPixels
                    val currentX = translationX
                    if (currentX < dp(45f)) {
                        dockToEdge(preferLeft = true)
                    } else if (currentX > parentW - width - dp(45f)) {
                        dockToEdge(preferLeft = false)
                    } else {
                        isDocked = false
                        resetAutoDockCountdown()
                        onPositionChanged?.invoke(false, isSnappedLeft, translationY)
                    }
                } else {
                    // 单次轻触点击！
                    if (isDocked) {
                        // 侧边点击：弹性外弹为悬浮球，同时连贯打开控制面板
                        popToFloating {
                            onOpenPanelListener?.invoke()
                        }
                    } else {
                        // 正常悬浮球点击：直接打开控制面板
                        onOpenPanelListener?.invoke()
                    }
                }
                isDragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!isDocked) {
                    bubbleView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140).start()
                }
                if (isDragging) {
                    dockToEdge()
                }
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 高端触觉同心光环 (AssistiveTouch 级极简高雅设计)
     */
    private class HaloLensDrawable(val isDark: Boolean) : Drawable() {
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.4f
            color = if (isDark) Color.WHITE else Color.BLACK
            alpha = if (isDark) 125 else 105
        }

        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isDark) Color.WHITE else Color.BLACK
            alpha = if (isDark) 240 else 225
        }

        override fun draw(canvas: Canvas) {
            val bounds = bounds
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            val maxR = min(bounds.width(), bounds.height()) / 2f
            if (maxR <= 0) return

            // 1. 外层柔光细光环 (AssistiveTouch 光学透镜感)
            canvas.drawCircle(cx, cy, maxR * 0.72f, ringPaint)

            // 2. 中心实心纯净触点
            canvas.drawCircle(cx, cy, maxR * 0.28f, corePaint)
        }

        override fun setAlpha(alpha: Int) {
            ringPaint.alpha = (alpha * 0.5f).toInt()
            corePaint.alpha = alpha
        }
        override fun setColorFilter(colorFilter: ColorFilter?) {
            ringPaint.colorFilter = colorFilter
            corePaint.colorFilter = colorFilter
        }
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
