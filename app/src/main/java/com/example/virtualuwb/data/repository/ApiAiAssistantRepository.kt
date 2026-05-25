package com.example.virtualuwb.data.repository

import android.util.Log
import com.example.virtualuwb.BuildConfig
import com.example.virtualuwb.data.remote.MongoClientProvider
import com.example.virtualuwb.data.remote.dto.ApiAiAssistantRequestDto
import com.example.virtualuwb.data.remote.dto.ApiAiAssistantResponseDto
import com.example.virtualuwb.data.remote.dto.ApiAiPhoneDto
import com.example.virtualuwb.data.remote.dto.ApiAiRouteDto
import com.example.virtualuwb.data.remote.dto.ApiAiRouteStepDto
import com.example.virtualuwb.data.remote.dto.AiAssistantContextDto
import com.example.virtualuwb.domain.repository.AiAssistantRepository
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class ApiAiAssistantRepository : AiAssistantRepository {

    companion object {
        private const val TAG = "API_AI_ASSISTANT"
        private const val ROUTE_PATH = "/api/ai/assistant"
    }

    private val jsonConfig = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun askAssistant(
        question: String,
        context: AiAssistantContextDto,
        selectedTagCode: String?
    ): Result<String> {
        return try {
        val request = ApiAiAssistantRequestDto(
            message = question,
            phone = context.phonePosition?.let { position ->
                ApiAiPhoneDto(
                    latitude = position.latitude,
                    longitude = position.longitude
                )
            },
            selectedTagCode = selectedTagCode?.takeIf { it.isNotBlank() },
            route = context.selectedTagRoute?.let { route ->
                ApiAiRouteDto(
                    distanceMeters = route.distanceMeters,
                    duration = route.duration,
                    steps = route.steps.map { step ->
                        ApiAiRouteStepDto(
                            instruction = step.instruction,
                            distanceMeters = step.distanceMeters,
                            duration = step.duration
                        )
                    }
                )
            }
        )

        val response = MongoClientProvider.client.post("${BuildConfig.MONGODB_API_BASE_URL}$ROUTE_PATH") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val responseText = response.bodyAsText()
        Log.d(TAG, "HTTP status=${response.status.value}")

        if (response.status.value !in 200..299) {
            return Result.failure(Exception(mapBackendError(response.status.value, responseText)))
        }

        val responseBody = runCatching {
            jsonConfig.decodeFromString(ApiAiAssistantResponseDto.serializer(), responseText)
        }.getOrElse { error ->
            Log.e(TAG, "Failed to parse AI response: ${error.message}", error)
            return Result.failure(Exception("Failed to parse AI response: ${error.message ?: error::class.simpleName}"))
        }

        val answer = responseBody.answer?.trim()
        if (!answer.isNullOrBlank()) {
            responseBody.contextSummary?.let {
                Log.d(TAG, "Context summary tags=${it.tags}, anchors=${it.anchors}, geofences=${it.geofences}, recentEvents=${it.recentEvents}, selectedTagCode=${it.selectedTagCode}")
            }
            Result.success(answer)
        } else {
            val message = responseBody.message
                ?: responseBody.detail
                ?: responseBody.error
                ?: "Unknown error from AI backend"
            Result.failure(Exception(mapFriendlyError(responseBody.error, message)))
        }
        } catch (e: java.io.IOException) {
            Log.e(TAG, "AI backend is unavailable: ${e.message}", e)
            return Result.failure(Exception("MongoDB AI backend is unavailable. Please check backend server."))
        } catch (e: Exception) {
            Log.e(TAG, "AI assistant request failed: ${e.message}", e)
            return Result.failure(Exception(e.message ?: "MongoDB AI backend is unavailable. Please check backend server."))
        }
    }

    private fun mapBackendError(statusCode: Int, responseText: String): String {
        val backendError = extractBackendField(responseText, listOf("error", "code"))
        val backendMessage = extractBackendField(responseText, listOf("message", "detail"))

        return when (backendError) {
            "INVALID_REQUEST" -> backendMessage ?: "Invalid AI request."
            "SERVICE_CONFIGURATION_ERROR" -> "AI backend is not configured. Please set GEMINI_API_KEY on the backend."
            "BAD_UPSTREAM_RESPONSE" -> "MongoDB AI backend returned an invalid upstream response. Please try again."
            "UPSTREAM_TIMEOUT" -> "MongoDB AI backend timed out. Please try again."
            else -> when (statusCode) {
                400 -> backendMessage ?: "Invalid AI request."
                500 -> backendMessage ?: "MongoDB AI backend returned an internal error."
                502, 503, 504 -> backendMessage ?: "MongoDB AI backend is unavailable. Please check backend server."
                else -> backendMessage ?: backendError ?: "MongoDB AI backend request failed ($statusCode)."
            }
        }
    }

    private fun mapFriendlyError(errorCode: String?, message: String): String {
        return when (errorCode) {
            "SERVICE_CONFIGURATION_ERROR" -> "AI backend is not configured. Please set GEMINI_API_KEY on the backend."
            "BAD_UPSTREAM_RESPONSE" -> "MongoDB AI backend returned an invalid upstream response. Please try again."
            "UPSTREAM_TIMEOUT" -> "MongoDB AI backend timed out. Please try again."
            "INVALID_REQUEST" -> message
            else -> message
        }
    }

    private fun extractBackendField(responseText: String, keys: List<String>): String? {
        val trimmed = responseText.trim()
        if (trimmed.isBlank()) {
            return null
        }

        return runCatching {
            val element = jsonConfig.decodeFromString<JsonObject>(trimmed)
            keys.firstNotNullOfOrNull { key -> element[key]?.jsonPrimitive?.contentOrNull }
                ?: trimmed
        }.getOrNull()
    }
}