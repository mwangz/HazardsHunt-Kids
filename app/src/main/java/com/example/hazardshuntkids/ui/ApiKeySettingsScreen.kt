package com.example.`hazardshuntkids`.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.`hazardshuntkids`.ApiKeyManager
import android.content.Context

@Composable
fun ApiKeySettingsScreen(
    context: Context,
    onDone: () -> Unit
) {
    var apiKey by remember { mutableStateOf(ApiKeyManager.get(context) ?: "") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Modify OpenAI API Key", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            placeholder = { Text("sk-...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = {
                ApiKeyManager.save(context, apiKey)
                onDone()
            }) {
                Text("Save")
            }

            Button(onClick = {
                ApiKeyManager.clear(context)
                onDone()
            }) {
                Text("Clear")
            }
        }
    }
}
