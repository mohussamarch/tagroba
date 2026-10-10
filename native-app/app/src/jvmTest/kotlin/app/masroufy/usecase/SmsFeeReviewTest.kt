package app.masroufy.usecase

import app.masroufy.core.BankFeeCategory
import app.masroufy.core.Category
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.SmsFee
import app.masroufy.core.parseEgyptBankSms
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * مراجعة الشريحة S2 (§77-B): الشاشة بتشوف الرسوم اللي هتتكتب قبل «سجّل الكل» · كل رقم رسوم بيتسجل مرة · رسالة الرسوم نفسها بتحافظ على
 * تصنيف المالك (§75-16) · أثر قبل الرسوم غيّر المبلغ ⇒ الرسوم اللي جوه المبلغ ما بتتقسمش · تصنيف «رسوم بنكية» الشغّال الأول.
 * كل الرسايل والأسامي والأرقام مخترعة.
 */
class SmsFeeReviewTest {
    private val feeOnly = "Debit fees\nReason: Card replacement fee\nAmount: SAR 15.00\nFrom: STC Bank wallet\nDate: 2026-03-05 09:10:44"
    private fun category(id: String, name: String, active: Boolean) = Category(id, null, name, "receipt", "#222222", "#dddddd", active, 5)

    @Test fun theScreenShowsTheFeeThatWillBeWritten() = runBlocking<Unit> {
        val desk = S2Desk()
        desk.receive("due", S2_SA_TOTAL_DUE)
        desk.receive("top", S2_SA_FEE_ON_TOP, "2026-03-05T10:00:00Z")
        desk.receive("fee", feeOnly, "2026-03-05T11:00:00Z")
        desk.receive("cafe", "شراء\nبـSR 40\nلدى:TEST MART\n26/03/05", "2026-03-05T12:00:00Z")
        val lines = desk.load().ready.associateBy { it.messageId }
        assertEquals(SmsFee(661, includedInAmount = true), lines.getValue("due").fee, "1,006.61 فيها 6.61 رسوم")
        assertEquals(SmsFee(575, includedInAmount = false), lines.getValue("top").fee)
        assertNull(lines.getValue("fee").fee, "المبلغ كله رسوم — مفيش رسوم زيادة")
        assertNull(lines.getValue("cafe").fee)
        desk.screen.recordAll(emptyMap(), emptyList())
        val extraFees = desk.all().filter { it.rawMerchantName == BankFeeCategory.NAME }.map { it.amountMinor }.sorted()
        assertEquals(listOf(575L, 661L), extraFees, "اللي اتعرض هو اللي اتكتب")
    }

    @Test fun eachFeeIsRecordedOnce() = runBlocking<Unit> {
        // الراجحي بلغتين (شكل معروف): كان الرسوم 11.50 والبنك −1,011.50
        val sa = S2Desk()
        sa.receive("k", "حوالة محلية صادرة\nمصرف:ANB\nمن:1111\nمبلغ:SAR 1000\nالى:TEST PERSON\nالرسوم:SAR 5.75\nFees: SAR 5.75\n26/03/05 09:10")
        assertEquals(SmsFee(575, false), sa.load().ready.single().fee)
        sa.screen.recordAll(emptyMap(), emptyList())
        assertEquals(listOf(575L, 100_000L), sa.all().map { it.amountMinor }.sorted())
        assertEquals(-100_575L, sa.balance(BANK))

        // مصر: «وخصم 2005 من محفظتك» على تحويل 2000 = الإجمالي ⇒ المصاريف 5 (كانت 2005 والمحفظة −4,005)
        val eg = S2Desk(wallets = listOf(S2_EG_CASH, S2_EG_BANK), parse = ::parseEgyptBankSms)
        eg.receive("g", "تم تحويل 2000 جنيه لرقم 01000001212 وخصم 2005 من محفظتك", "2026-10-07T10:00:00Z")
        assertEquals(SmsFee(500, false), eg.load(S2_EG_BANK).ready.single().fee)
        eg.screen.recordAll(emptyMap(), emptyList())
        assertEquals(listOf(500L, 200_000L), eg.all().map { it.amountMinor }.sorted())
        assertEquals(-200_500L, eg.balance(S2_EG_BANK))
    }

