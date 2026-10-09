package app.masroufy.ui.screens.operations

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Merchant
import app.masroufy.core.ReviewState
import app.masroufy.core.Tag
import app.masroufy.core.Texts
import app.masroufy.core.transferPartyOf
import app.masroufy.ui.components.AmountTone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * تفاصيل العملية (`OperationDetail` + `CategoryPicker` + `TagsSheet` + `TransferParty`): من نتيجة `EditTransaction.load` والتصنيفات والتجار والزون
 * للكارت والحقول — مقترح ≠ مؤكد، «بانتظار تحديد النوع»، سؤال «من هذا؟» للحوالة الداخلة من طرف لسه ما اتقررش بس، والربط حسب نوع العملية.
 */
class OperationDetailModelTest {
    @AfterTest fun reset() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val incomingSms = "حوالة محلية واردة\nمن:TEST PERSON\nبـSR 1000\nإلى:1111\n26/9/18"

    private fun detail(tx: app.masroufy.core.Transaction, merchants: List<Merchant> = emptyList(), decided: Set<String> = emptySet()) =
        detailView(tx, listOf(Fx.food, Fx.shop), listOf(Fx.bank, Fx.cash), merchants, decided, Fx.TODAY)

    @Test fun purchaseShowsMerchantWalletAndSuggestedOrConfirmedCategory() {
        val saved = Merchant("m1", "مطعم الريف", "مطعم الريف", aliases = listOf("الريف للمأكولات"))
        val suggested = detail(Fx.tx("t1", review = ReviewState.SUGGESTED, confirmedCategory = false, merchant = "الريف للمأكولات"), listOf(saved))
        assertEquals("مطعم الريف", suggested.title, "الاسم البديل بيوصل للتاجر المحفوظ")
        assertEquals("مطعم الريف", suggested.merchant?.displayName)
        assertEquals("الريف للمأكولات", suggested.merchantName, "صفحة التاجر بالاسم زي ما جه")
        assertEquals(CategoryStatus.SUGGESTED, suggested.category?.status)
        assertEquals("مطاعم وقهوة", suggested.category?.name)
        assertEquals(LinkFlow.PURCHASE, suggested.flow)
        assertEquals("حساب الراتب", suggested.fields.first().value)
        assertEquals("اليوم، مدين", suggested.sub)
        assertEquals(AmountTone.EXPENSE, suggested.tone)
        assertEquals(4_200L, suggested.amountMinor, "المبلغ زي ما هو من العملية")
        assertNull(suggested.askParty)

        assertEquals(CategoryStatus.CONFIRMED, detail(Fx.tx("t2")).category?.status)
        assertEquals(CategoryStatus.NONE, detail(Fx.tx("t3", category = null)).category?.status)
        assertFalse(detail(Fx.tx("t4")).unrecorded, "مفيش منطق عدّ الكاش ⇒ مفيش «غير مسجّلة» مخترعة")
    }

    @Test fun kindFieldSaysWaitingOrEstimated() {
        val waiting = detail(Fx.tx("t1", kind = EconomicKind.UNCLASSIFIED, confirmedKind = false))
        val kind = waiting.fields.last()
        assertEquals("بانتظار تحديد النوع", kind.value)
        assertEquals("بانتظارك", kind.chip?.text)
        val estimated = detail(Fx.tx("t2", confirmedKind = false)).fields.last()
        assertEquals("تقديري حتى تؤكده", estimated.hint)
        assertNull(detail(Fx.tx("t3")).fields.last().hint)
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("مستني تحدد النوع", detail(Fx.tx("t4", kind = EconomicKind.UNCLASSIFIED, confirmedKind = false)).fields.last().value)
    }

    @Test fun incomingTransferFromAnUndecidedPartyAsksWhoItIs() {
        val tx = Fx.tx("in", direction = Direction.IN, kind = EconomicKind.UNCLASSIFIED, confirmedKind = false, category = null, merchant = null, description = incomingSms)
        val party = assertNotNull(transferPartyOf(tx))
        val v = detail(tx)
        assertEquals(LinkFlow.TRANSFER_IN, v.flow)
        assertEquals(party, v.askParty)
        assertNull(v.merchantName, "الحوالة مالهاش صفحة تاجر")
        assertNull(detail(tx, decided = setOf(party.key)).askParty, "الطرف اللي ليه قرار ما بيتسألش تاني")
        assertNull(detail(tx.copy(economicKind = EconomicKind.GIFT_RECEIVED, economicKindConfirmed = true)).askParty, "النوع اتأكد ⇒ مفيش سؤال")
        val move = detail(Fx.tx("mv", kind = EconomicKind.INTERNAL_TRANSFER, category = null, toWallet = Fx.cash.id))
        assertEquals(LinkFlow.NONE, move.flow, "بين محافظك ما بتتربطش")
        assertNull(move.category, "التحويل الداخلي مالوش تصنيف")
    }

    @Test fun manualCategoryToastSaysItIsRememberedOnlyWhenTheMerchantIsSaved() {
        assertEquals("تم — «مطعم الريف» سيُصنَّف «مطاعم وقهوة» دائمًا", categoryToast(true, "مطعم الريف", "مطاعم وقهوة"))
        assertEquals("تم — صُنّفت «مطاعم وقهوة»", categoryToast(false, "مطعم الريف", "مطاعم وقهوة"))
    }

    @Test fun pickerShowsActiveMainCategoriesAndKeepsTheCurrentOne() {
        val all = listOf(Fx.food, Fx.shop, Fx.hidden, Fx.child)
        assertEquals(listOf("c-food", "c-shop"), pickerChoices(all, spendOnly = true, currentId = null).map { it.id }, "المخفي والفرعي ما بيظهروش")
        assertEquals(listOf("c-old", "c-food", "c-shop"), pickerChoices(all, spendOnly = true, currentId = "c-old").map { it.id }, "التصنيف القديم المخفي بيبان باسمه")
    }

    @Test fun tagsSheetFiltersByNormalizedNameAndSaysWhatHappened() {
        val gift = Tag("g", "هديه", "هدية")
        val trip = Tag("s", "سفر", "سفر")
        assertEquals(listOf(trip), offTags(listOf(gift, trip), on = listOf(gift), query = ""))
        assertEquals(listOf(gift), offTags(listOf(gift, trip), on = emptyList(), query = " هدية "), "ة ⇒ ه")
        assertTrue(offTags(listOf(gift), on = listOf(gift), query = "").isEmpty())
        assertEquals("«هدية» موجود على العملية", tagAddedStatus("هدية", wasOnTxn = true, existed = true))
        assertEquals("أُضيف «هدية»", tagAddedStatus("هدية", wasOnTxn = false, existed = true))
        assertEquals("أُنشئ وسم جديد «هدية» وأُضيف", tagAddedStatus("هدية", wasOnTxn = false, existed = false))
    }

    @Test fun storageFailuresAreShownAsNothingChanged() {
        assertEquals("الاسم مطلوب", userMessage(IllegalStateException("الاسم مطلوب")), "رفض حالة الاستخدام بيتعرض زي ما هو")
        assertEquals("تعذّر الحفظ — لم يتغير شيء. حاول مرة أخرى.", userMessage(RuntimeException("FirebaseFirestoreException: UNAVAILABLE")))
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("ما اتحفظش — ما اتغيرش حاجة. جرّب تاني.", userMessage(IllegalStateException()))
    }
}
