package com.yourgpt.ai

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Original "workbench instrument" visual language for Your GPT.
 * This deliberately avoids the familiar chat-app template.
 */
class MainActivity : Activity(), OfflineAiEngine.Listener {
    private lateinit var engine: OfflineAiEngine
    private lateinit var transcript: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var status: TextView
    private lateinit var progress: TextView
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private lateinit var retryButton: Button
    private var activeAssistantBubble: TextView? = null

    private val ink = Color.rgb(19, 23, 22)
    private val panel = Color.rgb(31, 38, 35)
    private val paper = Color.rgb(237, 229, 210)
    private val muted = Color.rgb(163, 166, 148)
    private val ember = Color.rgb(218, 104, 65)
    private val brass = Color.rgb(204, 169, 105)
    private val userFill = Color.rgb(54, 67, 59)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun shape(color: Int, radius: Float, stroke: Int = Color.TRANSPARENT): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius.toInt()).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            else typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            includeFontPadding = true
        }
    }

    private fun actionButton(value: String): Button {
        return Button(this).apply {
            text = value
            textSize = 12f
            isAllCaps = false
            setTextColor(ink)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(brass, ember)
            ).apply {
                cornerRadii = floatArrayOf(
                    dp(4).toFloat(), dp(4).toFloat(),
                    dp(18).toFloat(), dp(18).toFloat(),
                    dp(4).toFloat(), dp(4).toFloat(),
                    dp(18).toFloat(), dp(18).toFloat()
                )
            }
            minHeight = dp(48)
            setPadding(dp(18), 0, dp(18), 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ink
        window.navigationBarColor = ink
        window.decorView.systemUiVisibility = 0
        buildInterface()
        engine = OfflineAiEngine(this)
        engine.prepare(this)
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ink)
            setPadding(dp(18), dp(12), dp(18), dp(14))
        }

        val topLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val emblem = TextView(this).apply {
            text = "Y/"
            textSize = 22f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setTextColor(ink)
            gravity = Gravity.CENTER
            background = shape(brass, 6f)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        topLine.addView(emblem)

        val brandBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brandBlock.addView(label("YOUR GPT", 19f, paper, true))
        brandBlock.addView(label("FIELD INTELLIGENCE  /  LOCAL UNIT", 9f, muted))
        topLine.addView(brandBlock, LinearLayout.LayoutParams(0, -2, 1f))

        val edition = label("EDITION 01", 9f, brass, true).apply {
            background = shape(panel, 3f)
            setPadding(dp(9), dp(7), dp(9), dp(7))
        }
        topLine.addView(edition)
        root.addView(topLine)

        val divider = View(this).apply {
            setBackgroundColor(ember)
        }
        root.addView(divider, LinearLayout.LayoutParams(-1, dp(2)).apply {
            topMargin = dp(16)
            bottomMargin = dp(12)
        })

        val section = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        section.addView(label("01", 12f, ember, true))
        section.addView(label("   CONVERSATION LOG", 10f, muted, true))
        root.addView(section)

        status = label("BOOTING LOCAL CORE…", 10f, brass, true).apply {
            setPadding(0, dp(10), 0, dp(3))
        }
        root.addView(status)
        progress = label("The model is prepared on this device only.", 10f, muted)
        root.addView(progress)

        scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(8), 0, dp(8))
        }
        transcript = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(transcript)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val composerFrame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(panel, 5f, brass)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        composerFrame.addView(label("TRANSMIT A MESSAGE", 9f, brass, true))

        val composerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        input = EditText(this).apply {
            hint = "Write in Bangla or English…"
            setHintTextColor(muted)
            setTextColor(paper)
            textSize = 15f
            setSingleLine(false)
            maxLines = 4
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = null
        }
        composerRow.addView(input, LinearLayout.LayoutParams(0, dp(54), 1f))

        sendButton = actionButton("SEND  ↗").apply {
            isEnabled = false
            alpha = 0.45f
            setOnClickListener { sendCurrentMessage() }
        }
        composerRow.addView(sendButton)
        composerFrame.addView(composerRow)
        root.addView(composerFrame)

        retryButton = actionButton("RETRY MODEL SETUP").apply {
            visibility = View.GONE
            setOnClickListener {
                isEnabled = false
                engine.prepare(this@MainActivity)
            }
        }
        root.addView(retryButton, LinearLayout.LayoutParams(-1, dp(48)).apply {
            topMargin = dp(8)
        })

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentMessage()
                true
            } else false
        }

        setContentView(root)
        addSystemMessage(
            "This is your private workbench. The first model setup needs an internet connection; after setup, text generation runs on this phone."
        )
    }

    private fun sendCurrentMessage() {
        val message = input.text.toString().trim()
        if (message.isEmpty() || !sendButton.isEnabled) return
        addMessage(message, true)
        input.setText("")
        activeAssistantBubble = addMessage("", false)
        sendButton.isEnabled = false
        sendButton.alpha = 0.45f
        engine.send(message, this)
    }

    private fun addSystemMessage(value: String) {
        val view = label(value, 13f, muted).apply {
            background = shape(panel, 4f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        transcript.addView(view, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(8), 0, dp(8))
        })
    }

    private fun addMessage(value: String, fromUser: Boolean): TextView {
        val bubble = label(value, 14f, if (fromUser) paper else paper)
        bubble.setPadding(dp(14), dp(12), dp(14), dp(12))
        bubble.background = shape(if (fromUser) userFill else panel, 5f,
            if (fromUser) Color.TRANSPARENT else brass)
        val params = LinearLayout.LayoutParams(
            if (fromUser) dp(300) else -1, -2
        ).apply {
            gravity = if (fromUser) Gravity.END else Gravity.START
            setMargins(0, dp(5), 0, dp(5))
        }
        transcript.addView(bubble, params)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        return bubble
    }

    override fun onStatus(message: String) {
        status.text = message.uppercase()
        retryButton.visibility = View.GONE
    }

    override fun onProgress(percent: Int) {
        progress.text = "MODEL DOWNLOAD  /  $percent%"
    }

    override fun onReady() {
        status.text = "LOCAL CORE READY  /  OFFLINE"
        progress.text = "Model stored on this device. Network is no longer needed for chat."
        sendButton.isEnabled = true
        sendButton.alpha = 1f
        retryButton.visibility = View.GONE
    }

    override fun onReplyChunk(text: String) {
        activeAssistantBubble?.append(text)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onReplyFinished() {
        sendButton.isEnabled = true
        sendButton.alpha = 1f
    }

    override fun onError(message: String) {
        status.text = "LOCAL CORE NEEDS ATTENTION"
        progress.text = message
        retryButton.visibility = View.VISIBLE
        retryButton.isEnabled = true
        sendButton.isEnabled = false
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        if (::engine.isInitialized) engine.close()
        super.onDestroy()
    }
}
