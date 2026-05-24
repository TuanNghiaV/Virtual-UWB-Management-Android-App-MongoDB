package com.example.virtualuwb.data.repository

import com.example.virtualuwb.data.remote.supabase.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

class SupabaseRealtimeEventRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client
) {
    fun geofenceEventsInsertFlow(): Flow<Unit> = channelFlow {
        val channel = client.channel(
            "geofence-events-realtime-${System.currentTimeMillis()}"
        )

        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(
            schema = "public"
        ) {
            table = "geofence_events"
        }

        val collectJob = launch {
            changes.collect {
                trySend(Unit)
            }
        }

        try {
            channel.subscribe(blockUntilSubscribed = true)
        } catch (e: Exception) {
            trySend(Unit)
            close(e)
        }

        awaitClose {
            collectJob.cancel()
            launch {
                try {
                    channel.unsubscribe()
                } catch (_: Exception) {
                    // Ignore cleanup errors
                }
            }
        }
    }
}
