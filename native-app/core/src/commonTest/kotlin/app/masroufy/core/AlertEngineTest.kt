package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** محرك التنبيهات — القلب النقي (OVERRIDES §61). كل الأسامي والمبالغ مخترعة. */
class AlertEngineTest {
    private val noon = LocalMoment("2026-10-10", 14)

    private fun cand(kind: AlertKind, amount: Long? = null, scale: Long? = null, flow: DueFlow = DueFlow.PAY) =
        AlertCandidate(kind, "t|${kind.wire}", "عنوان", "تفاصيل", flow, amount, scale)

    /** فتح التطبيق 3 مرات الساعة 9 و3 مرات الساعة 21 ومرة الساعة 3 الفجر. */
    private val learned = UsualHours().let { h -> listOf(9, 9, 9, 21, 21, 21, 3).fold(h) { acc, hour -> acc.recordOpen(hour) } }

    private fun ignored() = KindStats(shown = 10, opened = 1)

    private fun engaged() = KindStats(shown = 10, opened = 8)

    @Test fun ladderGoesSoonThenTodayThenOverdue() {
        val due = "2026-10-10"
        val stages = listOf("2026-10-05", "2026-10-06", "2026-10-07", "2026-10-09", "2026-10-10", "2026-10-11", "2026-11-30").map { alertStage(due, it, DUE_SOON_DAYS) }
        assertEquals(listOf(null, null, AlertStage.SOON, AlertStage.SOON, AlertStage.TODAY, AlertStage.OVERDUE, AlertStage.OVERDUE), stages)
        assertEquals(AlertStage.SOON, alertStage("2026-10-20", "2026-10-10", ZAKAT_REMINDER_DAYS), "الزكاة قبلها بـ10 أيام")
        assertNull(alertStage("2026-10-21", "2026-10-10", ZAKAT_REMINDER_DAYS))
    }

    @Test fun dueCandidatesShareOneThreadAcrossStagesButHaveDifferentEventKeys() {
        val item = DueItem(DueSource.INSTALLMENT, "ip-1", "تمويل وهمي", "2026-10-10", 50_000, Currency.SAR, DueFlow.PAY, DueStatus.SOON)
        val days = listOf("2026-10-06", "2026-10-08", "2026-10-10", "2026-10-12")
        val produced = days.map { dueAlertCandidates(listOf(item), it) { null }.singleOrNull() }
        assertNull(produced[0])
        val live = produced.drop(1).map { assertNotNull(it) }
        assertEquals(listOf(AlertKind.DUE_SOON, AlertKind.DUE_TODAY, AlertKind.DUE_OVERDUE), live.map { it.kind })
        assertEquals(1, live.map { it.threadKey }.toSet().size)
        assertEquals(3, live.map { it.eventKey }.toSet().size)
        assertTrue(live[0].title.contains("تمويل وهمي") && live[0].body.contains("500.00"), "التفاصيل جوه التطبيق فيها الاسم والمبلغ")
    }

    @Test fun urgentAndEngagedTypeIsSentNow() {
        val d = decideAlert(cand(AlertKind.DUE_TODAY), engaged(), learned, noon)
        assertEquals(AlertDelivery.SEND_NOW, d.delivery)
        assertTrue(AlertFactor.YOU_OPEN_THESE in d.factors)
    }

    @Test fun ignoredTypeIsDemotedButUrgentNeverDropsBelowUsualTime() {
        assertEquals(AlertDelivery.DIGEST, decideAlert(cand(AlertKind.BUDGET_EXCEEDED), ignored(), learned, noon).delivery)
        assertEquals(AlertDelivery.INBOX_ONLY, decideAlert(cand(AlertKind.BUDGET_THRESHOLD), ignored(), learned, noon).delivery)
        val today = decideAlert(cand(AlertKind.DUE_TODAY), ignored(), learned, noon)
        assertEquals(AlertDelivery.AT_USUAL_TIME, today.delivery, "ميعاد النهارده ما بيتدفنش — بيستنى وقتك")
        assertTrue(AlertFactor.YOU_SKIP_THESE in today.factors)
        assertEquals(AlertDelivery.SEND_NOW, decideAlert(cand(AlertKind.DUE_TODAY), KindStats(), learned, noon).delivery)
        val owedToYou = decideAlert(cand(AlertKind.DUE_OVERDUE, flow = DueFlow.RECEIVE), ignored(), learned, noon)
        assertEquals(AlertDelivery.AT_USUAL_TIME, owedToYou.delivery, "فلوس ليك متأخرة وبتتجاهلها ⇒ برضه ما بتتدفنش في ملخص")
    }

