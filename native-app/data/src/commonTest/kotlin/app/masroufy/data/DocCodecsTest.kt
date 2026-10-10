package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.CycleUnit
import app.masroufy.core.DebtTerms
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPayment
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.Project
import app.masroufy.core.ProjectKind
import app.masroufy.core.Rosca
import app.masroufy.core.RoscaEntry
import app.masroufy.core.RoscaEntryKind
import app.masroufy.core.RoscaMember
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * اللي ملف المرجع ما بيغطيهوش: حروف في المعرّفات مش في العينات، وقص الأرقام العربية، ومجموعات «المستحقات»
 * (جديدة في كوتلن بس — مالهاش شكل قديم)، ورسايل المستند الغلط. الأرقام وهمية.
 */
class DocCodecsTest {
    @Test
    fun `معرّف إيصال التنبيه زي encodeURIComponent والنقطة متشفّرة`() {
        assertEquals("a%2Eb%7Cc%3Ad", receiptDocId("a.b|c:d"))
        assertEquals("%D8%AC%D9%85%D8%B9%D9%8A%D8%A9", jsEncodeUriComponent("جمعية"))
        assertEquals("AZaz09-_.!~*'()", jsEncodeUriComponent("AZaz09-_.!~*'()"))
        assertEquals("%20%2F%25", jsEncodeUriComponent(" /%"))
    }

    @Test
    fun `قص أرقام الحسابات — العربي كمان والمعرّفات لأ`() {
        assertEquals("حساب ****٤٥٦٧", sanitizeAccountNumbers("حساب ١٢٣٤٥٦٧"))
        assertEquals("رقم 1234 بس", sanitizeAccountNumbers("رقم 1234 بس"))
        val raw = mapOf("id" to "t-1234567890", "merchantId" to "m-1234567890", "note" to "1234567890", "amountMinor" to 1234567890L)
        val safe = storeForm("transactions", raw)
        assertEquals("t-1234567890", safe["id"])
        assertEquals("m-1234567890", safe["merchantId"])
        assertEquals("****7890", safe["note"])
        assertEquals(1234567890L, safe["amountMinor"])
        // المجموعات التانية ما بتتقصش — زي التطبيق الحالي
        assertEquals(raw, storeForm("people", raw))
    }

    private fun <T> roundTrip(codec: DocCodec<T>, value: T): Doc {
        val d = codec.toStore(value)
        assertEquals(value, codec.decode(d), codec.group)
        assertTrue(d.values.all { it == null || it is String || it is Long || it is Boolean || it is List<*> || it is Map<*, *> }, "${codec.group}: نوع مش من أنواع فايربيز")
        return d
    }

    @Test
    fun `مجموعات المستحقات بتتكتب وتتقرا من غير ما حاجة تضيع`() {
        val rosca = Rosca(
            "rc-1", "جمعية وهمية", Currency.SAR, 100_000, 2, "2026-01-01", 10, listOf(2, 7), 1_000_000,
            members = listOf(RoscaMember(1, "عضو وهمي", "p-1"), RoscaMember(2, "عضو تاني")), organizerPersonId = "p-1", createdAt = "x", unit = CycleUnit.WEEK,
        )
        val d = roundTrip(DuesCodecs.roscas, rosca)
        assertEquals(listOf(2L, 7L), d["myTurns"])
        assertEquals("week", d["unit"])
        assertFalse("personId" in (d["members"] as List<*>)[1] as Map<*, *>)
        roundTrip(DuesCodecs.roscas, rosca.copy(members = emptyList(), organizerPersonId = null, myTurns = emptyList(), payoutMinor = 0))
        roundTrip(DuesCodecs.roscaEntries, RoscaEntry("e-1", "rc-1", "t-1", RoscaEntryKind.PAYOUT, 1_000_000))
        val plan = InstallmentPlan("ip-1", "تمويل وهمي", "بنك وهمي", InstallmentKind.FINANCING, Currency.SAR, 1_000_000, 1_200_000, 100_000, 1, "2026-01-10", true, "x")
        roundTrip(DuesCodecs.installmentPlans, plan)
        assertFalse("hasInterest" in roundTrip(DuesCodecs.installmentPlans, plan.copy(hasInterest = null)))
        // مبلغ التمويل المستلم (§59): بيتكتب لما يتربط بس، وفكه بيتمسح من المستند صراحة (الكتابة merge)
        assertEquals("t-9", roundTrip(DuesCodecs.installmentPlans, plan.copy(receivedTransactionId = "t-9"))["receivedTransactionId"])
        assertFalse("receivedTransactionId" in roundTrip(DuesCodecs.installmentPlans, plan))
        roundTrip(DuesCodecs.installmentPayments, InstallmentPayment("pay-1", "ip-1", "t-1", 100_000))
        val terms = DebtTerms("o-1", "p-1", "2026-02-01", 1, 50_000, false)
        assertEquals("o-1", DuesCodecs.debtTerms.id(terms))
        assertEquals(setOf("obligationId", "personId", "firstDueAt", "cycleMonths"), roundTrip(DuesCodecs.debtTerms, terms.copy(installmentMinor = null, hasInterest = null)).keys)
        assertEquals(57, DocumentCodecs.byGroup.size, "44 + إعدادات التنبيهات وصفحتها وإيصالاتها (جلسة 18) + خطط الادخار وإيداعاتها (§68) + حسابات الورث (§69.4) + المساعد والإعدادات والإشعارات الممسوحة (§78)")
    }

