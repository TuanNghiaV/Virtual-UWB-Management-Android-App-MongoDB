package com.example.virtualuwb.presentation.screen.assistant

import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.UwbDevice
import com.example.virtualuwb.domain.model.Geofence
import com.example.virtualuwb.presentation.viewmodel.MapUiState
import com.example.virtualuwb.utils.GeofenceMath
import com.example.virtualuwb.utils.GeoMath
import java.util.Locale

/**
 * A local, rule-based assistant engine for VirtualUWB.
 * Parses simple natural language queries about the current app state.
 */
class UwbAssistantEngine {

    fun processQuestion(question: String, uiState: MapUiState): String {
        val q = question.lowercase().trim()

        return when {
            // A. Tag Location
            isTagLocationQuestion(q) -> handleTagLocation(q, uiState)
            
            // B. Direction to Tag
            isDirectionQuestion(q) -> handleDirection(q, uiState)
            
            // C. Geofence Safety Status
            isSafetyQuestion(q) -> handleSafety(q, uiState)
            
            // D. Danger Summary
            isDangerSummaryQuestion(q) -> handleDangerSummary(uiState)
            
            else -> "I can help with tag locations, directions, and geofence safety. Try asking: Where is Tag T1?"
        }
    }

    private fun isTagLocationQuestion(q: String) =
        q.contains("where is") || q.contains("ở đâu") || q.contains("vị trí")

    private fun isDirectionQuestion(q: String) =
        q.contains("guide me") || q.contains("chỉ đường") || q.contains("đi đến") || q.contains("direction to")

    private fun isSafetyQuestion(q: String) =
        q.contains("safe") || q.contains("nguy hiểm") || q.contains("vùng nào")

    private fun isDangerSummaryQuestion(q: String) =
        (q.contains("any tags") && q.contains("danger")) || 
        q.contains("có ai") || 
        q.contains("vùng nguy hiểm") || 
        q.contains("restricted zone") ||
        q.contains("có tag nào")

    private fun handleTagLocation(q: String, uiState: MapUiState): String {
        val tag = findMentionedTag(q, uiState.tags) ?: return "I couldn't find that tag in the inventory."
        
        val pos = tag.position
        val distance = GeoMath.haversineDistanceMeters(uiState.phonePosition, pos)
        val geofences = GeofenceMath.findContainingGeofences(pos, uiState.geofences)
        val zoneText = if (geofences.isEmpty()) {
            "outside known zones"
        } else {
            val names = geofences.joinToString { it.name }
            val typeText = when {
                geofences.any { it.isRestricted } -> "(Restricted Zone)"
                geofences.any { it.type == com.example.virtualuwb.domain.model.GeofenceType.SAFE_ZONE } -> "(Safe Zone)"
                else -> ""
            }
            "inside $names $typeText"
        }

        return "${tag.name} is at (${formatCoord(pos.latitude)}, ${formatCoord(pos.longitude)}), which is $zoneText. It is ${formatDist(distance)} away from your current position."
    }

    private fun handleDirection(q: String, uiState: MapUiState): String {
        val tag = findMentionedTag(q, uiState.tags) ?: return "I couldn't find that tag to guide you."
        
        val distance = GeoMath.haversineDistanceMeters(uiState.phonePosition, tag.position)
        if (distance < 1.5) {
            return "You have reached ${tag.name}. It is within 1.5 meters of you."
        }

        val bearing = GeoMath.initialBearingDegrees(uiState.phonePosition, tag.position)
        val dirLabel = getDirectionLabel(bearing)
        
        return "${tag.name} is ${formatDist(distance)} away. Head $dirLabel to reach it."
    }

    private fun handleSafety(q: String, uiState: MapUiState): String {
        val tag = findMentionedTag(q, uiState.tags) ?: return "I couldn't identify which tag you are asking about."
        
        val containing = GeofenceMath.findContainingGeofences(tag.position, uiState.geofences)
        if (containing.isEmpty()) return "${tag.name} is currently safe, located outside any defined zones."
        
        val restricted = containing.filter { it.isRestricted }
        return if (restricted.isNotEmpty()) {
            "Caution: ${tag.name} is in a Restricted Zone (${restricted.joinToString { it.name }}) and may be in danger."
        } else {
            "${tag.name} is safe, currently located in ${containing.joinToString { it.name }}."
        }
    }

    private fun handleDangerSummary(uiState: MapUiState): String {
        val dangerTags = uiState.tags.filter { tag ->
            GeofenceMath.findContainingGeofences(tag.position, uiState.geofences).any { it.isRestricted }
        }
        
        return if (dangerTags.isEmpty()) {
            "All tags are currently in safe areas. No restricted zone entries detected."
        } else {
            "Alert: ${dangerTags.size} tags are currently in restricted zones: ${dangerTags.joinToString { it.name }}."
        }
    }

    private fun findMentionedTag(q: String, tags: List<UwbDevice>): UwbDevice? {
        // Sort by name length descending to catch "Tag T1" before "T1"
        val sortedTags = tags.sortedByDescending { it.name.length }
        for (tag in sortedTags) {
            val name = tag.name.lowercase()
            val id = tag.id.lowercase()
            if (q.contains(name) || q.contains(id) || q.contains(name.replace(" ", ""))) {
                return tag
            }
        }
        return null
    }

    private fun getDirectionLabel(bearing: Double): String {
        return when (bearing) {
            in 22.5..67.5 -> "North-East"
            in 67.5..112.5 -> "East"
            in 112.5..157.5 -> "South-East"
            in 157.5..202.5 -> "South"
            in 202.5..247.5 -> "South-West"
            in 247.5..292.5 -> "West"
            in 292.5..337.5 -> "North-West"
            else -> "North"
        }
    }

    private fun formatCoord(v: Double) = String.format(Locale.US, "%.6f", v)
    private fun formatDist(m: Double) = if (m < 1000) String.format(Locale.US, "%.1fm", m) else String.format(Locale.US, "%.2fkm", m / 1000.0)
}
