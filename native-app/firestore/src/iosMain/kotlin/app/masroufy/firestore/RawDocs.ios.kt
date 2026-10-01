package app.masroufy.firestore

import app.masroufy.data.Doc
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.ios
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.Foundation.NSNull
import platform.Foundation.NSNumber

/**
 * على الآيفون: `FIRDocumentSnapshot.data()`. ⚠️ **لسه ما اتجربش على آيفون شغال** (محتاج ماك) — اتبنى بس.
 * الخطر المعروف: الرقم بييجي `NSNumber` والـ`true/false` كمان `NSNumber` ⇒ النوع بيتعرف من `objCType`.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun DocumentSnapshot.rawData(): Doc? = ios.data()?.entries?.associate { (k, v) -> k.toString() to normalizeValue(v) }

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformValue(value: Any): Any? = when (value) {
    is NSNull -> null
    // ⚠️ `FIRTimestamp` في `FirebaseCore` مش `FirebaseFirestoreInternal` (اتشاف من ملفات GitLive نفسها بعد ما بناء الآيفون فشل)
    is cocoapods.FirebaseCore.FIRTimestamp -> isoOfEpoch(value.seconds, value.nanoseconds)
    is NSNumber -> when (value.objCType?.toKString()) {
        "c", "B" -> value.boolValue
        "f", "d" -> value.doubleValue
        else -> value.longLongValue
    }
    else -> value.toString()
}
