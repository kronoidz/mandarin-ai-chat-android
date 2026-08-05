package com.mandarin.aichat

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var messageList: RecyclerView
    private lateinit var messageInput: TextInputEditText
    private lateinit var switchPinyin: SwitchCompat
    private lateinit var buttonSettings: ImageButton

    private val chatAdapter = ChatAdapter()

    private val chatService by lazy {
        ChatService(
                apiUrl = BuildConfig.OPENAI_API_URL,
                apiKey = BuildConfig.OPENAI_API_KEY,
                model = BuildConfig.OPENAI_MODEL
        )
    }

    private var streamJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        messageList = findViewById(R.id.message_list)
        messageInput = findViewById(R.id.message_input)
        switchPinyin = findViewById(R.id.switch_pinyin)
        buttonSettings = findViewById(R.id.button_settings)
        val sendButton = findViewById<MaterialButton>(R.id.button_send)

        messageList.layoutManager = LinearLayoutManager(this)
        messageList.adapter = chatAdapter

        restoreMessages(savedInstanceState)

        // Configure TTS availability based on build credentials.
        chatAdapter.ttsAvailable = TextToSpeechService.isAvailable

        chatAdapter.onSpeakClick = { text -> speakText(text) }

        switchPinyin.setOnCheckedChangeListener { _, isChecked ->
            chatAdapter.pinyinEnabled = isChecked
        }

        buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        sendButton.setOnClickListener { sendMessage() }
        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }

    override fun onDestroy() {
        streamJob?.cancel()
        streamJob = null
        releaseMediaPlayer()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_TEXTS, ArrayList(chatAdapter.snapshot().map { it.text }))
        outState.putBooleanArray(
                STATE_SENDERS,
                chatAdapter.snapshot().map { it.isUser }.toBooleanArray()
        )
    }

    private fun restoreMessages(savedInstanceState: Bundle?) {
        val texts: List<String> = savedInstanceState?.getStringArrayList(STATE_TEXTS) ?: emptyList()
        val senders = savedInstanceState?.getBooleanArray(STATE_SENDERS) ?: booleanArrayOf()
        val messages =
                texts.mapIndexed { index, text ->
                    ChatMessage(text, isUser = senders.getOrElse(index) { false })
                }
        chatAdapter.restore(messages)
        if (messages.isNotEmpty()) {
            messageList.scrollToPosition(messages.size - 1)
        }
    }

    private fun sendMessage() {
        val text = messageInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return

        chatAdapter.add(ChatMessage(text, isUser = true))
        scrollToBottom()
        messageInput.setText("")

        startStreamingResponse()
    }

    private fun startStreamingResponse() {
        streamJob?.cancel()

        val placeholderPosition = chatAdapter.add(ChatMessage("...", isUser = false))
        scrollToBottom()

        val history = buildHistory()
        val thinkingEffort = SettingsActivity.getThinkingEffort()

        streamJob =
                lifecycleScope.launch {
                    val raw = StringBuilder()
                    try {
                        chatService.streamChat(history, thinkingEffort).collect { token ->
                            raw.append(token)
                        }

                        Log.d(TAG, "Raw JSON (${raw.length} chars): ${raw.take(500)}")
                        if (raw.isNotEmpty() && raw.all { it.isWhitespace() }) {
                            val hex =
                                    raw.toString().take(200).toByteArray().joinToString(" ") {
                                        "%02x".format(it)
                                    }
                            Log.w(TAG, "Raw JSON appears to be all whitespace; hex: $hex")
                        }

                        val parsed = chatService.parseResponse(raw.toString())
                        chatAdapter.updateMessage(placeholderPosition, parsed.response)

                        if (parsed.feedback != null) {
                            chatAdapter.add(
                                    ChatMessage(parsed.feedback, isUser = false, isFeedback = true)
                            )
                        }
                        scrollToBottom()
                    } catch (e: Exception) {
                        Log.e(TAG, "Stream failed", e)
                        Log.e(TAG, "Raw JSON so far: ${raw.take(1000)}")
                        chatAdapter.updateMessage(
                                placeholderPosition,
                                getString(R.string.error_prefix, e.localizedMessage ?: "")
                        )
                    }
                }
    }

    /**
     * Builds the conversation history from the adapter, excluding feedback messages and the last
     * placeholder ("...") so neither is sent to the API.
     */
    private fun buildHistory(): List<ChatMessage> {
        val messages = chatAdapter.snapshot()
        if (messages.isEmpty()) return emptyList()
        return messages.dropLast(1).filter { !it.isFeedback }
    }

    private fun scrollToBottom() {
        messageList.post { messageList.smoothScrollToPosition(chatAdapter.itemCount - 1) }
    }

    // ---- TTS / Speaker ----

    private fun speakText(text: String) {
        releaseMediaPlayer()

        // Check cache first — no network call needed on a hit.
        val cached = TtsAudioCache.get(text)
        if (cached != null) {
            playAudio(cached)
            return
        }

        lifecycleScope.launch {
            try {
                val audioBytes = TextToSpeechService.synthesize(text)
                TtsAudioCache.put(text, audioBytes)
                playAudio(audioBytes)
            } catch (e: Exception) {
                Log.e(TAG, "TTS failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.error_prefix, e.localizedMessage ?: ""),
                                    Toast.LENGTH_SHORT
                            )
                            .show()
                }
            }
        }
    }

    private fun playAudio(audioBytes: ByteArray) {
        val tempFile = File.createTempFile("tts_", ".mp3", cacheDir)
        tempFile.writeBytes(audioBytes)

        mediaPlayer =
                MediaPlayer().apply {
                    setDataSource(tempFile.absolutePath)
                    setOnPreparedListener { start() }
                    setOnCompletionListener {
                        releaseMediaPlayer()
                        tempFile.delete()
                    }
                    setOnErrorListener { _, _, _ ->
                        releaseMediaPlayer()
                        tempFile.delete()
                        true
                    }
                    prepareAsync()
                }
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    private companion object {
        const val TAG = "MainActivity"
        const val STATE_TEXTS = "state_texts"
        const val STATE_SENDERS = "state_senders"
    }
}
