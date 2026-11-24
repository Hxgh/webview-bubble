package com.hxgh.webshell

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * 极简设置面板 (带平滑缩放进入/退出动画)
 * 实事求是，纯粹黑白灰，关闭即闭环收起
 */
class ControlPanelDialog(
    context: Context,
    private val isDark: Boolean,
    private val currentUrl: String,
    private val currentAutoDockSec: Int,
    private val currentThemeMode: String,
    private val callbacks: Callbacks
) : Dialog(context, android.R.style.Theme_Translucent_NoTitleBar) {

    interface Callbacks {
        fun onApplyUrl(url: String)
        fun onAutoDockSecChanged(sec: Int)
        fun onThemeModeChanged(mode: String)
    }

    private fun dp(v: Float): Int = (v * context.resources.displayMetrics.density).roundToInt()

    private lateinit var rootLayout: FrameLayout
    private lateinit var cardLayout: LinearLayout
    private var isDismissing = false

    init {
        buildUi()
    }

    private fun buildUi() {
        val bgColor = if (isDark) Color.rgb(28, 28, 30) else Color.WHITE
        val borderColor = if (isDark) Color.argb(40, 255, 255, 255) else Color.argb(25, 0, 0, 0)
        val textColor = if (isDark) Color.WHITE else Color.BLACK
        val subTextColor = if (isDark) Color.rgb(142, 142, 147) else Color.rgb(108, 108, 112)
        val inputBg = if (isDark) Color.rgb(22, 22, 24) else Color.rgb(242, 242, 247)
        val primaryBtnBg = if (isDark) Color.WHITE else Color.BLACK
        val primaryBtnText = if (isDark) Color.BLACK else Color.WHITE

        // 外层阴影半透明遮罩 (点击即闭环退出)
        rootLayout = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(160, 0, 0, 0))
            setOnClickListener { dismiss() }
        }

        // 居中极简卡片容器
        cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(20f).toFloat()
                setStroke(dp(1f), borderColor)
            }
            elevation = dp(16f).toFloat()
            setPadding(dp(20f), dp(18f), dp(20f), dp(20f))
            setOnClickListener { /* 拦截点击防穿透 */ }
        }

        val cardLp = FrameLayout.LayoutParams(
            dp(340f),
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
            setMargins(dp(20f), dp(20f), dp(20f), dp(20f))
        }
        rootLayout.addView(cardLayout, cardLp)

        // 1. 顶部栏 (仅标题 + 单一关闭按钮，无冲突)
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(14f))
        }

        val title = TextView(context).apply {
            text = "设置"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClose = TextView(context).apply {
            text = "✕"
            textSize = 17f
            setTextColor(subTextColor)
            setPadding(dp(12f), dp(4f), dp(4f), dp(4f))
            setOnClickListener { dismiss() }
        }

        header.addView(title)
        header.addView(btnClose)
        cardLayout.addView(header)

        // 2. 目标网址设置 (单行：输入框 + 紧凑应用按钮)
        val urlSectionTitle = TextView(context).apply {
            text = "目标网址"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(subTextColor)
            setPadding(0, dp(4f), 0, dp(8f))
        }
        cardLayout.addView(urlSectionTitle)

        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val input = EditText(context).apply {
            setText(currentUrl)
            hint = "https://..."
            setHintTextColor(subTextColor)
            setTextColor(textColor)
            textSize = 13.5f
            maxLines = 1
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = GradientDrawable().apply {
                setColor(inputBg)
                cornerRadius = dp(12f).toFloat()
                setStroke(dp(1.2f), borderColor)
            }
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnApply = TextView(context).apply {
            text = "应用"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(primaryBtnText)
            background = GradientDrawable().apply {
                setColor(primaryBtnBg)
                cornerRadius = dp(12f).toFloat()
            }
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(8f), 0, 0, 0)
            }
            setOnClickListener {
                dismiss()
                callbacks.onApplyUrl(input.text.toString().trim())
            }
        }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                dismiss()
                callbacks.onApplyUrl(input.text.toString().trim())
                true
            } else false
        }

        inputRow.addView(input)
        inputRow.addView(btnApply)
        cardLayout.addView(inputRow)

        // 3. 自动吸附倒计时
        val autoDockTitle = TextView(context).apply {
            text = "自动吸附倒计时"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(subTextColor)
            setPadding(0, dp(16f), 0, dp(8f))
        }
        cardLayout.addView(autoDockTitle)

        val autoDockRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(inputBg)
                cornerRadius = dp(12f).toFloat()
                setStroke(dp(1f), borderColor)
            }
            setPadding(dp(3f), dp(3f), dp(3f), dp(3f))
        }

        val autoDockOptions = listOf(3 to "3秒", 5 to "5秒", 10 to "10秒", 0 to "从不")
        for ((sec, label) in autoDockOptions) {
            val isSelected = sec == currentAutoDockSec
            val item = TextView(context).apply {
                text = label
                textSize = 12f
                typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = Gravity.CENTER
                setTextColor(if (isSelected) textColor else subTextColor)
                background = if (isSelected) {
                    GradientDrawable().apply {
                        setColor(bgColor)
                        cornerRadius = dp(9f).toFloat()
                    }
                } else null
                setPadding(0, dp(8f), 0, dp(8f))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    callbacks.onAutoDockSecChanged(sec)
                    dismiss()
                }
            }
            autoDockRow.addView(item)
        }
        cardLayout.addView(autoDockRow)

        // 4. 外观模式 (黑白灰)
        val themeTitle = TextView(context).apply {
            text = "外观模式"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(subTextColor)
            setPadding(0, dp(14f), 0, dp(8f))
        }
        cardLayout.addView(themeTitle)

        val themeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(inputBg)
                cornerRadius = dp(12f).toFloat()
                setStroke(dp(1f), borderColor)
            }
            setPadding(dp(3f), dp(3f), dp(3f), dp(3f))
        }

        val themeOptions = listOf("system" to "跟随系统", "light" to "浅色模式", "dark" to "深色模式")
        for ((mode, label) in themeOptions) {
            val isSelected = mode == currentThemeMode
            val item = TextView(context).apply {
                text = label
                textSize = 12f
                typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = Gravity.CENTER
                setTextColor(if (isSelected) textColor else subTextColor)
                background = if (isSelected) {
                    GradientDrawable().apply {
                        setColor(bgColor)
                        cornerRadius = dp(9f).toFloat()
                    }
                } else null
                setPadding(0, dp(8f), 0, dp(8f))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    callbacks.onThemeModeChanged(mode)
                    dismiss()
                }
            }
            themeRow.addView(item)
        }
        cardLayout.addView(themeRow)

        setContentView(rootLayout)
    }

    override fun onStart() {
        super.onStart()
        isDismissing = false
        cardLayout.alpha = 0f
        cardLayout.scaleX = 0.92f
        cardLayout.scaleY = 0.92f
        cardLayout.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220)
            .setInterpolator(OvershootInterpolator(1.15f))
            .start()
        rootLayout.alpha = 0f
        rootLayout.animate()
            .alpha(1f)
            .setDuration(180)
            .start()
    }

    override fun dismiss() {
        if (isDismissing) return
        isDismissing = true
        cardLayout.animate()
            .alpha(0f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(160)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                super.dismiss()
            }
            .start()
        rootLayout.animate()
            .alpha(0f)
            .setDuration(160)
            .start()
    }
}
