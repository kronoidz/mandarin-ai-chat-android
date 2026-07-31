package com.mandarin.aichat

data class ChatMessage(val text: String, val isUser: Boolean, val isFeedback: Boolean = false)
