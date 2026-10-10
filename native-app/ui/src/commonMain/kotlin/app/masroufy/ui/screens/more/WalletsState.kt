package app.masroufy.ui.screens.more

import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.sentenceDigits
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.text.t
import app.masroufy.usecase.TransactionsScreenData
import app.masroufy.usecase.WalletNow
import app.masroufy.usecase.WithYouNow

/**
 * المحافظ وتفاصيل المحفظة — من «معك الآن» (`LoadWithYouNow`) وحركات الشهر (`LoadTransactionsScreen`) للعرض، **دوال نقية** (JVM).
 * مفيش جمع ولا طرح هنا: الأرصدة والمجموع جايين جاهزين من حالة الاستخدام، والرصيد المجهول `null` ⇒ «غير متاح» (القاعدة 10).
 */
enum class WalletGroupKind(val title: TextKey) { BANKS(TextKey.WLIST_BANKS), DIGITAL(TextKey.WLIST_DIGITAL), CASH(TextKey.WLIST_CASH) }

data class WalletRowView(
    val id: Id,
    val name: String,
    val kindLabel: TextKey,
    /** آخر ٤ أرقام بالأرقام العربية في العربي (CLAUDE.md #11 — مفيش رقم حساب كامل). */
    val last4: String?,
    val balanceMinor: Halalas?,
    val isCash: Boolean,
    val isMain: Boolean,
    /** الكاش «تقريبي» (الكاش اللي اتصرف ومااتسجلش مش باين — §32). */
    val approx: Boolean,
    val openingMinor: Halalas,
    val openingAt: IsoDate,
)

data class WalletGroupView(val kind: WalletGroupKind, val rows: List<WalletRowView>)

data class WalletsView(val totalMinor: Halalas?, val count: Int, val unknown: List<String>, val groups: List<WalletGroupView>)

fun kindLabel(kind: String): TextKey = when (kind) {
    "cash" -> TextKey.WLIST_KIND_CASH
    "digital_wallet" -> TextKey.WLIST_KIND_DIGITAL
    "own_abroad" -> TextKey.WLIST_KIND_ABROAD
    else -> TextKey.WLIST_KIND_BANK
}

private fun groupOf(kind: String) = when (kind) {
    "cash" -> WalletGroupKind.CASH
    "digital_wallet" -> WalletGroupKind.DIGITAL
    else -> WalletGroupKind.BANKS
}

fun walletRow(w: WalletNow, mainId: Id?): WalletRowView = WalletRowView(
    id = w.wallet.id, name = w.wallet.name, kindLabel = kindLabel(w.wallet.kind), last4 = w.wallet.accountLast4?.let(::sentenceDigits),
    balanceMinor = w.balanceMinor, isCash = w.wallet.kind == "cash", isMain = mainId != null && mainId == w.wallet.id,
    approx = w.wallet.kind == "cash" && w.balanceMinor != null, openingMinor = w.wallet.openingBalanceMinor, openingAt = w.wallet.openingAt,
)

/** القايمة: البنوك · المحافظ الإلكترونية (لو فيه) · الكاش — بترتيب «معك الآن» جوه كل مجموعة. */
fun walletsView(now: WithYouNow, mainId: Id?): WalletsView {
    val rows = now.wallets.map { walletRow(it, mainId) to groupOf(it.wallet.kind) }
    val groups = WalletGroupKind.entries.mapNotNull { g ->
        rows.filter { it.second == g }.map { it.first }.takeIf { it.isNotEmpty() }?.let { WalletGroupView(g, it) }
    }
    return WalletsView(now.totalMinor, now.wallets.size, now.wallets.filter { it.balanceMinor == null }.map { it.wallet.name }, groups)
}

/** سطر تحت «معك الآن»: اللي رصيدها مش معروف بالاسم، وإلا «في N محافظ في هذا البلد». */
fun walletsTotalLine(v: WalletsView): String = when {
    v.count == 0 -> t(TextKey.WLIST_EMPTY_LINE)
    v.unknown.isNotEmpty() -> t(TextKey.WLIST_UNKNOWN_LINE, v.unknown.joinToString(t(TextKey.WLIST_AND)))
    else -> t(TextKey.WLIST_TOTAL_LINE, countText(v.count, WALLET_WORDS))
}

/** حركة في «آخر الحركات»: الاسم · التاريخ · المبلغ بنبرة اتجاهه **على المحفظة دي** (الطرف الداخل لتحويل = وارد بالأزرق). */
data class WalletMove(val id: Id, val title: String, val date: IsoDate, val amountMinor: Halalas, val tone: AmountTone)

/** حركات المحفظة من حركات الشهر (الأحدث الأول، [limit] بالكتير) — تصفية بالمعرّف بس، من غير أي حساب. */
fun walletMoves(data: TransactionsScreenData, walletId: Id, limit: Int = 8): List<WalletMove> = data.transactions
    .filter { it.walletId == walletId || it.transferToWalletId == walletId }
    .sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenByDescending { it.sourceOrder })
    .take(limit)
    .map { tx ->
        val incomingLeg = tx.transferToWalletId == walletId && tx.walletId != walletId
        val tone = when {
            tx.transferToWalletId != null -> AmountTone.TRANSFER
            tx.observedDirection == Direction.IN -> AmountTone.INCOME
            else -> AmountTone.EXPENSE
        }
        val name = data.merchantNamesByTransaction[tx.id]?.firstOrNull()
            ?: tx.rawMerchantName?.takeIf { it.isNotBlank() }
            ?: tx.rawDescription?.takeIf { it.isNotBlank() }
            ?: data.categories.firstOrNull { it.id == tx.categoryId }?.name
            ?: t(TextKey.WDET_MOVE_UNNAMED)
        WalletMove(tx.id, if (incomingLeg) t(TextKey.WDET_MOVE_IN_PREFIX, name) else name, tx.occurredAt, tx.amountMinor, tone)
    }

/** المحفظة اللي في الشاشة (أو null لو اتشالت). */
fun findWallet(now: WithYouNow, id: Id): WalletNow? = now.wallets.firstOrNull { it.wallet.id == id }

/** «الاسم مكرر» في لوحة «محفظة جديدة» — نفس الاسم بعد شيل المسافات الزيادة. */
fun walletNameTaken(name: String, existing: List<Wallet>): Boolean {
    val clean = name.trim().replace(Regex("\\s+"), " ")
    return clean.isNotEmpty() && existing.any { it.name.trim().replace(Regex("\\s+"), " ") == clean }
}

/** آخر ٤ أرقام بس من اللي اتكتب أو اتلصق (رقم حساب كامل أو آيبان) — [cut] = كان أطول واتقص. */
data class Last4(val digits: String, val cut: Boolean)

fun lastFour(typed: String): Last4 {
    val digits = app.masroufy.core.normalizeDigits(typed).filter { it in '0'..'9' }
    return Last4(digits.takeLast(4), digits.length > 4)
}
