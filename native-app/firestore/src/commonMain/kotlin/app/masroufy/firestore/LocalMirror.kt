package app.masroufy.firestore

import app.masroufy.data.Doc
import dev.gitlive.firebase.firestore.FieldValue
import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/**
 * نسخة **في الذاكرة** من مستندات الحساب، بتتملى من مستمعي [FirestoreSync] — والشاشات بتقرا منها.
 *
 * ⚠️ ليه مش من النسخة المحلية بتاعة المكتبة على طول (اتقاس على كشف المالك 2026-10-01، محاكي أندرويد):
 * استعلام فترة واحدة من النسخة المحلية ≈ ربع ثانية، لأن المكتبة بتعدّي على **كل** مستندات المجموعة وتفكها من الأول
 * (الفهارس المحلية ما فرقتش)، والرئيسية بتعمل ~15 استعلام ⇒ **5 ثواني للفترة**. المستمع بيجيب المستندات دي أصلًا،
 * فبنحتفظ بيها هنا. **مش مخزن تاني:** الأصل لسه نسخة المكتبة (قرار المالك §54) — دي مجرد عرض ليها في الذاكرة.
 *
 * القراية منها **للمجموعات اللي اتزامنت بس** ([isSynced]) — نسخة ناقصة = مجموع غلط من غير رسالة (القاعدة 10) —
 * أو نسخة الجهاز لو الجهاز ده خلّص تنزيل كامل قبل كده ([deviceCopyComplete] — نفس قاعدة الفتح من غير نت §54).
 * قراية قبل ما المستمع يسلّم ⇒ **بتستناه** ([awaitDocs]) بدل استعلام تاني للسيرفر بنفس البيانات (HANDOVER §7 «السرعة»).
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

    /** المجموعات اللي المستمع سلّمها أول نسخة (من الجهاز أو السيرفر) — نسخة الجهاز = نفس اللي `get()` بيرجّعه من غير نت. */
    private val delivered = MutableStateFlow<Set<String>>(emptySet())

    /** المجموعات اللي مستمعها وقع — القراية بترجع لفايربيز (ولا نستنى مستمع مش هييجي). */
    private val failed = MutableStateFlow<Set<String>>(emptySet())

    /**
     * الجهاز ده خلّص تنزيل كامل للحساب قبل كده (`FirstSyncMarks` — §54)؟ أيوه ⇒ لحد ما السيرفر يرد، القراية من **نسخة الجهاز**
     * اللي المستمع سلّمها (نفس اللي التطبيق بيفتح بيه من غير نت) بدل استعلام للسيرفر لكل قراية. لأ ⇒ نستنى السيرفر (القاعدة 10).
     */
    @Volatile var deviceCopyComplete: Boolean = false

    /** المجموعات اللي وصلها نسخة من السيرفر — للشاشة تعرض شريط تقدم أول مرة. */
    val syncedGroups: StateFlow<Set<String>> = synced.asStateFlow()

    fun isSynced(group: String): Boolean = group in synced.value

    internal fun markSynced(group: String) = synced.update { it + group }

    internal fun markDelivered(group: String) = delivered.update { it + group }

    /** المستمع وقف ⇒ الذاكرة للمجموعة دي ممكن تبقى قديمة، فالقراية ترجع لفايربيز. */
    internal fun markStale(group: String) {
        failed.update { it + group }
        synced.update { it - group }
    }

    /**
     * مستندات المجموعة (معرّف ⇐ مستند) لو ينفع تتقري دلوقتي: اتزامنت مع السيرفر، أو نسخة الجهاز وصلت والجهاز عنده تنزيل كامل قبل كده.
     * وإلا `null` ⇒ [awaitDocs] أو فايربيز.
     */
    internal fun docsOf(group: String): Map<String, Entry>? {
        val flow = docs[group] ?: return null
        if (group in failed.value) return null
        return if (isSynced(group) || (deviceCopyComplete && group in delivered.value)) flow.value else null
    }

    /**
     * زي [docsOf] بس **بيستنى المستمع** (لحد [timeoutMs]) بدل ما يرجع `null` على طول: المستمع بينزّل المجموعة كلها أصلًا،
     * فاستعلام للسيرفر جنبه = نفس البيانات مرتين (وكل شاشة كانت بتنزّلها لوحدها — 4000 عملية ≈ 1.5 ميجا للقراية الواحدة).
     * كذا قراية مع بعض ⇒ كلهم بيستنوا **نفس** التنزيل. مجموعة مش هنا أو مستمعها وقع ⇒ `null` على طول.
     */
    internal suspend fun awaitDocs(group: String, timeoutMs: Long): Map<String, Entry>? {
        docsOf(group)?.let { return it }
        if (group !in docs || group in failed.value) return null
        withTimeoutOrNull(timeoutMs) {
            combine(synced, delivered, failed) { _, _, f -> group in f || docsOf(group) != null }.first { it }
        }
        return docsOf(group)
    }

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

    /** نفس `set()` من غير merge: المستند كله بيتبدّل (ملف المستخدم مثلًا). */
    internal fun applyReplace(group: String, id: String, fields: Map<String, Any?>) {
        docs[group]?.update { current -> current + (id to Entry(mergeFields(emptyMap(), fields))) }
    }

    internal fun applyDelete(group: String, ids: Collection<String>) {
        docs[group]?.update { current -> current - ids.toSet() }
    }

    internal fun documentCount(): Int = docs.values.sumOf { it.value.size }

    internal fun clear() {
        synced.value = emptySet()
        delivered.value = emptySet()
        failed.value = emptySet()
        deviceCopyComplete = false
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
