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
     * Sync the most recently saved workout set to Talos.
     * If the set belongs to a routine, all sets from that routine session
     * are grouped into a single session payload.
     */
    suspend fun syncLatestWorkout() {
        if (!config.isPaired) {
            Logger.d { "TalosSync: Not paired with VPS, skipping sync" }
            return
        }

        try {
            val sessions = workoutRepository.getRecentSessionsSync(limit = 1)
            val latest = sessions.firstOrNull()
            if (latest == null) {
                Logger.d { "TalosSync: No recent session to sync" }
                return
            }

            // If this set belongs to a routine, fetch all sets from the same routine session
            val toSync = if (latest.routineSessionId != null) {
                workoutRepository.getRecentSessionsSync(limit = 500)
                    .filter { it.routineSessionId == latest.routineSessionId }
            } else {
                listOf(latest)
            }

            val payload = TalosWorkoutSyncRequest(
                sessions = groupAndConvert(toSync)
            )

            val result = apiClient.syncWorkout(payload)
            if (result.isSuccess) {
                Logger.i { "TalosSync: Successfully synced ${toSync.size} sets as ${payload.sessions.size} session(s)" }
            } else {
                Logger.w { "TalosSync: Failed to sync: ${result.exceptionOrNull()?.message}" }
            }
        } catch (e: Exception) {
            Logger.w { "TalosSync: Error during sync: ${e.message}" }
        }
    }

    /**
     * Sync ALL workout sessions to Talos VPS.
     * Used for manual "Sync Now" — pushes all historical data.
     * Sets are grouped by routine session before sending.
     * VPS uses upsert so duplicates are safe.
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

            val grouped = groupAndConvert(allSessions)
            Logger.i { "TalosSync: Grouped ${allSessions.size} sets into ${grouped.size} sessions" }

            // Send in batches of 50
            var totalSynced = 0
            grouped.chunked(50).forEach { batch ->
                val payload = TalosWorkoutSyncRequest(sessions = batch)
                val result = apiClient.syncWorkout(payload)
                if (result.isSuccess) {
                    totalSynced += batch.size
                    Logger.i { "TalosSync: Synced batch of ${batch.size} sessions" }
                } else {
                    Logger.w { "TalosSync: Batch sync failed: ${result.exceptionOrNull()?.message}" }
                }
            }

            Logger.i { "TalosSync: Full sync complete — $totalSynced/${grouped.size} sessions" }
            Result.success(totalSynced)
        } catch (e: Exception) {
            Logger.w { "TalosSync: Full sync error: ${e.message}" }
            Result.failure(e)
        }
    }
}

/**
 * Group sets by routineSessionId into logical sessions.
 * Sets without a routineSessionId are sent individually (ad-hoc lifts).
 */
private fun groupAndConvert(sessions: List<WorkoutSession>): List<TalosWorkoutSessionPayload> {
    val (routineSets, singleSets) = sessions.partition { it.routineSessionId != null }

    val grouped = routineSets.groupBy { it.routineSessionId!! }
        .map { (routineSessionId, sets) ->
            val sorted = sets.sortedBy { it.timestamp }
            val first = sorted.first()
            val last = sorted.last()
            val totalDurationMs = (last.timestamp + last.duration) - first.timestamp

            TalosWorkoutSessionPayload(
                externalId = routineSessionId,
                exerciseName = first.routineName ?: "Workout",
                startedAt = formatTimestamp(first.timestamp),
                endedAt = formatTimestamp(last.timestamp + last.duration),
                durationSeconds = if (totalDurationMs > 0) (totalDurationMs / 1000).toInt() else null,
                totalReps = sorted.sumOf { it.totalReps }.takeIf { it > 0 },
                totalVolumeKg = sorted.mapNotNull { it.totalVolumeKg?.toDouble() }.sum().takeIf { it > 0 },
                calories = sorted.mapNotNull { it.estimatedCalories?.toDouble() }.sum().takeIf { it > 0 },
                sets = sorted.mapIndexed { index, set -> set.toSetPayload(index + 1) },
            )
        }

    val singles = singleSets.map { it.toTalosPayload() }

    return grouped + singles
}

/**
 * Convert a single set to a [TalosWorkoutSetPayload] for inclusion in a grouped session.
 */
private fun WorkoutSession.toSetPayload(setNumber: Int): TalosWorkoutSetPayload {
    return TalosWorkoutSetPayload(
        setNumber = setNumber,
        exerciseName = exerciseName,
        setType = if (warmupReps > 0 && workingReps == 0) "warmup" else "working",
        actualReps = if (totalReps > 0) totalReps else null,
        actualWeightKg = weightPerCableKg.toDouble(),
        rpe = rpe?.toDouble(),
        volumeKg = totalVolumeKg?.toDouble(),
    )
}

/**
 * Convert a Phoenix [WorkoutSession] to the Talos API payload format.
 * Used for ad-hoc single sets that don't belong to a routine.
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
