package com.example.virtualuwb.utils

import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.Geofence

object GeofenceMath {

    /**
     * Determines if a point is inside a polygon using the ray casting algorithm.
     * For indoor/small areas, we treat longitude as the X coordinate and latitude as the Y coordinate.
     */
    fun containsPoint(point: GeoPoint, polygon: List<GeoPoint>): Boolean {
        if (polygon.size < 3) return false

        var isInside = false
        val x = point.longitude
        val y = point.latitude

        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val xi = polygon[i].longitude
            val yi = polygon[i].latitude
            val xj = polygon[j].longitude
            val yj = polygon[j].latitude

            val intersect = ((yi > y) != (yj > y)) &&
                    (x < (xj - xi) * (y - yi) / (yj - yi) + xi)
            if (intersect) isInside = !isInside
            j = i
        }

        return isInside
    }

    /**
     * Determines if a point is inside a specific geofence.
     */
    fun containsPoint(point: GeoPoint, geofence: Geofence): Boolean {
        return containsPoint(point, geofence.vertices)
    }

    /**
     * Finds all geofences that contain the given point.
     */
    fun findContainingGeofences(point: GeoPoint, geofences: List<Geofence>): List<Geofence> {
        return geofences.filter { containsPoint(point, it) }
    }
}
