package app.masroufy.usecase

import app.masroufy.core.CategorizationSource
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.ImportSourceType
import app.masroufy.core.Merchant
import app.masroufy.core.ReviewState
import app.masroufy.core.SchemaId
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryMerchantCategoryRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTagRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransactionTagRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.MerchantRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «نفتكره؟» اتلغى — التصنيف اللي المالك بيختاره بيتحفظ للمحل لوحده (قرار المالك §75-16). كل الأسامي مخترعة. */
class MerchantMemoryTest {
    private fun cat(id: String) = Category(id, null, "تصنيف $id", "tag", "#000000", "#ffffff", true, 1)
    private val categories = MemoryCategoryRepository(listOf(cat("cat-food"), cat("cat-coffee"), cat("cat-gift")))
    private val merchants = MemoryMerchantRepository()
    private val ids = SequentialIdGenerator()
    private val clock = FixedClock("2026-10-08T10:00:00.000Z")
    private val shared = mutableListOf<Pair<String, Id>>()
    private val memory = MerchantMemory(merchants, ids) { c, id -> shared += c.rawMerchantName to id }

    private fun purchase(id: String, merchant: String?, dir: Direction = Direction.OUT, desc: String? = null, op: String? = null) = Transaction(
        id = id, occurredAt = "2026-10-05", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = dir, amountMinor = 2_500, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
        rawMerchantName = merchant, rawDescription = desc, sourceOperationType = op,
    )

    private val txns = MemoryTransactionRepository(
        listOf(purchase("t-1", "TEST CAFE"), purchase("t-2", "TEST CAFE"), purchase("t-digits", "123456"), purchase("t-mask", "****1234")),
    )

    private fun edit(memory: MerchantMemory? = this.memory, onConfirmed: (suspend (Transaction, Id) -> Unit)? = null) = EditTransaction(
        EditTransactionDeps(txns, categories, MemoryTagRepository(), MemoryTransactionTagRepository(), MemoryUnitOfWork(listOf(txns)), ids, clock, onConfirmed, merchantMemory = memory),
    )

    private fun categorize(repo: MerchantRepository = merchants, memory: MerchantMemory? = this.memory) = CategorizeTransactions(
        CategorizeTransactionsDeps(txns, repo, categories, MemoryRuleRepository(), MemoryUnitOfWork(listOf(txns)), clock, merchantMemory = memory),
    )

    private suspend fun cafe(): Merchant? = merchants.findByNormalizedName("test cafe")

    /** الاستيراد الجاي: معاينة كشف فيه نفس المحل. */
    private suspend fun nextImportLine(merchant: String) = ImportStatement(
        ImportStatementDeps(txns, MemorySourceRecordRepository(), MemoryImportBatchRepository(), merchants, categories, MemoryRuleRepository(), MemoryUnitOfWork(listOf(txns)), ids, clock),
    ).preview(
        ImportRequest(
            "t.csv", "التاريخ,مدين,دائن,الرصيد,التاجر,التصنيف,نوع العملية,التفاصيل\n2026/10/09,25.00,0.00,975.00,$merchant,,شراء,شراء\n",
            "acc-test", ImportSourceType.CSV_LEGACY, "w-1", SchemaId.LEGACY,
        ),
    ).lines.single()

    @Test fun pickingACategoryRemembersTheMerchantWithoutAsking() = runBlocking<Unit> {
        var confirmed = 0
        edit(onConfirmed = { _, _ -> confirmed++ }).setCategory("t-1", "cat-coffee")
        assertEquals("cat-coffee", cafe()?.verifiedCategoryId)
        assertEquals(1, confirmed, "رفع القايمة المشتركة من نفس الخطاف زي الأول")
        assertEquals(emptyList(), shared, "مش مرتين — الذاكرة ما بترفعش هنا")
        // الجاي بياخده مؤكد من «التاجر المؤكد» (§36)
        val next = nextImportLine("TEST CAFE")
        assertEquals("cat-coffee" to CategorizationSource.VERIFIED_MERCHANT, next.categoryId to next.categorySource)
        // القديم ما بيتصنفش من جديد
        assertNull(txns.findByIds(listOf("t-2")).single().categoryId)
    }

    @Test fun confirmFromTheCategorizeScreenRemembersToo() = runBlocking<Unit> {
        categorize().confirm("t-1", "cat-food")
        assertEquals("cat-food", cafe()?.verifiedCategoryId)
        assertEquals(listOf("TEST CAFE" to "cat-food"), shared, "شراء صادر ⇒ بيترفع للقايمة المشتركة")
        assertEquals("cat-food" to CategorizationSource.VERIFIED_MERCHANT, nextImportLine("test cafe").let { it.categoryId to it.categorySource })
    }

