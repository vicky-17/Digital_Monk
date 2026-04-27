package com.example.digitalmonk.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * PinGateScreen remains in Kotlin to support Jetpack Compose.
 * It observes the AuthViewModel (Kotlin) which communicates with PrefsManager (Java).
 */
@Composable
fun PinGateScreen(viewModel: AuthViewModel, onSuccess: () -> Unit) {
    var enteredPin by remember { mutableStateOf("") }
    val error by viewModel.pinError.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Enter Parent PIN",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = enteredPin,
            onValueChange = {
                if (it.length <= 6) {
                    enteredPin = it
                    viewModel.clearError()
                }
            },
            label = { Text("Parent PIN") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            isError = error,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    // Calls validatePin which uses Java logic internally
                    if (viewModel.validatePin(enteredPin)) {
                        onSuccess()
                    } else {
                        enteredPin = ""
                    }
                }
            )
        )

        if (error) {
            Text(
                text = "Incorrect PIN. Please try again.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (viewModel.validatePin(enteredPin)) {
                    onSuccess()
                } else {
                    enteredPin = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enteredPin.isNotEmpty()
        ) {
            Text("Unlock")
        }
    }
}