package com.example.virtualuwb.domain.repository

import com.example.virtualuwb.data.remote.dto.AiAssistantContextDto

interface AiAssistantRepository {
    suspend fun askAssistant(
        question: String,
        context: AiAssistantContextDto,
        selectedTagCode: String? = null
    ): Result<String>
}
