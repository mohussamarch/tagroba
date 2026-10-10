package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.SmsRow
import app.masroufy.core.SourceRecord
import app.masroufy.core.Transaction
import app.masroufy.port.IdGenerator
import kotlin.coroutines.cancellation.CancellationException

/**
 * عقد C0 — **أثر وقت التسجيل** (قرارات §75 · §77): كل قرار من قرارات المالك اللي بتغيّر العملية وهي بتتسجل (الراتب · حسابي التاني ·
 * السحب للكاش · الرسوم · الاسترداد · اللي رجع · الاشتراك …) = [RecordEffect] لوحده، و`ImportStatement` بيشغّلهم بالترتيب جوه نفس
 * وحدة العمل. **الكشف والرسايل نفس الخط** — الأثر اللي للرسايل بس بيبص على [RecordedLine.sms].
 */

/** سطر اتسجل: سطر المعاينة · صف الرسالة لو جه من رسالة بنك (null = كشف) · العملية (الأثر ممكن يبدّلها). */
class RecordedLine(val line: ImportPreviewLine, val sms: SmsRow?, var transaction: Transaction)

/**
 * اللي الأثر بيشوفه: الطلب · الدفعة · الوقت · [byOwner] (المالك هو اللي سجّل — مش التسجيل التلقائي) · التصنيفات اللي اختارها ·
 * السطور اللي اتسجلت · [extra] = عمليات زيادة الأثر بيضيفها (زي «رسوم بنكية» §77-B) **ومعاها سجل مصدرها** — بتتحفظ مع الدفعة.
 */
class RecordContext(
    val request: ImportRequest,
    val batchId: Id,
    val nowIso: String,
    val byOwner: Boolean,
    val chosenCategories: Map<Int, Id>,
    val lines: MutableList<RecordedLine>,
    val extra: MutableList<Pair<Transaction, SourceRecord>>,
    val ids: IdGenerator,
)

/**
 * [prepare] جوه وحدة العمل قبل الحفظ (يبدّل عمليات أو يضيف [RecordContext.extra]) — فشله بيرجّع الدفعة كلها.
 * [afterCommit] بعد ما الدفعة اتقفلت — كل واحد لوحده، وفشله ما بيوقفش التسجيل (الشرائح اللي بتستعمله ليها تصليح بعدين).
 */
interface RecordEffect {
    suspend fun prepare(ctx: RecordContext) {}

    suspend fun afterCommit(ctx: RecordContext) {}
}

/** عقد C0: التراجع عن دفعة ([RevertImportBatch]) بينادي ده **قبل** ما سجلات المصدر والعمليات تتمسح — عشان الأثر يرجّع اللي غيّره. */
fun interface BatchUndo {
    suspend fun undo(batchId: Id, records: List<SourceRecord>, deleting: List<Id>)
}

/** الخطأ (لو حصل) من غير ما يطلع — الإلغاء بس بيطلع. */
internal suspend fun captureFailure(block: suspend () -> Unit): Exception? = try {
    block()
    null
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    e
}

/** الدفعة اتقفلت خلاص ⇒ فشل أثر بعد الحفظ ما بيرجّعش حاجة (الشرائح اللي بتستعمله ليها تصليح ولحاق بعدين). */
internal suspend fun afterCommitIsolated(effect: RecordEffect, ctx: RecordContext) {
    try {
        effect.afterCommit(ctx)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // مقصود: الأثر بعد الحفظ اختياري
    }
}
