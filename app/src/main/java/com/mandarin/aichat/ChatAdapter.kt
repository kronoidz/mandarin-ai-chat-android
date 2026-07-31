package com.mandarin.aichat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    private val items = mutableListOf<ChatMessage>()

    /** Toggled by the activity; [notifyDataSetChanged] on change. */
    var pinyinEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    /** Adds a message and returns its adapter position. */
    fun add(message: ChatMessage): Int {
        items.add(message)
        val position = items.size - 1
        notifyItemInserted(position)
        return position
    }

    /** Replaces the text of the message at [position] in place (for streaming updates). */
    fun updateMessage(position: Int, text: String) {
        items[position] = items[position].copy(text = text)
        notifyItemChanged(position)
    }

    fun restore(messages: List<ChatMessage>) {
        items.clear()
        items.addAll(messages)
        notifyDataSetChanged()
    }

    fun snapshot(): List<ChatMessage> = items.toList()

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int =
            when {
                items[position].isFeedback -> TYPE_FEEDBACK
                items[position].isUser -> TYPE_USER
                else -> TYPE_BOT
            }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutRes =
                when (viewType) {
                    TYPE_USER -> R.layout.item_chat_message_user
                    TYPE_BOT -> R.layout.item_chat_message_bot
                    else -> R.layout.item_chat_message_feedback
                }
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        val textView = view.findViewById<PinyinTextView>(R.id.message_text)
        return MessageViewHolder(view, textView)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(items[position], pinyinEnabled)
    }

    class MessageViewHolder(rootView: View, private val textView: PinyinTextView) :
            RecyclerView.ViewHolder(rootView) {

        fun bind(message: ChatMessage, pinyinEnabled: Boolean) {
            textView.text = message.text
            textView.pinyinEnabled = pinyinEnabled
        }
    }

    private companion object {
        const val TYPE_USER = 0
        const val TYPE_BOT = 1
        const val TYPE_FEEDBACK = 2
    }
}
