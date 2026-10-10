package app.masroufy.ui.screens.imports

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import app.masroufy.core.Category
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.text.t

/**
 * العدّ في الجمل بقواعد العربي (عملية واحدة · عمليتان · ٣ عمليات · ١٢ عملية) — زي `plural` في النموذج. الأرقام شرقية في العربي
 * (`sentenceNumber`). النص نفسه من خرايط النصوص (الفصحى · المصري · الإنجليزي).
 */
fun countText(n: Int, one: TextRef, two: TextRef, few: TextRef, many: TextRef): String = when (n) {
    1 -> t(one)
    2 -> t(two)
    in 3..10 -> t(few, sentenceNumber(n))
    else -> t(many, sentenceNumber(n))
}

fun opsCount(n: Int) = countText(n, UiKey.IMPORTS_OPS_ONE, UiKey.IMPORTS_OPS_TWO, UiKey.IMPORTS_OPS_FEW, UiKey.IMPORTS_OPS_MANY)

fun msgsCount(n: Int) = countText(n, UiKey.IMPORTS_MSGS_ONE, UiKey.IMPORTS_MSGS_TWO, UiKey.IMPORTS_MSGS_FEW, UiKey.IMPORTS_MSGS_MANY)

fun linesCount(n: Int) = countText(n, UiKey.IMPORTS_LINES_ONE, UiKey.IMPORTS_LINES_TWO, UiKey.IMPORTS_LINES_FEW, UiKey.IMPORTS_LINES_MANY)

fun pagesCount(n: Int) = countText(n, UiKey.IMPORTS_PAGES_ONE, UiKey.IMPORTS_PAGES_TWO, UiKey.IMPORTS_PAGES_FEW, UiKey.IMPORTS_PAGES_MANY)

/** اسم التصنيف للعرض — مفيش تصنيف أو اتمسح ⇒ «غير مصنّف». */
fun categoryName(id: String?, categories: List<Category>): String =
    categories.firstOrNull { it.id == id }?.name ?: t(UiKey.IMPORTS_UNCATEGORIZED)
