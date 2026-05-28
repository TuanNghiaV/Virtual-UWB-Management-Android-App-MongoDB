package com.example.virtualuwb.presentation.screen.assistant

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.virtualuwb.data.remote.dto.*
import com.example.virtualuwb.data.repository.ApiAiAssistantRepository

import com.example.virtualuwb.domain.model.AssistantMessage
import com.example.virtualuwb.domain.model.DataSourceMode
import com.example.virtualuwb.domain.model.GeofenceType
import com.example.virtualuwb.domain.repository.AiAssistantRepository
import com.example.virtualuwb.presentation.viewmodel.MapUiState
import com.example.virtualuwb.utils.GeoMath
import com.example.virtualuwb.utils.GeofenceMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.round
import java.util.*

/**
 * ViewModel for the AI Assistant screen.
 * Handles the conversation state and coordinates context building for the Gemini-powered backend.
 */
class AssistantViewModel(
) : ViewModel() {

    companion object {
        private const val TAG = "AI_ASSISTANT"
    }

    private val apiRepository: AiAssistantRepository = ApiAiAssistantRepository()

    private val _messages = MutableStateFlow<List<AssistantMessage>>(
        listOf(
            AssistantMessage(
                id = UUID.randomUUID().toString(),
                text = "Hi, I can locate tags, check safety status, and guide you directly to them.",
                isUser = false
            )
        )
    )
    val messages = _messages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText = _inputText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    /**
     * Sends a question to the AI Assistant.
     * Builds a comprehensive UWB context (tags, positions, zones) from the current [uiState].
     */
    fun sendMessage(uiState: MapUiState, text: String? = null) {
        val question = (text ?: _inputText.value).trim()
        if (question.isBlank() || _isLoading.value) return

        Log.d(TAG, "sendMessage question=$question")

        val userMessage = AssistantMessage(
            id = UUID.randomUUID().toString(),
            text = question,
            isUser = true
        )
        
        _messages.value = _messages.value + userMessage
        if (text == null) _inputText.value = "" // Clear input if it wasn't a suggestion
        _isLoading.value = true

        viewModelScope.launch {
            val context = buildContext(uiState)
            val selectedTagCode = uiState.selectedTag?.id
            val repository = resolveRepository(uiState.dataSourceMode)
            Log.d(TAG, "sendMessage compactContext=${compactContextLog(context)}")
            val result = repository.askAssistant(question, context, selectedTagCode)
            
            _isLoading.value = false
            
            result.onSuccess { answer ->
                Log.d(TAG, "sendMessage success answer=$answer")
                val assistantMessage = AssistantMessage(
                    id = UUID.randomUUID().toString(),
                    text = answer,
                    isUser = false
                )
                _messages.value = _messages.value + assistantMessage
            }.onFailure {
                Log.e(TAG, "sendMessage failed: ${it.message}", it)
                val errorMessage = AssistantMessage(
                    id = UUID.randomUUID().toString(),
                    text = it.message?.takeIf { message -> message.isNotBlank() }
                        ?: "AI Assistant is temporarily unavailable. Please try again.",
                    isUser = false
                )
                _messages.value = _messages.value + errorMessage
            }
        }
    }

    private fun buildContext(uiState: MapUiState): AiAssistantContextDto {
        val hasGeofences = uiState.geofences.isNotEmpty()
        val phoneZones = GeofenceMath.findContainingGeofences(uiState.phonePosition, uiState.geofences)
        val phoneZone = resolvePriorityZone(phoneZones)
        val phoneZoneName = phoneZone?.name ?: "Unknown"
        val phoneZoneType = mapZoneType(phoneZone?.type)
        val phoneSafetyStatus = mapSafetyStatus(phoneZone?.type)
        val phoneInsideKnownUwbArea = if (hasGeofences) phoneZone != null else null
        val phoneLocationSource = if (uiState.isPhoneGpsLocation) "GPS" else "SIMULATED"

        val tags = uiState.devices.filter { it.isTag }.map { tag ->
            val containing = GeofenceMath.findContainingGeofences(tag.position, uiState.geofences)
            val zone = resolvePriorityZone(containing)
            val zoneName = zone?.name ?: "Unknown"
            val zoneType = mapZoneType(zone?.type)
            val safetyStatus = mapSafetyStatus(zone?.type)
            val insideKnownUwbArea = if (hasGeofences) zone != null else null

            val distanceMeters = GeoMath.haversineDistanceMeters(uiState.phonePosition, tag.position)
            val bearingDegrees = GeoMath.initialBearingDegrees(uiState.phonePosition, tag.position)
            val distanceRounded = roundToOneDecimal(distanceMeters)
            val bearingRounded = roundToOneDecimal(bearingDegrees)
            val hasHeading = uiState.phoneAzimuthDegrees != 0.0
            val relativeBearing = if (hasHeading) {
                GeoMath.normalizeDegrees(bearingDegrees - uiState.phoneAzimuthDegrees)
            } else {
                bearingDegrees
            }
            val relativeDirection = if (hasHeading) bearingToUserRelativeDirection(relativeBearing) else bearingToMapRelativeDirection(bearingDegrees)
            val navigationHintText = buildNavigationHint(distanceRounded, bearingDegrees, relativeDirection, hasHeading)
            val guidanceType = if (hasHeading) "USER_RELATIVE" else "MAP_BASED"

            TagContextDto(
                id = tag.id,
                name = tag.name,
                deviceCode = tag.id,
                latitude = tag.position.latitude,
                longitude = tag.position.longitude,
                currentZoneName = zoneName,
                currentZoneType = zoneType,
                safetyStatus = safetyStatus,
                distanceFromPhoneMeters = distanceRounded,
                bearingFromPhoneDegrees = bearingRounded,
                relativeDirection = relativeDirection,
                navigationHint = navigationHintText,
                navigationHintText = navigationHintText,
                guidanceType = guidanceType,
                insideKnownUwbArea = insideKnownUwbArea,
                zoneName = zone?.name,
                zoneType = zone?.type?.name,
                distance = String.format(Locale.US, "%.1fm", distanceRounded),
                direction = relativeDirection
            )
        }

        val phoneContext = PhoneContextDto(
            latitude = uiState.phonePosition.latitude,
            longitude = uiState.phonePosition.longitude,
            locationSource = phoneLocationSource,
            currentZoneName = phoneZoneName,
            currentZoneType = phoneZoneType,
            safetyStatus = phoneSafetyStatus,
            insideKnownUwbArea = phoneInsideKnownUwbArea
        )

        val geofences = uiState.geofences.map {
            GeofenceContextDto(name = it.name, type = it.type.name)
        }

        val routeContext = uiState.routeToSelectedTag?.takeIf { it.success }?.let { route ->
            RouteContextDto(
                source = route.source,
                distanceMeters = route.distanceMeters,
                duration = route.duration,
                steps = route.steps.map { step ->
                    RouteStepContextDto(
                        instruction = step.instruction,
                        distanceMeters = step.distanceMeters,
                        duration = step.duration
                    )
                }
            )
        }

        return AiAssistantContextDto(
            tags = tags,
            geofences = geofences,
            phonePosition = PhonePositionDto(uiState.phonePosition.latitude, uiState.phonePosition.longitude),
            phone = phoneContext,
            selectedTagRoute = routeContext,
            note = if (tags.isEmpty()) "No live UWB context is currently available." else null
        )
    }

    private fun resolvePriorityZone(containingZones: List<com.example.virtualuwb.domain.model.Geofence>): com.example.virtualuwb.domain.model.Geofence? {
        return containingZones.firstOrNull { it.type == GeofenceType.RESTRICTED_ZONE }
            ?: containingZones.firstOrNull { it.type == GeofenceType.SAFE_ZONE }
            ?: containingZones.firstOrNull { it.type == GeofenceType.ROOM }
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
            append("\"phone\":")
            append(context.phone?.let {
                "{\"latitude\":${it.latitude},\"longitude\":${it.longitude},\"currentZoneName\":${it.currentZoneName?.let { zoneName -> "\"$zoneName\"" } ?: "null"},\"currentZoneType\":\"${it.currentZoneType}\",\"safetyStatus\":\"${it.safetyStatus}\"}"
            } ?: "null")
            append(',')
            append("\"selectedTagRoute\":")
            append(context.selectedTagRoute?.let { route ->
                "{\"source\":\"${route.source}\",\"distanceMeters\":${route.distanceMeters},\"steps\":${route.steps.size}}"
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
                append("\"direction\":${tag.direction?.let { "\"$it\"" } ?: "null"},")
                append("\"guidanceType\":${tag.guidanceType?.let { "\"$it\"" } ?: "null"},")
                append("\"navigationHintText\":${tag.navigationHintText?.let { "\"$it\"" } ?: "null"}")
                append('}')
            }
            append("]}")
        }
    }

    private fun mapZoneType(type: GeofenceType?): String {
        return when (type) {
            GeofenceType.RESTRICTED_ZONE -> "RESTRICTED"
            GeofenceType.SAFE_ZONE -> "SAFE"
            GeofenceType.ROOM -> "ROOM"
            null -> "UNKNOWN"
        }
    }

    private fun mapSafetyStatus(type: GeofenceType?): String {
        return when (type) {
            GeofenceType.RESTRICTED_ZONE -> "DANGER"
            GeofenceType.SAFE_ZONE -> "SAFE"
            GeofenceType.ROOM, null -> "UNKNOWN"
        }
    }

    private fun bearingToUserRelativeDirection(bearingDegrees: Double): String {
        val normalized = GeoMath.normalizeDegrees(bearingDegrees)
        return when {
            normalized >= 337.5 || normalized < 22.5 -> "đi thẳng"
            normalized < 67.5 -> "đi chếch sang phải"
            normalized < 112.5 -> "rẽ phải"
            normalized < 157.5 -> "đi chếch sang phải phía sau"
            normalized < 202.5 -> "quay lại"
            normalized < 247.5 -> "đi chếch sang trái phía sau"
            normalized < 292.5 -> "rẽ trái"
            else -> "đi chếch sang trái"
        }
    }

    private fun bearingToMapRelativeDirection(bearingDegrees: Double): String {
        val normalized = GeoMath.normalizeDegrees(bearingDegrees)
        return when {
            normalized >= 337.5 || normalized < 22.5 -> "đi về phía Bắc trên bản đồ"
            normalized < 67.5 -> "đi về phía Đông Bắc trên bản đồ"
            normalized < 112.5 -> "đi về phía Đông trên bản đồ"
            normalized < 157.5 -> "đi về phía Đông Nam trên bản đồ"
            normalized < 202.5 -> "đi về phía Nam trên bản đồ"
            normalized < 247.5 -> "đi về phía Tây Nam trên bản đồ"
            normalized < 292.5 -> "đi về phía Tây trên bản đồ"
            else -> "đi về phía Tây Bắc trên bản đồ"
        }
    }

    private fun buildNavigationHint(
        distanceMeters: Double?,
        bearingDegrees: Double?,
        relativeDirection: String,
        hasHeading: Boolean
    ): String {
        if (distanceMeters == null || bearingDegrees == null) {
            return "Không có vị trí điện thoại hiện tại nên chưa thể chỉ hướng."
        }

        val distanceText = String.format(Locale.US, "%.1f", distanceMeters)
        return if (hasHeading) {
            "Hãy $relativeDirection khoảng $distanceText m."
        } else {
            "Hãy $relativeDirection khoảng $distanceText m."
        }
    }

    private fun roundToOneDecimal(value: Double): Double {
        return round(value * 10.0) / 10.0
    }

    private fun resolveRepository(mode: DataSourceMode): AiAssistantRepository {
        // Always use MongoDB API repository.
        return apiRepository
    }
}
