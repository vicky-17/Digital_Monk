package com.example.digitalmonk.services;

import android.util.Log;
import com.example.digitalmonk.data.PrefsManager;
import com.example.digitalmonk.utils.Constants;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d("FCM", "New Token Generated: " + token);

        // 1. Save it locally
        PrefsManager prefs = new PrefsManager(this);
        prefs.saveFcmToken(token);

        // 2. Tell the Node.js server about the new token immediately
        String deviceId = prefs.getDeviceId();
        if (deviceId != null) {
            sendTokenToServer(deviceId, token);
        }
    }

    // Sends the fresh token to your Heroku backend
    private void sendTokenToServer(String deviceId, String token) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Gson gson = new Gson();

                // Prepare JSON Payload
                Map<String, Object> payload = new HashMap<>();
                payload.put("deviceId", deviceId);
                payload.put("fcmToken", token);

                RequestBody body = RequestBody.create(gson.toJson(payload), MediaType.get("application/json"));

                // Make the POST request
                Request req = new Request.Builder()
                        .url(Constants.BASE_URL + "/api/devices/update-token")
                        .post(body)
                        .build();

                client.newCall(req).execute().close();
                Log.d("FCM", "✅ Updated Token sent to Server");

            } catch (Exception e) {
                Log.e("FCM", "❌ Failed to update token on server", e);
            }
        }).start();
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        // This runs when the Node.js server sends a silent ping!
        if (remoteMessage.getData().size() > 0) {
            String action = remoteMessage.getData().get("action");
            Log.d("FCM", "📥 Received Silent Ping from Server: " + action);

            if ("SYNC_SETTINGS".equals(action)) {
                // The server told us settings changed!
                Log.d("FCM", "🔄 Triggering instant settings sync...");

                // Note: To actually trigger SyncService here later, you can broadcast
                // an intent or directly start the sync function.
            }
        }

        // This triggers if you sent a visible notification (like your test script does)
        if (remoteMessage.getNotification() != null) {
            Log.d("FCM", "🔔 Message Notification Body: " + remoteMessage.getNotification().getBody());
        }
    }
}