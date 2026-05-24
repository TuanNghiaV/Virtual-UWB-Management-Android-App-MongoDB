package com.example.virtualuwb.data.repository

import com.example.virtualuwb.data.mapper.PositionLogMapper.toDomain
import com.example.virtualuwb.data.mapper.PositionLogMapper.toInsertParams
import com.example.virtualuwb.data.remote.dto.GetPositionLogsParams
import com.example.virtualuwb.data.remote.dto.PositionLogDto
import com.example.virtualuwb.data.remote.supabase.SupabaseClientProvider
import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.PositionLog
import com.example.virtualuwb.domain.repository.PositionLogRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc

class SupabasePositionLogRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client
) : PositionLogRepository {

    override suspend fun insertLog(log: PositionLog) {
        client.postgrest.rpc(
            "insert_position_log_by_device_code",
            log.toInsertParams()
        )
    }

    override suspend fun insertDevicePosition(
        deviceId: String,
        position: GeoPoint,
        source: String
    ) {
        val log = PositionLog(
            deviceId = deviceId,
            position = position,
            source = source
        )
        insertLog(log)
    }

    override suspend fun getLogsForDevice(deviceId: String, limit: Int): List<PositionLog> {
        val dtoList = client.postgrest.rpc(
            "get_position_logs_by_device_code",
            GetPositionLogsParams(
                p_device_code = deviceId,
                p_limit = limit
            )
        ).decodeList<PositionLogDto>()

        return dtoList.map { it.toDomain() }
    }

    suspend fun testConnection(deviceId: String = "tag-t1"): Result<Int> {
        return try {
            val logs = getLogsForDevice(deviceId, 10)
            Result.success(logs.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
