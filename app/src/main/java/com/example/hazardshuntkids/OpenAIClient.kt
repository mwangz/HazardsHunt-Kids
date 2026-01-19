package com.example.`hazardshuntkids`

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

object OpenAIClient {

    private const val TAG = "OpenAIClient"
    private const val API_URL = "https://api.openai.com/v1/responses"

    private val client = OkHttpClient()

    fun analyzeImage(
        context: Context,
        imageUri: Uri,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val apiKey = ApiKeyManager.get(context)?.trim()
        if (apiKey.isNullOrEmpty()) {
            onError("API key not found")
            return
        }

        try {
            // 1️⃣ 读取图片 bytes
            val inputStream: InputStream =
                context.contentResolver.openInputStream(imageUri)
                    ?: run {
                        onError("Cannot open image Uri")
                        return
                    }

            val imageBytes = inputStream.readBytes()
            inputStream.close()

            // 2️⃣ Base64 编码
            val imageBase64 =
                Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            // 3️⃣ 构造 JSON（Responses API 正确格式）
            val contentArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "input_text")
                    put("text", "You are analyzing a photo for hazards for kids. \n" +
                            "Give a very short, simple, and friendly explanation of **only the hazards or safety issues you can actually see in the photo**. \n" +
                            "Do NOT make assumptions or imagine situations that are not visible. \n" +
                            "Please give a **short, clear summary** in 2-5 sentences using easy words and short sentences, focusing on what is directly visible in the image.\n" +
                            "Make it easy to read aloud.")
//                    put("text", "You are an assistant analyzing hazards in an image. \n" +
//                            "Please give a **short, clear summary** in 2-5 sentences, \n" +
//                            "focusing on key hazards or safety issues.")
                })
                put(JSONObject().apply {
                    put("type", "input_image")
                    put(
                        "image_url",
                        "data:image/jpeg;base64,$imageBase64"
                    )
                })
            }

            val inputArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", contentArray)
                })
            }

            val json = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("input", inputArray)
            }

            val requestBody = json.toString()
                .toRequestBody("application/json".toMediaType())

            // 4️⃣ HTTP 请求
            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    Log.e(TAG, "Request failed", e)
                    onError(e.message ?: "Network error")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val body = it.body?.string()
                        if (!it.isSuccessful || body == null) {
                            onError("HTTP ${it.code}: $body")
                            return
                        }

                        try {
                            val root = JSONObject(body)
                            val outputText = extractText(root)
                            onResult(outputText)
                        } catch (e: Exception) {
                            onError("Parse error: ${e.message}")
                        }
                    }
                }
            })

        } catch (e: Exception) {
            onError(e.message ?: "Unexpected error")
        }
    }

    // 从 responses API 返回中提取文本
    private fun extractText(root: JSONObject): String {
        val output = root.optJSONArray("output") ?: return "No output"
        for (i in 0 until output.length()) {
            val item = output.getJSONObject(i)
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val c = content.getJSONObject(j)
                if (c.optString("type") == "output_text") {
                    return c.optString("text")
                }
            }
        }
        return "No analysis text found"
    }
}
