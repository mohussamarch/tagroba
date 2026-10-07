package app.masroufy.data

import app.masroufy.core.Person
import app.masroufy.core.TRANSFER_PARTIES_GROUP
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict
import app.masroufy.core.emptyBackupData
import app.masroufy.memory.MemoryFullBackup
import app.masroufy.usecase.FullBackup
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** قرارات «زون التحويلات» (§60) في النسخة الشاملة — بيانات مخترعة. */
class TransferBackupTest {
    private val decision = TransferParty("سامي#4567", "سامي", "4567", TransferVerdict.PERSON, "p-1", "2026-10-03T00:00:00.000Z")

    private fun account(withParty: Boolean) = emptyBackupData().also {
        it.getValue("people") += ReferenceCodecs.people.toStore(Person("p-1", "شخص وهمي"))
        if (withParty) it.getValue(TRANSFER_PARTIES_GROUP) += TransferCodecs.transferParties.toStore(decision)
    }

    @Test fun decisionsTravelAndComeBackWithTheSameChecksum() = runBlocking<Unit> {
        val source = account(withParty = true)
        val file = FullBackup(MemoryFullBackup(source)).create("2026-10-03T00:00:00.000Z")
        val text = file.toJsonText()
        assertTrue("\"transferParties\"" in text)
        assertFalse("\"roscas\"" in text, "«المستحقات» الفاضية ما بتتكتبش حتى لو الأطراف اتكتبت")
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        val outcome = restore.apply(restore.plan(text).file)
        assertEquals(1, outcome.added[TRANSFER_PARTIES_GROUP])
        assertEquals(source.getValue(TRANSFER_PARTIES_GROUP), target.read().getValue(TRANSFER_PARTIES_GROUP))
        assertEquals(file.checksum, FullBackup(target).create("2026-10-03T00:00:00.000Z").checksum)
    }

    @Test fun withoutDecisionsTheGroupIsNotWritten() = runBlocking<Unit> {
        val text = FullBackup(MemoryFullBackup(account(withParty = false))).create("2026-10-03T00:00:00.000Z").toJsonText()
        assertFalse("\"transferParties\"" in text)
    }

    @Test fun aDecisionPointingAtAMissingPersonOrAFullNumberIsRefused() = runBlocking<Unit> {
        val noPerson = account(withParty = true).also { it.getValue("people").clear() }
        val e = assertFailsWith<IllegalArgumentException> { FullBackup(MemoryFullBackup(noPerson)).create("2026-10-03T00:00:00.000Z") }
        assertTrue(TRANSFER_PARTIES_GROUP in (e.message ?: ""), e.message)
        val fullNumber = account(withParty = false).also {
            it.getValue(TRANSFER_PARTIES_GROUP) += TransferCodecs.transferParties.toStore(decision.copy(last4 = "12345678"))
        }
        assertFailsWith<IllegalArgumentException> { FullBackup(MemoryFullBackup(fullNumber)).create("2026-10-03T00:00:00.000Z") }
    }
}
