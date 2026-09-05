package com.curbme.app.ui.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * Manages a small floating badge over short-video viewers showing today's reel scroll count.
 */
class ReelCounterOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: TextView? = null
    private var isShowing = false

    fun showCount(count: Int) {
        if (!Settings.canDrawOverlays(context)) return

        mainHandler.post {
            try {
                if (overlayView == null) {
                    createOverlayView()
                }

                overlayView?.text = "🎬 $count"

                if (!isShowing && overlayView != null) {
                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.END
                        x = dpToPx(16)
                        y = dpToPx(80)
                    }

                    windowManager.addView(overlayView, params)
                    isShowing = true
                }
            } catch (e: Exception) {
                // Non-fatal overlay error
            }
        }
    }

    fun hide() {
        mainHandler.post {
            try {
                if (isShowing && overlayView != null) {
                    windowManager.removeView(overlayView)
                    isShowing = false
                }
            } catch (_: Exception) {}
        }
    }

    fun destroy() {
        hide()
        overlayView = null
    }

    private fun createOverlayView() {
        val tv = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6))

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12).toFloat()
                setColor(Color.parseColor("#CC000000"))
                setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
            }
        }
        overlayView = tv
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
