package app.masroufy.ui.screens.operations

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Texts
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.theme.Ink
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * خانة «العمليات» (`Operations`): من نتيجة `LoadTransactionsScreen` للحالة اللي بتترسم — الأرقام زي ما هي (مفيش جمع هنا)، و`null` ⇒ «غير متاح»،
 * الأيام بالأحدث، الشرايط بس لما فيه حاجة مستنياك، والخطأ بيسيب القايمة القديمة. بالفصحى (السعودية) والمصري (مصر).
 */
class OperationsModelTest {
    @AfterTest fun reset() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private fun view(vararg txs: app.masroufy.core.Transaction, income: Long? = 1_250_000, expense: Long? = 665_000, estimated: Int = 0, review: Int = 0) =
        operationsView(Fx.screen(txs.toList(), income, expense, estimated, review), listOf(Fx.bank, Fx.cash), Fx.TODAY, Currency.SAR)

    @Test fun totalsComeFromTheUseCaseAsIsAndUnknownStaysNull() {
        val v = view(Fx.tx("t1"))
        assertEquals(1_250_000L, v.incomeMinor)
        assertEquals(665_000L, v.expenseMinor)
        assertEquals("أكتوبر", v.periodLabel, "الشهر المالي ٢٨ سبتمبر–٢٧ أكتوبر اسمه الشهر اللي بيخلص فيه")
        assertFalse(v.approx)
        val unknown = view(Fx.tx("t1"), income = null, expense = null)
        assertNull(unknown.incomeMinor, "غير متاح مش صفر")
        assertNull(unknown.expenseMinor)
        assertTrue(view(Fx.tx("t1"), estimated = 2).approx, "نوع تقديري ⇒ «تقريبي»")
    }

    @Test fun daysAreNewestFirstWithTodayAndYesterday() {
        val v = view(Fx.tx("old", date = "2026-10-01"), Fx.tx("today", date = Fx.TODAY), Fx.tx("y", date = "2026-10-08"))
        assertEquals(listOf("اليوم", "أمس", "١ أكتوبر"), v.days.map { it.label })
        assertEquals(listOf("today"), v.days.first().rows.map { it.id })
        assertTrue(view().empty, "مفيش عمليات ⇒ الحالة الفاضية")
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("النهارده", dayLabel(Fx.TODAY, Fx.TODAY), "مصر بالمصري")
    }

    @Test fun rowShowsMerchantCategoryAndToneWithoutComputing() {
        val ctx = RowContext(listOf(Fx.food), mapOf("t1" to listOf("مطعم الريف المحفوظ", "اسم بديل")), listOf(Fx.bank, Fx.cash))
        val row = opRow(Fx.tx("t1", amount = 4_250), ctx)
        assertEquals("مطعم الريف المحفوظ", row.title, "اسم التاجر المحفوظ أولًا")
        assertEquals("مطاعم وقهوة", row.subtitle)
        assertEquals(4_250L, row.amountMinor)
        assertEquals(AmountTone.EXPENSE, row.tone)
        assertEquals(Lucide.UTENSILS, row.icon)

        val income = opRow(Fx.tx("in", direction = Direction.IN, kind = EconomicKind.SALARY, category = null, merchant = null), ctx)
        assertEquals(AmountTone.INCOME, income.tone)
        assertEquals(Ink.income, income.color)
        assertEquals("راتب", income.title, "من غير تاجر ولا وصف ⇒ اسم النوع")

        val move = opRow(Fx.tx("mv", kind = EconomicKind.INTERNAL_TRANSFER, category = null, merchant = null, toWallet = Fx.cash.id), ctx)
        assertEquals(AmountTone.TRANSFER, move.tone, "بين محافظك أزرق من غير علامة")
        assertEquals("تحويل داخلي، حساب الراتب ← الكاش", move.subtitle)

        val unknown = opRow(Fx.tx("u", kind = EconomicKind.UNCLASSIFIED, category = null, merchant = "محل"), ctx)
        assertEquals("بلا تصنيف", unknown.subtitle)
        assertFalse(unknown.unrecorded, "فرق عدّ الكاش مالوش منطق لسه ⇒ مفيش نقطة حمرا مخترعة")
    }

    @Test fun bannersShowOnlyWhenSomethingWaits() {
        assertTrue(banners(bankSmsWaiting = null, reviewCount = 0, partiesWaiting = 0).isEmpty())
        val all = banners(bankSmsWaiting = 4, reviewCount = 8, partiesWaiting = 2)
        assertEquals(listOf(BannerKind.BANK_SMS, BannerKind.REVIEW, BannerKind.TRANSFERS), all.map { it.kind })
        assertEquals(listOf("٤ رسائل من البنك بانتظار تأكيدك", "٨ عمليات نوعها غير مؤكد", "طرفان بانتظار ردك"), all.map { it.text })
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals(listOf("٨ عمليات نوعها مش أكيد", "طرفين مستنيين ردك"), banners(null, 8, 2).map { it.text })
    }

    @Test fun arabicCountWordsFollowTheNumber() {
        assertEquals(listOf("عملية واحدة", "عمليتان", "٥ عمليات", "١٢ عملية"), listOf(1, 2, 5, 12).map(::operationsCount))
    }

    @Test fun aFailedReloadKeepsTheLastListUnderTheBanner() {
        val first = OpsUi()
        assertTrue(first.skeleton, "أول تحميل ⇒ الهيكل الرمادي")
        val ready = first.loaded(view(Fx.tx("t1")), parties = 2)
        assertFalse(ready.skeleton)
        assertEquals(2, ready.partiesWaiting)
        val failed = ready.retrying().loaded(null, parties = null)
        assertTrue(failed.failed)
        assertEquals(ready.view, failed.view, "القايمة اللي اتحمّلت بتفضل تحت شريط الخطأ")
        assertEquals(2, failed.partiesWaiting)
        val nothing = OpsUi().loaded(null, null)
        assertTrue(nothing.failed)
        assertFalse(nothing.skeleton, "الخطأ من غير بيانات ⇒ الشريط بس")
        assertEquals(0, ready.loaded(view(Fx.tx("t1")), parties = 0).partiesWaiting)
    }
}
