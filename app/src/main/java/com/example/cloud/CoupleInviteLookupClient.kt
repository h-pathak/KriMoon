package com.example.cloud

import androidx.compose.runtime.Immutable
import com.google.firebase.FirebaseApp
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Typed outcome of the server-side rate-limited `lookupCoupleInvite` Cloud Function.
 */
sealed class InviteLookupOutcome {
    data class Available(val invite: CoupleCloudInvite) : InviteLookupOutcome()

    data class Unavailable(
        val coupleCode: String,
        val status: String // NOT_FOUND, PENDING, LOCKED, RETIRED
    ) : InviteLookupOutcome()

    data class RateLimited(
        val message: String,
        val reason: String,
        val retryAfterSeconds: Int
    ) : InviteLookupOutcome()

    data class Failed(
        val message: String,
        val isRetryable: Boolean,
        val cause: Throwable? = null
    ) : InviteLookupOutcome()
}

/**
 * Thrown/returned in Result.failure when the trusted Cloud Function rejects an invitation
 * lookup due to per-UID rate limiting, daily quota, or consecutive-miss lockout.
 */
class InviteRateLimitedException(
    message: String,
    val reason: String,
    val retryAfterSeconds: Int
) : SecurityException(message)

/**
 * Thrown/returned in Result.failure for structured invitation lookup errors with explicit retryability.
 */