    @Test
    fun `قرار طرف التحويل بيتكتب ويتقرا وآخر 4 أرقام بس`() {
        val party = app.masroufy.core.TransferParty("سامي#4567", "سامي", "4567", app.masroufy.core.TransferVerdict.PERSON, "p-1", "2026-10-03T00:00:00.000Z")
        val d = roundTrip(TransferCodecs.transferParties, party)
        assertEquals("person", d["verdict"])
        assertEquals("4567", d["accountLast4"])
        val own = party.copy(verdict = app.masroufy.core.TransferVerdict.OWN_ACCOUNT, personId = null, last4 = null)
        assertEquals(setOf("key", "label", "verdict", "decidedAt"), roundTrip(TransferCodecs.transferParties, own).keys)
        assertEquals("سامي#4567", TransferCodecs.transferParties.id(party))
    }

    @Test
    fun `المشروع الشخصي ما بيكتبش نوع — نفس شكل التطبيق الحالي`() {
        val p = Project("pr-1", "مشروع وهمي", "مشروع وهمي", false, "x")
        assertFalse("kind" in roundTrip(AssetProjectCodecs.projects, p))
        assertEquals("work", roundTrip(AssetProjectCodecs.projects, p.copy(kind = ProjectKind.WORK))["kind"])
        // آخر ميعاد (§65): اختياري — الفاضي ما بيتكتبش، والمكتوب بيرجع زي ما هو
        assertEquals(setOf("id", "name", "normalizedName", "archived", "createdAt"), roundTrip(AssetProjectCodecs.projects, p).keys)
        assertEquals("2026-12-31", roundTrip(AssetProjectCodecs.projects, p.copy(deadline = "2026-12-31"))["deadline"])
    }

    @Test
    fun `المستند الغلط بيقول أنهي حقل`() {
        val base = LedgerCodecs.settlements.encode(app.masroufy.core.Settlement("s-1", "t-1", "o-1", 500))
        val missing = assertFailsWith<DocumentError> { LedgerCodecs.settlements.decode(base - "amountMinor") }
        assertTrue("amountMinor" in missing.message!!)
        // عدد عشري بقيمة صحيحة مقبول، والكسر مرفوض — فلوس بالهللة بس
        assertEquals(500, LedgerCodecs.settlements.decode(base + ("amountMinor" to 500.0)).amountMinor)
        assertFailsWith<DocumentError> { LedgerCodecs.settlements.decode(base + ("amountMinor" to 500.5)) }
        assertFailsWith<DocumentError> { LedgerCodecs.settlements.decode(base + ("amountMinor" to "500")) }
        val obligation = LedgerCodecs.obligations.encode(app.masroufy.core.Obligation("o-1", "p-1", null, app.masroufy.core.ObligationKind.RECEIVABLE, 1, Currency.SAR))
        val unknown = assertFailsWith<DocumentError> { LedgerCodecs.obligations.decode(obligation + ("kind" to "gift_card")) }
        assertTrue("gift_card" in unknown.message!!)
    }
}
