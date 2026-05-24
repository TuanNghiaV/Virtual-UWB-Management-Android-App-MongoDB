package com.example.virtualuwb.domain.model

enum class DataSourceMode {
    LOCAL,
    SUPABASE;

    val label: String
        get() = when (this) {
            LOCAL -> "Local"
            SUPABASE -> "Supabase"
        }
}