    @Test fun aFeeOnlyMessageKeepsTheCategoryTheOwnerGaveTheMerchant() = runBlocking<Unit> {
        val desk = S2Desk(extraCategories = listOf(category("cat-subs", "اشتراكات", true)))
        desk.receive("f1", feeOnly)
        val merchant = desk.load().ready.single().merchant
        assertTrue(desk.screen.remember(merchant, "cat-subs", Direction.OUT))
        val line = desk.load().ready.single()
        assertEquals("cat-subs" to true, line.categoryId to line.remembered, "الشاشة بتعرض تصنيف المالك")
        desk.screen.recordAll(emptyMap(), emptyList())
        val t = desk.all().single()
        assertEquals("cat-subs", t.categoryId, "المتسجل = اللي اتعرض (كان «رسوم بنكية» مؤكد)")
        assertEquals(EconomicKind.FEE to true, t.economicKind to t.economicKindConfirmed)
        assertTrue(desk.space.categories.listAll().none { it.id == BankFeeCategory.ID }, "ما اتعملش «رسوم بنكية» من غير لازمة")

        // التصنيف اللي اختاره دلوقتي بيفضل مؤكد
        val chosen = S2Desk()
        chosen.receive("f1", feeOnly)
        val number = chosen.load().ready.single().lineNumber
        chosen.screen.recordAll(mapOf(number to "cat-food"), emptyList())
        assertEquals("cat-food" to true, chosen.all().single().let { it.categoryId to it.categoryConfirmed })
    }

    @Test fun anAmountChangedByAnEarlierEffectIsNotSplitAgain() = runBlocking<Unit> {
        // أثر قبل الرسوم غيّر المبلغ (زي المبلغ المحلي للشراء الأجنبي) — عقد الترتيب
        val earlier = object : RecordEffect {
            override suspend fun prepare(ctx: RecordContext) {
                for (line in ctx.lines) line.transaction = line.transaction.copy(amountMinor = 90_000)
            }
        }
        val included = S2Desk(before = listOf(earlier))
        included.receive("due", S2_SA_TOTAL_DUE)
        included.recordAll()
        assertEquals(listOf(90_000L), included.all().map { it.amountMinor }, "المبلغ الجديد زي ما هو ومفيش رسوم من جوه مبلغ قديم")
        val onTop = S2Desk(before = listOf(earlier))
        onTop.receive("top", S2_SA_FEE_ON_TOP)
        onTop.recordAll()
        assertEquals(listOf(575L, 90_000L), onTop.all().map { it.amountMinor }.sorted(), "الرسوم اللي برّه المبلغ بتتكتب والأصلية ما اتلمستش")
    }

    @Test fun anActiveBankFeeCategoryIsPreferredOverAHiddenOne() = runBlocking<Unit> {
        val hidden = category(BankFeeCategory.ID, BankFeeCategory.NAME, active = false)
        val mine = category("c-mine", BankFeeCategory.NAME, active = true)
        val desk = S2Desk(extraCategories = listOf(hidden, mine))
        desk.receive("t1", S2_SA_FEE_ON_TOP)
        desk.recordAll()
        assertEquals("c-mine", desk.all().single { it.economicKind == EconomicKind.FEE }.categoryId)

        val named = category("c-named", BankFeeCategory.NAME, active = false)
        assertEquals(BankFeeCategory.ID, BankFeeCategory.existingId(listOf(mine, hidden.copy(active = true))), "الثابت الشغّال الأول")
        assertEquals(BankFeeCategory.ID, BankFeeCategory.existingId(listOf(named, hidden)), "مفيش شغّال ⇒ الثابت (ما بنعملش تاني)")
        assertEquals("c-named", BankFeeCategory.existingId(listOf(named)))
        assertNull(BankFeeCategory.existingId(listOf(category("c-other", "مطاعم", true))))
    }
}
