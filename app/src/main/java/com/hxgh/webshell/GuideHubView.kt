package com.hxgh.webshell

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * 起始引导页 (未设定网址时展示)
 * 纯粹黑白灰，无任何冗余第三方书签预设
 */
class GuideHubView(
    context: Context,
    private val isDark: Boolean,
    private val onUrlSelected: (String) -> Unit
) : ScrollView(context) {

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).roundToInt()

    init {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        buildUi()
    }

    private fun buildUi() {
        val bgColor = if (isDark) Color.rgb(18, 18, 18) else Color.rgb(242, 242, 247)
        val cardBg = if (isDark) Color.rgb(28, 28, 30) else Color.WHITE
        val borderColor = if (isDark) Color.argb(35, 255, 255, 255) else Color.argb(20, 0, 0, 0)
        val textColor = if (isDark) Color.WHITE else Color.BLACK
        val subTextColor = if (isDark) Color.rgb(142, 142, 147) else Color.rgb(108, 108, 112)
        val inputBg = if (isDark) Color.rgb(22, 22, 24) else Color.rgb(242, 242, 247)
        val primaryBtnBg = if (isDark) Color.WHITE else Color.BLACK
        val primaryBtnText = if (isDark) Color.BLACK else Color.WHITE

        setBackgroundColor(bgColor)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(36f), dp(20f), dp(48f))
        }

        // 1. Hero 标题区
        val heroLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(12f), 0, dp(28f))
        }

        val title = TextView(context).apply {
            text = "Web Shell"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setPadding(0, 0, 0, dp(6f))
        }

        val desc = TextView(context).apply {
            text = "轻量网页包裹容器 · 全局安全区沉浸 · 极简黑白灰"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(subTextColor)
        }

        heroLayout.addView(title)
        heroLayout.addView(desc)
        container.addView(heroLayout)

        // 2. 目标网址快速输入卡片
        val inputCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardBg)
                cornerRadius = dp(18f).toFloat()
                setStroke(dp(1f), borderColor)
            }
            setPadding(dp(18f), dp(16f), dp(18f), dp(18f))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(20f))
            }
        }

        val cardTitle = TextView(context).apply {
            text = "🌐 输入要包裹的目标网址"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            setPadding(0, 0, 0, dp(12f))
        }
        inputCard.addView(cardTitle)

        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val urlInput = EditText(context).apply {
            setText(context.getString(R.string.default_url))
            hint = "https://..."
            setHintTextColor(subTextColor)
            setTextColor(textColor)
            textSize = 13.5f
            maxLines = 1
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_GO
            background = GradientDrawable().apply {
                setColor(inputBg)
                cornerRadius = dp(12f).toFloat()
                setStroke(dp(1.2f), borderColor)
            }
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnGo = TextView(context).apply {
            text = "开始 ➔"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(primaryBtnText)
            background = GradientDrawable().apply {
                setColor(primaryBtnBg)
                cornerRadius = dp(12f).toFloat()
            }
            setPadding(dp(18f), dp(10f), dp(18f), dp(10f))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(8f), 0, 0, 0)
            }
            setOnClickListener {
                val raw = urlInput.text.toString().trim()
                if (raw.isNotEmpty()) onUrlSelected(raw)
            }
        }

        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                val raw = urlInput.text.toString().trim()
                if (raw.isNotEmpty()) onUrlSelected(raw)
                true
            } else false
        }

        inputRow.addView(urlInput)
        inputRow.addView(btnGo)
        inputCard.addView(inputRow)
        container.addView(inputCard)

        // 3. 操作与体验指引卡片
        val tipsCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardBg)
                cornerRadius = dp(18f).toFloat()
                setStroke(dp(1f), borderColor)
            }
            setPadding(dp(18f), dp(16f), dp(18f), dp(18f))
        }

        val tipsTitle = TextView(context).apply {
            text = "💡 核心体验指引"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            setPadding(0, 0, 0, dp(12f))
        }
        tipsCard.addView(tipsTitle)

        val tips = listOf(
            "1" to "贴边小条：轻触平滑弹性外弹为悬浮球并直接呼出控制面板；闲置自动贴边。",
            "2" to "全静默手势返回：深度拦截 Android 边缘侧滑，网页内后退完全静默流畅，杜绝误触直接退回桌面。",
            "3" to "全局安全高度：状态栏与手势条由原生框架物理锁死，任何子页面与跳转永远保持安全高度。",
            "4" to "极简深浅色：默认纯净黑白灰高对比度呈现，无多余彩色主题色干扰。"
        )

        for ((num, textStr) in tips) {
            val tipRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4f), 0, dp(6f))
            }
            val numBadge = TextView(context).apply {
                text = num
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(textColor)
                background = GradientDrawable().apply {
                    setColor(if (isDark) Color.argb(40, 255, 255, 255) else Color.argb(20, 0, 0, 0))
                    cornerRadius = dp(10f).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(dp(18f), dp(18f)).apply {
                    setMargins(0, dp(1f), dp(10f), 0)
                }
            }
            val descView = TextView(context).apply {
                text = textStr
                textSize = 12f
                setTextColor(subTextColor)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            tipRow.addView(numBadge)
            tipRow.addView(descView)
            tipsCard.addView(tipRow)
        }
        container.addView(tipsCard)

        addView(container)
    }
}
