package app.masroufy.ui.screens.imports

import app.masroufy.core.Direction
import app.masroufy.core.MatchingState
import app.masroufy.core.SmsForeignPending
import app.masroufy.core.SmsForeignAmount
import app.masroufy.core.SmsKind
import app.masroufy.core.SmsParseResult
import app.masroufy.usecase.UnmappedSender
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «رسائل البنك» + «بانتظار تأكيدك» (`BankSms` · `SmsWaiting`): من صورة الصندوق (`SmsOverview` — `AutoRecordSms` · `ReviewSmsInbox` ·
 * `ManageSmsInbox`) لحالة الشاشة. **من غير حساب فلوس**: المبالغ زي ما الرسالة قالتها، والعدّ عدّ رسايل.
 */
class BankSmsModelTest {
    @AfterTest fun reset() = Fx.resetTexts()

    private fun overview(
        inbox: app.masroufy.usecase.InboxView = Fx.inbox(),
        unmapped: List<UnmappedSender> = emptyList(),
        reviews: List<SenderReview> = emptyList(),
    ) = SmsOverview(inbox, unmapped, reviews, senderWallets = emptyMap())

    @Test fun aDeviceThatCannotReadMessagesIsUnavailableNotEmpty() {
        val ui = bankSmsUi(null, emptyList(), emptySet())
        assertEquals(SmsStatus.UNAVAILABLE, ui.status)
        assertFalse(ui.live, "الآيفون: «الصق رسالة» بس — مفيش قايمة ولا «فاضي»")
    }

    @Test fun statusFollowsTheInboxSwitchAndThePermission() {
        assertEquals(SmsStatus.OFF, bankSmsUi(overview(Fx.inbox(enabled = false)), emptyList(), emptySet()).status)
        val lost = bankSmsUi(overview(Fx.inbox(permission = false)), emptyList(), emptySet())
        assertEquals(SmsStatus.PERMISSION, lost.status)
        assertTrue(lost.live, "الإذن مسحوب ⇒ المستني والقايمة بيفضلوا ظاهرين (حالة «خطأ» في النموذج)")
        val ok = bankSmsUi(overview(), emptyList(), emptySet())
        assertEquals(SmsStatus.READING, ok.status)
        assertTrue(ok.isEmpty, "مفيش مستني ولا حاجة اتسجلت النهارده ⇒ «لا رسائل اليوم»")
    }

    @Test fun aNewBankWithoutWalletShowsItsUnderstoodMessagesAsSamples() {
        val items = listOf(
            Fx.item("m1", "ALWAHA", SmsParseResult.Ok(Fx.smsRow(1, "  مكتبة الفجر ", 4500))),
            Fx.item("m2", "alwaha", SmsParseResult.Ok(Fx.smsRow(2, "مقهى المرسى", 2300))),
            Fx.item("m3", "OTHER", SmsParseResult.Ok(Fx.smsRow(3, "متجر", 100))),
        )
        val ui = bankSmsUi(overview(Fx.inbox(items = items), unmapped = listOf(UnmappedSender("sa", "ALWAHA", 2))), emptyList(), emptySet())
        val bank = ui.banks.single()
        assertEquals("ALWAHA", bank.sender)
        assertEquals(2, bank.count)
        assertEquals(listOf("مكتبة الفجر", "مقهى المرسى"), bank.samples.map { it.merchant }, "نفس المرسل بأي حالة حروف، والاسم من غير مسافات")
        assertEquals(listOf(4500L, 2300L), bank.samples.map { it.amountMinor })
        assertEquals(2, ui.waitingCount)
    }

    @Test fun readyLinesAreConfirmationsAndSimilarOrConflictAreSeparate() {
        val review = Fx.review(
            ready = listOf(Fx.reviewLine("r1", "مطعم", 4200, categoryId = "food", confirmReason = "اتفهمت من كلمات عامة")),
            similar = listOf(Fx.reviewLine("s1", "قهوة", 1800, MatchingState.SIMILAR), Fx.reviewLine("c1", "متجر", 15900, MatchingState.CONFLICT)),
        )
        val ui = bankSmsUi(overview(reviews = listOf(SenderReview("BANK-A", "w1", review))), emptyList(), emptySet())
        val confirm = ui.confirm.single()
        assertEquals("food", confirm.categoryId)
        assertEquals("اتفهمت من كلمات عامة", confirm.reason, "السبب = جملة المنطق نفسها")
        assertEquals(listOf(false, true), ui.similar.map { it.conflict }, "التعارض ما بيتسجلش — من غير «سجّلها»")
        assertEquals(3, ui.waitingCount)
    }

