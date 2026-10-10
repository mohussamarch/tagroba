package app.masroufy.ui.screens.imports

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
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

/** «العمود ٣» (الترقيم من ١). */
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
