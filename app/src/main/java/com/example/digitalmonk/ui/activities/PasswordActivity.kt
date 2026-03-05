package com.example.digitalmonk.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class PasswordActivity : ComponentActivity() {
    companion object {
        var isUnlocked = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle back button press to prevent exiting the lock screen
        onBackPressedDispatcher.addCallback(this) {
            val startMain = Intent(Intent.ACTION_MAIN)
            startMain.addCategory(Intent.CATEGORY_HOME)
            startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(startMain)
        }

        setContent {
            PasswordScreen()
        }
    }

    @Composable
    fun PasswordScreen() {
        var password by remember { mutableStateOf("") }

        Column(
            modifier = Modifier.Companion.fillMaxSize().background(Color.Companion.White),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Companion.CenterHorizontally
        ) {
            Text("Restricted Access", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.Companion.height(20.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Enter Parent PIN") },
                singleLine = true
            )

            Spacer(modifier = Modifier.Companion.height(20.dp))

            Button(onClick = {
                if (password == "1234") { // CHANGE THIS PIN
                    isUnlocked = true
                    finish() // Close lock screen
                } else {
                    Toast.makeText(applicationContext, "Wrong PIN", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("Unlock Settings")
            }

            Spacer(modifier = Modifier.Companion.height(10.dp))
            Button(onClick = {
                // Go to home screen
                val startMain = Intent(Intent.ACTION_MAIN)
                startMain.addCategory(Intent.CATEGORY_HOME)
                startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(startMain)
            }, colors = ButtonDefaults.buttonColors(containerColor = Color.Companion.Gray)) {
                Text("Exit")
            }
        }
    }


}