package app.masroufy.ui.screens.operations

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import androidx.compose.ui.graphics.Color
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Liquidity
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.dayMonth
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.groupByDay
import app.masroufy.core.parseIsoDate
import app.masroufy.core.ruleFor
import app.masroufy.core.sentenceNumber
import app.masroufy.core.toDayNumber
import app.masroufy.core.transferPartyOf
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink

/**
 * صف عملية جاهز للعرض (مشترك بين العمليات والتصفية وصفحة التاجر) — **مفيش حساب فلوس هنا**: المبلغ زي ما هو من حالة الاستخدام بالهللة،
 * والاسم والسطر التاني واللون والأيقونة من العملية وتصنيفها ونوعها.
 * [unrecorded] = النقطة الحمرا «غير مسجّلة» (فرق عدّ الكاش — §73): ⚠️ مفيش منطق عدّ الكاش في كوتلن لسه ⇒ دايمًا `false` لحد ما يتبني.
 */
data class OpRow(
    val id: Id,
    val title: String,
    val subtitle: String,
    val icon: Lucide,
    val color: Color,
    val amountMinor: Halalas,
    val currency: Currency,
    val tone: AmountTone,
    val unrecorded: Boolean = false,
)

/** مجموعة يوم: «اليوم» · «أمس» · «٧ أكتوبر». */
data class OpDay(val date: IsoDate, val label: String, val rows: List<OpRow>)

/** اللي الصف محتاجه من برّه العملية: التصنيفات · أسماء التجار · المحافظ. */
class RowContext(
    categories: List<Category>,
    private val merchantNames: Map<Id, List<String>> = emptyMap(),
    wallets: List<Wallet> = emptyList(),
) {
    private val categoryById = categories.associateBy { it.id }
    private val walletById = wallets.associateBy { it.id }

    fun category(id: Id?): Category? = id?.let(categoryById::get)

    fun wallet(id: Id?): Wallet? = id?.let(walletById::get)

    fun merchant(id: Id): String? = merchantNames[id]?.firstOrNull()
}

/** نبرة المبلغ: التحويل الداخلي أزرق من غير علامة · الداخل أخضر «+» · الطالع أحمر «−» (DESIGN-SYSTEM). */
fun toneOf(tx: Transaction): AmountTone = when {
    ruleFor(tx.economicKind).liquidity == Liquidity.INTERNAL -> AmountTone.TRANSFER
    tx.observedDirection == Direction.IN -> AmountTone.INCOME
    else -> AmountTone.EXPENSE
}

/** اسم العملية: التاجر المعروف ⇒ الاسم من المصدر ⇒ ملاحظتك ⇒ الوصف ⇒ اسم النوع. */
fun titleOf(tx: Transaction, ctx: RowContext): String =
    ctx.merchant(tx.id) ?: tx.rawMerchantName?.takeIf { it.isNotBlank() } ?: tx.note?.takeIf { it.isNotBlank() }
        ?: tx.rawDescription?.trim()?.takeIf { it.isNotEmpty() }?.take(60) ?: ruleFor(tx.economicKind).label

/** السطر التاني: التصنيف · أو النوع · أو الطرف · أو «بلا تصنيف». التحويل الداخلي: «تحويل داخلي، البنك ← الكاش». */
fun subtitleOf(tx: Transaction, ctx: RowContext): String {
    val kind = ruleFor(tx.economicKind)
    if (kind.liquidity == Liquidity.INTERNAL) {
        val from = ctx.wallet(tx.walletId)?.name
        val to = ctx.wallet(tx.transferToWalletId)?.name
        return if (from != null && to != null) t(UiKey.OPERATIONS_ROW_MOVE, kind.label, from, to) else kind.label
    }
    ctx.category(tx.categoryId)?.let { return it.name }
    transferPartyOf(tx)?.let { party ->
        val who = party.label.ifBlank { party.last4?.let { t(UiKey.OPERATIONS_ROW_ACCOUNT, sentenceDigitsOf(it)) }.orEmpty() }
        if (who.isNotBlank()) return t(if (tx.observedDirection == Direction.IN) UiKey.OPERATIONS_ROW_FROM else UiKey.OPERATIONS_ROW_TO, who)
    }
    if (tx.economicKind != EconomicKind.UNCLASSIFIED) return kind.label
    return t(UiKey.OPERATIONS_ROW_UNCLASSIFIED)
}

private fun sentenceDigitsOf(digits: String): String = app.masroufy.core.sentenceDigits(digits)

/** أيقونة الصف: رمز التصنيف، وإلا بالنوع (تحويل · دخل · كاش)، وإلا وسم. */
fun iconOf(tx: Transaction, ctx: RowContext): Lucide {
    ctx.category(tx.categoryId)?.let { return categoryIcon(it.iconKey) }
    return when {
        ruleFor(tx.economicKind).liquidity == Liquidity.INTERNAL -> OperationsIcons.MOVE
        tx.observedDirection == Direction.IN -> OperationsIcons.INCOME
        tx.isCashTagged || ctx.wallet(tx.walletId)?.kind == "cash" -> Lucide.BANKNOTE
        else -> Lucide.TAG
    }
}

/** لون الأيقونة: لون التصنيف، وإلا بالنوع. **المبلغ ما بيورثش اللون ده.** */
fun colorOf(tx: Transaction, ctx: RowContext): Color {
    val fallback = when {
        ruleFor(tx.economicKind).liquidity == Liquidity.INTERNAL -> Ink.transfer
        tx.observedDirection == Direction.IN -> Ink.income
        else -> Ink.muted
    }
    return categoryColor(ctx.category(tx.categoryId), fallback)
}

fun opRow(tx: Transaction, ctx: RowContext): OpRow =
    OpRow(tx.id, titleOf(tx, ctx), subtitleOf(tx, ctx), iconOf(tx, ctx), colorOf(tx, ctx), tx.amountMinor, tx.currency, toneOf(tx))

/** «اليوم» · «أمس» · «٧ أكتوبر» — [today] من الجهاز (`ShellDeps.today`). */
fun dayLabel(date: IsoDate, today: IsoDate): String {
    val yesterday = dayNumberToIso(toDayNumber(parseIsoDate(today)) - 1)
    return when (date) {
        today -> t(UiKey.OPERATIONS_TODAY)
        yesterday -> t(UiKey.OPERATIONS_YESTERDAY)
        else -> dayMonth(date)
    }
}

/** العمليات متجمعة بالأيام (الأحدث الأول — `groupByDay` من `core`). */
fun dayGroups(transactions: List<Transaction>, ctx: RowContext, today: IsoDate): List<OpDay> =
    groupByDay(transactions).map { g -> OpDay(g.date, dayLabel(g.date, today), g.transactions.map { opRow(it, ctx) }) }

/** العدد في جملة بقاعدة العربي: واحدة · اتنين · ٣–١٠ جمع · ١١+ مفرد. [few]/[many] فيهم `{0}`. */
fun countText(n: Int, one: TextRef, two: TextRef, few: TextRef, many: TextRef): String = when {
    n == 1 -> t(one)
    n == 2 -> t(two)
    n in 3..10 -> t(few, sentenceNumber(n))
    else -> t(many, sentenceNumber(n))
}

/** «عملية واحدة» · «عمليتان» · «٥ عمليات» · «١٢ عملية». */
fun operationsCount(n: Int): String =
    countText(n, UiKey.OPERATIONS_COUNT_ONE, UiKey.OPERATIONS_COUNT_TWO, UiKey.OPERATIONS_COUNT_FEW, UiKey.OPERATIONS_COUNT_MANY)
