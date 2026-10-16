package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.MAX_SAFE_HALALAS
import app.masroufy.core.SAUDI_TIME_ZONE
import app.masroufy.core.TextKey
import app.masroufy.core.UiKey
import app.masroufy.core.Wallet
import app.masroufy.core.bankByKey
import app.masroufy.core.dayIn
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.jsTrim
import app.masroufy.core.normalizeDigits
import app.masroufy.core.normalizeText
import app.masroufy.core.uiText
import app.masroufy.port.Clock
import app.masroufy.port.IdGenerator
import app.masroufy.port.WalletRepository

/**
 * محفظة جديدة من «أضف محفظة»: [openingMinor] null = صفر من النهارده. [last4] آخر 4 أرقام بس (CLAUDE.md #11) — الكاش من غيرها.
 * [bankId] = البنك من «اختار بنكك» (`Bank.key` في دليل البنوك — OVERRIDES §81) أو null («بنك تاني» باسم بيكتبه).
 */
data class AddWalletInput(
    val kind: String,
    val name: String,
    val last4: String?,
    val openingMinor: Halalas?,
    val openingAt: IsoDate?,
    val bankId: String? = null,
)

class WalletError(message: String) : IllegalArgumentException(message)

/**
 * إضافة محفظة وتعديل رصيد بدايتها (منطقة «المزيد» — `WalletEditor`؛ والحساب الجديد بيوصل هنا بعد أسئلة البداية). **كل حاجة بتتأكد قبل الكتابة**،
 * والغلط رسالة صريحة (اسم مكرر · آخر 4 مش 4 أرقام · مبلغ سالب · تاريخ بعد النهارده). العملة = عملة البلد الشغالة. المبالغ هللات صحيحة.
 * ⚠️ تعديل رصيد البداية بيغيّر الرصيد المحسوب للمحفظة كلها — الشاشة بتعرضه كتعديل صريح من المستخدم بس.
 * «النهارده» = **يوم البلد** ([timeZone] من حزمة البلد — `core/CountryTime.kt`) مش يوم جرينتش: بين 00:00 و03:00 بتوقيت الرياض يوم
 * جرينتش لسه امبارح، فمحفظة بتاريخ النهارده كانت بتترفض «بعد النهارده» (اتلقى في جولة 0.3).
 */
class ManageWallets(
    private val wallets: WalletRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val currency: Currency,
    private val timeZone: String = SAUDI_TIME_ZONE,
) {
    /** النهارده في البلد — اللوحة بتبدأ بيه والتأكد بيقارن بيه. */
    fun today(): IsoDate = clock.nowIso().let { dayIn(timeZone, it) ?: it.take(10) }

    suspend fun add(input: AddWalletInput): Wallet {
        val wallet = prepare(input)
        wallets.save(wallet)
        return wallet
    }

    /** نفس التأكد بتاع [add] **من غير كتابة** — للي محتاج يتأكد من كل حاجة قبل أول كتابة (أسئلة البداية: «عندك حساب بنكي؟»). */
    suspend fun prepare(input: AddWalletInput): Wallet {
        val name = jsTrim(input.name).replace(Regex("\\s+"), " ")
        if (name.isEmpty()) throw WalletError(uiText(UiKey.WADD_ERR_NAME))
        if (name.length > MAX_WALLET_NAME) throw WalletError(uiText(TextKey.PROFILE_NAME_TOO_LONG, MAX_WALLET_NAME.toString()))
        if (input.kind !in WALLET_KINDS) throw WalletError(uiText(UiKey.WADD_ERR_NAME))
        if (wallets.listAll().any { normalizeText(it.name) == normalizeText(name) }) throw WalletError(uiText(UiKey.WADD_ERR_DUP))
        val last4 = if (input.kind == "cash") null else input.last4?.let { normalizeDigits(jsTrim(it)) }?.ifEmpty { null }
        if (last4 != null && (last4.length != 4 || !last4.all { it in '0'..'9' })) throw WalletError(uiText(UiKey.WADD_ERR_LAST4))
        val today = today()
        val opening = input.openingMinor ?: 0L
        val at = if (input.openingMinor == null) today else input.openingAt ?: today
        checkOpening(opening, at, today)
        val bankId = if (input.kind == "cash") null else input.bankId?.takeIf { it.isNotBlank() }
        if (bankId != null && bankByKey(bankId) == null) throw WalletError(uiText(UiKey.WADD_ERR_BANK))
        // r041b P4 (CLAUDE.md #10): بنك/محفظة من غير رصيد بداية ⇒ رصيدها «غير متاح» (زي ما اللوحة بتقول) — مش صفر معروف.
        // الكاش زي ما هو (صفر من النهارده) لحد قرار المالك
        val unknown = input.openingMinor == null && input.kind != "cash"
        return Wallet(ids.next("wallet"), name, currency, input.kind, opening, at, last4, bankId, openingUnknown = unknown)
    }

    suspend fun setOpening(walletId: Id, openingMinor: Halalas, openingAt: IsoDate): Wallet {
        val wallet = wallets.findById(walletId) ?: throw WalletError(uiText(TextKey.NOT_AVAILABLE))
        checkOpening(openingMinor, openingAt, today())
        val updated = wallet.copy(openingBalanceMinor = openingMinor, openingAt = openingAt, openingUnknown = false)
        wallets.save(updated)
        return updated
    }

    /**
     * sms-fix (HANDOVER §6.5-3): آخر 4 أرقام حساب محفظة **موجودة** (اختياري — CLAUDE.md #11: الأربعة بس). رسايل البنك بتتوزع بيها وتعرف
     * اتجاه «حوالة بين حساباتك». فاضي ⇒ يشيلها. الكاش من غيرها · نفس الأرقام لمحفظة بنك تانية ⇒ خطأ (ما بنفرّقش بيهم).
     */
    suspend fun setLast4(walletId: Id, last4: String?): Wallet {
        val wallet = wallets.findById(walletId) ?: throw WalletError(uiText(TextKey.NOT_AVAILABLE))
        val digits = last4?.let { normalizeDigits(jsTrim(it)) }?.ifEmpty { null }
        if (digits != null && (wallet.kind == "cash" || digits.length != 4 || !digits.all { it in '0'..'9' })) throw WalletError(uiText(UiKey.WADD_ERR_LAST4))
        if (digits != null && wallets.listAll().any { it.id != walletId && walletLast4(it.accountLast4) == digits }) throw WalletError(uiText(UiKey.WADD_ERR_LAST4))
        val updated = wallet.copy(accountLast4 = digits)
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
