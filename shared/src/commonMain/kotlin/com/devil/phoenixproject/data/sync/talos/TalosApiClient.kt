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
 * Uses static bearer token auth (single-user fork).
 */
class TalosApiClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
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
     */
    suspend fun syncWorkout(request: TalosWorkoutSyncRequest): Result<TalosSyncResponse> {
        return try {
            val response = httpClient.post("${TalosConfig.VPS_URL}/api/workouts/sync") {
                bearerAuth(TalosConfig.API_TOKEN)
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val body = response.body<TalosSyncResponse>()
                Logger.i { "Talos sync success: ${body.synced?.sessions ?: 0} sessions" }
                Result.success(body)
            } else {
                val statusCode = response.status.value
                Logger.w { "Talos sync failed with HTTP $statusCode" }
                Result.failure(Exception("Talos sync failed: HTTP $statusCode"))
            }
        } catch (e: Exception) {
            Logger.w { "Talos sync error: ${e.message}" }
            Result.failure(e)
        }
    }
}
