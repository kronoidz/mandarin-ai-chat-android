package com.mandarin.aichat

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ChatService(
        private val apiUrl: String,
        private val apiKey: String,
        private val model: String
) {
    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(2, TimeUnit.MINUTES)
                    .build()

    /** Streams tokens from the OpenAI-compatible chat completions endpoint. */
    fun streamChat(messages: List<ChatMessage>): Flow<String> = flow {
        val request = buildRequest(messages)

        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}: ${response.body?.string()}")
        }

        val source = response.body?.source() ?: throw IOException("Empty response body")

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.startsWith("data: ")) {
                val data = line.removePrefix("data: ")
                if (data == "[DONE]") break

                try {
                    val content = parseToken(data)
                    if (content.isNotEmpty()) {
                        emit(content)
                    }
                } catch (_: Exception) {
                    // Skip malformed or non-content SSE chunks
                }
            }
        }
    }

    private fun buildRequest(messages: List<ChatMessage>): Request {
        val body =
                JSONObject().run {
                    put("model", model)
                    put("messages", buildMessagesArray(messages))
                    put("stream", true)
                    put("thinking", JSONObject().apply { put("type", "disabled") })
                    toString()
                }

        return Request.Builder()
                .url("$apiUrl/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
    }

    private fun buildMessagesArray(messages: List<ChatMessage>): JSONArray {
        val array = JSONArray()
        array.put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                }
        )
        messages.forEach { msg ->
            array.put(
                    JSONObject().apply {
                        put("role", if (msg.isUser) "user" else "assistant")
                        put("content", msg.text)
                    }
            )
        }
        return array
    }

    private fun parseToken(data: String): String {
        val root = JSONObject(data)
        val choices = root.getJSONArray("choices")
        if (choices.length() == 0) return ""

        val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return ""
        return delta.optString("content", "")
    }

    private companion object {
        const val SYSTEM_PROMPT =
                "You are a helpful AI language tutor specializing in Mandarin Chinese. " +
                        "Respond to the user's messages, helping them learn and practice Mandarin. " +
                        "When appropriate, include pinyin and English translations."
    }
}
