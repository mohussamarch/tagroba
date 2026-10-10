package app.masroufy.ui.screens.imports

import app.masroufy.core.BalanceColumnCheck
import app.masroufy.core.Currency
import app.masroufy.core.MappedBalanceRow
import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import app.masroufy.core.checkBalanceColumn
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.text.t

/** معنى عمود في ملف CSV (اختيار المالك — مفيش تخمين للأعمدة المالية، spec/05). */
enum class ColumnRole(val label: TextRef) {
    DATE(UiKey.STATEMENT_COLUMNS_ROLE_DATE),
    DESC(UiKey.STATEMENT_COLUMNS_ROLE_DESC),
    DEBIT(UiKey.STATEMENT_COLUMNS_ROLE_DEBIT),
    CREDIT(UiKey.STATEMENT_COLUMNS_ROLE_CREDIT),
    SIGNED(UiKey.STATEMENT_COLUMNS_ROLE_SIGNED),
    BALANCE(UiKey.STATEMENT_COLUMNS_ROLE_BALANCE),
    REF(UiKey.STATEMENT_COLUMNS_ROLE_REF),
    SKIP(UiKey.STATEMENT_COLUMNS_ROLE_SKIP),
}

/** معنى لعمود: المعنى الواحد (غير «تجاهل») ما يتكررش — لو كان على عمود تاني بيتشال من هناك (زي النموذج). */
fun assignRole(roles: List<ColumnRole?>, column: Int, role: ColumnRole): List<ColumnRole?> =
    roles.mapIndexed { i, r ->
        when {
            i == column -> role
            role != ColumnRole.SKIP && r == role -> null
            else -> r
        }
    }

/** العمود الجاي اللي لسه مالوش معنى (أو نفس العمود لو كله اتحدد). */
fun nextUnset(roles: List<ColumnRole?>, current: Int): Int = roles.indexOfFirst { it == null }.takeIf { it >= 0 } ?: current

/** «العمود 3» (الترقيم من 1). */
fun columnWord(index: Int): String = t(UiKey.STATEMENT_COLUMNS_WORD, sentenceNumber(index + 1))

/**
 * سطور الفحص (التاريخ · الوصف · المبلغ · الرصيد) بالأعمدة اللي المالك اختارها — **عرض الاختيار بس**؛ فحص القيم نفسها (تواريخ؟ أرقام؟ الرصيد
 * بيتسلسل؟) محتاج حالة استخدام التعيين اليدوي.
 */
fun columnChecks(roles: List<ColumnRole?>): List<Pair<TextRef, String>> {
    fun cols(vararg rs: ColumnRole) = roles.withIndex().filter { it.value in rs }.joinToString(t(UiKey.IMPORTS_SEP)) { columnWord(it.index) }
    return listOf(
        UiKey.STATEMENT_COLUMNS_CHECK_DATE to cols(ColumnRole.DATE),
        UiKey.STATEMENT_COLUMNS_CHECK_DESC to cols(ColumnRole.DESC),
        UiKey.STATEMENT_COLUMNS_CHECK_AMOUNT to cols(ColumnRole.DEBIT, ColumnRole.CREDIT, ColumnRole.SIGNED),
        UiKey.STATEMENT_COLUMNS_CHECK_BALANCE to cols(ColumnRole.BALANCE),
    )
}

/** حالة عمود الرصيد على الشاشة: [blocks] = بيوقف (قيم مش أرقام)، وإلا تنبيه أو تمام. */
data class BalanceNote(val text: String, val tone: BalanceTone) {
    val blocks: Boolean get() = tone == BalanceTone.BLOCK
}

enum class BalanceTone { OK, WARN, BLOCK }

/**
 * فحص عمود الرصيد بالأعمدة المختارة (رد المالك L3): الحساب كله في `core` ([checkBalanceColumn] — هللات صحيحة)، هنا ترتيب الخانات والنص بس.
 * مش متسلسل ⇒ «تنبيه: الرصيد مش متسلسل» والاستيراد بيكمل · مش أرقام ⇒ بيوقف · من غير عمود رصيد أو مبلغ ⇒ ولا حاجة (null).
 */
fun balanceNote(rows: List<List<String>>, roles: List<ColumnRole?>, currency: Currency): BalanceNote? {
    fun col(role: ColumnRole) = roles.indexOf(role).takeIf { it >= 0 }
    val balance = col(ColumnRole.BALANCE) ?: return null
    fun cell(r: List<String>, i: Int?) = i?.let { r.getOrElse(it) { "" } }
    val mapped = rows.map { r -> MappedBalanceRow(r.getOrElse(balance) { "" }, cell(r, col(ColumnRole.DEBIT)), cell(r, col(ColumnRole.CREDIT)), cell(r, col(ColumnRole.SIGNED))) }
    fun lines(rs: List<Int>) = rs.take(5).joinToString(t(UiKey.IMPORTS_SEP)) { sentenceNumber(it) } + if (rs.size > 5) "…" else ""
    return when (val check = checkBalanceColumn(mapped, currency)) {
        BalanceColumnCheck.Unchecked -> null
        BalanceColumnCheck.Chained -> BalanceNote(t(UiKey.STATEMENT_COLUMNS_BALANCE_CHAINED), BalanceTone.OK)
        is BalanceColumnCheck.NotNumbers -> BalanceNote(t(UiKey.STATEMENT_COLUMNS_BALANCE_NOT_NUMBERS, lines(check.rows)), BalanceTone.BLOCK)
        is BalanceColumnCheck.NotChained -> BalanceNote(t(UiKey.STATEMENT_COLUMNS_BALANCE_NOT_CHAINED, lines(check.rows)), BalanceTone.WARN)
    }
}
