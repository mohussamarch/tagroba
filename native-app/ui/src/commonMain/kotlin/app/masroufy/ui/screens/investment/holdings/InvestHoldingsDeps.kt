package app.masroufy.ui.screens.investment.holdings

import app.masroufy.core.IsoDate
import app.masroufy.usecase.AddHolding
import app.masroufy.usecase.PreviewHoldingValue
import app.masroufy.usecase.ProjectHoldingGroup
import app.masroufy.usecase.SetHoldingPurpose

/**
 * «حاجاتي» · «ضيف حاجة» · صفحة النوع (مواصفة r05 §4.2) — **حالات استخدام بس** (CLAUDE.md #4). التنفيذ في `:wiring` (`InvestHoldingsGraph`).
 * الصورة نفسها (`HoldingsSnapshot`) والمعدلات (`InvestRatesState`) بتتحمّل مرة واحدة في الشاشة وبتتبعت للقسم.
 */
interface InvestHoldingsDeps {
    /** `add(حاجة، النهارده)` ⇒ معرّف الأصل الجديد. */
    val add: AddHolding

    /** «قيمته النهارده ≈» للدهب: `metalToday(النوع، العيار، الجرامات، العملة)` — null ⇒ مش ظاهر. */
    val preview: PreviewHoldingValue

    /** تشارت صفحة النوع: `project(البداية، الإضافة الشهرية، المجموعة، المعدلات، السنين)` — null ⇒ مفيش تشارت. */
    val group: ProjectHoldingGroup

    /** «الغرض» في «تفاصيل الأصل». */
    val purpose: SetHoldingPurpose

    fun today(): IsoDate
}
