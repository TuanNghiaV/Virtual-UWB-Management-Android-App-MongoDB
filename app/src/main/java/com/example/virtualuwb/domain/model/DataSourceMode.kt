package com.example.virtualuwb.domain.model

enum class DataSourceMode {
    LOCAL,
    API_MONGODB;

    val label: String
        get() = when (this) {
            LOCAL -> "Local"
            API_MONGODB -> "MongoDB API"
        }
}
