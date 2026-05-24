package com.example.virtualuwb.data.repository

import android.util.Log
import com.example.virtualuwb.BuildConfig
import com.example.virtualuwb.data.remote.dto.GoogleRoutesRequestDto
import com.example.virtualuwb.data.remote.supabase.SupabaseClientProvider
import com.example.virtualuwb.domain.model.RoutePoint
import com.example.virtualuwb.domain.model.RouteResult
import com.example.virtualuwb.domain.repository.GoogleRoutesRepository
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SupabaseGoogleRoutesRepository : GoogleRoutesRepository {

    companion object {
        private const val TAG = "GOOGLE_ROUTES"
        private const val FUNCTION_NAME = "google-routes"
    }

    private val jsonConfig = Json { ignoreUnknownKeys = true }

    override suspend fun computeRoute(
        origin: RoutePoint,
        destination: RoutePoint,
        travelMode: String
    ): RouteResult {
        return try {
            val request = GoogleRoutesRequestDto(origin, destination, travelMode)
            val requestJson = jsonConfig.encodeToString(request)
            val supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY

            Log.d(TAG, "Calling function=$FUNCTION_NAME")

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
            
            val responseBody = jsonConfig.decodeFromString<RouteResult>(responseText)
            responseBody
        } catch (e: Exception) {
            Log.e(TAG, "Route request failed: ${e.message}", e)
            RouteResult(
                success = false,
                error = e.message ?: "Unknown error",
                source = "GOOGLE_ROUTES"
            )
        }
    }
}
