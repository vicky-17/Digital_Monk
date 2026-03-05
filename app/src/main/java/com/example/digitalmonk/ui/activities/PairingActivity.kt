package com.example.digitalmonk.ui.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.digitalmonk.utils.Constants
import com.example.digitalmonk.data.PrefsManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

class PairingActivity : ComponentActivity() {

    private val PAIR_URL = Constants.API_BASE_URL + "/devices/pair"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isForcePair = intent.getBooleanExtra("FORCE_PAIR", false)

        if (!isForcePair && PrefsManager(this).isPaired) {
            goToMain(syncNow = false)
            return
        }

        setContent {
            PairingScreen()
        }
    }

    @Composable
    fun PairingScreen() {
        var code by remember { mutableStateOf(TextFieldValue("")) }
        var isLoading by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier.fillMaxSize().padding(30.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🔗 Connect Device", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(10.dp))
            Text("Enter the 6-digit code from the Parent Dashboard")

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { if (it.text.length <= 6) code = it },
                label = { Text("Pairing Code") },
                singleLine = true,
                // ✅ OPEN NUMBER KEYBOARD
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val cleanCode = code.text.trim()
                    if (cleanCode.length == 6) {
                        isLoading = true
                        pairDevice(cleanCode) { success ->
                            // ✅ RESET BUTTON IF FAILED
                            if (!success) {
                                isLoading = false
                            }
                        }
                    } else {
                        Toast.makeText(applicationContext, "Enter 6 digits", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLoading) "Connecting..." else "Link Device")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { code = TextFieldValue("") },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear Code")
            }
        }
    }

    private fun pairDevice(code: String, onResult: (Boolean) -> Unit) {
        val client = OkHttpClient()
        val prefs = PrefsManager(applicationContext)

        val deviceId = prefs.deviceId ?: UUID.randomUUID().toString()
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".replaceFirstChar { it.uppercase() }

        val json = JSONObject()
        json.put("code", code)
        json.put("deviceId", deviceId)
        json.put("deviceName", deviceName)

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(PAIR_URL).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Network Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    onResult(false) // Trigger UI Reset
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val respBody = it.body?.string()
                        val jsonResp = JSONObject(respBody ?: "{}")

                        if (jsonResp.optBoolean("success")) {
                            prefs.saveDeviceId(deviceId)
                            runOnUiThread {
                                Toast.makeText(applicationContext, "Paired Successfully!", Toast.LENGTH_SHORT).show()
                                goToMain(syncNow = true)
                            }
                        } else {
                            runOnUiThread {
                                Toast.makeText(applicationContext, "Invalid Code", Toast.LENGTH_LONG).show()
                                onResult(false) // Trigger UI Reset
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(applicationContext, "Server Error: ${it.code}", Toast.LENGTH_SHORT).show()
                            onResult(false) // Trigger UI Reset
                        }
                    }
                }
            }
        })
    }

    private fun goToMain(syncNow: Boolean) {
        val intent = Intent(this, MainActivity::class.java)
        if (syncNow) intent.putExtra("SYNC_NOW", true)
        startActivity(intent)
        finish()
    }
}