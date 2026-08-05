package com.mandarin.aichat

import android.util.Log
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
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

data class AiResponse(val response: String, val feedback: String?)

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

    /** Streams raw tokens from the OpenAI-compatible chat completions endpoint. */
    fun streamChat(messages: List<ChatMessage>, thinkingEffort: String): Flow<String> = flow {
        val request = buildRequest(messages, thinkingEffort)

        Log.d(TAG, "→ POST ${request.url}  model=$model  messages=${messages.size}")

        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string().orEmpty()
            Log.e(TAG, "HTTP ${response.code}: $errorBody")
            throw IOException("HTTP ${response.code}: $errorBody")
        }

        val source = response.body?.source() ?: throw IOException("Empty response body")

        var chunkCount = 0
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.startsWith("data: ")) {
                val data = line.removePrefix("data: ")
                if (data == "[DONE]") {
                    Log.d(TAG, "Stream complete after $chunkCount chunks")
                    break
                }

                try {
                    val content = parseToken(data)
                    if (content.isNotEmpty()) {
                        chunkCount++
                        emit(content)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping malformed SSE chunk: $data", e)
                }
            }
        }
    }

    /**
     * Parses the accumulated JSON response into its [response] and optional [feedback] fields.
     * Handles multiple concatenated JSON objects (DeepSeek streaming + json_object mode sometimes
     * emits response and feedback as separate top-level objects).
     */
    fun parseResponse(rawJson: String): AiResponse {
        Log.d(TAG, "Parsing response (${rawJson.length} chars)")

        if (rawJson.isBlank()) {
            val hex =
                    rawJson.take(200).toByteArray(Charsets.UTF_8).joinToString(" ") {
                        "%02x".format(it)
                    }
            Log.e(TAG, "Raw JSON is blank; hex: $hex")
            throw IOException("Model returned empty response")
        }

        val tokener = JSONTokener(rawJson.trim())
        var response = ""
        var feedback: String? = null

        try {
            while (true) {
                val value = tokener.nextValue()
                if (value is JSONObject) {
                    if (value.has("response")) {
                        response = value.getString("response")
                    }
                    if (value.has("feedback")) {
                        feedback = value.getString("feedback")
                    }
                }
            }
        } catch (_: JSONException) {
            // Normal end of token stream
        }

        if (response.isEmpty()) {
            Log.e(TAG, "No 'response' field in parsed JSON; raw=${rawJson.take(500)}")
            throw IOException("Model response missing 'response' field")
        }

        Log.d(
                TAG,
                "Parsed: response=${response.length} chars, feedback=${feedback?.length ?: 0} chars"
        )
        return AiResponse(response, feedback)
    }

    private fun buildRequest(messages: List<ChatMessage>, thinkingEffort: String): Request {
        val body =
                JSONObject().run {
                    put("model", model)
                    put("messages", buildMessagesArray(messages))
                    put("stream", true)
                    put(
                            "thinking",
                            if (thinkingEffort == "disabled") {
                                JSONObject().apply { put("type", "disabled") }
                            } else {
                                JSONObject().apply {
                                    put("type", "enabled")
                                    put("thinking_effort", thinkingEffort)
                                }
                            }
                    )
                    put("response_format", JSONObject().apply { put("type", "json_object") })
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
                        if (!msg.isUser) {
                            // Wrap assistant content as JSON to match the system prompt
                            // format, preventing the model from forgetting JSON mode in
                            // multi-turn conversations.
                            val wrapped = JSONObject().apply { put("response", msg.text) }
                            put("content", wrapped.toString())
                        } else {
                            put("content", msg.text)
                        }
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
        const val TAG = "ChatService"
        const val SYSTEM_PROMPT =
                "You are a helpful AI language tutor specializing in Mandarin Chinese. " +
                        "You MUST respond with a JSON object in exactly this format:\n" +
                        "{\n" +
                        "  \"response\": \"your reply in Simplified Chinese using only Hanzi characters\",\n" +
                        "  \"feedback\": \"optional correction guidance in English\"\n" +
                        "}\n\n" +
                        "Rules:\n" +
                        "- \"response\" is REQUIRED. Reply in Simplified Chinese using ONLY Hanzi characters " +
                        "(no Pinyin, no English). Be natural and friendly, like having a conversation " +
                        "with a friend. Keep the conversation going.\n" +
                        "- \"feedback\" is OPTIONAL. ONLY include this field when the user's message has " +
                        "issues: contains English, contains Pinyin instead of Hanzi, is grammatically wrong, " +
                        "or is unnaturally phrased. When present, write the feedback in English — " +
                        "gently explain the issue and show the correct Chinese phrasing.\n" +
                        "- If no feedback is needed, OMIT the \"feedback\" field entirely. " +
                        "Do NOT set it to null, empty string, or a placeholder."
    }
}