    @Test fun engagedLowTypeIsLiftedOutOfTheDigest() {
        assertEquals(AlertDelivery.DIGEST, decideAlert(cand(AlertKind.BUDGET_THRESHOLD), KindStats(), learned, noon).delivery)
        assertEquals(AlertDelivery.AT_USUAL_TIME, decideAlert(cand(AlertKind.BUDGET_THRESHOLD), engaged(), learned, noon).delivery)
    }

    @Test fun engagementNeedsEnoughSamples() {
        assertEquals(Engagement.UNKNOWN, engagementOf(KindStats(4, 0)))
        assertEquals(Engagement.IGNORED, engagementOf(KindStats(5, 0)))
        assertEquals(Engagement.NEUTRAL, engagementOf(KindStats(5, 1)))
        assertEquals(Engagement.ENGAGED, engagementOf(KindStats(5, 3)))
    }

    @Test fun usualHoursDecideWhenAMediumAlertArrives() {
        assertTrue(learned.isUsual(9) && learned.isUsual(21))
        assertFalse(learned.isUsual(3), "فتحة واحدة الفجر مش عادة")
        val waiting = decideAlert(cand(AlertKind.DUE_SOON), KindStats(), learned, noon)
        assertEquals(AlertDelivery.AT_USUAL_TIME, waiting.delivery)
        assertEquals(LocalMoment("2026-10-10", 21), waiting.deliverAt)
        assertEquals(LocalMoment("2026-10-11", 9), decideAlert(cand(AlertKind.DUE_SOON), KindStats(), learned, LocalMoment("2026-10-10", 22)).deliverAt)
        assertEquals(AlertDelivery.SEND_NOW, decideAlert(cand(AlertKind.DUE_SOON), KindStats(), learned, LocalMoment("2026-10-10", 21)).delivery)
        val unlearned = decideAlert(cand(AlertKind.DUE_SOON), KindStats(), UsualHours(), noon)
        assertEquals(AlertDelivery.SEND_NOW, unlearned.delivery)
        assertTrue(AlertFactor.HOURS_NOT_LEARNED in unlearned.factors)
    }

    @Test fun sizeRelativeToTheMonthMovesTheDecision() {
        val big = decideAlert(cand(AlertKind.DUE_SOON, 100_000, 500_000), KindStats(), learned, noon)
        assertEquals(AlertDelivery.SEND_NOW, big.delivery, "20% من الشهر ⇒ على طول")
        assertTrue(AlertFactor.BIG_FOR_MONTH in big.factors)
        val small = decideAlert(cand(AlertKind.DUE_SOON, 5_000, 500_000), KindStats(), learned, noon)
        assertEquals(AlertDelivery.DIGEST, small.delivery, "1% من الشهر ⇒ ملخص")
        assertEquals(AlertDelivery.AT_USUAL_TIME, decideAlert(cand(AlertKind.DUE_SOON, 5_000, null), KindStats(), learned, noon).delivery, "مفيش سقف ⇒ مفيش حكم")
        assertEquals(AlertDelivery.SEND_NOW, decideAlert(cand(AlertKind.DUE_TODAY, 1, 500_000), KindStats(), learned, noon).delivery, "النهارده ما بيتصغرش")
    }

    @Test fun decisionsAreNeverBuriedAndMoneyComingInIsCalmer() {
        val q = decideAlert(cand(AlertKind.TRANSFER_QUESTION), KindStats(), learned, noon)
        assertEquals(AlertDelivery.AT_USUAL_TIME, q.delivery)
        assertTrue(AlertFactor.NEEDS_DECISION in q.factors)
        assertEquals(AlertDelivery.AT_USUAL_TIME, decideAlert(cand(AlertKind.DUE_TODAY, flow = DueFlow.RECEIVE), KindStats(), learned, noon).delivery)
        assertEquals(AlertDelivery.INBOX_ONLY, decideAlert(cand(AlertKind.PROFILE_INCOMPLETE), engaged(), learned, noon).delivery)
        assertTrue(decideAlert(cand(AlertKind.DUE_OVERDUE), KindStats(), learned, noon).inAppWindow, "المتأخر: نافذة جوه التطبيق")
    }

