package com.example.cloud

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

private fun maskUid(uid: String?): String {
    if (uid.isNullOrBlank()) return "unauthenticated"
    return if (uid.length <= 6) "***" else "${uid.take(4)}***${uid.takeLast(2)}"
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()

    // Never log plaintext email addresses or full UIDs to Android Logcat
    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("hasEmail", !provider.email.isNullOrBlank())
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userIdMasked", maskUid(currentUser?.uid))
        put("authenticated", currentUser != null)
        put("emailVerified", currentUser?.isEmailVerified)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorCode = (rootError as? FirebaseFirestoreException)?.code?.name ?: rootError.javaClass.simpleName
    val errorInfoJson = JSONObject().apply {
        put("errorCode", errorCode)
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}
