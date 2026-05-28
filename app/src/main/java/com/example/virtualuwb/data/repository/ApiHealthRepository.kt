package com.example.virtualuwb.data.repository

import com.example.virtualuwb.BuildConfig
import com.example.virtualuwb.data.remote.MongoClientProvider
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

@Serializable
data class HealthStatusDto(
    val status: String,
    val service: String,
    val database: String,
    val timestamp: String
)

class ApiHealthRepository {
    private val baseUrl = BuildConfig.MONGODB_API_BASE_URL

    suspend fun checkHealth(): HealthStatusDto {
        return MongoClientProvider.client
            .get("$baseUrl/api/health")
            .body()
    }
}
