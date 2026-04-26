package com.example.digitalmonk.service.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.digitalmonk.data.local.prefs.PrefsManager;

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
public class PinPromptOverlay {

    private static final String TAG = "PinPromptOverlay";

    private final Context context;
    private final WindowManager windowManager;
    private final PrefsManager prefs;
    private View overlayView;

    /**
     * The Listener Interface. Whoever summons this overlay MUST provide a
     * callback to know what happens next.
     */
    public interface PinResultCallback {
        void onSuccess();
        void onCancel();
    }

    public PinPromptOverlay(Context context) {
        this.context = context.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
        this.prefs = new PrefsManager(this.context);
    }

    /**
     * Displays the PIN prompt overlay.
     *
     * @param reason   The message to display (e.g., "Enter PIN to uninstall").
     * @param callback The listener to trigger on success or cancel.
     */
    public void show(String reason, PinResultCallback callback) {
        if (overlayView != null) return; // Already showing
        if (windowManager == null) return;

        try {
            overlayView = createProgrammaticView(reason, callback);

            int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutFlag,
                    // CRITICAL DIFFERENCE from BlockOverlayView:
                    // We DO NOT use FLAG_NOT_FOCUSABLE here because we need the EditText
                    // to receive focus so the Android soft-keyboard pops up!
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                    PixelFormat.TRANSLUCENT
            );

            // Darken the background behind the PIN prompt to focus the user's attention
            params.dimAmount = 0.8f;
            params.gravity = Gravity.CENTER;

            windowManager.addView(overlayView, params);
            Log.i(TAG, "PIN Prompt shown for: " + reason);

        } catch (Exception e) {
            Log.e(TAG, "Failed to show PIN overlay", e);
            callback.onCancel(); // Failsafe
        }
    }

    public void hide() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
                overlayView = null;
            } catch (Exception e) {
                Log.e(TAG, "Error removing PIN overlay", e);
            }
        }
    }

    /**
     * Builds the UI programmatically.
     */
    private View createProgrammaticView(String reason, PinResultCallback callback) {
        LinearLayout rootLayout = new LinearLayout(context);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setGravity(Gravity.CENTER);

        // Container for the actual dialog box to make it look nice
        LinearLayout dialogBox = new LinearLayout(context);
        dialogBox.setOrientation(LinearLayout.VERTICAL);
        dialogBox.setBackgroundColor(Color.WHITE);
        dialogBox.setPadding(64, 64, 64, 64);
        dialogBox.setGravity(Gravity.CENTER);

        TextView title = new TextView(context);
        title.setText("Parental Lock");
        title.setTextSize(24f);
        title.setTextColor(Color.BLACK);
        title.setPadding(0, 0, 0, 16);

        TextView reasonText = new TextView(context);
        reasonText.setText(reason);
        reasonText.setTextColor(Color.DKGRAY);
        reasonText.setPadding(0, 0, 0, 32);

        EditText pinInput = new EditText(context);
        pinInput.setHint("Enter 4-Digit PIN");
        // Ensure the input is treated as a numeric password (dots instead of numbers)
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setGravity(Gravity.CENTER);

        Button submitButton = new Button(context);
        submitButton.setText("Unlock");
        submitButton.setOnClickListener(v -> {
            String enteredPin = pinInput.getText().toString();
            // Assuming your PrefsManager has a method to validate the PIN
            if (prefs.validatePin(enteredPin)) {
                hide();
                callback.onSuccess();
            } else {
                Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show();
                pinInput.setText(""); // Clear the input on failure
            }
        });

        Button cancelButton = new Button(context);
        cancelButton.setText("Cancel");
        cancelButton.setBackgroundColor(Color.TRANSPARENT);
        cancelButton.setTextColor(Color.GRAY);
        cancelButton.setOnClickListener(v -> {
            hide();
            callback.onCancel();
        });

        dialogBox.addView(title);
        dialogBox.addView(reasonText);
        dialogBox.addView(pinInput);
        dialogBox.addView(submitButton);
        dialogBox.addView(cancelButton);

        rootLayout.addView(dialogBox);

        return rootLayout;
    }
}