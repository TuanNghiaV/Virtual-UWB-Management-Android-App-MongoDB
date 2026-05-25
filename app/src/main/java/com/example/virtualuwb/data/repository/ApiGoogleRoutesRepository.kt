package com.example.virtualuwb.data.repository

import android.util.Log
import com.example.virtualuwb.BuildConfig
import com.example.virtualuwb.data.remote.MongoClientProvider
import com.example.virtualuwb.data.remote.dto.ApiRouteResponseDto
import com.example.virtualuwb.data.remote.dto.GoogleRoutesRequestDto
import com.example.virtualuwb.domain.model.RoutePoint
import com.example.virtualuwb.domain.model.RouteResult
import com.example.virtualuwb.domain.model.RouteStep
import com.example.virtualuwb.domain.repository.GoogleRoutesRepository
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

class ApiGoogleRoutesRepository : GoogleRoutesRepository {

    companion object {
        private const val TAG = "API_GOOGLE_ROUTES"
        private const val ROUTE_PATH = "/api/routes/google"
        private const val SOURCE = "GOOGLE_ROUTES"
    }

    private val jsonConfig = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun computeRoute(
        origin: RoutePoint,
        destination: RoutePoint,
        travelMode: String
    ): RouteResult {
        val request = GoogleRoutesRequestDto(origin, destination, travelMode)

        return try {
            val response = MongoClientProvider.client.post("${BuildConfig.MONGODB_API_BASE_URL}$ROUTE_PATH") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            val responseText = response.bodyAsText()
            Log.d(TAG, "HTTP status=${response.status.value}")

            if (response.status.value !in 200..299) {
                return routeFailure(
                    statusCode = response.status.value,
                    responseText = responseText
                )
            }

            val apiResponse = runCatching {
                jsonConfig.decodeFromString(ApiRouteResponseDto.serializer(), responseText)
            }.getOrElse { error ->
                Log.e(TAG, "Failed to parse route response: ${error.message}", error)
                return RouteResult(
                    success = false,
                    error = "Failed to parse route response: ${error.message ?: error::class.simpleName}",
                    source = SOURCE
                )
            }

            RouteResult(
                success = true,
                distanceMeters = apiResponse.distanceMeters,
                duration = apiResponse.duration,
                encodedPolyline = apiResponse.encodedPolyline,
                steps = apiResponse.steps.map { step ->
                    RouteStep(
                        distanceMeters = step.distanceMeters,
                        duration = step.duration,
                        instruction = step.instruction
                    )
                },
                source = apiResponse.source ?: SOURCE
            )
        } catch (e: java.io.IOException) {
            Log.e(TAG, "Route backend is unavailable: ${e.message}", e)
            RouteResult(
                success = false,
                error = "Backend is not running or connection failed",
                source = SOURCE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Route request failed: ${e.message}", e)
            RouteResult(
                success = false,
                error = e.message ?: e::class.simpleName ?: "Unknown route error",
                source = SOURCE
            )
        }
    }

    private fun routeFailure(statusCode: Int, responseText: String): RouteResult {
        val backendMessage = extractBackendMessage(responseText)
        val normalizedMessage = when {
            backendMessage?.contains("SERVICE_CONFIGURATION_ERROR", ignoreCase = true) == true -> {
                backendMessage
            }
            backendMessage.isNullOrBlank() -> when (statusCode) {
                400 -> "Invalid route request (400)"
                500 -> "Backend route error (500)"
                502, 503, 504 -> "Route backend unavailable ($statusCode)"
                else -> "Route request failed ($statusCode)"
            }
            else -> backendMessage
        }

        return RouteResult(
            success = false,
            error = normalizedMessage,
            source = SOURCE
        )
    }

    private fun extractBackendMessage(responseText: String): String? {
        val trimmed = responseText.trim()
        if (trimmed.isBlank()) {
            return null
        }

        return runCatching {
            val element = jsonConfig.decodeFromString<kotlinx.serialization.json.JsonObject>(trimmed)
            element["message"]?.jsonPrimitive?.contentOrNull
                ?: element["error"]?.jsonPrimitive?.contentOrNull
                ?: element["code"]?.jsonPrimitive?.contentOrNull
                ?: element["details"]?.jsonPrimitive?.contentOrNull
                ?: trimmed
        }.getOrElse {
            trimmed
        }
    }
}