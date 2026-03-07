package com.example.digitalmonk.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.digitalmonk.data.local.prefs.PrefsManager

@Composable
fun PinGateScreen(prefs: PrefsManager, onSuccess: () -> Unit) {
    var enteredPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text("Enter PIN")
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = enteredPin,
            onValueChange = {
                if (it.length <= 6) {
                    enteredPin = it
                    error = false
                }
            },
            label = { Text("Parent PIN") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            isError = error,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (enteredPin == prefs.getPin()) {
                    onSuccess()
                } else {
                    error = true
                    enteredPin = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Unlock")
        }
        if (error) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Incorrect PIN")
        }
    }
}
