package app.masroufy.firestore

import app.masroufy.data.Doc
import dev.gitlive.firebase.firestore.DocumentSnapshot

/**
 * المستند خام كـ`Map` محايد (`Doc` — نص · `Long` · `Double` · `Boolean` · قايمة · `Map` · `null`) عشان المحوّلات تقراه.
 *
 * **ليه مش `snapshot.data<Map<…>>()` بتاعة GitLive:** القراية دي في GitLive 2.7.0 بترجع `Map` **فاضي**
 * (`FirebaseMapSerializer.deserialize` متعلّق عليها في الكود بتاعهم). فبناخد المستند من مكتبة جوجل تحت GitLive
 * مباشرة (`snapshot.android` / `snapshot.ios`) — ملف صغير لكل جهاز، والباقي كله مشترك.
 * الكتابة **مش محتاجة كده**: `set(Map)` في GitLive شغالة في الكود المشترك.
 */
expect fun DocumentSnapshot.rawData(): Doc?

/** نفس القواعد للجهازين: الأعداد الصحيحة `Long`، والباقي زي ما هو. */
internal fun normalizeValue(value: Any?): Any? = when (value) {
    null -> null
    is String, is Boolean, is Long, is Double -> value
    is Int -> value.toLong()
    is Short -> value.toLong()
    is Byte -> value.toLong()
    is Float -> value.toDouble()
    is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to normalizeValue(v) }
    is List<*> -> value.map(::normalizeValue)
    else -> platformValue(value)
}

/** قيمة خاصة بالجهاز (رقم الآيفون `NSNumber` مثلًا). */
internal expect fun platformValue(value: Any): Any?
