package app.masroufy.usecase

import app.masroufy.core.Wallet
import app.masroufy.core.bankForWallet
import app.masroufy.port.smsSenderKey

/**
 * مرسل رسايل بنك البرنامج عارفه (دليل البنوك — المؤكد بس، §74) ومحفظته. [walletId] = null ⇒ البنك ده عليه أكتر من محفظة في البلد
 * (الرسايل بتستنى وتسأل بآخر 4 أرقام زي الأول — مفيش تخمين).
 */
data class KnownSender(val sender: String, val walletId: String?)

/**
 * مرسلين رسايل البنوك اللي البرنامج عارفها لمحافظ البلد (قرار المالك 2026-10-15 مساءً — OVERRIDES §91): البنك اللي اختاره في أول تشغيل
 * (أو أي محفظة بنك بعدين) بيتسجّل **لوحده** بأسماء مرسلينه من الدليل — **ما حدش يكتب اسم مرسل لبنك البرنامج عارفه**.
 * الكاش ما لوش بنك · بنك مالوش مرسل مؤكد في الدليل ⇒ مش هنا (الشاشة بتسأل عن الاسم زي الأول). بدون تكرار بحالة الحروف.
 */
fun knownBankSenders(wallets: List<Wallet>, country: String?): List<KnownSender> {
    val byBank = wallets.filter { it.kind != "cash" }
        .mapNotNull { w -> bankForWallet(w, country)?.let { it to w } }
        .groupBy({ it.first }, { it.second })
    return byBank.flatMap { (bank, ws) -> bank.senders.map { KnownSender(it, ws.singleOrNull()?.id) } }
        .distinctBy { smsSenderKey(it.sender) }
}

/**
 * المرسلين اللي بيتبعتوا لـ`ManageSmsInbox.enable`: الموجودين + المعروفين الجداد (بدون تكرار بحالة الحروف)، لحد [max] (حد الصندوق — `MAX_SMS_SENDERS` §112-7).
 * الموجود ما بيتشالش عشان يدّي مكان لجديد.
 */
fun sendersWithKnown(current: List<String>, known: List<KnownSender>, max: Int = app.masroufy.core.MAX_SMS_SENDERS): List<String> {
    val out = current.toMutableList()
    for (k in known) {
        if (out.size >= max) break
        if (out.none { smsSenderKey(it) == smsSenderKey(k.sender) }) out += k.sender
    }
    return out
}
