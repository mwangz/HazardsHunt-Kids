package com.example.`hazardshuntkids`.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ApiKeySetupScreen(
    onSave: (String) -> Unit
) {
    var apiKey by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Enter your OpenAI API Key",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            placeholder = { Text("sk-...") },
            singleLine = true
        )

        Button(
            onClick = { onSave(apiKey) },
            enabled = apiKey.startsWith("sk-")
        ) {
            Text("Save API Key")
        }
    }
}
