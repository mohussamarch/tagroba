package app.masroufy.usecase

import app.masroufy.core.AlertKind
import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.LocalMoment
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Occasion
import app.masroufy.core.OccasionKind
import app.masroufy.core.Period
import app.masroufy.core.Person
import app.masroufy.core.Settlement
import app.masroufy.core.Space
import app.masroufy.core.TextKey
import app.masroufy.core.defaultSpace
import app.masroufy.core.isLockSafe
import app.masroufy.core.uiText
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAlertInbox
import app.masroufy.memory.MemoryAlertInteractions
import app.masroufy.memory.MemoryAlertReceipts
import app.masroufy.memory.MemoryAlertSettings
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryEventLinkRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryLifeEventRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryOccasionRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryRecurringRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryRoscaRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySpaceRegistry
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUsualHours
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** اللي بيبص على البلدين مع بعض (§64): التنبيهات · ديون نفس الشخص · جملة الزكاة — أسماء ومبالغ مخترعة. */
class AcrossSpacesTest {
    private val egypt = Space("eg", "مصر", "EG", Currency.EGP, "2026-10-04T10:00:00.000Z")
    private val period = Period("2026-09", "2026-09-28", "2026-10-27", 30)
    private val person = Person("p-1", "شخص في البلدين")
    private val clock = FixedClock("2026-10-08T10:00:00.000Z")

    /** قسط بنفس المعرّف في البلدين (كل بلد عدّادها) — ميعاده 10 أكتوبر. */
    private fun dues(currency: Currency) = LoadDues(
        LoadDuesDeps(
            MemoryRoscaRepository(), MemoryRoscaEntryRepository(),
            MemoryInstallmentPlanRepository(listOf(InstallmentPlan("ip-1", "تقسيط وهمي", "جهة وهمية", InstallmentKind.PURCHASE_PLAN, currency, 500_000, 500_000, 50_000, 1, "2026-10-10"))),
            MemoryInstallmentPaymentRepository(), MemoryDebtTermsRepository(), MemoryPersonRepository(), MemoryObligationRepository(), MemorySettlementRepository(),
            MemoryRecurringRepository(), MemoryTransactionRepository(),
        ),
    )

    private val occasions = ManageOccasions(
        ManageOccasionsDeps(
            MemoryOccasionRepository(listOf(Occasion("occ-1", "p-1", OccasionKind.BIRTHDAY, month = 10, day = 9, yearly = true, createdAt = "c"))),
            MemoryPersonRepository(listOf(person)), MemoryLifeEventRepository(), MemoryEventLinkRepository(), MemoryTransactionRepository(), SequentialIdGenerator(), clock,
        ),
    )

    private fun source(space: Space) = SpaceAlertSource(
        space,
        // حتى لو حد ادّى المناسبات لكل بلد بالغلط، بتتجمع مرة واحدة
        GatherAlertsDeps(dues(space.currency), occasions = occasions),
        AlertGatherInput("2026-10-08", period, space.currency, profileCompletionPercent = 40),
    )

    @Test fun alertsComeFromEveryCountryWithItsNameInsideTheAppOnly() = runBlocking<Unit> {
        val all = GatherAllSpaceAlerts(listOf(source(defaultSpace()), source(egypt)), occasions).gather("2026-10-08", profileCompletionPercent = 40)
        val dues = all.filter { it.kind == AlertKind.DUE_SOON }
        assertEquals(2, dues.size, "قسط السعودية وقسط مصر — مش موضوع واحد رغم نفس المعرّف")
        val saudi = dues.single { it.spaceId == DEFAULT_SPACE_ID }
        val eg = dues.single { it.spaceId == "eg" }
        // موضوع السعودية زي ما كان قبل البلاد (الإيصالات القديمة شغالة)، ومصر بتبدأ بمعرّفها
        val alone = GatherAlerts(GatherAlertsDeps(dues(Currency.SAR))).gather(AlertGatherInput("2026-10-08", period, Currency.SAR)).single { it.kind == AlertKind.DUE_SOON }
        assertEquals(alone.threadKey, saudi.threadKey)
        assertEquals("eg:${alone.threadKey}", eg.threadKey)
        assertEquals(uiText(TextKey.COUNTRY_SA) to "مصر", saudi.spaceLabel to eg.spaceLabel)
        assertEquals(1, all.count { it.kind == AlertKind.OCCASION_SOON }, "المناسبة على مستوى الحساب مرة واحدة")
        assertEquals(1, all.count { it.kind == AlertKind.PROFILE_INCOMPLETE }, "كارت الملف مرة واحدة")
        assertNull(all.single { it.kind == AlertKind.OCCASION_SOON }.spaceLabel)

        // بلد واحدة ⇒ من غير اسم بلد خالص
        assertNull(GatherAllSpaceAlerts(listOf(source(defaultSpace())), occasions).gather("2026-10-08").single { it.kind == AlertKind.DUE_SOON }.spaceLabel)

        // المحرك: الاسم في الصفحة، ونص شاشة القفل من النوع بس (من غير اسم البلد)
        val inbox = MemoryAlertInbox()
        val run = RunAlertEngine(AlertEngineDeps(MemoryAlertSettings(), MemoryAlertInteractions(), MemoryUsualHours(), MemoryAlertReceipts(), inbox, clock)).run(all, LocalMoment("2026-10-08", 14))
        assertEquals(setOf(uiText(TextKey.COUNTRY_SA), "مصر"), inbox.listAll().filter { it.kind == AlertKind.DUE_SOON }.map { it.spaceLabel }.toSet())
        assertTrue(run.posts.isNotEmpty())
        for (post in run.posts) {
            val text = post.notice.title + " " + post.notice.body
            assertTrue(isLockSafe(text), text)
            // «مصروفي» (اسم التطبيق) فيها «مصر» كحروف — الفحص على الكلمة
            val words = text.split(Regex("[\\s—،.:]+")).toSet()
            assertTrue("مصر" !in words && uiText(TextKey.COUNTRY_SA) !in words, "اسم البلد مش على شاشة القفل: $text")
        }
        // نفس النص بالحرف اللي كان بيطلع قبل البلاد (من النوع بس)
        assertEquals(setOf(app.masroufy.core.systemNoticeFor(AlertKind.DUE_SOON, app.masroufy.core.DueFlow.PAY)), run.posts.filter { p -> p.eventKeys.any { "due" in it } }.map { it.notice }.toSet())
    }

