package com.devil.phoenixproject.data.sync.talos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TalosWorkoutSyncRequest(
    val sessions: List<TalosWorkoutSessionPayload>,
    val source: String = "vitruvian",
)

@Serializable
data class TalosWorkoutSessionPayload(
    @SerialName("external_id") val externalId: String,
    @SerialName("exercise_name") val exerciseName: String,
    @SerialName("workout_mode") val workoutMode: String? = null,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("total_reps") val totalReps: Int? = null,
    @SerialName("warmup_reps") val warmupReps: Int? = null,
    @SerialName("working_reps") val workingReps: Int? = null,
    @SerialName("weight_per_cable_kg") val weightPerCableKg: Double? = null,
    @SerialName("total_volume_kg") val totalVolumeKg: Double? = null,
    val calories: Double? = null,
    @SerialName("peak_force_n") val peakForceN: Double? = null,
    @SerialName("avg_force_n") val avgForceN: Double? = null,
    @SerialName("peak_velocity") val peakVelocity: Double? = null,
    @SerialName("avg_velocity") val avgVelocity: Double? = null,
    @SerialName("peak_power_w") val peakPowerW: Double? = null,
    @SerialName("avg_power_w") val avgPowerW: Double? = null,
    val rpe: Double? = null,
    @SerialName("safety_events") val safetyEvents: List<Map<String, String>> = emptyList(),
    val sets: List<TalosWorkoutSetPayload> = emptyList(),
    @SerialName("personal_records") val personalRecords: List<TalosPersonalRecordPayload> = emptyList(),
)

@Serializable
data class TalosWorkoutSetPayload(
    @SerialName("set_number") val setNumber: Int,
    @SerialName("set_type") val setType: String = "working",
    @SerialName("actual_reps") val actualReps: Int? = null,
    @SerialName("actual_weight_kg") val actualWeightKg: Double? = null,
    val rpe: Double? = null,
    @SerialName("volume_kg") val volumeKg: Double? = null,
    @SerialName("estimated_1rm_kg") val estimated1rmKg: Double? = null,
)

@Serializable
data class TalosPersonalRecordPayload(
    @SerialName("exercise_name") val exerciseName: String,
    @SerialName("record_type") val recordType: String,
    val value: Double,
    val unit: String,
    @SerialName("achieved_at") val achievedAt: String,
    @SerialName("previous_value") val previousValue: Double? = null,
)

@Serializable
data class TalosSyncResponse(
    val success: Boolean,
    val synced: TalosSyncCounts? = null,
)

@Serializable
data class TalosSyncCounts(
    val sessions: Int = 0,
    val sets: Int = 0,
    @SerialName("personal_records") val personalRecords: Int = 0,
)
