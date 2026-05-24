package com.example.virtualuwb.data.repository

import android.util.Log
import com.example.virtualuwb.BuildConfig
import com.example.virtualuwb.data.remote.dto.AiAssistantContextDto
import com.example.virtualuwb.data.remote.dto.AiAssistantRequestDto
import com.example.virtualuwb.data.remote.dto.AiAssistantResponseDto
import com.example.virtualuwb.data.remote.supabase.SupabaseClientProvider
import com.example.virtualuwb.domain.repository.AiAssistantRepository
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SupabaseAiAssistantRepository : AiAssistantRepository {

    companion object {
        private const val TAG = "AI_ASSISTANT"
        private const val FUNCTION_NAME = "uwb-ai-assistant"
    }

    override suspend fun askAssistant(
        question: String,
        context: AiAssistantContextDto
    ): Result<String> = try {
        val request = AiAssistantRequestDto(question, context)
        val requestJson = Json.encodeToString(request)
        val supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY

        Log.d(TAG, "Calling function=$FUNCTION_NAME")
        Log.d(TAG, "Request question=$question")
        Log.d(TAG, "Request compactContext=${compactContextLog(context)}")
        
        // Call the Supabase Edge Function using direct HTTP POST
        val response = SupabaseClientProvider.client.httpClient.post(
            "${BuildConfig.SUPABASE_URL}/functions/v1/$FUNCTION_NAME"
        ) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $supabaseAnonKey")
            header("apikey", supabaseAnonKey)
            setBody(requestJson)
        }
        
        val responseText = response.bodyAsText()
        Log.d(TAG, "HTTP status=${response.status.value}")
        Log.d(TAG, "Raw response body=$responseText")
        val responseBody = Json.decodeFromString<AiAssistantResponseDto>(responseText)
        
        if (responseBody.answer != null) {
            Log.d(TAG, "Parsed answer=${responseBody.answer}")
            Result.success(responseBody.answer)
        } else {
            val errorMessage = responseBody.error ?: responseBody.detail ?: "Unknown error from AI Assistant"
            Log.e(TAG, "Function returned error: $errorMessage")
            Result.failure(Exception(errorMessage))
        }
    } catch (e: Exception) {
        Log.e(TAG, "AI assistant request failed: ${e.message}", e)
        Result.failure(e)
    }

    private fun compactContextLog(context: AiAssistantContextDto): String {
        return buildString {
            append('{')
            append("\"tags\":")
            append(context.tags.size)
            append(',')
            append("\"geofences\":")
            append(context.geofences.size)
            append(',')
            append("\"phonePosition\":")
            append(context.phonePosition?.let {
                "{\"latitude\":${it.latitude},\"longitude\":${it.longitude}}"
            } ?: "null")
            append(',')
            append("\"note\":")
            append(context.note?.let { "\"$it\"" } ?: "null")
            append(',')
            append("\"tagDetails\":[")
            context.tags.forEachIndexed { index, tag ->
                if (index > 0) append(',')
                append('{')
                append("\"id\":\"${tag.id}\",")
                append("\"name\":\"${tag.name}\",")
                append("\"latitude\":${tag.latitude},")
                append("\"longitude\":${tag.longitude},")
                append("\"zoneName\":${tag.zoneName?.let { "\"$it\"" } ?: "null"},")
                append("\"zoneType\":${tag.zoneType?.let { "\"$it\"" } ?: "null"},")
                append("\"distance\":${tag.distance?.let { "\"$it\"" } ?: "null"},")
                append("\"direction\":${tag.direction?.let { "\"$it\"" } ?: "null"}")
                append('}')
            }
            append("]}")
        }
    }
}
