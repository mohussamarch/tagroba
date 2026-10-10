package app.masroufy.ui.screens.imports

import app.masroufy.core.Id
import app.masroufy.core.Wallet
import app.masroufy.port.smsSenderKey

/** بنك (مرسل) في «إعداد القراءة»: محفظته (null = مستني تختار) وعدد رسايله المستنية المحفظة. */
data class SenderUi(val sender: String, val walletId: Id?, val walletName: String?, val waiting: Int)

/** حالة «إعداد القراءة» (`BankSmsSettings`). [available] = الجهاز بيقرا رسايل. */
data class SmsSettingsUi(val available: Boolean, val enabled: Boolean, val permission: Boolean, val senders: List<SenderUi>) {
    /** القراءة شغالة فعلًا (متفعّلة والإذن موجود). */
    val reading: Boolean get() = enabled && permission

    /** الإذن اتسحب وهي متفعّلة ⇒ تنبيه «سُحب إذن الرسائل». */
    val permissionLost: Boolean get() = enabled && !permission

    /** أقصى ١٠ مرسلين (`ManageSmsInbox.enable`). */
    val full: Boolean get() = senders.size >= MAX_SENDERS

    companion object {
        const val MAX_SENDERS = 10
    }
}

/** من صورة الصندوق (من غير تسجيل) ومحافظ البلد. [overview] = null ⇒ الجهاز ما بيقراش رسايل. */
fun smsSettingsUi(overview: SmsOverview?, wallets: List<Wallet>): SmsSettingsUi {
    if (overview == null) return SmsSettingsUi(available = false, enabled = false, permission = false, senders = emptyList())
    val senders = overview.inbox.senders.map { sender ->
        val walletId = overview.senderWallets[sender]
        val waiting = overview.unmapped.firstOrNull { smsSenderKey(it.sender) == smsSenderKey(sender) }?.messages ?: 0
        SenderUi(sender, walletId, wallets.firstOrNull { it.id == walletId }?.name, waiting)
    }
    return SmsSettingsUi(available = true, enabled = overview.inbox.enabled, permission = overview.inbox.permission, senders = senders)
}

/** المرسلين بعد إضافة واحد (من غير تكرار بحالة الحروف) — اللي بيتبعت لـ`enable`. */
fun sendersWith(current: List<String>, added: String): List<String> {
    val name = added.trim()
    return if (current.any { smsSenderKey(it) == smsSenderKey(name) }) current else current + name
}

/** المرسلين بعد شيل واحد. */
fun sendersWithout(current: List<String>, removed: String): List<String> = current.filter { smsSenderKey(it) != smsSenderKey(removed) }
