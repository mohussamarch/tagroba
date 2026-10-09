package app.masroufy.data

import app.masroufy.core.BackupRow
import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.FullBackupData
import app.masroufy.core.MerchantCategory
import app.masroufy.core.Merchant
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.ReviewState
import app.masroufy.core.Space
import app.masroufy.core.SpaceTransfer
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.emptyBackupData
import app.masroufy.core.spaceTransferId
import app.masroufy.memory.MemoryFullBackup
import app.masroufy.memory.MemorySpaceRegistry
import app.masroufy.memory.MemorySpacesBackup
import app.masroufy.usecase.FullBackup
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** النسخة الشاملة للحساب كله بالبلاد (الإصدار 3 — §41.1 · §64) — بيانات مخترعة. */
class SpacesBackupTest {
    private val egypt = Space("eg", "مصر", "EG", Currency.EGP, "2026-10-04T10:00:00.000Z")
    private val now = "2026-10-04T12:00:00.000Z"

    private fun txn(id: String, dir: Direction, minor: Long, currency: Currency, kind: EconomicKind = EconomicKind.PURCHASE, merchant: String? = null) = Transaction(
        id = id, occurredAt = "2026-09-10", datePrecision = "day", sourceOrder = 1, economicKind = kind, economicKindConfirmed = true,
        observedDirection = dir, amountMinor = minor, currency = currency, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "c", updatedAt = "c", walletId = "wallet-bank", merchantId = merchant,
    )

    /** الجذر: الحساب (شخص وتاجر) + السعودية (محفظة وعمليتين منهم رجل طالعة). */
    private fun root(): FullBackupData = emptyBackupData().also {
        it.getValue("people") += ReferenceCodecs.people.toStore(Person("p-1", "شخص في البلدين"))
        it.getValue("merchants") += ReferenceCodecs.merchants.toStore(Merchant("merch-1", "تاجر وهمي", "تاجر وهمي"))
        it.getValue("wallets") += ReferenceCodecs.wallets.toStore(Wallet("wallet-bank", "بنك وهمي", Currency.SAR, "bank", 100_000, "2025-01-01", "1234"))
        it.getValue("transactions") += LedgerCodecs.transactions.toStore(txn("t-food", Direction.OUT, 3_000, Currency.SAR, merchant = "merch-1"))
        it.getValue("transactions") += LedgerCodecs.transactions.toStore(txn("t-out", Direction.OUT, 200_000, Currency.SAR, EconomicKind.INTERNAL_TRANSFER))
    }

    /** مصر: محفظة · عملية من نفس التاجر المشترك · رجل داخلة · دين على نفس الشخص (بعملته) · تصنيف التاجر في مصر. */
    private fun egyptData(): FullBackupData = emptyBackupData().also {
        it.getValue("wallets") += ReferenceCodecs.wallets.toStore(Wallet("wallet-bank", "بنك مصري وهمي", Currency.EGP, "bank", 50_000, "2026-01-01", "9876"))
        it.getValue("categories") += ReferenceCodecs.categories.toStore(app.masroufy.core.Category("cat-x", null, "أكل", "utensils", "#AA3344", "#FF8899", true, 1))
        it.getValue("transactions") += LedgerCodecs.transactions.toStore(txn("t-kiosk", Direction.OUT, 7_500, Currency.EGP, merchant = "merch-1"))
        it.getValue("transactions") += LedgerCodecs.transactions.toStore(txn("t-in", Direction.IN, 2_469_120, Currency.EGP, EconomicKind.INTERNAL_TRANSFER))
        it.getValue("obligations") += LedgerCodecs.obligations.toStore(Obligation("o-eg", "p-1", "t-kiosk", ObligationKind.RECEIVABLE, 5_000, Currency.EGP))
        it.getValue("merchantCategories") += SpaceCodecs.merchantCategories.toStore(MerchantCategory("merch-1", "cat-x"))
    }

    private val pair = SpaceCodecs.spaceTransfers.toStore(SpaceTransfer(spaceTransferId(DEFAULT_SPACE_ID, "t-out"), DEFAULT_SPACE_ID, "t-out", 200_000, Currency.SAR, "eg", "t-in", 2_469_120, Currency.EGP, now))

    private fun withEgypt() = MemorySpacesBackup(MemorySpaceRegistry(listOf(egypt)), linkedMapOf("eg" to MemoryFullBackup(egyptData())), listOf(pair))

    @Test fun withoutAnotherCountryTheFileIsVersionTwoByteForByte() = runBlocking<Unit> {
        val profile = mapOf<String, Any?>("displayName" to "حساب وهمي", "payday" to 28L)
        val v2 = FullBackup(MemoryFullBackup(root(), profile)).create(now).toJsonText()
        val withPort = FullBackup(MemoryFullBackup(root(), profile), MemorySpacesBackup()).create(now).toJsonText()
        assertEquals(v2, withPort, "من غير بلد تانية: نفس الملف بالحرف")
        assertTrue("\"schemaVersion\":2," in v2 && "\"spaces\"" !in v2 && "merchantCategories" !in v2)
        reportFile("kotlin-spaces-backup-v2.json").writeText(withPort)
    }

