package com.example.virtualuwb.domain.model

enum class DataSourceMode {
    LOCAL,
    SUPABASE,
    API_MONGODB;

    val label: String
        get() = when (this) {
            LOCAL -> "Local"
            SUPABASE -> "Supabase"
            API_MONGODB -> "MongoDB API"
        }
}
