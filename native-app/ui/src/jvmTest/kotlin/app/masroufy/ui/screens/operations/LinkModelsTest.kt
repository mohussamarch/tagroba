package app.masroufy.ui.screens.operations

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.EventLink
import app.masroufy.core.EventRole
import app.masroufy.core.EventSummary
import app.masroufy.core.LifeEvent
import app.masroufy.core.LifeEventKind
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.PersonBalance
import app.masroufy.core.Texts
import app.masroufy.ui.components.AmountTone
import app.masroufy.usecase.EventDetail
import app.masroufy.usecase.EventLinkedTransaction
import app.masroufy.usecase.PersonObligationRow
import app.masroufy.usecase.PersonRow
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «اربطها بـ» في التفاصيل: `LinkPersonSheet` (الأشخاص بأرصدتهم من `ManagePeople.listWithBalances` · الربط القديم من التزامات العملية ·
 * الأثر **من غير حساب**) و`LinkProjectEventSheet` (الحدث المربوط ونصيبه **من حالة الاستخدام** · النسبة 1–100 · فك وربط).
 */
class LinkModelsTest {
    @AfterTest fun reset() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val khaled = Person("p-k", "خالد")
    private val omar = Person("p-o", "عمر")
    private val old = Person("p-x", "قديم", archived = true)

    private fun row(p: Person, receivable: Long = 0, loan: Long = 0, obligations: List<PersonObligationRow> = emptyList()) =
        PersonRow(p, PersonBalance(p.id, receivable, loan, 0), obligations)

    @Test fun existingDebtsFromThisOperationShowOnTheCard() {
        val fromThis = PersonObligationRow(Obligation("o1", khaled.id, "t1", ObligationKind.RECEIVABLE, 2_000, Currency.SAR), 2_000)
        val other = PersonObligationRow(Obligation("o2", khaled.id, "t9", ObligationKind.RECEIVABLE, 5_000, Currency.SAR), 5_000)
        val lines = existingPersonLinks(listOf(row(khaled, 7_000, obligations = listOf(fromThis, other))), "t1")
        assertEquals(listOf("خالد — دين عليه"), lines.map { it.label })
        assertEquals(2_000L, lines.single().amountMinor, "المبلغ الأصلي من الالتزام زي ما هو")
        assertEquals(AmountTone.INCOME, lines.single().tone)
    }

    @Test fun peopleSearchAndAddingANewName() {
        val people = listOf(khaled, omar, old)
        assertEquals(listOf(khaled, omar), personChoices(people, "", null), "المؤرشف ما بيظهرش")
        assertEquals(listOf(omar), personChoices(people, "عم", null))
        assertEquals(listOf(khaled, omar), personChoices(people, "عم", khaled), "المختار دايمًا ظاهر")
        assertTrue(canAddPerson(people, "سارة"))
        assertFalse(canAddPerson(people, " خالد "), "نفس الاسم موجود")
        assertFalse(canAddPerson(people, ""))
    }

    @Test fun balanceLineAndEffectComeWithoutArithmetic() {
        assertEquals("لك عنده 500.00 ر.س", personBalanceLine(row(khaled, receivable = 50_000), Currency.SAR))
        assertEquals("عليك له 2,000.00 ر.س", personBalanceLine(row(omar, loan = 200_000), Currency.SAR))
        assertEquals("لا رصيد بينكما", personBalanceLine(null, Currency.SAR))
        assertEquals(listOf("اختر الشخص والنوع"), personEffect(null, null, null, Currency.SAR).map { it.label })
        assertEquals(listOf("اختر النوع"), personEffect(null, "خالد", 2_000, Currency.SAR).map { it.label })
        assertEquals(listOf("اكتب المبلغ"), personEffect(PersonLinkKind.GIFT, "خالد", null, Currency.SAR).map { it.label })
        val debt = personEffect(PersonLinkKind.RECEIVABLE, "خالد", 2_000, Currency.SAR)
        assertEquals(listOf("مصروفك من العملية", "لك عند خالد"), debt.map { it.label })
        assertEquals(listOf("−20.00 ر.س", "+20.00 ر.س"), debt.map { it.value }, "الفرق نفسه = المبلغ اللي كتبته (مفيش قبل ← بعد محسوب في الشاشة)")
        assertEquals(listOf("بلا تغيير", "لا شيء"), personEffect(PersonLinkKind.GIFT, "خالد", 2_000, Currency.SAR).map { it.value })
        assertEquals("اختر الشخص أو اكتب اسمًا جديدًا.", personLinkError(false, null, null))
        assertEquals("اختر: دين عليه أم هدية له.", personLinkError(true, null, 100))
        assertEquals("اكتب مبلغًا أكبر من صفر.", personLinkError(true, PersonLinkKind.GIFT, 0))
        assertNull(personLinkError(true, PersonLinkKind.GIFT, 100))
    }