class InviteLookupException(
    message: String,
    val isRetryable: Boolean,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

/**
 * Compose UI state for the Couple Lounge invitation verification & join workflow.
 */
@Immutable
sealed class InviteJoinUiState {
    data object Idle : InviteJoinUiState()

    data class Loading(val coupleCode: String) : InviteJoinUiState()

    data class RateLimited(
        val coupleCode: String,
        val message: String,
        val reason: String,
        val retryAfterEpochMs: Long
    ) : InviteJoinUiState()

    data class Error(
        val coupleCode: String,
        val message: String,
        val canRetry: Boolean
    ) : InviteJoinUiState()
}

/**
 * Injectable contract for invitation lookup so unit tests can verify zero direct Firestore
 * reads on `/coupleInvites/{code}` without needing live network/emulator calls.
 */
interface CoupleInviteLookupClient {
    suspend fun lookup(coupleCode: String): InviteLookupOutcome
}

/**
 * Production implementation backed by the Firebase Functions v2 callable `lookupCoupleInvite`.
 *
 * App Check token propagation:
 * `FirebaseFunctions.getInstance(FirebaseApp.getInstance(), REGION)` binds to the default
 * [FirebaseApp] initialized in `PhasesApplication.onCreate()`, where `AppCheckInstaller.install()`
 * registers `PlayIntegrityAppCheckProviderFactory` (release) or `DebugAppCheckProviderFactory`
 * (debug). The Firebase Functions Android SDK automatically resolves `InternalAppCheckTokenProvider`
 * from that `FirebaseApp` and attaches both `Authorization: Bearer <FirebaseAuth ID token>` and
 * `X-Firebase-AppCheck: <App Check token>` on every callable invocation.
 */
class FirebaseCallableInviteLookupClient(
    private val functionsProvider: () -> FirebaseFunctions = {
        FirebaseFunctions.getInstance(FirebaseApp.getInstance(), REGION)
    }
) : CoupleInviteLookupClient {

    override suspend fun lookup(coupleCode: String): InviteLookupOutcome {
        return try {
            val callable = functionsProvider()
                .getHttpsCallable(FUNCTION_NAME)
                .apply {
                    setTimeout(CALLABLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                }
            val result = callable.call(mapOf("coupleCode" to coupleCode)).await()
            parseCallablePayload(coupleCode, result.getData())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mapLookupException(coupleCode, e)
        }
    }

    companion object {
        const val FUNCTION_NAME = "lookupCoupleInvite"
        const val REGION = "us-central1"
        const val CALLABLE_TIMEOUT_SECONDS = 15L

        internal fun parseCallablePayload(coupleCode: String, rawData: Any?): InviteLookupOutcome {
            val map = rawData as? Map<*, *>
                ?: return InviteLookupOutcome.Failed(
                    message = "Unexpected response from invite verification service. Please retry.",
                    isRetryable = true
                )

            val exists = map["exists"] as? Boolean ?: false
            val status = (map["status"] as? String)?.trim()?.uppercase().orEmpty()
                .ifBlank { if (exists) "LOCKED" else "NOT_FOUND" }
            val hostDisplayHint = (map["hostDisplayHint"] as? String)?.trim().orEmpty()
            val schemaVersion = (map["schemaVersion"] as? Number)?.toInt() ?: 0

            return if (exists && status == "WAITING") {
                InviteLookupOutcome.Available(
                    CoupleCloudInvite(
                        id = coupleCode,
                        coupleCode = coupleCode,
                        status = "WAITING",
                        hostDisplayHint = hostDisplayHint,
                        schemaVersion = schemaVersion.coerceAtLeast(1)
                    )
                )
            } else {
                InviteLookupOutcome.Unavailable(
                    coupleCode = coupleCode,
                    status = if (exists) status else "NOT_FOUND"
                )
            }
        }

        internal fun mapLookupException(coupleCode: String, error: Throwable): InviteLookupOutcome {
            if (error is FirebaseFunctionsException) {
                val detailsMap = error.details as? Map<*, *>
                return when (error.code) {
                    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> {
                        val retryAfter = (detailsMap?.get("retryAfterSeconds") as? Number)
                            ?.toInt()
                            ?.coerceIn(1, 86_400)
                            ?: 60
                        val reason = (detailsMap?.get("reason") as? String)
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: "rate_limited"
                        val msg = error.message
                            ?.takeIf { it.isNotBlank() }
                            ?: "Too many code lookups. Please wait before trying again."
                        InviteLookupOutcome.RateLimited(
                            message = msg,
                            reason = reason,
                            retryAfterSeconds = retryAfter
                        )
                    }
                    FirebaseFunctionsException.Code.UNAUTHENTICATED -> InviteLookupOutcome.Failed(
                        message = "Please sign in with Google to verify an invite code.",
                        isRetryable = false,
                        cause = error
                    )
                    FirebaseFunctionsException.Code.INVALID_ARGUMENT -> InviteLookupOutcome.Failed(
                        message = "Room code must be exactly 6 numeric digits.",
                        isRetryable = false,
                        cause = error
                    )
                    FirebaseFunctionsException.Code.FAILED_PRECONDITION -> InviteLookupOutcome.Failed(
                        message = "Device security verification failed. Please update KriMoon or try again.",
                        isRetryable = true,
                        cause = error
                    )
                    FirebaseFunctionsException.Code.NOT_FOUND,
                    FirebaseFunctionsException.Code.UNIMPLEMENTED -> InviteLookupOutcome.Failed(
                        message = "Invite verification service is temporarily unavailable. Please retry shortly.",
                        isRetryable = true,
                        cause = error
                    )
                    FirebaseFunctionsException.Code.UNAVAILABLE,
                    FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                    FirebaseFunctionsException.Code.INTERNAL,
                    FirebaseFunctionsException.Code.ABORTED -> InviteLookupOutcome.Failed(
                        message = "Could not reach the invite service. Check your connection and retry.",
                        isRetryable = true,
                        cause = error
                    )
                    else -> InviteLookupOutcome.Failed(
                        message = error.message?.takeIf { it.isNotBlank() }
                            ?: "Could not verify Room #$coupleCode. Please retry.",
                        isRetryable = true,
                        cause = error
                    )
                }
            }

            if (error is IOException) {
                return InviteLookupOutcome.Failed(
                    message = "Network error while verifying Room #$coupleCode. Check your connection and retry.",
                    isRetryable = true,
                    cause = error
                )
            }

            return InviteLookupOutcome.Failed(
                message = error.message?.takeIf { it.isNotBlank() }
                    ?: "Could not verify Room #$coupleCode. Please retry.",
                isRetryable = true,
                cause = error
            )
        }
    }
}
