package com.curbme.app.service.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.curbme.app.data.local.prefs.PrefsManager

/**
 * Why we made this file:
 * This overlay acts as the security gatekeeper. It draws a dialog-like window
 * over the screen asking for a PIN. It uses a Callback Interface to report
 * back whether the user successfully entered the PIN or canceled the prompt.
 * 
 * What the file name defines:
 * "PinPrompt" dictates the UI's purpose.
 * "Overlay" means it is drawn at the system level via WindowManager.
 */
class PinPromptOverlay(context: Context) {
    private val context: Context
    private val windowManager: WindowManager?
    private val prefs: PrefsManager
    private var overlayView: View? = null

    /**
     * The Listener Interface. Whoever summons this overlay MUST provide a
     * callback to know what happens next.
     */
    interface PinResultCallback {
        fun onSuccess()
        fun onCancel()
    }

    init {
        this.context = context.getApplicationContext()
        this.windowManager = this.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager?
        this.prefs = PrefsManager(this.context)
    }

    /**
     * Displays the PIN prompt overlay.
     * 
     * @param reason   The message to display (e.g., "Enter PIN to uninstall").
     * @param callback The listener to trigger on success or cancel.
     */
    fun show(reason: String?, callback: PinResultCallback) {
        if (overlayView != null) return  // Already showing

        if (windowManager == null) return

        try {
            overlayView = createProgrammaticView(reason, callback)

            val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,  // CRITICAL DIFFERENCE from BlockOverlayView:
                // We DO NOT use FLAG_NOT_FOCUSABLE here because we need the EditText
                // to receive focus so the Android soft-keyboard pops up!
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT
            )

            // Darken the background behind the PIN prompt to focus the user's attention
            params.dimAmount = 0.8f
            params.gravity = Gravity.CENTER

            windowManager.addView(overlayView, params)
            Log.i(TAG, "PIN Prompt shown for: " + reason)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show PIN overlay", e)
            callback.onCancel() // Failsafe
        }
    }

    fun hide() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView)
                overlayView = null
            } catch (e: Exception) {
                Log.e(TAG, "Error removing PIN overlay", e)
            }
        }
    }

    /**
     * Builds the UI programmatically.
     */
    private fun createProgrammaticView(reason: String?, callback: PinResultCallback): View {
        val rootLayout = LinearLayout(context)
        rootLayout.setOrientation(LinearLayout.VERTICAL)
        rootLayout.setGravity(Gravity.CENTER)

        // Container for the actual dialog box to make it look nice
        val dialogBox = LinearLayout(context)
        dialogBox.setOrientation(LinearLayout.VERTICAL)
        dialogBox.setBackgroundColor(Color.WHITE)
        dialogBox.setPadding(64, 64, 64, 64)
        dialogBox.setGravity(Gravity.CENTER)

        val title = TextView(context)
        title.setText("Parental Lock")
        title.setTextSize(24f)
        title.setTextColor(Color.BLACK)
        title.setPadding(0, 0, 0, 16)

        val reasonText = TextView(context)
        reasonText.setText(reason)
        reasonText.setTextColor(Color.DKGRAY)
        reasonText.setPadding(0, 0, 0, 32)

        val pinInput = EditText(context)
        pinInput.setHint("Enter 4-Digit PIN")
        // Ensure the input is treated as a numeric password (dots instead of numbers)
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        pinInput.setGravity(Gravity.CENTER)

        val submitButton = Button(context)
        submitButton.setText("Unlock")
        submitButton.setOnClickListener(View.OnClickListener { v: View? ->
            val enteredPin = pinInput.getText().toString()
            // Assuming your PrefsManager has a method to validate the PIN
            if (prefs.validatePin(enteredPin)) {
                hide()
                callback.onSuccess()
            } else {
                Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                pinInput.setText("") // Clear the input on failure
            }
        })

        val cancelButton = Button(context)
        cancelButton.setText("Cancel")
        cancelButton.setBackgroundColor(Color.TRANSPARENT)
        cancelButton.setTextColor(Color.GRAY)
        cancelButton.setOnClickListener(View.OnClickListener { v: View? ->
            hide()
            callback.onCancel()
        })

        dialogBox.addView(title)
        dialogBox.addView(reasonText)
        dialogBox.addView(pinInput)
        dialogBox.addView(submitButton)
        dialogBox.addView(cancelButton)

        rootLayout.addView(dialogBox)

        return rootLayout
    }

    companion object {
        private const val TAG = "PinPromptOverlay"
    }
}