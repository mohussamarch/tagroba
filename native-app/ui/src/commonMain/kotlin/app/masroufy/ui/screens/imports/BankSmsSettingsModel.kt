package app.masroufy.ui.screens.imports

import app.masroufy.core.Id
import app.masroufy.core.UiKey
import app.masroufy.core.Wallet
import app.masroufy.core.Bank
import app.masroufy.core.bankForSender
import app.masroufy.port.smsSenderKey

/**
 * بنك (مرسل) في «إعداد القراءة»: محفظته (null = مستني تختار) وعدد رسايله المستنية المحفظة. [bank] = البنك المقترح من اسم المرسل
 * (دليل البنوك — §74: المؤكد بس)؛ null ⇒ الاسم الخام زي ما جه في الرسالة.
 */
data class SenderUi(val sender: String, val walletId: Id?, val walletName: String?, val waiting: Int, val bank: Bank? = null)

/** حالة «إعداد القراءة» (`BankSmsSettings`). [available] = الجهاز بيقرا رسايل. */
data class SmsSettingsUi(val available: Boolean, val enabled: Boolean, val permission: Boolean, val senders: List<SenderUi>) {
    /** القراءة شغالة فعلًا (متفعّلة والإذن موجود). */
    val reading: Boolean get() = enabled && permission

    /** الإذن اتسحب وهي متفعّلة ⇒ تنبيه «سُحب إذن الرسائل». */
    val permissionLost: Boolean get() = enabled && !permission

    /** أقصى 10 مرسلين (`ManageSmsInbox.enable`). */
    val full: Boolean get() = senders.size >= MAX_SENDERS

    companion object {
        const val MAX_SENDERS = 10
    }
}

/** من صورة الصندوق (من غير تسجيل) ومحافظ البلد. [overview] = null ⇒ الجهاز ما بيقراش رسايل. [country] = بلد المساحة (اقتراح البنك من المرسل). */
fun smsSettingsUi(overview: SmsOverview?, wallets: List<Wallet>, country: String? = null): SmsSettingsUi {
    if (overview == null) return SmsSettingsUi(available = false, enabled = false, permission = false, senders = emptyList())
    val senders = overview.inbox.senders.map { sender ->
        val walletId = overview.senderWallets[sender]
        val waiting = overview.unmapped.firstOrNull { smsSenderKey(it.sender) == smsSenderKey(sender) }?.messages ?: 0
        SenderUi(sender, walletId, wallets.firstOrNull { it.id == walletId }?.name, waiting, bankForSender(sender, country))
    }
    return SmsSettingsUi(available = true, enabled = overview.inbox.enabled, permission = overview.inbox.permission, senders = senders)
}

/**
 * سطر «إذن الرسائل» في «إعداد القراءة»: حالة النظام الحقيقية ([granted] = `Permissions.hasSms` وقت العرض) — «ممنوح» بس لو الإذن موجود فعلًا.
 * قبل كده كان بيبص على «سُحب الإذن» بس ⇒ بيقول «ممنوح» والإذن مرفوض لو القراءة متوقفة (اتشاف في رحلة أول تشغيل 2026-10-15).
 */
fun permissionLine(granted: Boolean): UiKey = if (granted) UiKey.SMS_SETTINGS_PERM_LINE_ON else UiKey.SMS_SETTINGS_PERM_LINE_OFF

/** المرسلين بعد إضافة واحد (من غير تكرار بحالة الحروف) — اللي بيتبعت لـ`enable`. */
fun sendersWith(current: List<String>, added: String): List<String> {
    val name = added.trim()
    return if (current.any { smsSenderKey(it) == smsSenderKey(name) }) current else current + name
}

/** المرسلين بعد شيل واحد. */
fun sendersWithout(current: List<String>, removed: String): List<String> = current.filter { smsSenderKey(it) != smsSenderKey(removed) }
