package app.masroufy.wiring

import app.masroufy.core.Id
import app.masroufy.port.SmsInboxPort
import app.masroufy.ui.screens.imports.SmsConfirmed
import app.masroufy.usecase.AutoRecordSms


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
    private val inbox: SmsInboxPort,
    /** الصفحة اللي الشاشة عارضاها (4b) — الرسالة المؤكَّدة من صفحة أقدم لازم تتدوّر هناك. */
    private val shownSkip: () -> Int,
) {
    /** 4a · 3b · 3c: تأكيد المالك ⇒ تسجيل وتعلّم الشكل (`AutoRecordSms.confirm`) ⇒ تشغيلة بتسجّل باقي نفس الشكل دلوقتي (مش لما الخلفية تشتغل). */
    suspend fun confirm(ids: List<String>, categories: Map<String, Id>, isSalary: Boolean?): SmsConfirmed = withContext(io) {
        val a = auto()
        val txns = mutableListOf<Id>()
        val skip = shownSkip()
        if (skip > 0) inbox.page(skip)
        val n = try {
            a.confirm(ids, isSalary, categories = categories, recordedIds = txns)
        } finally {
            if (skip > 0) inbox.page(0)
        }
        val swept = if (n > 0) a.run().recorded else 0
        SmsConfirmed(n, swept, txns)
    }
}
