package com.example.virtualuwb.data.repository

object RepositoryProvider {
    val apiUwbRepository by lazy { ApiUwbRepository() }
    val apiGeofenceRepository by lazy { ApiGeofenceRepository() }
    val apiGeofenceEventRepository by lazy { ApiGeofenceEventRepository() }
    val apiHealthRepository by lazy { ApiHealthRepository() }
}
