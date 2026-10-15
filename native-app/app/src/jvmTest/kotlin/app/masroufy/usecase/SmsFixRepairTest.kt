package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportCounts
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.ReviewState
import app.masroufy.core.SaudiBankSmsReader
import app.masroufy.core.SmsKind
import app.masroufy.core.SmsParseResult
import app.masroufy.core.SmsRow
import app.masroufy.core.SourceRecord
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryRepairBackup
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryWalletRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * sms-fix (HANDOVER §6.5-3) — بيانات مخترعة بس: A = «بنك أ» آخره 2222 · B = «بنك ب» آخره 1111 · 3333 مش لأي محفظة.
 */
class SmsFixRepairTest {
    private val a = Wallet("wA", "بنك أ", Currency.SAR, "bank", 0, "2026-01-01", "2222", null)
    private val b = Wallet("wB", "بنك ب", Currency.SAR, "bank", 0, "2026-01-01", "1111", null)
    private val bToA = "حوالة بين حساباتك\nمن:1111\nالى:2222\nمبلغ:SAR 500.00"

    private fun row(kind: SmsKind, direction: Direction, raw: String) = SmsRow(1, "2026-03-03", 50_000, direction, "", "SMS:r", "", "", raw, kind = kind)

    private fun item(id: String, r: SmsRow) = InboxItem(id, "AlRajhiBank", "2026-03-03T10:00:00", SmsParseResult.Ok(r))

    @Test
    fun ownTransferRoutesBothLegsAndOnlyTheIncomingLegWhenTheOtherAccountIsNotAWallet() {
        val r = row(SmsKind.OWN_TRANSFER, Direction.OUT, bToA)
        val both = routeSender(listOf(a, b), "alrajhibank", emptyMap(), listOf(item("m1", r)), false)
        assertEquals(setOf("wA", "wB"), both.routes.map { it.target.walletId }.toSet())
        val legs = legRowsOf(r, listOf(a, b))
        assertEquals(listOf("wB" to Direction.OUT, "wA" to Direction.IN), legs.map { legWallet(it, "alrajhibank", listOf(a, b), emptyMap())?.id to it.direction })
        val onlyA = legRowsOf(r, listOf(a))
        assertEquals(listOf(Direction.IN), onlyA.map { it.direction })
    }

    @Test
    fun otherAccountIncomingWaitsWithTheQuestionInsteadOfLandingInTheOnlyBankWallet() {
        val r = row(SmsKind.TRANSFER_IN, Direction.IN, "حوالة داخلية واردة\nمن:7777\nالى:3333")
        assertEquals(TextKey.SMS_WAIT_UNKNOWN_ACCOUNT, waitReasonOf(r, targetOf(a, listOf(a), false)))
        // اتجاه رسالة لحساب المحفظة نفسها بيمشي عادي
        val mine = row(SmsKind.TRANSFER_IN, Direction.IN, "حوالة داخلية واردة\nمن:7777\nالى:2222")
        assertNull(waitReasonOf(legRowsOf(mine, listOf(a)).single(), targetOf(a, listOf(a), false)))
    }

    @Test
    fun learnedShapeNeverOverridesEachMessagesOwnAccountLines() {
        // نفس الشكل (نفس بصمة التعلّم) برسالتين: واحدة داخلة لـA وواحدة طالعة منها — كل واحدة باتجاه أرقامها
        val learnKey = "alrajhi/transfer-own-accounts"
        val incoming = row(SmsKind.OWN_TRANSFER, Direction.OUT, bToA).copy(learnKey = learnKey)
        val outgoing = row(SmsKind.OWN_TRANSFER, Direction.OUT, "حوالة بين حساباتك\nمن:2222\nالى:1111").copy(learnKey = learnKey)
        assertEquals(Direction.IN, legRowsOf(incoming, listOf(a)).single().direction)
        assertEquals(Direction.OUT, legRowsOf(outgoing, listOf(a)).single().direction)
    }

    @Test
    fun repairPreviewApplyAndUndoTheOldOutgoingOwnTransfer() = runBlocking {
        val batch = ImportBatch("bSms", ImportSourceType.SMS, "h", "bank-sms.json", "2026-03-03T10:00:00Z", committedState(), ImportCounts(1, 1, 0, 0, 0, 0))
        val tx = transaction("t1", "wA", Direction.OUT)
        val record = SourceRecord("r1", "bSms", "بنك أ", "SMS:r1", "hash", 1, bToA, "t1", MatchingState.NEW, "")
        val txns = MemoryTransactionRepository(listOf(tx))
        val sources = MemorySourceRecordRepository(listOf(record))
        val repair = RepairOldSms(
            txns, sources, MemoryImportBatchRepository(listOf(batch)), MemoryWalletRepository(listOf(a)),
            listOf(SaudiBankSmsReader), MemoryRepairBackup(), FixedClock("2026-10-15T00:00:00Z"),
        )
        val preview = repair.preview()
        assertEquals(listOf(SmsRepairLine("t1", "2026-03-03", 50_000, "wA", "wA", Direction.OUT, Direction.IN)), preview)
        val result = assertNotNull(repair.apply(setOf("t1")))
        assertTrue(result.backup.fileName.contains("sms-repair"))
        assertEquals(Direction.IN, txns.findByIds(listOf("t1")).single().observedDirection)
        assertEquals(EconomicKind.INTERNAL_TRANSFER, txns.findByIds(listOf("t1")).single().economicKind)
        assertTrue(repair.preview().isEmpty())
        repair.undo(result.undo)
        assertEquals(tx, txns.findByIds(listOf("t1")).single())
    }

    private fun committedState() = ImportBatchState.entries.first { it != ImportBatchState.STAGED && it != ImportBatchState.REVERTED }

    private fun transaction(id: String, wallet: String, direction: Direction) = Transaction(
        id = id, occurredAt = "2026-03-03", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.entries.first { it != EconomicKind.INTERNAL_TRANSFER },
        economicKindConfirmed = false, observedDirection = direction, amountMinor = 50_000, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "2026-03-03T10:00:00Z",
        updatedAt = "2026-03-03T10:00:00Z", walletId = wallet,
    )
}