    @Test fun anUnreadableMessageKeepsItsReasonTextAndForeignFlag() {
        val foreign = SmsForeignPending("2026-10-07", SmsForeignAmount("USD", 4999), Direction.OUT, "متجر تطبيقات", SmsKind.OTHER)
        val items = listOf(
            Fx.item("f1", "BANK-A", SmsParseResult.Rejected("لا مبلغ فيها")),
            Fx.item("f2", "BANK-A", SmsParseResult.Rejected("عملة أجنبية", foreign = foreign)),
        )
        val ui = bankSmsUi(overview(Fx.inbox(items = items, messages = listOf(Fx.queued("f1", "BANK-A", "تمت عملية على بطاقتك")))), emptyList(), emptySet())
        assertEquals(listOf("لا مبلغ فيها", "عملة أجنبية"), ui.failed.map { it.reason })
        assertEquals("تمت عملية على بطاقتك", ui.failed[0].body)
        assertNull(ui.failed[1].body, "النص مش موجود ⇒ ما بنخترعش نص")
        assertEquals(listOf(false, true), ui.failed.map { it.foreign }, "العملة الأجنبية: سؤال المبلغ المحلي لسه ما اتبناش (§75-12)")
        assertEquals("2026-10-08", ui.failed[0].date)
    }

    @Test fun recordedTodayNeedsConfirmOrCategoryAndOnlyFreshOnesShine() {
        val today = listOf(
            Fx.txn("t1", "مطعم الريف", 4200, categoryId = "food", confirmed = true),
            Fx.txn("t2", "سوبرماركت الحي", 31840, categoryId = "shop"),
            Fx.txn("t3", null, 1850),
        )
        val ui = bankSmsUi(overview(), today, fresh = setOf("t1"))
        assertEquals(listOf(RecordedNeed.NONE, RecordedNeed.CONFIRM, RecordedNeed.CATEGORIZE), ui.recorded.map { it.need })
        assertEquals(listOf(true, false, false), ui.recorded.map { it.sheen }, "اللمعة على اللي اتسجل في الفتحة دي بس")
        assertEquals("وصف t3", ui.recorded[2].merchant, "مفيش اسم تاجر ⇒ الوصف")
        assertFalse(ui.isEmpty)
        assertEquals(31840L, ui.recorded[1].amountMinor)
    }

    @Test fun hiddenCategoriesAreNotPickable() {
        val all = listOf(Fx.category("a", "مطاعم"), Fx.category("b", "قديم", active = false))
        assertEquals(listOf("a"), pickableCategories(all).map { it.id })
    }

    @Test fun pickerGroupsMainsThenTheirSubsAndSearches() {
        val all = listOf(
            Fx.category("food", "مطاعم وقهوة", group = "food", order = 1),
            Fx.category("coffee", "قهوة", parent = "food", order = 2),
            Fx.category("bake", "مخبوزات", group = "food", order = 0),
            Fx.category("fuel", "بنزين", group = "transport"),
            Fx.category("old", "قديم بلا مجموعة"),
        )
        val groups = categoryGroups(all, "")
        assertEquals(3, groups.size)
        assertEquals(listOf("bake", "food", "coffee"), groups[0].second.map { it.id }, "الأساسي بترتيبه وبعده فرعياته")
        assertEquals(listOf("fuel"), groups[1].second.map { it.id })
        assertNull(groups[2].first, "من غير مجموعة ⇒ آخر حاجة من غير عنوان")
        assertEquals(listOf("coffee"), categoryGroups(all, " قهوة").flatMap { g -> g.second.map { it.id } }.filter { it == "coffee" })
        assertTrue(categoryGroups(all, "غير موجود").isEmpty(), "بحث من غير نتايج ⇒ «لا نتائج»")
    }
}
