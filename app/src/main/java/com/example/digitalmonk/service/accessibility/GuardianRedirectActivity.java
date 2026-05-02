package com.example.digitalmonk.service.accessibility;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.digitalmonk.service.overlay.SettingsBlockOverlayService;
import com.example.digitalmonk.ui.dashboard.MainActivity;

/**
 * GuardianRedirectActivity
 *
 * Launched by SettingsPageReader when the "Device admin app" or "App Info"
 * page is detected. Immediately covers the dangerous Settings page by
 * launching our own MainActivity on top, then finishes itself.
 *
 * No SYSTEM_ALERT_WINDOW permission needed — Activity launch is sufficient.
 */
public class GuardianRedirectActivity extends Activity {

    private static final String TAG = "GuardianRedirect";

    // How long to wait before finishing this transparent bridge activity
    private static final long DELAY_FINISH_MS = 500L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Black screen — child sees nothing between Settings and our app
        getWindow().getDecorView().setBackgroundColor(android.graphics.Color.BLACK);
        if (getActionBar() != null) getActionBar().hide();

        Log.w(TAG, "GuardianRedirectActivity launched");

        redirectToMainApp();
    }

    /**
     * Immediately launches MainActivity on top of the dangerous Settings page,
     * then hides the overlay and finishes this bridge activity after a short delay.
     */
    private void redirectToMainApp() {
        // Step 1 (0ms): Launch MainActivity — this instantly covers the Settings page.
        // FLAG_ACTIVITY_CLEAR_TOP ensures any existing MainActivity instance is reused
        // and brought to front rather than creating a duplicate.
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        getApplicationContext().startActivity(intent);

        // Step 2 (0ms): Hide the settings block overlay since we've taken over the screen
        SettingsBlockOverlayService.hide(getApplicationContext());

        Log.i(TAG, "✅ MainActivity launched over Settings page");

        // Step 3 (500ms): Finish this transparent bridge activity.
        // Delayed slightly to ensure MainActivity has fully rendered before we disappear.
        handler.postDelayed(() -> {
            Log.i(TAG, "Bridge activity finishing");
            finish();
        }, DELAY_FINISH_MS);
    }

    /**
     * Fallback: if something goes wrong, at minimum go to home screen.
     * Called if the accessibility service is unexpectedly null.
     */
    private void fallbackAndFinish() {
        Log.e(TAG, "Fallback triggered — going to home screen");

        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);

        SettingsBlockOverlayService.hide(getApplicationContext());
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}