    @Test fun theLastPickWins() = runBlocking<Unit> {
        edit().setCategory("t-1", "cat-coffee")
        edit().setCategory("t-2", "cat-food")
        assertEquals("cat-food", cafe()?.verifiedCategoryId)
        assertEquals(1, merchants.listAll().size, "نفس المحل — مش محل جديد")
        // نفس الاختيار تاني ⇒ ولا كتابة
        assertFalse(memory.remember(MerchantPick("Test Cafe", "cat-food")))
    }

    @Test fun aDigitOnlyOrMaskedNameRemembersNothing() = runBlocking<Unit> {
        edit().setCategory("t-digits", "cat-food")
        edit().setCategory("t-mask", "cat-food")
        assertEquals(emptyList(), merchants.listAll())
        assertEquals(0, memory.rememberAll(listOf(MerchantPick(null, "cat-food"), MerchantPick("  ", "cat-food"))))
        assertEquals("cat-food", txns.findByIds(listOf("t-digits")).single().categoryId, "تصنيف العملية نفسه اتحفظ")
        // العملية اليدوية اللي من غير اسم (`AddTransaction` بيخزن «بلا اسم») مش محل
        val wallets = MemoryWalletRepository(listOf(Wallet("w-1", "TEST WALLET", Currency.SAR, "bank", 0, "2026-01-01")))
        val manual = AddTransaction(AddTransactionDeps(txns, wallets, ids, clock))
            .add(NewTransactionInput(1_000, occurredAt = "2026-10-05", walletId = "w-1", economicKind = EconomicKind.PURCHASE, merchantName = " "))
        assertEquals(MANUAL_NO_NAME, manual.rawMerchantName, "نفس الاسم اللي `AddTransaction` بيخزنه")
        edit().setCategory(manual.id, "cat-food")
        assertEquals(0, memory.rememberAll(listOf(MerchantPick(" $MANUAL_NO_NAME ", "cat-food"))))
        assertEquals(emptyList(), merchants.listAll())
    }

    @Test fun withoutTheMemoryNothingIsRemembered() = runBlocking<Unit> {
        edit(memory = null).setCategory("t-1", "cat-coffee")
        categorize(memory = null).confirm("t-2", "cat-food")
        assertEquals(emptyList(), merchants.listAll(), "التوصيل القديم (ملفات المرجع) زي ما هو")
    }

    @Test fun transfersAndConfirmedNonPurchasesAreNotSharedAndSharingFailuresAreIgnored() = runBlocking<Unit> {
        val transfer = purchase("t-x", "TEST PERSON", desc = "W-/TOACCT/11112222333344445TOTESTPERSON:ملاحظة", op = "عملية تحويل داخلية")
        val gift = purchase("t-g", "TEST FLOWERS").copy(economicKind = EconomicKind.SUPPORT_GIFT, economicKindConfirmed = true)
        val salary = purchase("t-s", "TEST EMPLOYER", dir = Direction.IN)
        assertFalse(MerchantPick.of(transfer, "cat-gift").shareable, "اسم الشخص اللي حولتله ما يطلعش برّه حسابك")
        assertFalse(MerchantPick.of(gift, "cat-gift").shareable)
        assertFalse(MerchantPick.of(salary, "cat-gift").shareable, "الصادر بس")
        assertTrue(MerchantPick.of(purchase("t-p", "TEST MART"), "cat-food").shareable)
        assertEquals(3, memory.rememberAll(listOf(transfer, gift, salary).map { MerchantPick.of(it, "cat-gift") }))
        assertEquals(emptyList(), shared)

        val failing = MerchantMemory(merchants, ids) { _, _ -> throw IllegalStateException("السيرفر وقع") }
        assertTrue(failing.remember(MerchantPick("TEST MART", "cat-food", shareable = true)))
        assertEquals("cat-food", merchants.findByNormalizedName("test mart")?.verifiedCategoryId, "المحل اتحفظ رغم إن الرفع وقع")
    }

    @Test fun recordAllRemembersTheOwnersPicksThroughTheEffect() = runBlocking<Unit> {
        val space = SmsSpace()
        val world = SmsWorld(listOf(space)).enable()
        val deps = space.importDeps().copy(merchants = merchants, categories = categories, effects = listOf(RememberChosenCategoryEffect(memory)))
        val screen = ReviewSmsInbox(ReviewSmsInboxDeps(ManageSmsInbox(world.inbox, space.parse), ImportStatement(deps), merchants, categories, space.ids))
        world.receive(sms("m1", MART), sms("m2", CAFE))
        val mart = screen.load(SmsReviewTarget(BANK.id, BANK.name)).ready.single { it.merchant == "TEST MART" }
        assertFalse(mart.remembered)
        assertEquals(2, screen.recordAll(mapOf(mart.lineNumber to "cat-coffee"), emptyList()))
        assertEquals("cat-coffee", merchants.findByNormalizedName("test mart")?.verifiedCategoryId, "من غير «نفتكره؟»")
        assertNull(merchants.findByNormalizedName("test cafe"), "اقتراح القاعدة مش اختيار المالك ⇒ ما بيتحفظش")
        assertEquals(listOf("TEST MART" to "cat-coffee"), shared)
        world.receive(sms("m3", MART.replace("SR 40", "SR 41").replace("26/10/07", "26/10/08")))
        val next = screen.load(SmsReviewTarget(BANK.id, BANK.name)).ready.single()
        assertEquals(true to "cat-coffee", next.remembered to next.categoryId, "الرسالة الجاية من نفس المحل")
    }