    private fun event(id: String, name: String, archived: Boolean = false, links: List<EventLinkedTransaction> = emptyList(), host: String? = null) = EventDetail(
        LifeEvent(id, name, name, LifeEventKind.WEDDING, "2026-10-18", mine = host == null, archived = archived, createdAt = "x"),
        EventSummary(emptyList(), 0, null, 0), host, links,
    )

    @Test fun linkedEventCarriesTheShareFromTheUseCase() {
        val linked = EventLinkedTransaction(EventLink("l1", "e1", "t1", EventRole.SPEND, createdAt = "x", sharePercent = 50), Fx.tx("t1", amount = 90_001), null, shareMinor = 45_001)
        val details = listOf(event("e0", "ولادة جود"), event("e1", "زواج خالد", links = listOf(linked), host = "خالد"), event("e2", "قديم", archived = true))
        val now = eventLinkOf(details, "t1")!!
        assertEquals(50, now.sharePercent)
        assertEquals(45_001L, now.shareMinor, "النصيب بالهللة من `EventLinkedTransaction.shareMinor` — مش محسوب هنا")
        assertNull(eventLinkOf(details, "t2"))
        assertEquals(listOf("ولادة جود", "زواج خالد"), eventChoices(details).map { it.name }, "المؤرشف ما بيظهرش في الاختيار")
        assertEquals("18 أكتوبر، حدث خالد", eventChoices(details)[1].sub)
        assertEquals("18 أكتوبر، حدثك", eventChoices(details)[0].sub)
    }

    @Test fun shareIsAWholePercentAndSavingIsUnlinkThenLink() {
        assertEquals(50, parsePercent("50"))
        assertNull(parsePercent("abc"))
        assertEquals(75 to 25, sharePercents(75))
        assertNull(sharePercents(0))
        assertNull(sharePercents(101))
        assertEquals("50%", percentText(50))
        assertEquals("نسبة الحدث من العملية يجب أن تكون من 1 إلى 100.", projectEventError(false, true, 0))
        assertEquals("فكّ الربط القديم أولًا، أو اختر الحدث نفسه.", projectEventError(true, true, 50))
        assertNull(projectEventError(false, false, null))

        val now = EventLinkNow("e1", "زواج خالد", 100, 90_000)
        assertEquals(EventPlan(null, "e1", 100), eventPlan(null, "e1", unlinked = false, percent = 100, outgoing = true), "ربط جديد")
        assertEquals(EventPlan(null, null, 100), eventPlan(now, "e1", unlinked = false, percent = 100, outgoing = true), "مفيش تغيير ⇒ ولا كتابة")
        assertEquals(EventPlan("e1", "e1", 50), eventPlan(now, "e1", unlinked = false, percent = 50, outgoing = true), "تغيير النسبة = فك وربط")
        assertEquals(EventPlan("e1", "e2", 100), eventPlan(now, "e2", unlinked = true, percent = 100, outgoing = true), "حدث تاني بعد «فك الربط»")
        assertEquals(EventPlan("e1", null, 100), eventPlan(now, null, unlinked = true, percent = 100, outgoing = true), "«بلا حدث»")
        assertEquals(EventPlan(null, null, 100), eventPlan(now, "e2", unlinked = true, percent = 100, outgoing = false), "الوارد بيتربط نقطة من صفحة الحدث")
    }
}
