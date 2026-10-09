package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.Wallet
import app.masroufy.core.sumMoney
import app.masroufy.core.walletBalancesOn
import app.masroufy.port.TransactionRepository
import app.masroufy.port.WalletRepository

/**
 * «معك الآن» (OVERRIDES §73 — الرقم البطل في الرئيسية): كل محافظ البلد الشغالة (البنوك + الكاش) آخر يوم [load]`(today)`,
 * من نفس سلسلة الرصيد اللي الزكاة بتستعملها (`walletBalancesOn` — رصيد البداية + الوارد − الصادر، والتحويل الداخلي داخل على المحفظة).
 * - محفظة لسه ما اتفتحتش في التطبيق يومها ⇒ رصيدها `null` ⇒ **المجموع `null` («غير متاح»)** — مش صفر (CLAUDE.md #10).
 * - مفيش محافظ خالص ⇒ المجموع `null`.
 * - [WithYouNow.cashMinor] = مجموع محافظ الكاش («يشمل X كاش»)، و`null` لو مفيش كاش أو رصيده مش معروف.
 * ⚠️ رصيد كل بنك هنا **من العمليات المسجّلة** — مفيش رصيد معلن من رسالة البنك متخزن لسه (§73 «رصيد البنك غير متاح حتى تصل رسالة…» مش متبني).
 */
data class WalletNow(val wallet: Wallet, val balanceMinor: Halalas?)

data class WithYouNow(
    val currency: Currency,
    /** كل المحافظ (البنوك الأول بترتيبها، والكاش آخر حاجة). */
    val wallets: List<WalletNow>,
    val totalMinor: Halalas?,
    val cashMinor: Halalas?,
) {
    /** عدسات «معك الآن» (`HeroBanks`): البنوك والمحافظ الإلكترونية — كل اللي مش كاش. */
    val banks: List<WalletNow> get() = wallets.filter { it.wallet.kind != CASH }
    val cash: List<WalletNow> get() = wallets.filter { it.wallet.kind == CASH }
}

private const val CASH = "cash"

class LoadWithYouNow(private val wallets: WalletRepository, private val txns: TransactionRepository, private val currency: Currency) {
    suspend fun load(today: IsoDate): WithYouNow {
        val all = wallets.listAll().sortedBy { if (it.kind == CASH) 1 else 0 }
        if (all.isEmpty()) return WithYouNow(currency, emptyList(), null, null)
        val from = all.minOf { it.openingAt }
        val rows = if (from <= today) txns.listByDateRange(from, today) else emptyList()
        val balances = walletBalancesOn(all, rows, today)
        val list = all.map { WalletNow(it, balances[it.id]) }
        val total = if (list.any { it.balanceMinor == null }) null else sumMoney(list.map { it.balanceMinor!! })
        val cashWallets = list.filter { it.wallet.kind == CASH }
        val cash = if (cashWallets.isEmpty() || cashWallets.any { it.balanceMinor == null }) null else sumMoney(cashWallets.map { it.balanceMinor!! })
        return WithYouNow(currency, list, total, cash)
    }
}
