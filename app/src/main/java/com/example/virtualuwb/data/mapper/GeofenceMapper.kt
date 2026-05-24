package com.example.virtualuwb.data.mapper

import com.example.virtualuwb.data.remote.dto.GeofenceDto
import com.example.virtualuwb.data.remote.dto.UpsertRectangleGeofenceParams
import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.Geofence
import com.example.virtualuwb.domain.model.GeofenceType
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

object GeofenceMapper {

    fun GeofenceDto.toDomain(): Geofence {
        val parsedType = runCatching { GeofenceType.valueOf(this.type) }
            .getOrDefault(GeofenceType.RESTRICTED_ZONE)

        val parsedVertices = mutableListOf<GeoPoint>()
        
        try {
            val jsonArray = this.vertices.jsonArray
            for (i in 0 until jsonArray.size) {
                val item = jsonArray[i].jsonArray
                val lon = item[0].jsonPrimitive.double
                val lat = item[1].jsonPrimitive.double
                parsedVertices.add(GeoPoint(latitude = lat, longitude = lon))
            }
            
            // Remove duplicate last point if it matches the first point (GeoJSON polygon closure)
            if (parsedVertices.size > 1 && parsedVertices.first() == parsedVertices.last()) {
                parsedVertices.removeAt(parsedVertices.size - 1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (parsedVertices.size < 3) {
            throw IllegalArgumentException("Geofence must have at least 3 unique vertices")
        }

        return Geofence(
            id = this.geofence_code,
            name = this.name,
            type = parsedType,
            vertices = parsedVertices
        )
    }

    fun Geofence.toRectangleRpcParams(): UpsertRectangleGeofenceParams {
        val minLat = this.vertices.minOf { it.latitude }
        val maxLat = this.vertices.maxOf { it.latitude }
        val minLon = this.vertices.minOf { it.longitude }
        val maxLon = this.vertices.maxOf { it.longitude }

        return UpsertRectangleGeofenceParams(
            p_geofence_code = this.id,
            p_name = this.name,
            p_type = this.type.name,
            p_min_lat = minLat,
            p_max_lat = maxLat,
            p_min_lon = minLon,
            p_max_lon = maxLon
        )
    }
}
