package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Space
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.defaultSpace
import app.masroufy.memory.FixedClock
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.usecase.SpaceBook
import app.masroufy.usecase.TransactionLinkRepos
import app.masroufy.usecase.TransferBetweenSpaces
import app.masroufy.usecase.TransferBetweenSpacesDeps
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.Source
import dev.gitlive.firebase.firestore.code
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * التحويل لنفسك (§64) على فايربيز **بقواعد المشروع الحقيقية** (`emulator-auth/` 9099 + 8089) — حسابات ومبالغ مخترعة:
 * الزوج والرجلين في دفعة كتابة واحدة · رجل اتمنعت (مكان حساب تاني) ⇒ **ولا حاجة اتكتبت** · الفك بيرجّع الرجلين يتسألوا.
 */
@RunWith(AndroidJUnit4::class)
class SpaceTransfersOnFirestoreTest {
    private fun txn(id: String, dir: Direction, minor: Long, currency: Currency) = Transaction(
        id = id, occurredAt = "2026-09-10", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false,
        observedDirection = dir, amountMinor = minor, currency = currency, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "c", updatedAt = "c", walletId = "wallet-bank",
    )

    private fun book(space: Space, c: FirestoreContainer) = SpaceBook(
        space, c.transactions, c.wallets,
        TransactionLinkRepos(c.roscaEntries, c.installmentPayments, c.installmentPlans, c.zakatPayments, c.eventLinks, c.projectLinks, c.allocations, c.obligations, c.settlements),
    )

    @Test fun pairAndBothLegsAreOneBatch() = runBlocking<Unit> {
        withTimeout(120_000) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val auth = FirebaseAuthAdapter(AuthEmulator.auth("stx"), scope)
            val me = auth.registerWithEmail("stx-${java.util.UUID.randomUUID().toString().take(8)}@example.com", "secret1")
            val other = "kt-not-me-" + java.util.UUID.randomUUID()
            val db = AuthEmulator.firestore("stx")
            val account = FirestoreSpace.forAccount(db, me.uid)
            val egypt = Space("eg", "مصر", "EG", Currency.EGP, "2026-10-04T10:00:00.000Z")
            val sa = FirestoreContainer(account, FirestoreSpace.forSpace(db, me.uid, DEFAULT_SPACE_ID), DEFAULT_SPACE_ID)
            val eg = FirestoreContainer(account, FirestoreSpace.forSpace(db, me.uid, "eg"), "eg")
            FirestoreSpaceRegistry(account).addIfMissing(egypt)
            sa.wallets.save(Wallet("wallet-bank", "بنك وهمي", Currency.SAR, "bank", 0, "2026-01-01"))
            eg.wallets.save(Wallet("wallet-bank", "بنك مصري وهمي", Currency.EGP, "bank", 0, "2026-01-01"))
            sa.transactions.saveMany(listOf(txn("t-out", Direction.OUT, 200_000, Currency.SAR)))
            eg.transactions.saveMany(listOf(txn("t-in", Direction.IN, 2_469_120, Currency.EGP)))
            val books = mapOf(DEFAULT_SPACE_ID to book(defaultSpace(), sa), "eg" to book(egypt, eg))
            fun transfers(spaceOf: (String) -> FirestoreSpace) = TransferBetweenSpaces(
                TransferBetweenSpacesDeps(sa.spaceTransfers, FirestoreSpaceTransferWriter(account, spaceOf), { books[it] }, SequentialIdGenerator(), FixedClock("2026-10-04T10:00:00.000Z")),
            )
            suspend fun kindOn(c: FirestoreContainer, id: String) = c.spaceRoot.collection("transactions").document(id).get(Source.SERVER).rawData()!!["economicKind"]
            val pairRef = account.collection("spaceTransfers").document("stx-default-t-out")

            // رجل مصر رايحة لمكان حساب تاني ⇒ القواعد بترفض ⇒ الدفعة كلها اترفضت: لا زوج ولا رجل سعودية اتغيرت
            val broken = transfers { id -> if (id == "eg") FirestoreSpace.forSpace(db, other, "eg") else sa.spaceRoot }
            val denied = assertFailsWith<FirebaseFirestoreException> { broken.linkExisting(DEFAULT_SPACE_ID, "t-out", "eg", "t-in") }
            assertEquals(FirestoreExceptionCode.PERMISSION_DENIED, denied.code)
            assertNull(pairRef.get(Source.SERVER).rawData(), "الزوج ما اتكتبش")
            assertEquals("unclassified", kindOn(sa, "t-out"), "الرجل السعودية ما اتلمستش")

            // الطبيعي: الزوج والرجلين مع بعض
            val ok = transfers { id -> if (id == "eg") eg.spaceRoot else sa.spaceRoot }
            val pair = ok.linkExisting(DEFAULT_SPACE_ID, "t-out", "eg", "t-in")
            val stored = pairRef.get(Source.SERVER).rawData()!!
            assertEquals(200_000L, stored["fromAmountMinor"])
            assertEquals(2_469_120L, stored["toAmountMinor"])
            assertEquals("EGP", stored["toCurrency"])
            assertEquals(false, stored.keys.any { "rate" in it.lowercase() }, "مفيش سعر متخزن")
            assertEquals("internal_transfer", kindOn(sa, "t-out"))
            assertEquals("internal_transfer", kindOn(eg, "t-in"))
            assertEquals(listOf(pair.id), sa.spaceTransfers.listAll().map { it.id })

            ok.unlink(pair.id)
            assertNull(pairRef.get(Source.SERVER).rawData())
            assertEquals("unclassified", kindOn(sa, "t-out"))
            assertEquals("unclassified", kindOn(eg, "t-in"), "الرجلين موجودين وبيتسألوا تاني")
            auth.signOut()
            scope.cancel()
        }
    }
}
