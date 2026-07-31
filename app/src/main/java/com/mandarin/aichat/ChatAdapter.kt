package com.mandarin.aichat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    private val items = mutableListOf<ChatMessage>()

    fun add(message: ChatMessage) {
        items.add(message)
        notifyItemInserted(items.size - 1)
    }

    fun restore(messages: List<ChatMessage>) {
        items.clear()
        items.addAll(messages)
        notifyDataSetChanged()
    }

    fun snapshot(): List<ChatMessage> = items.toList()

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int =
            if (items[position].isUser) TYPE_USER else TYPE_BOT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutRes =
                if (viewType == TYPE_USER) {
                    R.layout.item_chat_message_user
                } else {
                    R.layout.item_chat_message_bot
                }
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        val textView = view.findViewById<TextView>(R.id.message_text)
        return MessageViewHolder(view, textView)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(items[position])
    }

    class MessageViewHolder(rootView: View, private val textView: TextView) :
            RecyclerView.ViewHolder(rootView) {

        fun bind(message: ChatMessage) {
            textView.text = message.text
        }
    }

    private companion object {
        const val TYPE_USER = 0
        const val TYPE_BOT = 1
    }
}
