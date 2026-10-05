package app.masroufy.firestore

import app.masroufy.data.Doc
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.android

/** على الكمبيوتر (الاختبارات): GitLive فوق firebase-java-sdk — نفس واجهة أندرويد (`getData()`). */
actual fun DocumentSnapshot.rawData(): Doc? = android.data?.let { data -> data.entries.associate { (k, v) -> k to normalizeValue(v) } }

internal actual fun platformValue(value: Any): Any? = when (value) {
    // وقت فايربيز ⇒ نص ISO زي `toDate().toISOString()` (قاعدة التجار المشتركة)
    is com.google.firebase.Timestamp -> isoOfEpoch(value.seconds, value.nanoseconds)
    is Number -> value.toLong()
    else -> value.toString()
}
