package app.masroufy.ui.screens.investment.future

import app.masroufy.core.IsoDate
import app.masroufy.usecase.LoadFutureInputs
import app.masroufy.usecase.ManageBirthDate
import app.masroufy.usecase.ManageInvestView
import app.masroufy.usecase.ProjectInvestFuture

/**
 * «هتبقى معاك كام» في «الاستثمار» (حزمة FUTURE — مواصفة النقل §4.1): **حالات استخدام بس** (CLAUDE.md #4) — التنفيذ في
 * `wiring/InvestFutureGraph.kt`.
 */
interface InvestFutureDeps {
    /** `load(today, holdings, rates)` · `otherSpaces(today)` (فاضية ⇒ «في البلدين» مستخبي) · `fx()` (null ⇒ المجموع «غير متاح»). */
    val inputs: LoadFutureInputs

    /** `project(inputs, choice)` (null = المتوسطات مش متاحة) · `countries(mine, others, choice, fx)`. */
    val project: ProjectInvestFuture

    /** `load(spaceId)` · `save(spaceId, choice)` · `addMark` · `removeMark` — العلامات والطريق والمبلغ والخلطة لكل بلد. */
    val view: ManageInvestView

    /** تاريخ الميلاد (من حاسبة التقاعد) ⇒ «عمرك وقتها». */
    val birth: ManageBirthDate

    fun today(): IsoDate
}
