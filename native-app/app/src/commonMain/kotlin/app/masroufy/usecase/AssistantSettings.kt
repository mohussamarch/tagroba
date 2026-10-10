package app.masroufy.usecase

import app.masroufy.core.AlertDismissal
import app.masroufy.core.FactKeys
import app.masroufy.core.ForgottenMark
import app.masroufy.core.Id
import app.masroufy.core.MainWalletSource
import app.masroufy.core.TextKey
import app.masroufy.core.UserSetting
import app.masroufy.core.Wallet
import app.masroufy.core.mainWalletKey
import app.masroufy.core.uiText
import app.masroufy.port.AlertDismissalStore
import app.masroufy.port.AlertInboxEntry
import app.masroufy.port.AlertInboxStore
import app.masroufy.port.AssistantForgottenStore
import app.masroufy.port.Clock
import app.masroufy.port.UserSettingsStore
import app.masroufy.port.WalletRepository

/**
 * المحفظة الأساسية للبلد الشغالة (رد المالك ٢ — 2026-10-09): «البرنامج يسأله أول مرة إيه هي الحاجة الأساسية اللي بتصرف منها ويتسجل على
 * أساس ده علطول (وأي عملية يقدر يغيرها عادي بعدين)». **لكل بلد لوحدها** (السعودية ومصر). للشاشات: [get] · [set] («اجعلها الأساسية» ·
 * لوحة «+» · الشات) · [needsAsk] · [askOptions] (زرار حساب البنك وزرار الكاش) · [defaultForAdd] (لوحة «+»).
 * [wallets] = محافظ البلد دي بس — محفظة من بلد تانية مرفوضة.
 */
class MainSpendingWallets(
    private val settings: UserSettingsStore,
    private val wallets: WalletRepository,
    private val spaceId: String,
    private val clock: Clock,
) {
    suspend fun stored(): UserSetting.MainWallet? =
        settings.listAll().filterIsInstance<UserSetting.MainWallet>().firstOrNull { it.spaceId == spaceId }

    /** المحفظة الأساسية لو لسه موجودة في البلد. اتشالت ⇒ null ⇒ يتسأل تاني (عمره ما بيختار غيرها في صمت). */
    suspend fun get(): Wallet? = stored()?.let { wallets.findById(it.walletId) }

    suspend fun needsAsk(): Boolean = get() == null

    suspend fun isMain(walletId: Id): Boolean = get()?.id == walletId

    suspend fun set(walletId: Id, source: MainWalletSource): UserSetting.MainWallet {
        wallets.findById(walletId) ?: throw IllegalArgumentException(uiText(TextKey.TXN_WALLET_REQUIRED))
        val main = UserSetting.MainWallet(spaceId, walletId, source, clock.nowIso())
        settings.save(main)
        return main
    }

    /**
     * زراير «بتصرف عادةً منين؟» (النموذج: حساب البنك والكاش): البنوك والمحافظ الإلكترونية الأول، وبعدهم الكاش. لو أكتر من بنك كلهم بيظهروا
     * (اختيار Claude: ما بنختارش بالنيابة عنه). حساباتك برّه البلد مش منها.
     */
    suspend fun askOptions(): List<Wallet> {
        val all = wallets.listAll().filter { it.kind != "own_abroad" }
        return all.filter { it.kind == "bank" || it.kind == "digital_wallet" } + all.filter { it.kind == "cash" }
    }

    /** لوحة «+»: الأساسية، أو مفيش ⇒ فاضية ومكتوب «بتصرف عادةً منين؟» والحفظ مقفول لحد ما يختار. */
    suspend fun defaultForAdd(): AddWalletDefault {
        val main = get()
        return AddWalletDefault(main, main == null, uiText(TextKey.ASSIST_ASK_MAIN_WALLET))
    }
}

/** الافتراضي في لوحة «+». [prompt] = «بتصرف عادةً منين؟». */
data class AddWalletDefault(val wallet: Wallet?, val needsAsk: Boolean, val prompt: String) {
    fun canSave(picked: Id?): Boolean = picked != null || wallet != null
}

/** شارة «الأساسية» على قايمة المحافظ: معرّف الأساسية (أو null). */
suspend fun MainSpendingWallets.badgeWalletId(): Id? = get()?.id

/** مفتاح «يتعلّم من أسئلتي» (على الحساب). مفيش مستند = شغال. */
class AssistantLearning(private val settings: UserSettingsStore, private val clock: Clock) {
    suspend fun isOn(): Boolean = settings.listAll().filterIsInstance<UserSetting.Assistant>().firstOrNull()?.learningOn ?: true

    suspend fun set(on: Boolean) = settings.save(UserSetting.Assistant(on, clock.nowIso()))
}

/**
 * المسح بـ«×» من الجرس وصفحة الإشعارات (رد المالك ٣): السطر بيتشال، والعلامة على الحساب عشان المحرك ما يرجّعوش وكارته في «أمور لم
 * تُنجزها بعد» يختفي والنقطة الحمرا تروح. «رجّعه» (٤ ثواني في الشاشة) = [undo].
 */
class ManageAlertDismissals(private val store: AlertDismissalStore, private val inbox: AlertInboxStore, private val clock: Clock) {
    suspend fun dismiss(threadKey: String): DismissedAlert {
        val entry = inbox.listAll().firstOrNull { it.threadKey == threadKey }
        store.save(AlertDismissal(threadKey, clock.nowIso()))
        if (entry != null) inbox.remove(listOf(threadKey))
        return DismissedAlert(threadKey, entry)
    }

    suspend fun undo(dismissed: DismissedAlert) {
        store.remove(listOf(dismissed.threadKey))
        dismissed.entry?.let { inbox.save(it) }
    }

    suspend fun dismissedThreads(): Set<String> = store.listAll().map { it.threadKey }.toSet()

    /** النقطة الحمرا على التبويب: فيه إشعار ما اتفتحش ولا اتمسح. */
    suspend fun hasUnread(): Boolean {
        val gone = dismissedThreads()
        return inbox.listAll().any { it.openedAt == null && it.threadKey !in gone }
    }
}

data class DismissedAlert(val threadKey: String, val entry: AlertInboxEntry?)

/** «×» على كارت في «أمور لم تُنجزها بعد»: علامة `card:<المفتاح>` (المفتاح فيه الحاجة نفسها ⇒ كارت الشهر الجاي بيظهر عادي). */
class ManageStartCards(private val forgotten: AssistantForgottenStore, private val clock: Clock) {
    suspend fun close(cardKey: String): ForgottenMark = ForgottenMark(FactKeys.card(cardKey), clock.nowIso()).also { forgotten.save(it) }

    suspend fun undo(mark: ForgottenMark) = forgotten.remove(listOf(mark.id))

    suspend fun closedKeys(): Set<String> =
        forgotten.listAll().map { it.factKey }.filter { it.startsWith("card:") }.map { it.removePrefix("card:") }.toSet()
}
