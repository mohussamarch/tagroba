package app.masroufy.firestore

import app.masroufy.data.Doc
import dev.gitlive.firebase.firestore.FieldValue
import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * نسخة **في الذاكرة** من مستندات الحساب، بتتملى من مستمعي [FirestoreSync] — والشاشات بتقرا منها.
 *
 * ⚠️ ليه مش من النسخة المحلية بتاعة المكتبة على طول (اتقاس على كشف المالك 2026-10-01، محاكي أندرويد):
 * استعلام فترة واحدة من النسخة المحلية ≈ ربع ثانية، لأن المكتبة بتعدّي على **كل** مستندات المجموعة وتفكها من الأول
 * (الفهارس المحلية ما فرقتش)، والرئيسية بتعمل ~15 استعلام ⇒ **5 ثواني للفترة**. المستمع بيجيب المستندات دي أصلًا،
 * فبنحتفظ بيها هنا. **مش مخزن تاني:** الأصل لسه نسخة المكتبة (قرار المالك §54) — دي مجرد عرض ليها في الذاكرة.
 *
 * القراية منها **للمجموعات اللي اتزامنت بس** ([isSynced]) — نسخة ناقصة = مجموع غلط من غير رسالة (القاعدة 10).
 * الكتابة من التطبيق نفسه بتتطبّق هنا **لحظتها** ([applySet] / [applyUpdate] / [applyDelete]) عشان قراية بعدها
 * تشوفها، والمستمع بيجيب الحالة النهائية من المكتبة بعدها (ولو السيرفر رفض الكتابة، المكتبة بترجّعها والمستمع كمان).
 */
class LocalMirror(groups: Collection<String>) {
    /**
     * مستند + الكيان بعد التحويل (أول ما يتطلب، ومرة واحدة لكل نسخة من المستند): التحويل كان أغلى حاجة في القراية
     * من الذاكرة (الرئيسية بتقرا نفس العمليات لكذا فترة). مستند اتغير = `Entry` جديد ⇒ تحويل جديد.
     */
    internal class Entry(val doc: Doc) {
        @Volatile private var decoded: Any? = Unset

        @Suppress("UNCHECKED_CAST")
        fun <T> entity(decode: (Doc) -> T): T {
            val cached = decoded
            if (cached !== Unset) return cached as T
            return decode(doc).also { decoded = it }
        }

        private object Unset
    }

    private val docs: Map<String, MutableStateFlow<Map<String, Entry>>> = groups.associateWith { MutableStateFlow(emptyMap()) }
    private val synced = MutableStateFlow<Set<String>>(emptySet())

    /** المجموعات اللي وصلها نسخة من السيرفر — للشاشة تعرض شريط تقدم أول مرة. */
    val syncedGroups: StateFlow<Set<String>> = synced.asStateFlow()

    fun isSynced(group: String): Boolean = group in synced.value

    internal fun markSynced(group: String) = synced.update { it + group }

    /** مستندات المجموعة (معرّف ⇐ مستند) لو اتزامنت، وإلا `null` ⇒ القراية تروح لفايربيز. */
    internal fun docsOf(group: String): Map<String, Entry>? = if (isSynced(group)) docs[group]?.value else null

    /** تغييرات من المستمع: مستند جديد أو متعدّل بقيمته، و`null` = اتمسح. */
    internal fun applyChanges(group: String, changes: List<Pair<String, Doc?>>) {
        if (changes.isEmpty()) return
        val flow = docs[group] ?: return
        flow.update { current ->
            val next = current.toMutableMap()
            for ((id, doc) in changes) if (doc == null) next.remove(id) else next[id] = Entry(doc)
            next
        }
    }

    /** نفس `set(…, merge = true)`: الحقول المكتوبة بتتحط (والخرايط جوه بعض بتندمج زي فايربيز)، و`FieldValue.delete` بيمسح. */
    internal fun applySet(group: String, id: String, fields: Map<String, Any?>) {
        docs[group]?.update { current -> current + (id to Entry(mergeFields(current[id]?.doc ?: emptyMap(), fields))) }
    }

    /** نفس `update()`: على مستند موجود بس (فايربيز بيرفض التعديل على مستند مش موجود). */
    internal fun applyUpdate(group: String, id: String, fields: Map<String, Any?>) {
        docs[group]?.update { current -> current[id]?.let { current + (id to Entry(mergeFields(it.doc, fields))) } ?: current }
    }

    internal fun applyDelete(group: String, ids: Collection<String>) {
        docs[group]?.update { current -> current - ids.toSet() }
    }

    internal fun clear() {
        synced.value = emptySet()
        docs.values.forEach { it.value = emptyMap() }
    }

    private fun mergeFields(base: Doc, fields: Map<String, Any?>): Doc {
        val out = base.toMutableMap()
        for ((key, value) in fields) {
            val old = out[key]
            when {
                // الحاجة الوحيدة من `FieldValue` اللي بنكتبها هي المسح (§31.4)
                value is FieldValue -> out.remove(key)
                value is Map<*, *> && old is Map<*, *> ->
                    @Suppress("UNCHECKED_CAST")
                    out[key] = mergeFields(old as Doc, value as Map<String, Any?>)
                else -> out[key] = normalizeValue(value)
            }
        }
        return out
    }
}
