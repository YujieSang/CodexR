package com.example.codexmobile

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** A small, app-owned control surface shown only while CodexR works in the background. */
class ExecutionOverlay(
    private val context: Context,
    private val onStop: () -> Unit,
    private val onFollowUp: (String) -> Boolean,
    private val onDismiss: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private var messageView: TextView? = null
    private var composer: LinearLayout? = null
    private var input: EditText? = null
    private var params: WindowManager.LayoutParams? = null

    fun showOrUpdate(message: String) {
        if (!Settings.canDrawOverlays(context) || isDeviceLocked()) {
            hide()
            return
        }
        if (root == null) create()
        messageView?.text = compactOverlayMessage(message)
    }

    fun hide() {
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
        messageView = null
        composer = null
        input = null
        params = null
    }

    private fun create() {
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val panelColor = if (dark) Color.rgb(31, 31, 35) else Color.rgb(250, 249, 255)
        val textColor = if (dark) Color.WHITE else Color.rgb(32, 31, 36)
        val accentColor = if (dark) Color.rgb(128, 108, 255) else Color.rgb(79, 55, 224)

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                color = android.content.res.ColorStateList.valueOf(panelColor)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), if (dark) Color.rgb(75, 75, 82) else Color.rgb(215, 212, 222))
            }
            elevation = dp(10).toFloat()
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(context).apply {
            text = "CodexR is working"
            setTextColor(textColor)
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            contentDescription = "Hide overlay"
            setColorFilter(textColor)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { onDismiss(); hide() }
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        panel.addView(header)

        messageView = TextView(context).apply {
            setTextColor(textColor)
            textSize = 14f
            maxLines = 6
            setPadding(0, dp(4), 0, dp(10))
        }.also { panel.addView(it, LinearLayout.LayoutParams(-1, -2)) }

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        actions.addView(Button(context).apply {
            text = "Stop"
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(183, 28, 28))
            setOnClickListener { onStop() }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) })
        actions.addView(Button(context).apply {
            text = "Follow up"
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
            setOnClickListener { setComposerVisible(composer?.visibility != View.VISIBLE) }
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        panel.addView(actions)

        composer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(0, dp(10), 0, 0)
        }.also { row ->
            input = EditText(context).apply {
                hint = "Add a follow-up"
                setTextColor(textColor)
                setHintTextColor(if (dark) Color.LTGRAY else Color.DKGRAY)
                maxLines = 3
                minLines = 1
                setSingleLine(false)
            }.also { row.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
            row.addView(Button(context).apply {
                text = "Send"
                setOnClickListener {
                    val text = input?.text?.toString().orEmpty().trim()
                    if (text.isNotEmpty() && onFollowUp(text)) {
                        input?.text?.clear()
                        setComposerVisible(false)
                        messageView?.text = "Follow-up queued. It will be delivered at the next tool boundary."
                    }
                }
            }, LinearLayout.LayoutParams(-2, dp(44)).apply { marginStart = dp(6) })
            panel.addView(row)
        }

        val layoutParams = WindowManager.LayoutParams(
            minOf(dp(340), context.resources.displayMetrics.widthPixels - dp(24)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(72)
        }
        makeDraggable(header, layoutParams)
        runCatching { windowManager.addView(panel, layoutParams) }
            .onSuccess {
                root = panel
                params = layoutParams
            }
    }

    private fun setComposerVisible(visible: Boolean) {
        val panel = root ?: return
        val layoutParams = params ?: return
        composer?.visibility = if (visible) View.VISIBLE else View.GONE
        layoutParams.flags = if (visible) {
            layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        runCatching { windowManager.updateViewLayout(panel, layoutParams) }
        if (visible) {
            input?.requestFocus()
            input?.post {
                context.getSystemService(InputMethodManager::class.java)
                    .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        } else {
            context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(panel.windowToken, 0)
        }
    }

    private fun makeDraggable(handle: View, layoutParams: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layoutParams.x
                    startY = layoutParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // END gravity reverses horizontal coordinates.
                    layoutParams.x = (startX - (event.rawX - touchX)).roundToInt().coerceAtLeast(0)
                    layoutParams.y = (startY + (event.rawY - touchY)).roundToInt().coerceAtLeast(0)
                    root?.let { runCatching { windowManager.updateViewLayout(it, layoutParams) } }
                    true
                }
                else -> false
            }
        }
    }

    private fun isDeviceLocked(): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
}

internal fun compactOverlayMessage(message: String, limit: Int = 600): String {
    val recent = if (message.length > limit * 2) message.takeLast(limit * 2) else message
    val normalized = recent.replace(Regex("\\s+"), " ").trim()
    if (normalized.isEmpty()) return "Processing…"
    return if (normalized.length <= limit) normalized else "…" + normalized.takeLast(limit - 1).trimStart()
}
