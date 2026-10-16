package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.core.UiKey
import app.masroufy.core.Wallet
import app.masroufy.core.normalizeDigits
import app.masroufy.core.uiText
import app.masroufy.port.WalletRepository

/**
 * إجابة المالك 2026-10-15 (OVERRIDES §91-1): رسالة تحويل فيها «من:/الى:» وأرقام الحساب مش لأي محفظة ⇒ بتستنى وتسأل «الحساب ده بتاعك؟»
 * (`smsLegVerdict` ⇒ `SMS_WAIT_UNKNOWN_ACCOUNT`). الرد هنا، والتشغيلة اللي بعده (`AutoRecordSms.run`) بتسجّل المستني لوحده:
 * - «أيوه، ده حسابي» ([mine]): الأرقام بتتكتب على المحفظة اللي المالك اختارها ⇒ رسايل الحساب ده بتتسجل فيها (والتحويل بين حساباته برجلين).
 * - «لأ» ([notMine]): الأرقام بتتحفظ «مش بتاعي» على محفظة الرسالة ⇒ بتتسجل فلوس لشخص تاني أو منه بالاتجاه اللي الرسالة بتقوله.
 * الأربعة أرقام بس (CLAUDE.md #11).
 */
class AnswerSmsAccount(private val wallets: WalletRepository) {
    /** «أيوه، ده حسابي»: المحفظة مش كاش ومالهاش أرقام تانية، والأرقام مش لمحفظة تانية — وإلا خطأ (ما بنكتبش فوق أرقام موجودة). */
    suspend fun mine(walletId: Id, last4: String): Wallet {
        val digits = digitsOf(last4)
        val all = wallets.listAll()
        val wallet = all.firstOrNull { it.id == walletId } ?: throw WalletError(uiText(TextKey.NOT_AVAILABLE))
        val current = walletLast4(wallet.accountLast4)
        if (wallet.kind == "cash" || (current != null && current != digits)) throw WalletError(uiText(UiKey.WADD_ERR_LAST4))
        if (all.any { it.id != walletId && walletLast4(it.accountLast4) == digits }) throw WalletError(uiText(UiKey.WADD_ERR_LAST4))
        // لو كان قال «لأ» قبل كده على نفس الأرقام ⇒ الرد الجديد هو اللي بيحكم
        for (other in all) if (other.id != walletId && digits in other.notMineLast4) wallets.save(other.copy(notMineLast4 = other.notMineLast4 - digits))
        val updated = wallet.copy(accountLast4 = digits, notMineLast4 = wallet.notMineLast4 - digits)
        wallets.save(updated)
        return updated
    }

    /** «لأ»: الأرقام دي مش حساب من حساباته — بتتحفظ على محفظة الرسالة. أرقام محفظة من محافظه ⇒ خطأ (ده حسابه فعلًا). */
    suspend fun notMine(walletId: Id, last4: String): Wallet {
        val digits = digitsOf(last4)
        val all = wallets.listAll()
        val wallet = all.firstOrNull { it.id == walletId } ?: throw WalletError(uiText(TextKey.NOT_AVAILABLE))
        if (all.any { walletLast4(it.accountLast4) == digits }) throw WalletError(uiText(UiKey.WADD_ERR_LAST4))
        if (digits in wallet.notMineLast4) return wallet
        val updated = wallet.copy(notMineLast4 = wallet.notMineLast4 + digits)
        wallets.save(updated)
        return updated
    }

    /**
     * r041 A3: الرد على الرسالة [messageId] نفسها = تأكيد صريح من المالك ليها ⇒ بتتسجل على طول وشكلها بيتعلّم بنفس طريق «سجّل عملية
     * واحدة» (`AutoRecordSms.confirm` — §77-A)، وبعدين التشغيلة العادية للباقي. قبل كده الرد كان بيتحفظ والرسالة ترجع تستنى «أول رسالة
     * بهذا الشكل» فالمالك يدوس تاني. رسايل تانية بشكل تاني بتمشي على قاعدة التعلّم العادية.
     */
    suspend fun answerAndRecord(auto: AutoRecordSms, walletId: Id, last4: String, mine: Boolean, messageId: String?): AutoRecordResult {
        if (mine) mine(walletId, last4) else notMine(walletId, last4)
        val confirmed = if (messageId.isNullOrBlank()) 0 else auto.confirm(listOf(messageId))
        val result = auto.run()
        return result.copy(recorded = result.recorded + confirmed)
    }

    private fun digitsOf(text: String): String {
        val digits = normalizeDigits(text).filter { it in '0'..'9' }
        return digits.takeIf { it.length == 4 } ?: throw WalletError(uiText(UiKey.WADD_ERR_LAST4))
    }
}
