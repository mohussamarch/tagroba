package app.masroufy.ui.screens.imports

import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.MatchingState
import app.masroufy.core.Wallet
import app.masroufy.core.jsTrim
import app.masroufy.usecase.ImportPreview
import app.masroufy.usecase.SmsBatch

/** حالة سطر مقروء: قبل اختيار المحفظة مش معروف لسه (منع التكرار بيتحسب على المحفظة). */
enum class PastedState { PENDING, NEW, DUPLICATE, SIMILAR, CONFLICT }

data class PastedRowUi(val lineNumber: Int, val merchant: String, val date: IsoDate, val amountMinor: Halalas, val direction: Direction, val state: PastedState)

/** رسايل اتعدّت بنفس السبب (رسالة تحقق · عرض · عملية مرفوضة …) — السبب جملة القارئ نفسه. */
data class SkipGroupUi(val reason: String, val count: Int)

/** نتيجة «اقرأها» / «اقرأ الرسائل» (`ReadBankSms`) + المعاينة على المحفظة (`ImportStatement.preview`). */
data class SmsReadUi(val messages: Int, val rows: List<PastedRowUi>, val skipped: List<SkipGroupUi>, val truncated: Boolean) {
    val skippedCount: Int get() = skipped.sumOf { it.count }

    /** اللي هيتسجل = الجديد بس (المكرر ما بيتكررش، والشبيه والتعارض محتاجين قرار في «مراجعة الكشف» — هنا ما بيتسجلوش). */
    val toSave: Int get() = rows.count { it.state == PastedState.NEW }

    val duplicates: Int get() = rows.count { it.state == PastedState.DUPLICATE }

    /** أرقام السطور اللي هتتبعت للتأكيد. */
    val selected: List<Int> get() = rows.filter { it.state == PastedState.NEW }.map { it.lineNumber }
}

fun smsReadUi(batch: SmsBatch, preview: ImportPreview?): SmsReadUi {
    val states = preview?.lines?.associate { it.row.lineNumber to it.state }
    val rows = batch.rows.map { r ->
        val state = when (states?.get(r.lineNumber)) {
            null -> PastedState.PENDING
            MatchingState.NEW -> PastedState.NEW
            MatchingState.DUPLICATE -> PastedState.DUPLICATE
            MatchingState.SIMILAR -> PastedState.SIMILAR
            // «غير صالح» ما بيطلعش في سطور الرسايل (الأخطاء لوحدها) — لو طلع ما بيتسجلش زي التعارض
            MatchingState.CONFLICT, MatchingState.INVALID -> PastedState.CONFLICT
        }
        PastedRowUi(r.lineNumber, jsTrim(r.merchantName), r.date, r.amountMinor, r.direction, state)
    }
    val skipped = batch.skipped.groupBy { it.reason }.map { (reason, list) -> SkipGroupUi(reason, list.size) }
    return SmsReadUi(batch.rows.size + batch.skipped.size, rows, skipped, batch.truncated)
}

/** المحفظة المبدئية: لو في البلد محفظة غير الكاش واحدة بس هي (نفس قاعدة رسايل البنك §72)، وإلا المالك يختار. */
fun defaultWallet(wallets: List<Wallet>): Wallet? = wallets.filter { it.kind != "cash" }.singleOrNull()
