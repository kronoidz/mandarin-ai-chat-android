package com.mandarin.aichat

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var messageList: RecyclerView
    private lateinit var messageInput: TextInputEditText

    private val chatAdapter = ChatAdapter()
    private val replyHandler = Handler(Looper.getMainLooper())
    private var pendingReply: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        messageList = findViewById(R.id.message_list)
        messageInput = findViewById(R.id.message_input)
        val sendButton = findViewById<MaterialButton>(R.id.button_send)

        messageList.layoutManager = LinearLayoutManager(this)
        messageList.adapter = chatAdapter

        restoreMessages(savedInstanceState)

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
        replyHandler.removeCallbacksAndMessages(null)
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

        scheduleReply()
    }

    private fun scheduleReply() {
        pendingReply?.let(replyHandler::removeCallbacks)
        pendingReply = Runnable {
            chatAdapter.add(ChatMessage(LOREM_IPSUM, isUser = false))
            scrollToBottom()
        }
        replyHandler.postDelayed(pendingReply!!, REPLY_DELAY_MS)
    }

    private fun scrollToBottom() {
        messageList.scrollToPosition(chatAdapter.itemCount - 1)
    }

    private companion object {
        const val STATE_TEXTS = "state_texts"
        const val STATE_SENDERS = "state_senders"
        const val REPLY_DELAY_MS = 600L
        const val LOREM_IPSUM =
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " +
                        "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. " +
                        "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris."
    }
}
