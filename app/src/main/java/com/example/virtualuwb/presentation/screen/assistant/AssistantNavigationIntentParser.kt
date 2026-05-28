package com.example.virtualuwb.presentation.screen.assistant

import java.text.Normalizer

object AssistantNavigationIntentParser {

    private val NAV_PREFIXES = listOf(
        "guide me to",
        "directions to",
        "route to",
        "navigate to",
        "how do i get to",
        "take me to",
        "find route to",
        "show route to",
        "chỉ đường đến",
        "chỉ đường tới",
        "đường đến",
        "đường tới",
        "đi đến",
        "đi tới",
        "dẫn tôi đến",
        "dẫn tôi tới",
        "tìm đường đến",
        "tìm đường tới",
        "tới",
        "đến"
    )

    fun isNavigationIntent(message: String): Boolean {
        val normalizedMsg = normalizeText(message)
        return NAV_PREFIXES.any { prefix ->
            val normalizedPrefix = normalizeText(prefix)
            normalizedMsg.contains(normalizedPrefix)
        }
    }

    fun extractTargetName(message: String): String? {
        val lowerMessage = message.trim()
        
        for (prefix in NAV_PREFIXES) {
            val idx = lowerMessage.indexOf(prefix, ignoreCase = true)
            if (idx != -1) {
                val remainder = lowerMessage.substring(idx + prefix.length).trim()
                val cleaned = remainder.replace(Regex("[?.!,]"), "").trim()
                if (cleaned.isNotEmpty()) {
                    return cleaned
                }
            }
        }
        
        val lastWord = lowerMessage.split(Regex("\\s+")).lastOrNull()
        return lastWord?.replace(Regex("[?.!,]"), "")?.trim()
    }

    fun normalizeText(input: String): String {
        val temp = Normalizer.normalize(input, Normalizer.Form.NFD)
        val regex = Regex("\\p{Mn}+")
        val clean = regex.replace(temp, "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .lowercase()
            .trim()
        return clean.replace(Regex("\\s+"), " ")
    }
}
