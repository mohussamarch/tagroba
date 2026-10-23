package app.masroufy.ui.screens.imports

import app.masroufy.core.Id
import app.masroufy.core.UiKey
import app.masroufy.core.Wallet
import app.masroufy.core.Bank
import app.masroufy.core.bankForSender
import app.masroufy.port.smsSenderKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** r041c — «إعداد القراءة» وهي بتتحمّل: [Loading] الأول على طول (الصفحة بترسم إطارها) ثم [Ready] (مرة من المحفوظ ومرة من الجديد). */
sealed interface SmsSettingsState {
    data object Loading : SmsSettingsState

    /** [wallets] = محافظ البلد اللي اتعرضت بيها (لو قرايتها وقعت ⇒ اللي كانت عند الشاشة). */
    data class Ready(val ui: SmsSettingsUi, val wallets: List<Wallet>) : SmsSettingsState
}

/**
 * r041c — اللي «إعداد القراءة» بتعرضه بالترتيب: «بيحمّل» على طول ⇒ آخر صورة اتحسبت لو فيه (`SmsDeps.cachedSettings`) ⇒ الصورة الجديدة
 * (`SmsDeps.settings` — خفيفة وبرّه خيط الواجهة). كانت بتستنى `overview` كامل (معاينة كل بنك) على خيط الواجهة فالصفحة تفضل بيضا.
 * [previous] = المحافظ اللي عند الشاشة (لو قراية المحافظ وقعت). [sms] = null أو القراية وقعت ⇒ «الجهاز ما بيقراش رسايل» زي الأول.
 */
fun smsSettingsStates(sms: SmsDeps?, country: String?, previous: List<Wallet>, wallets: suspend () -> List<Wallet>): Flow<SmsSettingsState> = flow {
    emit(SmsSettingsState.Loading)
    val shown = quietly { wallets() } ?: previous
    sms?.cachedSettings()?.let { emit(SmsSettingsState.Ready(smsSettingsUi(it, shown, country), shown)) }
    val fresh = sms?.let { s -> quietly { s.settings() } }
    emit(SmsSettingsState.Ready(smsSettingsUi(fresh, shown, country), shown))
}

private suspend fun <T> quietly(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

/**
 * بنك (مرسل) في «إعداد القراءة»: محفظته (null = مستني تختار) وعدد رسايله المستنية المحفظة. [bank] = البنك المقترح من اسم المرسل
 * (دليل البنوك — §74: المؤكد بس)؛ null ⇒ الاسم الخام زي ما جه في الرسالة.
 */
data class SenderUi(val sender: String, val walletId: Id?, val walletName: String?, val waiting: Int, val bank: Bank? = null)

/** حالة «إعداد القراءة» (`BankSmsSettings`). [available] = الجهاز بيقرا رسايل. */
/**
 * [senders] = مرسلين البلد دي بس (اللي بيتعرضوا). [allSenders] = قايمة الجهاز كلها (البلدين) — هي اللي بتتبعت لـ`enable` لما تضيف أو تشيل،
 * عشان مرسلين البلد التانية ما يتمسحوش من القايمة.
 */
data class SmsSettingsUi(
    val available: Boolean,
    val enabled: Boolean,
    val permission: Boolean,
    val senders: List<SenderUi>,
    val allSenders: List<String> = senders.map { it.sender },
) {
    /** القراءة شغالة فعلًا (متفعّلة والإذن موجود). */
    val reading: Boolean get() = enabled && permission

    /** الإذن اتسحب وهي متفعّلة ⇒ تنبيه «سُحب إذن الرسائل». */
    val permissionLost: Boolean get() = enabled && !permission

    /** أقصى عدد مرسلين (`MAX_SMS_SENDERS` — 25 لكل بلد §112-7). */
    val full: Boolean get() = senders.size >= MAX_SENDERS

    companion object {
        val MAX_SENDERS: Int get() = app.masroufy.core.MAX_SMS_SENDERS
    }
}

/** من صورة الصندوق (من غير تسجيل) ومحافظ البلد. [overview] = null ⇒ الجهاز ما بيقراش رسايل. [country] = بلد المساحة (اقتراح البنك من المرسل). */
fun smsSettingsUi(overview: SmsOverview?, wallets: List<Wallet>, country: String? = null): SmsSettingsUi {
    if (overview == null) return SmsSettingsUi(available = false, enabled = false, permission = false, senders = emptyList())
    // فحص 0.5.3: كل بلد بتعرض مرسلينها بس — المرسل المعروف لبلد تانية (`AlRajhiBank` في مصر) ما يظهرش ولا يتربط هنا
    val senders = overview.inbox.senders.filterNot { app.masroufy.core.senderBelongsElsewhere(it, country) }.map { sender ->
        val walletId = overview.senderWallets[sender]
        val waiting = overview.unmapped.firstOrNull { smsSenderKey(it.sender) == smsSenderKey(sender) }?.messages ?: 0
        SenderUi(sender, walletId, wallets.firstOrNull { it.id == walletId }?.name, waiting, bankForSender(sender, country))
    }
    return SmsSettingsUi(available = true, enabled = overview.inbox.enabled, permission = overview.inbox.permission, senders = senders, allSenders = overview.inbox.senders)
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
