package com.example.digitalmonk.ui.activities

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities // Added
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class LockScreenActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var checkLoop: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Prevent screen capture/recording (optional security)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // 2. Disable Back Button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing (User cannot exit)
            }
        })

        setContent {
            LockScreenUI()
        }
    }

    override fun onResume() {
        super.onResume()
        startChecking()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(checkLoop)
    }

    // Continuously check if the user turned the settings back ON
    private fun startChecking() {
        checkLoop = object : Runnable {
            override fun run() {
                if (isEverythingEnabled(applicationContext)) {
                    finish() // UNLOCK the phone
                } else {
                    handler.postDelayed(this, 1000) // Check every second
                }
            }
        }
        handler.post(checkLoop)
    }

    companion object {
        fun isEverythingEnabled(context: Context): Boolean {
            val internet = isInternetAvailable(context)
            val location = isLocationEnabled(context)
            return internet && location
        }

        private fun isInternetAvailable(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            // Modern way to check for internet (Replaces Deprecated NetworkInfo)
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        private fun isLocationEnabled(context: Context): Boolean {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    @Composable
    fun LockScreenUI() {
        val context = LocalContext.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "⚠️ DEVICE LOCKED ⚠️",
                color = Color.Red,
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "Internet and Location are REQUIRED for this device.",
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Please pull down the status bar and enable Wi-Fi/Data and Location to continue.",
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(30.dp))

            Button(onClick = {
                // Open Location Settings as a shortcut
                try {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } catch (e: Exception) { }
            }) { Text("Open Location Settings") }

            Spacer(modifier = Modifier.height(10.dp))

            Button(onClick = {
                // Open Wifi Settings
                try {
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                } catch (e: Exception) { }
            }) { Text("Open Wi-Fi Settings") }
        }
    }
}