    @Test fun statementPicksAreRememberedTheNewestWinsAndBackgroundRecordingIsNot() = runBlocking<Unit> {
        val header = "التاريخ,مدين,دائن,الرصيد,التاجر,التصنيف,نوع العملية,التفاصيل\n"
        fun importer(repo: MerchantRepository = merchants) = ImportStatement(
            ImportStatementDeps(
                txns, MemorySourceRecordRepository(), MemoryImportBatchRepository(), repo, categories, MemoryRuleRepository(), MemoryUnitOfWork(listOf(txns)),
                ids, clock, effects = listOf(RememberChosenCategoryEffect(MerchantMemory(repo, ids))),
            ),
        )
        suspend fun commit(rows: String, picks: (List<Int>) -> Map<Int, Id>, byOwner: Boolean = true, repo: MerchantRepository = merchants) {
            val request = ImportRequest("t.csv", header + rows, "acc-test", ImportSourceType.CSV_LEGACY, "w-1", SchemaId.LEGACY, byOwner = byOwner)
            val preview = importer(repo).preview(request)
            importer(repo).commit(request, preview, chosenCategories = picks(preview.lines.map { it.row.lineNumber }))
        }
        commit("2026/10/09,25.00,0.00,975.00,TEST BAKERY,,شراء,أ\n2026/10/08,30.00,0.00,1000.00,TEST BAKERY,,شراء,ب\n", { (a, b) -> mapOf(a to "cat-food", b to "cat-coffee") })
        assertEquals("cat-food", merchants.findByNormalizedName("test bakery")?.verifiedCategoryId, "الأحدث (9 أكتوبر) يكسب")
        commit("2026/10/09,12.00,0.00,963.00,TEST OVEN,,شراء,ج\n", { (a) -> mapOf(a to "cat-food") }, byOwner = false)
        assertNull(merchants.findByNormalizedName("test oven"), "التسجيل التلقائي مش اختيار المالك")
        // فشل الحفظ بعد ما الدفعة اتقفلت ما بيرجّعش التسجيل
        val broken = object : MerchantRepository by merchants {
            override suspend fun saveMany(merchants: List<Merchant>) = throw IllegalStateException("انقطاع وهمي")
        }
        commit("2026/10/09,14.00,0.00,949.00,TEST GRILL,,شراء,د\n", { (a) -> mapOf(a to "cat-food") }, repo = broken)
        val grill = txns.all().single { it.rawMerchantName == "TEST GRILL" }
        assertEquals("cat-food" to true, grill.categoryId to grill.categoryConfirmed)
        assertNull(merchants.findByNormalizedName("test grill"))
    }

    @Test fun inEgyptTheCategoryStaysInEgypt() = runBlocking<Unit> {
        val saudi = MemoryMerchantRepository(listOf(Merchant("merch-00001", "TEST STORE", "TEST STORE", verifiedCategoryId = "cat-sa")))
        val local = MemoryMerchantCategoryRepository()
        val egypt = SpaceMerchantRepository(saudi, local, categories)
        MerchantMemory(egypt, ids).remember(MerchantPick("TEST STORE", "cat-eg"))
        MerchantMemory(egypt, ids).remember(MerchantPick("TEST KIOSK", "cat-eg-2"))
        assertEquals("cat-sa", saudi.findByNormalizedName("test store")?.verifiedCategoryId, "تصنيف السعودية زي ما هو")
        assertNull(saudi.findByNormalizedName("test kiosk")?.verifiedCategoryId, "المحل الجديد مشترك من غير تصنيف")
        assertEquals(setOf("cat-eg", "cat-eg-2"), local.listAll().values.toSet())
        assertEquals("cat-eg", egypt.findByNormalizedName("test store")?.verifiedCategoryId)
        // التصنيف في مصر بييجي مؤكد من نفس الطريق
        val egyptTxns = MemoryTransactionRepository(listOf(purchase("t-eg", "TEST STORE").copy(currency = Currency.EGP)))
        val report = CategorizeTransactions(
            CategorizeTransactionsDeps(egyptTxns, egypt, categories, MemoryRuleRepository(), MemoryUnitOfWork(listOf(egyptTxns)), clock),
        ).plan(egyptTxns.findByIds(listOf("t-eg")))
        assertEquals("cat-eg" to CategorizationSource.VERIFIED_MERCHANT.wire, report.changed.single().let { it.toCategoryId to it.source })
    }
}