    @Test fun samePersonDebtsStaySideBySideNeverSummed() = runBlocking<Unit> {
        val saObligations = MemoryObligationRepository(listOf(Obligation("o-1", "p-1", null, ObligationKind.RECEIVABLE, 100_000, Currency.SAR)))
        val saSettlements = MemorySettlementRepository(listOf(Settlement("s-1", "t-1", "o-1", 40_000)))
        val egObligations = MemoryObligationRepository(
            listOf(
                Obligation("o-1", "p-1", null, ObligationKind.RECEIVABLE, 250_000, Currency.EGP), Obligation("o-2", "p-1", null, ObligationKind.LOAN_PAYABLE, 30_000, Currency.EGP),
                // دين بالريال متسجل في مصر — يفضل في سطر مصر، ما يتجمعش مع ريال السعودية
                Obligation("o-3", "p-1", null, ObligationKind.RECEIVABLE, 5_000, Currency.SAR),
            ),
        )
        val across = PersonAcrossSpaces(
            listOf(PersonSpaceBook(defaultSpace(), saObligations, saSettlements), PersonSpaceBook(egypt, egObligations, MemorySettlementRepository())),
        )
        val debts = across.debts("p-1")
        assertEquals(
            listOf(
                PersonSpaceDebt(DEFAULT_SPACE_ID, uiText(TextKey.COUNTRY_SA), Currency.SAR, 60_000, 0, 0),
                PersonSpaceDebt("eg", "مصر", Currency.EGP, 250_000, 30_000, 0),
                PersonSpaceDebt("eg", "مصر", Currency.SAR, 5_000, 0, 0),
            ),
            debts,
            "كل بلد بعملتها، و«ليك» و«عليك» منفصلين — مفيش سطر إجمالي",
        )
        assertTrue(across.debts("p-ghost").isEmpty())
    }

    @Test fun zakatPageSaysItIsForThisCountryOnlyWhenThereIsAnother() = runBlocking<Unit> {
        fun zakat(registry: MemorySpaceRegistry?) = ManageZakat(
            ManageZakatDeps(
                "SA", Currency.SAR, app.masroufy.memory.MemoryProfileRepository(), app.masroufy.memory.MemoryWalletRepository(), MemoryTransactionRepository(),
                app.masroufy.memory.MemoryAssetRepository(), app.masroufy.memory.MemoryAssetLotRepository(), app.masroufy.memory.MemoryAssetSaleRepository(),
                app.masroufy.memory.MemoryAssetPriceRepository(), MemoryPersonRepository(), MemoryObligationRepository(), MemorySettlementRepository(), MemoryRoscaRepository(),
                MemoryRoscaEntryRepository(), app.masroufy.memory.MemoryZakatFactRepository(), app.masroufy.memory.MemoryZakatYearRepository(),
                app.masroufy.memory.PassthroughUnitOfWork(), clock, registry,
            ),
        )
        assertNull(zakat(MemorySpaceRegistry()).scopeNote(), "بلد واحدة ⇒ مفيش جملة")
        assertEquals("الحساب ده على فلوسك في البلد دي بس", zakat(MemorySpaceRegistry(listOf(egypt))).scopeNote())
        assertEquals("الحساب ده على فلوسك في البلد دي بس", zakat(MemorySpaceRegistry(listOf(egypt.copy(archived = true)))).scopeNote(), "المؤرشفة فلوسها لسه موجودة")
    }
}
