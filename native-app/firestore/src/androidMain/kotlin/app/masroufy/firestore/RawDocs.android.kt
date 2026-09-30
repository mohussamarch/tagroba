package app.masroufy.firestore

import app.masroufy.data.Doc
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.android

/** على أندرويد: مكتبة جوجل تحت GitLive (`getData()`) — الأعداد بتيجي `Long` و`Double`. */
actual fun DocumentSnapshot.rawData(): Doc? = android.data?.let { data -> data.entries.associate { (k, v) -> k to normalizeValue(v) } }

internal actual fun platformValue(value: Any): Any? = when (value) {
    is Number -> value.toLong()
    else -> value.toString()
}
