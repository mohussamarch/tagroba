package app.masroufy.wiring

import app.masroufy.core.Id
import app.masroufy.ui.screens.imports.SmsConfirmed
import app.masroufy.usecase.AutoRecordSms
import app.masroufy.usecase.BnplAnswer
import app.masroufy.usecase.BnplAsk
import app.masroufy.usecase.BnplCharges
import app.masroufy.usecase.BnplChargesDeps
import app.masroufy.usecase.BnplDone
import app.masroufy.usecase.ManageInstallments
import app.masroufy.usecase.ManageInstallmentsDeps
import app.masroufy.usecase.SmsRunMemo
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * r05 — زيادات شاشة «رسائل البنك» (اتفصلت عن [SmsGraph] عشان حد الـ300 سطر). **كل الشغل هنا على [io]** (مش خيط الشاشة — 4d: فتح
 * «رسائل البنك» ما يقفش؛ حارة r041c-perf بتنقل الباقي). تجميع حالات استخدام موجودة بس — مفيش حساب هنا.
 */
internal class SmsScreenExtras(
    private val c: AreaContext,
    private val auto: () -> AutoRecordSms,
    private val io: CoroutineContext,
    /** r051: التشغيلة الكاملة بعد التأكيد بتتفتكر ⇒ فتح الشاشة بعدها بيقرا الجديد بس (`SmsGraph.overview`). */
    private val memo: SmsRunMemo,
) {
    /**
     * 4a · 3b · 3c: تأكيد المالك ⇒ تسجيل وتعلّم الشكل (`AutoRecordSms.confirm`) ⇒ تشغيلة بتسجّل باقي نفس الشكل دلوقتي (مش لما الخلفية تشتغل).
     * r051: الرسالة بتتدوّر في صفحات الصندوق بالمؤشر (من غير ما نحرّك صفحة الشاشة — كانت بتلخبط تشغيلة شغالة)، والتسجيل من غير حساب «مستنية».
     */
    suspend fun confirm(ids: List<String>, categories: Map<String, Id>, isSalary: Boolean?): SmsConfirmed = withContext(io) {
        val a = auto()
        val txns = mutableListOf<Id>()
        val n = a.confirm(ids, isSalary, categories = categories, recordedIds = txns)
        val swept = if (n > 0) a.sweep(memo) else 0
        SmsConfirmed(n, swept, txns)
    }

    private val bnpl: BnplCharges by lazy {
        val r = c.repos
        val installments = ManageInstallments(
            ManageInstallmentsDeps(
                r.installmentPlans, r.installmentPayments, r.roscaEntries, r.debtTerms, r.obligations, r.transactions, r.uow, c.env.ids, c.env.clock,
                r.categories, zakatPayments = r.zakatPayments, eventLinks = r.eventLinks,
            ),
        )
        BnplCharges(BnplChargesDeps(r.transactions, r.installmentPlans, r.installmentPayments, installments, c.env.clock, c.env.today))
    }

    /** الخصم اللي يتربط لوحده بالقسط الجاي بيتربط هنا، والباقي أسئلة (الأقدم الأول). */
    suspend fun bnplAsks(): List<BnplAsk> = withContext(io) { bnpl.settle().asks }

    suspend fun bnplAnswer(answer: BnplAnswer): BnplDone = withContext(io) { bnpl.answer(answer) }
}
