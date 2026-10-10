package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.MAX_SAFE_HALALAS
import app.masroufy.core.TextKey
import app.masroufy.core.UiKey
import app.masroufy.core.Wallet
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.jsTrim
import app.masroufy.core.normalizeDigits
import app.masroufy.core.normalizeText
import app.masroufy.core.uiText
import app.masroufy.port.Clock
import app.masroufy.port.IdGenerator
import app.masroufy.port.WalletRepository

/** محفظة جديدة من «أضف محفظة»: [openingMinor] null = صفر من النهارده. [last4] آخر 4 أرقام بس (CLAUDE.md #11) — الكاش من غيرها. */
data class AddWalletInput(val kind: String, val name: String, val last4: String?, val openingMinor: Halalas?, val openingAt: IsoDate?)

class WalletError(message: String) : IllegalArgumentException(message)

/**
 * إضافة محفظة وتعديل رصيد بدايتها (منطقة «المزيد» — `WalletEditor`؛ والحساب الجديد بيوصل هنا بعد أسئلة البداية). **كل حاجة بتتأكد قبل الكتابة**،
 * والغلط رسالة صريحة (اسم مكرر · آخر 4 مش 4 أرقام · مبلغ سالب · تاريخ بعد النهارده). العملة = عملة البلد الشغالة. المبالغ هللات صحيحة.
 * ⚠️ تعديل رصيد البداية بيغيّر الرصيد المحسوب للمحفظة كلها — الشاشة بتعرضه كتعديل صريح من المستخدم بس.
 */
class ManageWallets(
    private val wallets: WalletRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val currency: Currency,
) {
    suspend fun add(input: AddWalletInput): Wallet {
        val name = jsTrim(input.name).replace(Regex("\\s+"), " ")
        if (name.isEmpty()) throw WalletError(uiText(UiKey.WADD_ERR_NAME))
        if (name.length > MAX_WALLET_NAME) throw WalletError(uiText(TextKey.PROFILE_NAME_TOO_LONG, MAX_WALLET_NAME.toString()))
        if (input.kind !in WALLET_KINDS) throw WalletError(uiText(UiKey.WADD_ERR_NAME))
        if (wallets.listAll().any { normalizeText(it.name) == normalizeText(name) }) throw WalletError(uiText(UiKey.WADD_ERR_DUP))
        val last4 = if (input.kind == "cash") null else input.last4?.let { normalizeDigits(jsTrim(it)) }?.ifEmpty { null }
        if (last4 != null && (last4.length != 4 || !last4.all { it in '0'..'9' })) throw WalletError(uiText(UiKey.WADD_ERR_LAST4))
        val today = clock.nowIso().take(10)
        val opening = input.openingMinor ?: 0L
        val at = if (input.openingMinor == null) today else input.openingAt ?: today
        checkOpening(opening, at, today)
        val wallet = Wallet(ids.next("wallet"), name, currency, input.kind, opening, at, last4)
        wallets.save(wallet)
        return wallet
    }

    suspend fun setOpening(walletId: Id, openingMinor: Halalas, openingAt: IsoDate): Wallet {
        val wallet = wallets.findById(walletId) ?: throw WalletError(uiText(TextKey.NOT_AVAILABLE))
        checkOpening(openingMinor, openingAt, clock.nowIso().take(10))
        val updated = wallet.copy(openingBalanceMinor = openingMinor, openingAt = openingAt)
        wallets.save(updated)
        return updated
    }

    private fun checkOpening(amount: Halalas, at: IsoDate, today: IsoDate) {
        if (amount < 0 || amount > MAX_SAFE_HALALAS) throw WalletError(uiText(UiKey.WADD_ERR_AMOUNT))
        if (!isValidIsoDate(at) || at > today) throw WalletError(uiText(UiKey.WADD_ERR_DATE))
    }
}

private const val MAX_WALLET_NAME = 80

/** الأنواع اللي «أضف محفظة» بيعرضها (البنك · محفظة رقمية · الكاش) + حساب برا البلد (`own_abroad`) زي المخزن. */
private val WALLET_KINDS = setOf("bank", "digital_wallet", "cash", "own_abroad")
