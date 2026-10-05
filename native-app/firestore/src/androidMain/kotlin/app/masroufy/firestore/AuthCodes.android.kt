package app.masroufy.firestore

/** على أندرويد: `FirebaseAuthException.errorCode` (`ERROR_INVALID_EMAIL` …). */
internal actual fun platformAuthCode(e: Throwable): String? =
    (e as? com.google.firebase.auth.FirebaseAuthException)?.errorCode?.let(::webAuthCode)
