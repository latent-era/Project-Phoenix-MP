package com.devil.phoenixproject.data.sync.talos

import co.touchlab.kermit.Logger
import com.devil.phoenixproject.data.repository.WorkoutRepository
import com.devil.phoenixproject.domain.model.WorkoutSession
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Service that maps Phoenix workout data to Talos payload format
 * and syncs via [TalosApiClient].
 *
 * Called after each workout completion. Failures are logged but never
 * block the user — Talos sync is best-effort.
 */
class TalosSyncService(
    private val config: TalosConfig,
    private val apiClient: TalosApiClient,
    private val workoutRepository: WorkoutRepository,
) {
    /**
     * Sync the most recently saved workout session to Talos.
     * Called from [SyncTriggerManager.onWorkoutCompleted].
     */
    suspend fun syncLatestWorkout() {
        if (!config.isPaired) {
            Logger.d { "TalosSync: Not paired with VPS, skipping sync" }
            return
        }

        try {
            val sessions = workoutRepository.getRecentSessionsSync(limit = 1)
            val session = sessions.firstOrNull()
            if (session == null) {
                Logger.d { "TalosSync: No recent session to sync" }
                return
            }

            val payload = TalosWorkoutSyncRequest(
                sessions = listOf(session.toTalosPayload())
            )

            val result = apiClient.syncWorkout(payload)
            if (result.isSuccess) {
                Logger.i { "TalosSync: Successfully synced session ${session.id}" }
            } else {
                Logger.w { "TalosSync: Failed to sync session ${session.id}: ${result.exceptionOrNull()?.message}" }
            }
        } catch (e: Exception) {
            Logger.w { "TalosSync: Error during sync: ${e.message}" }
        }
    }

    /**
     * Sync ALL workout sessions to Talos VPS.
     * Used for manual "Sync Now" — pushes all historical data.
     * VPS uses upsert so duplicates are safe.
     * Returns the number of sessions synced.
     */
    suspend fun syncAllWorkouts(): Result<Int> {
        if (!config.isPaired) {
            return Result.failure(Exception("Not paired with VPS"))
        }

        return try {
            val allSessions = workoutRepository.getRecentSessionsSync(limit = 10000)
            if (allSessions.isEmpty()) {
                Logger.d { "TalosSync: No sessions to sync" }
                return Result.success(0)
            }

            // Send in batches of 50
            var totalSynced = 0
            allSessions.chunked(50).forEach { batch ->
                val payload = TalosWorkoutSyncRequest(
                    sessions = batch.map { it.toTalosPayload() }
                )
                val result = apiClient.syncWorkout(payload)
                if (result.isSuccess) {
                    totalSynced += batch.size
                    Logger.i { "TalosSync: Synced batch of ${batch.size} sessions" }
                } else {
                    Logger.w { "TalosSync: Batch sync failed: ${result.exceptionOrNull()?.message}" }
                }
            }

            Logger.i { "TalosSync: Full sync complete — $totalSynced/${allSessions.size} sessions" }
            Result.success(totalSynced)
        } catch (e: Exception) {
            Logger.w { "TalosSync: Full sync error: ${e.message}" }
            Result.failure(e)
        }
    }
}

/**
 * Convert a Phoenix [WorkoutSession] to the Talos API payload format.
 */
private fun WorkoutSession.toTalosPayload(): TalosWorkoutSessionPayload {
    val startedAt = formatTimestamp(timestamp)
    val endedAt = if (duration > 0) formatTimestamp(timestamp + duration) else null

    // Compute peak force as max of concentric A+B
    val peakForce = listOfNotNull(
        peakForceConcentricA,
        peakForceConcentricB
    ).maxOrNull()?.toDouble()

    // Compute avg force as mean of concentric A+B averages
    val avgForce = listOfNotNull(
        avgForceConcentricA,
        avgForceConcentricB
    ).let { forces ->
        if (forces.isNotEmpty()) forces.map { it.toDouble() }.average() else null
    }

    // Build safety events list
    val safetyEvents = buildList {
        if (safetyFlags > 0) add(mapOf("type" to "safety_flags", "message" to "$safetyFlags safety flags"))
        if (deloadWarningCount > 0) add(mapOf("type" to "deload_warning", "message" to "$deloadWarningCount deload warnings"))
        if (romViolationCount > 0) add(mapOf("type" to "rom_violation", "message" to "$romViolationCount ROM violations"))
        if (spotterActivations > 0) add(mapOf("type" to "spotter_activation", "message" to "$spotterActivations spotter activations"))
    }

    return TalosWorkoutSessionPayload(
        externalId = id,
        exerciseName = exerciseName ?: "Unknown Exercise",
        workoutMode = mode,
        startedAt = startedAt,
        endedAt = endedAt,
        durationSeconds = if (duration > 0) (duration / 1000).toInt() else null,
        totalReps = if (totalReps > 0) totalReps else null,
        warmupReps = if (warmupReps > 0) warmupReps else null,
        workingReps = if (workingReps > 0) workingReps else null,
        weightPerCableKg = weightPerCableKg.toDouble(),
        totalVolumeKg = totalVolumeKg?.toDouble(),
        calories = estimatedCalories?.toDouble(),
        peakForceN = peakForce,
        avgForceN = avgForce,
        rpe = rpe?.toDouble(),
        safetyEvents = safetyEvents,
    )
}

/**
 * Format epoch millis to ISO 8601 string.
 */
private fun formatTimestamp(epochMillis: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localDateTime.date}T${localDateTime.time}"
}
