package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Merchant
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.ReviewState
import app.masroufy.core.Space
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.defaultSpace
import app.masroufy.memory.FixedClock
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.usecase.FullBackup
import app.masroufy.usecase.SpaceBook
import app.masroufy.usecase.TransactionLinkRepos
import app.masroufy.usecase.TransferBetweenSpaces
import app.masroufy.usecase.TransferBetweenSpacesDeps
import dev.gitlive.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * النسخة الشاملة **الإصدار 3** (§41.1 · §64) على Firestore Emulator (8088) بحالة الاستخدام نفسها — بيانات مخترعة:
 * حساب فيه السعودية ومصر وتحويل لنفسك ⇒ نسخة ⇒ استعادة في حساب جديد فاضي ⇒ **نفس البصمة**، ومصر في مكانها، والإعادة ما بتضيفش حاجة.
 */
@RunWith(AndroidJUnit4::class)
class SpacesBackupOnFirestoreTest {
    private fun txn(id: String, dir: Direction, minor: Long, currency: Currency, merchant: String? = null) = Transaction(
        id = id, occurredAt = "2026-09-10", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true,
        observedDirection = dir, amountMinor = minor, currency = currency, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "c", updatedAt = "c", walletId = "wallet-bank", merchantId = merchant,
    )

    private fun book(space: Space, c: FirestoreContainer) = SpaceBook(
        space, c.transactions, c.wallets,
        TransactionLinkRepos(c.roscaEntries, c.installmentPayments, c.installmentPlans, c.zakatPayments, c.eventLinks, c.projectLinks, c.allocations, c.obligations, c.settlements),
    )

    @Test fun versionThreeRoundTripIntoAFreshAccount() = runBlocking<Unit> {
        withTimeout(150_000) {
            val db = Emulator.firestore()
            val uidA = "kt-bk3-a-" + java.util.UUID.randomUUID()
            val accountA = FirestoreSpace.forAccount(db, uidA)
            val egypt = Space("eg", "مصر", "EG", Currency.EGP, "2026-10-04T10:00:00.000Z")
            val sa = FirestoreContainer(accountA, FirestoreSpace.forSpace(db, uidA, DEFAULT_SPACE_ID), DEFAULT_SPACE_ID)
            val eg = FirestoreContainer(accountA, FirestoreSpace.forSpace(db, uidA, "eg"), "eg")
            sa.profile.save(app.masroufy.core.emptyProfile().copy(displayName = "حساب وهمي", payday = 28))
            sa.people.save(Person("p-1", "شخص في البلدين"))
            sa.merchants.saveMany(listOf(Merchant("merch-1", "تاجر وهمي", "تاجر وهمي")))
            sa.wallets.save(Wallet("wallet-bank", "بنك وهمي", Currency.SAR, "bank", 100_000, "2025-01-01"))
            sa.transactions.saveMany(listOf(txn("t-food", Direction.OUT, 3_000, Currency.SAR, "merch-1"), txn("t-out", Direction.OUT, 200_000, Currency.SAR)))
            sa.spaces.addIfMissing(egypt)
            eg.wallets.save(Wallet("wallet-bank", "بنك مصري وهمي", Currency.EGP, "bank", 50_000, "2026-01-01"))
            eg.transactions.saveMany(listOf(txn("t-kiosk", Direction.OUT, 7_500, Currency.EGP, "merch-1"), txn("t-in", Direction.IN, 2_469_120, Currency.EGP)))
            eg.obligations.saveMany(listOf(Obligation("o-eg", "p-1", "t-kiosk", ObligationKind.RECEIVABLE, 5_000, Currency.EGP)))
            eg.merchants.saveMany(listOf(eg.merchants.listAll().single().copy(verifiedCategoryId = "cat-x")))
            eg.categories.save(app.masroufy.core.Category("cat-x", null, "أكل", "utensils", "#AA3344", "#FF8899", true, 1))
            val books = mapOf(DEFAULT_SPACE_ID to book(defaultSpace(), sa), "eg" to book(egypt, eg))
            TransferBetweenSpaces(
                TransferBetweenSpacesDeps(sa.spaceTransfers, FirestoreSpaceTransferWriter(accountA) { if (it == "eg") eg.spaceRoot else sa.spaceRoot }, { books[it] }, SequentialIdGenerator(), FixedClock("2026-10-04T12:00:00.000Z")),
            ).linkExisting(DEFAULT_SPACE_ID, "t-out", "eg", "t-in")

            val file = FullBackup(sa.fullBackup, sa.spacesBackup).create("2026-10-04T12:00:00.000Z")
            val text = file.toJsonText()
            assertTrue("\"schemaVersion\":3," in text)
            assertEquals(listOf("eg"), file.spaces!!.map { it.space.id })
            assertEquals(2, file.spaces!!.single().data.getValue("transactions").size)
            assertEquals(1, file.spaceTransfers.size)

            // حساب جديد فاضي ⇒ استعادة
            val uidB = "kt-bk3-b-" + java.util.UUID.randomUUID()
            val accountB = FirestoreSpace.forAccount(db, uidB)
            val b = FirestoreContainer(accountB, FirestoreSpace.forSpace(db, uidB, DEFAULT_SPACE_ID), DEFAULT_SPACE_ID)
            val restore = FullBackup(b.fullBackup, b.spacesBackup)
            val outcome = restore.apply(restore.plan(text).file)
            assertEquals(2, outcome.added["eg/transactions"])
            assertEquals(1, outcome.added["spaceTransfers"])
            assertEquals(listOf("eg"), b.spaces.listAll().map { it.id })
            val egB = FirestoreSpace.forSpace(db, uidB, "eg")
            assertEquals(setOf("t-kiosk", "t-in"), egB.collection("transactions").get(Source.SERVER).documents.map { it.id }.toSet(), "عمليات مصر في مكان مصر")
            assertEquals(setOf("t-food", "t-out"), accountB.collection("transactions").get(Source.SERVER).documents.map { it.id }.toSet(), "والسعودية في الجذر بس")
            assertEquals("cat-x", egB.collection("merchantCategories").document("merch-1").get(Source.SERVER).rawData()!!["categoryId"])
            assertEquals(null, accountB.collection("merchants").document("merch-1").get(Source.SERVER).rawData()!!["verifiedCategoryId"], "تصنيف مصر ما وصلش للتاجر المشترك")
            assertEquals(file.checksum, FullBackup(b.fullBackup, b.spacesBackup).create("2026-10-04T12:00:00.000Z").checksum, "اللي رجع هو هو")
            assertEquals(0, restore.apply(restore.plan(text).file).totalAdded, "إعادة الاستعادة ما بتضيفش حاجة")
        }
    }
}
