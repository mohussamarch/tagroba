package app.masroufy.ui.screens.home.cards

import app.masroufy.usecase.LoadDailyAllowance
import app.masroufy.usecase.LoadGoalsProgress
import app.masroufy.usecase.LoadInvestToday
import app.masroufy.usecase.LoadTopCategories
import app.masroufy.usecase.ManageHomeCards

/**
 * كروت الرئيسية (اختيار المالك 2026-10-15): حالات الاستخدام اللي الكروت محتاجاها **بس** — التنفيذ `:wiring` → `HomeCardsGraph`.
 * «القادم» من `HomeDeps.calendar` · «محتاجة مراجعة» و«آخر العمليات» و«شخصيتك» من `HomeDeps.homeScreen` · «ليك وعليك» من `PeopleDeps.overview`.
 */
interface HomeCardsDeps {
    /** الترتيب والمخفي في الحساب: `layout` · `save` («تمام») · `reset` («الترتيب الأصلي»). */
    val layout: ManageHomeCards

    /** «تقدر تصرف النهارده» + شريط الشهر. */
    val daily: LoadDailyAllowance

    /** «أكتر حاجة صرفت فيها» — `of(HomeScreenData)` من القراية الواحدة للكروت. */
    val top: LoadTopCategories

    /** «أهدافك». */
    val goals: LoadGoalsProgress

    /** «استثماراتك النهارده» (مواصفة النقل §1.14): `load(spaceId, today)` — null = «غير متاح» (مفيش يوم قبل كده). */
    val investToday: LoadInvestToday
}