    @Test fun everyDecisionCarriesAHumanReason() {
        for (kind in AlertKind.entries) for (stats in listOf(KindStats(), ignored(), engaged())) {
            val d = decideAlert(cand(kind, 100_000, 500_000), stats, learned, noon)
            val text = alertReasonText(d)
            assertTrue(text.isNotBlank() && d.factors.isNotEmpty(), "$kind")
            assertEquals(d.factors.size + 1, text.split(" · ").size, "السبب الأساسي + كل عامل: $text")
        }
        val waiting = decideAlert(cand(AlertKind.DUE_SOON), KindStats(), learned, noon)
        assertTrue(alertReasonText(waiting).contains("21"), alertReasonText(waiting))
    }

    @Test fun lockScreenTextNeverCarriesNumbersAmountsOrNames() {
        val names = listOf("مطعم الوهم", "شركة تقسيط وهمية", "Fake Store", "سامي الوهمي")
        try {
            for (lang in Language.entries) {
                Texts.language = lang
                for (kind in AlertKind.entries) for (flow in DueFlow.entries) for ((i, name) in names.withIndex()) {
                    val amount = 1_234L * (i + 1) * (kind.ordinal + 1)
                    val item = DueItem(DueSource.RECURRING, "r-$i", name, "2026-10-12", amount, Currency.entries[i % Currency.entries.size], flow, DueStatus.SOON)
                    val detail = dueAlertCandidates(listOf(item), "2026-10-10") { null }.single()
                    val notice = systemNoticeFor(kind, flow)
                    for (text in listOf(notice.title, notice.body)) {
                        assertTrue(isLockSafe(text), "[$lang] $kind: $text")
                        assertFalse(text.contains(name) || text.contains(formatMoney(amount, item.currency, showCurrency = false)), "[$lang] $kind: $text")
                    }
                    assertTrue(detail.title.contains(name), "التفاصيل نفسها فيها الاسم — عشان الاختبار يبقى له معنى")
                }
                assertTrue(isLockSafe(digestNotice().body))
            }
        } finally {
            Texts.language = Language.AR
        }
        assertFalse(isLockSafe("عندك قسط بعد 3 أيام"))
        assertFalse(isLockSafe("بعد ٣ أيام"))
        assertFalse(isLockSafe("عدّى 80٪"))
        assertFalse(isLockSafe("عليك ر.س"))
    }

    @Test fun budgetAlertsNeverFireOnAnIncompleteNumber() {
        val over = BudgetStatus(300_000, 310_000, -10_000, 1_033, BudgetLevel.entries.last(), true)
        assertTrue(budgetAlertCandidates(buildBudgetNotifications("2026-09-28", over, 80, emptyList(), spentKnown = false)).isEmpty())
        val known = budgetAlertCandidates(buildBudgetNotifications("2026-09-28", over, 80, emptyList(), spentKnown = true))
        assertEquals(listOf(AlertKind.BUDGET_EXCEEDED), known.map { it.kind }, "80% و100% مع بعض ⇒ تنبيه واحد بالأعلى")
    }

    @Test fun zakatProfileAndBalanceCandidates() {
        val year = ZakatYear("2026-10-20", "2025-10-31", "2026-10-20", Currency.SAR, "x")
        assertNull(zakatAlertCandidate(year, null, "2026-10-09"))
        assertEquals(AlertKind.ZAKAT_SOON, zakatAlertCandidate(year, null, "2026-10-10")?.kind)
        assertEquals(AlertKind.ZAKAT_TODAY, zakatAlertCandidate(year, null, "2026-10-20")?.kind)
        assertEquals(AlertKind.ZAKAT_OVERDUE, zakatAlertCandidate(year, null, "2026-10-25")?.kind)
        val closed = year.copy(closedAt = "y")
        assertNull(zakatAlertCandidate(closed, 0, "2026-10-25"), "اتثبتت واتدفعت ⇒ اتحلت")
        assertEquals(AlertKind.ZAKAT_OVERDUE, zakatAlertCandidate(closed, 5_000, "2026-10-25")?.kind)
        assertNull(profileCompletionCandidate(null))
        assertNull(profileCompletionCandidate(100))
        assertEquals("profile|40", profileCompletionCandidate(40)?.threadKey)
        assertNull(balanceMismatchCandidate("w-1", "بنك وهمي", emptyList()))
        val m = BalanceMismatch(3, "2026-01-05", 100, 200, -100, null, null, 1)
        assertEquals(AlertKind.BALANCE_MISMATCH, balanceMismatchCandidate("w-1", "بنك وهمي", listOf(m))?.kind)
    }
}
