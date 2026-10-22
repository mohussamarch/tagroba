package app.masroufy.ui.screens.more

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackupPickCheckTest {
    @Test
    fun onlyJsonNamesPass() {
        assertNull(checkBackupPick("masroufy-backup-2026-01-15.json", 2_000))
        assertNull(checkBackupPick("COPY.JSON", null))
        assertEquals(PickRejection.NOT_BACKUP, checkBackupPick("holiday.mp4", 1_000))
        assertEquals(PickRejection.NOT_BACKUP, checkBackupPick("backup", 10))
        assertEquals(PickRejection.NOT_BACKUP, checkBackupPick("backup.json.exe", 10))
    }

    @Test
    fun overFiftyMegabytesIsRejected() {
        assertNull(checkBackupPick("a.json", MAX_BACKUP_PICK_BYTES))
        assertEquals(PickRejection.TOO_BIG, checkBackupPick("a.json", MAX_BACKUP_PICK_BYTES + 1))
    }
}
