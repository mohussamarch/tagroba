package app.masroufy.memory

import app.masroufy.core.BACKUP_GROUPS
import app.masroufy.core.BackupRow
import app.masroufy.core.FullBackupData
import app.masroufy.core.backupRowId
import app.masroufy.core.emptyBackupData
import app.masroufy.port.FullBackupPort
import app.masroufy.port.RepairBackupPort
import app.masroufy.port.SavedBackup

/** النسخة الشاملة في الذاكرة — نقل `memory/fullBackup.ts`. */
class MemoryFullBackup(initial: FullBackupData = emptyBackupData(), private var profile: BackupRow? = null) : FullBackupPort {
    private val data = BACKUP_GROUPS.associateWith { group -> initial.getValue(group).toMutableList() }

    override suspend fun read(): FullBackupData = data.mapValues { it.value.toList() }

    override suspend fun addMissing(data: FullBackupData): Map<String, Int> {
        val added = LinkedHashMap<String, Int>()
        for (group in BACKUP_GROUPS) {
            val stored = this.data.getValue(group)
            val ids = stored.map { backupRowId(group, it) }.toSet()
            val rows = data.getValue(group).filter { backupRowId(group, it) !in ids }
            stored += rows
            added[group] = rows.size
        }
        return added
    }

    override suspend fun readProfile(): BackupRow? = profile

    override suspend fun addProfileIfMissing(profile: BackupRow): Boolean {
        if (this.profile != null) return false
        this.profile = profile
        return true
    }
}

/** البلاد غير السعودية في النسخة الشاملة (الإصدار 3) في الذاكرة: سجل + بيانات لكل بلد + أزواج التحويل لنفسك. */
class MemorySpacesBackup(
    val registry: MemorySpaceRegistry = MemorySpaceRegistry(),
    private val spaces: MutableMap<String, MemoryFullBackup> = LinkedHashMap(),
    transfers: List<BackupRow> = emptyList(),
) : app.masroufy.port.SpacesBackupPort {
    private val pairs = LinkedHashMap<String, BackupRow>().apply { transfers.forEach { put(it["id"] as String, it) } }

    override suspend fun registry(): List<app.masroufy.core.Space> = registry.listAll()

    override suspend fun addSpaceIfMissing(space: app.masroufy.core.Space): Boolean = registry.addIfMissing(space)

    override fun dataOf(spaceId: String): MemoryFullBackup = spaces.getOrPut(spaceId) { MemoryFullBackup() }

    override suspend fun readSpaceTransfers(): List<BackupRow> = pairs.values.toList()

    override suspend fun addMissingSpaceTransfers(rows: List<BackupRow>): Int = rows.count { row ->
        val id = row["id"] as String
        if (id in pairs) false else {
            pairs[id] = row
            true
        }
    }
}

/** نسخ ما قبل الصيانة في الذاكرة. `truncate` بيقلّد كتابة ناقصة (نص الوحدات بس). */
class MemoryRepairBackup(private val truncate: Boolean = false) : RepairBackupPort {
    val files = LinkedHashMap<String, String>()

    override suspend fun save(fileName: String, content: String): SavedBackup {
        val stored = if (truncate) content.substring(0, content.length / 2) else content
        files[fileName] = stored
        return SavedBackup("memory:$fileName", stored.encodeToByteArray().size.toLong())
    }
}
