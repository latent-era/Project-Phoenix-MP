package com.devil.phoenixproject.data.sync.talos

import co.touchlab.kermit.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * HTTP client for syncing workout data to Talos VPS.
 * Uses dynamic bearer token auth from [TalosConfig].
 */
class TalosApiClient(private val config: TalosConfig) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false  // Don't serialize null fields — Zod .optional() expects missing, not null
        explicitNulls = false   // Omit null values from JSON output
    }

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }

    /**
     * POST workout session(s) to Talos VPS.
     * Returns success/failure — does not throw.
     * Fails immediately if not paired (no device token).
     */
    suspend fun syncWorkout(request: TalosWorkoutSyncRequest): Result<TalosSyncResponse> {
        return try {
            val token = config.deviceToken
                ?: return Result.failure(Exception("Not paired with VPS"))

            val response = httpClient.post("${config.vpsUrl}/api/workouts/sync") {
                bearerAuth(token)
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val body = response.body<TalosSyncResponse>()
                Logger.i { "Talos sync success: ${body.synced?.sessions ?: 0} sessions" }
                Result.success(body)
            } else {
                val statusCode = response.status.value
                val errorBody = try { response.body<String>() } catch (_: Exception) { "no body" }
                Logger.w { "Talos sync failed HTTP $statusCode: $errorBody" }
                Result.failure(Exception("HTTP $statusCode: $errorBody"))
            }
        } catch (e: Exception) {
            Logger.w { "Talos sync error: ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Validate a 6-character pairing code against the VPS.
     * On success, returns the device token string.
     */
    suspend fun validatePairingCode(code: String): Result<String> {
        return try {
            val response = httpClient.post("${config.vpsUrl}/api/pairing/validate") {
                setBody(mapOf("code" to code))
            }
            if (response.status.isSuccess()) {
                val body = response.body<PairingResponse>()
                Result.success(body.deviceToken)
            } else {
                Result.failure(Exception("Invalid code: HTTP ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
