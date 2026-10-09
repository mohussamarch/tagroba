package app.masroufy.usecase

import app.masroufy.core.Dismissal
import app.masroufy.core.Id
import app.masroufy.core.MainSpendingWallet
import app.masroufy.core.MainWalletSource
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.uiText
import app.masroufy.port.AlertInboxEntry
import app.masroufy.port.AlertInboxStore
import app.masroufy.port.Clock
import app.masroufy.port.DismissalStore
import app.masroufy.port.MainSpendingWalletStore
import app.masroufy.port.WalletRepository

/**
 * المحفظة الأساسية للبلد الشغالة (رد المالك ٢ — 2026-10-09): «البرنامج يسأله أول مرة إيه هي الحاجة الأساسية اللي بتصرف منها ويتسجل على
 * أساس ده علطول (وأي عملية يقدر يغيرها عادي بعدين)». للشاشات: [get] · [set] (من «اجعلها الأساسية» أو لوحة «+» أو الشات) · [needsAsk] ·
 * [askOptions] (زرار حساب البنك وزرار الكاش) · [defaultForAdd] (لوحة «+»: المحفظة الأساسية، أو فاضية ومكتوب «بتصرف عادةً منين؟» والحفظ
 * مقفول لحد ما يختار).
 */
class MainSpendingWallets(
    private val store: MainSpendingWalletStore,
    private val wallets: WalletRepository,
    private val spaceId: String,
    private val clock: Clock,
) {
    /** المحفظة الأساسية لو لسه موجودة في البلد (محفظة اتشالت ⇒ null ⇒ يتسأل تاني). */
    suspend fun get(): Wallet? {
        val main = store.listAll().firstOrNull { it.spaceId == spaceId } ?: return null
        return wallets.findById(main.walletId)
    }

    suspend fun needsAsk(): Boolean = get() == null

    suspend fun isMain(walletId: Id): Boolean = get()?.id == walletId

    /** المحفظة لازم تبقى في البلد دي. */
    suspend fun set(walletId: Id, source: MainWalletSource): MainSpendingWallet {
        wallets.findById(walletId) ?: throw IllegalArgumentException(uiText(TextKey.TXN_WALLET_REQUIRED))
        val main = MainSpendingWallet(spaceId, walletId, clock.nowIso(), source)
        store.save(main)
        return main
    }

    /**
     * زراير «بتصرف عادةً منين؟» (النموذج: حساب البنك والكاش): حسابات البنك والمحافظ الإلكترونية الأول، وبعدهم الكاش — بترتيب المحافظ.
     * المعتاد حساب بنك واحد وكاش واحد = زرارين (اختيار Claude: لو أكتر، كلهم بيظهروا بدل ما نختار واحد بالنيابة عنه).
     */
    suspend fun askOptions(): List<Wallet> {
        val all = wallets.listAll().filter { it.kind != "own_abroad" }
        return all.filter { it.kind == "bank" || it.kind == "digital_wallet" } + all.filter { it.kind == "cash" }
    }

    /** لوحة «+»: المحفظة الأساسية، أو مفيش ⇒ [needsAsk] والنص «بتصرف عادةً منين؟» والحفظ مقفول لحد ما يختار. */
    suspend fun defaultForAdd(): AddWalletDefault {
        val main = get()
        return AddWalletDefault(main, main == null, uiText(TextKey.ASSIST_ASK_MAIN_WALLET_SHORT))
    }
}

data class AddWalletDefault(val wallet: Wallet?, val needsAsk: Boolean, val prompt: String) {
    /** الحفظ في لوحة «+» مسموح بس لما فيه محفظة (الأساسية أو اللي اختارها). */
    fun canSave(picked: Id?): Boolean = picked != null || wallet != null
}

/**
 * المسح بـ«×» من الجرس وصفحة الإشعارات (رد المالك ٣): السطر بيتشال، والمسح بيتحفظ على الحساب عشان المحرك ما يرجّعوش وكارته في «أمور لم
 * تُنجزها بعد» يختفي. «رجّعه» (٤ ثواني في الشاشة) = [undo].
 */
class ManageAlertDismissals(private val store: DismissalStore, private val inbox: AlertInboxStore, private val clock: Clock) {
    suspend fun dismiss(threadKey: String): DismissedAlert {
        val entry = inbox.listAll().firstOrNull { it.threadKey == threadKey }
        store.save(Dismissal(threadKey, clock.nowIso()))
        if (entry != null) inbox.remove(listOf(threadKey))
        return DismissedAlert(threadKey, entry)
    }

    suspend fun undo(dismissed: DismissedAlert) {
        store.remove(listOf(dismissed.threadKey))
        dismissed.entry?.let { inbox.save(it) }
    }

    suspend fun dismissedThreads(): Set<String> = store.listAll().map { it.key }.toSet()
}

data class DismissedAlert(val threadKey: String, val entry: AlertInboxEntry?)

/** «×» على كارت في «أمور لم تُنجزها بعد» (الرد التاني ٢): بيقفله حتى لو الحاجة ما اتعملتش. */
class ManageStartDismissals(private val store: DismissalStore, private val clock: Clock) {
    suspend fun dismiss(key: String) = store.save(Dismissal(key, clock.nowIso()))

    suspend fun undo(key: String) = store.remove(listOf(key))

    suspend fun keys(): Set<String> = store.listAll().map { it.key }.toSet()

    /** الكروت اللي حاجتها خلصت (مش في المرشحين دلوقتي) ⇒ مسحها بيتنسى. */
    suspend fun prune(current: Set<String>) {
        val stale = store.listAll().map { it.key }.filter { it !in current }
        if (stale.isNotEmpty()) store.remove(stale)
    }
}