    @Test fun withEgyptItIsVersionThreeAndComesBackWhole() = runBlocking<Unit> {
        val file = FullBackup(MemoryFullBackup(root()), withEgypt()).create(now)
        val text = file.toJsonText()
        assertTrue("\"schemaVersion\":3," in text)
        assertEquals(listOf("eg"), file.spaces!!.map { it.space.id })
        assertEquals(1L, file.counts["spaceTransfers"])
        assertTrue("\"spaces\":[{\"id\":\"eg\",\"name\":\"مصر\",\"countryCode\":\"EG\",\"currency\":\"EGP\"" in text)
        reportFile("kotlin-spaces-backup-v3.json").writeText(text)

        // حساب جديد فاضي ⇒ استعادة ⇒ نفس البصمة، والإعادة ما بتضيفش حاجة
        val rootB = MemoryFullBackup()
        val spacesB = MemorySpacesBackup()
        val restore = FullBackup(rootB, spacesB)
        val plan = restore.plan(text)
        assertEquals(listOf("eg"), plan.spaces.map { it.targetSpaceId })
        assertTrue(plan.spaces.single().isNew)
        assertEquals(1, plan.spaceTransfersToAdd)
        val outcome = restore.apply(plan.file)
        assertEquals(2, outcome.added["eg/transactions"])
        assertEquals(1, outcome.added["spaces/registry"])
        assertEquals(1, outcome.added["spaceTransfers"])
        assertEquals(listOf(egypt), spacesB.registry())
        assertEquals(file.checksum, FullBackup(rootB, spacesB).create(now).checksum, "اللي رجع هو هو")
        val again = restore.apply(restore.plan(text).file)
        assertEquals(0, again.totalAdded, "نفس النسخة تاني ⇒ ولا حاجة")
    }

    @Test fun eachCountryGoesBackToItsOwnCountryAndLinksFollowTheMerge() = runBlocking<Unit> {
        val text = FullBackup(MemoryFullBackup(root()), withEgypt()).create(now).toJsonText()
        // الحساب فيه مصر بالفعل، وفيه نفس الرجل الداخلة بمعرّف تاني (اتسجلت على جهاز تاني)
        val existingEgypt = emptyBackupData().also {
            it.getValue("wallets") += ReferenceCodecs.wallets.toStore(Wallet("wallet-bank", "بنك مصري وهمي", Currency.EGP, "bank", 50_000, "2026-01-01", "9876"))
            it.getValue("transactions") += LedgerCodecs.transactions.toStore(txn("t-other-id", Direction.IN, 2_469_120, Currency.EGP, EconomicKind.INTERNAL_TRANSFER))
        }
        val spaces = MemorySpacesBackup(MemorySpaceRegistry(listOf(egypt.copy(name = "مصر بتاعتي"))), linkedMapOf("eg" to MemoryFullBackup(existingEgypt)))
        val restore = FullBackup(MemoryFullBackup(), spaces)
        val plan = restore.plan(text)
        assertEquals(false, plan.spaces.single().isNew, "مصر اترجعت لمصر — ما اتعملتش تانية")
        restore.apply(plan.file)
        assertEquals("مصر بتاعتي", spaces.registry().single().name, "السجل الموجود ما اتكتبش فوقه")
        val ids = spaces.dataOf("eg").read().getValue("transactions").map { it["id"] }
        assertEquals(listOf("t-other-id", "t-kiosk"), ids, "الرجل الداخلة هي هي — ما اتكررتش")
        assertEquals("t-other-id", spaces.readSpaceTransfers().single()["toTransactionId"], "الزوج بيشاور على العملية الموجودة")
    }

    @Test fun brokenVersionThreeFilesAreRejected() = runBlocking<Unit> {
        val text = FullBackup(MemoryFullBackup(root()), withEgypt()).create(now).toJsonText()
        val restore = FullBackup(MemoryFullBackup(), MemorySpacesBackup())
        suspend fun reject(why: String, change: (String) -> String) = assertFailsWith<IllegalArgumentException>(why) { restore.plan(change(text)) }
        reject("بصمة متغيرة") { it.replace("\"t-kiosk\"", "\"t-kioks\"") }
        reject("السعودية في البلاد") { it.replace("\"countryCode\":\"EG\"", "\"countryCode\":\"SA\"") }
        reject("زوج على عملية مش موجودة") { it.replace("\"toTransactionId\":\"t-in\"", "\"toTransactionId\":\"t-nope\"") }
        reject("زوج بمبلغ غير العملية") { it.replace("\"toAmountMinor\":2469120", "\"toAmountMinor\":2469121") }
        // علاقة جوه مصر لشخص مش في الحساب
        val orphan = FullBackup(MemoryFullBackup(emptyBackupData()), withEgypt())
        assertFailsWith<IllegalArgumentException>("دين مصري على شخص مش في الحساب") { orphan.create(now) }
        // من غير مكان للبلاد: الإصدار 3 ما يترجعش نصه في صمت
        assertFailsWith<IllegalStateException> { FullBackup(MemoryFullBackup()).apply(FullBackup(MemoryFullBackup()).plan(text).file) }
    }

    @Suppress("unused")
    private fun rows(data: FullBackupData, group: String): List<BackupRow> = data.getValue(group)
}
