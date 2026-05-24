package com.example.virtualuwb.domain.model

data class AssistantMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
