package app.masroufy.usecase

import app.masroufy.core.ACCOUNT_DATA_GROUPS
import app.masroufy.core.BACKUP_LABELS
import app.masroufy.core.BackupError
import app.masroufy.core.BackupMerge
import app.masroufy.core.BackupRow
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.FullBackupData
import app.masroufy.core.LATER_BACKUP_GROUPS
import app.masroufy.core.NEW_APP_BACKUP_GROUPS
import app.masroufy.core.SPACE_GROUPS
import app.masroufy.core.Space
import app.masroufy.core.TextKey
import app.masroufy.core.backupRowId
import app.masroufy.core.canonicalBackup
import app.masroufy.core.checkBackupFinance
import app.masroufy.core.checkFullBackupData
import app.masroufy.core.checkSpaceTransferRows
import app.masroufy.core.emptyBackupData
import app.masroufy.core.exportedSpaceData
import app.masroufy.core.mergeFullBackupDetailed
import app.masroufy.core.normalizeBudgetIds
import app.masroufy.core.normalizeLegacySilverAssets
import app.masroufy.core.pointLinesAtLiveBudgets
import app.masroufy.core.spaceBackupData
import app.masroufy.core.spaceBackupHeader
import app.masroufy.core.spaceFromBackup
import app.masroufy.core.spaceTransferId
import app.masroufy.core.uiText
import app.masroufy.port.SpacesBackupPort

/**
 * الإصدار 3 من النسخة الشاملة (§41.1 · §64) — البلاد غير السعودية والتحويل لنفسك. اتفصل من `FullBackup.kt` (حد الـ300 سطر).
 * - **الاسترجاع بيربط كل بلد ببلدها** (بلد واحدة = حساب واحد — رد المالك §64-٢): مصر في الملف ⇒ مصر في الحساب (أو بتتعمل لو مش موجودة).
 * - البلد بتورث تحويلات معرّفات **الحساب** من دمج الجذر (الأشخاص · التجار · الوسوم · المناسبات) — عملية مصرية بشخص اتدمج في شخص موجود
 *   بتشاور على الموجود. والتحويل لنفسك بياخد معرّفات العمليات بعد الدمج في البلدين.
 * - زي الإصدار 2: **الموجود ما يتدهسش** والإعادة ما بتضيفش حاجة.
 */
data class SpaceBackupEntry(val space: Space, val data: FullBackupData, val counts: Map<String, Any?>)

data class SpaceRestorePlan(val space: Space, val targetSpaceId: String, val isNew: Boolean, val lines: List<BackupPlanLine>, val totalToAdd: Int)

/** مفتاح عدد أزواج التحويل لنفسك في `counts` ومفتاحه في نتيجة الاستعادة. */
const val SPACE_TRANSFERS_COUNT = "spaceTransfers"

internal class SpacesPlan(val spaces: List<SpaceRestorePlan>, val transfersToAdd: Int) {
    val total: Int get() = spaces.sumOf { it.totalToAdd } + transfersToAdd
}

/** البلاد زي ما بتتكتب في الملف: الرأس + بياناتها (من غير مجموعات التطبيق الجديد الفاضية) + عدادها. */
internal fun spacesAsWritten(entries: List<SpaceBackupEntry>): List<Map<String, Any?>> = entries.map { e ->
    val shown = exportedSpaceData(e.data)
    spaceBackupHeader(e.space).apply {
        put("data", shown)
        put("counts", e.counts.filterKeys { it !in NEW_APP_BACKUP_GROUPS || it in shown })
    }
}

internal fun signedV3(data: Any?, profile: Any?, spaces: Any?, transfers: Any?): String =
    canonicalBackup(linkedMapOf("data" to data, "profile" to profile, "spaces" to spaces, "spaceTransfers" to transfers))

private fun accountIds(vararg sources: FullBackupData): Map<String, Set<String>> =
    ACCOUNT_DATA_GROUPS.associateWith { g -> sources.flatMap { s -> s[g].orEmpty().map { backupRowId(g, it) } }.toSet() }

private fun spaceCounts(data: FullBackupData): Map<String, Any?> = SPACE_GROUPS.associateWith { data[it].orEmpty().size.toLong() }

/** نسخة: كل البلاد في السجل (المؤرشفة كمان) + الأزواج. `null` = مفيش بلد تانية ⇒ الإصدار 2. */
internal suspend fun readSpaces(port: SpacesBackupPort, root: FullBackupData): Pair<List<SpaceBackupEntry>, List<BackupRow>>? {
    val registry = port.registry().sortedBy { it.createdAt }
    if (registry.isEmpty()) return null
    val external = accountIds(root)
    val entries = registry.map { space ->
        // فضة قديمة («silver») بتتصدّر بالشكل الجديد «other» + العلامة (§69.9)
        val data = normalizeLegacySilverAssets(normalizeBudgetIds(port.dataOf(space.id).read()))
        checkFullBackupData(data, SPACE_GROUPS, external)
        checkBackupFinance(data)
        SpaceBackupEntry(space, spaceBackupData(data), spaceCounts(data))
    }
    val transfers = port.readSpaceTransfers().sortedBy { it["id"] as? String }
    checkSpaceTransferRows(transfers, transactionsBySpace(root, entries))
    return entries to transfers
}

private fun transactionsBySpace(root: FullBackupData, entries: List<SpaceBackupEntry>): Map<String, Map<String, BackupRow>> =
    mapOf(DEFAULT_SPACE_ID to root.getValue("transactions").associateBy { it["id"] as String }) +
        entries.associate { e -> e.space.id to e.data.getValue("transactions").associateBy { it["id"] as String } }

/** فحص جزء البلاد في ملف الإصدار 3 (بعد ما الجذر اتفحص). */
@Suppress("UNCHECKED_CAST")
internal fun checkSpacesPart(file: Map<String, Any?>, root: FullBackupData, counts: Map<String, Any?>): Pair<List<SpaceBackupEntry>, List<BackupRow>> {
    val rawSpaces = file["spaces"] as? List<*> ?: throw BackupError(uiText(TextKey.BACKUP_DATA_INVALID))
    val external = accountIds(root)
    val seen = HashSet<String>()
    val entries = rawSpaces.map { raw ->
        val row = raw as? Map<String, Any?> ?: throw BackupError(uiText(TextKey.BACKUP_DATA_INVALID))
        val space = spaceFromBackup(row)
        if (!seen.add(space.countryCode)) throw BackupError(uiText(TextKey.BACKUP_SPACE_INVALID, space.id))
        val rawData = row["data"] as? Map<String, Any?> ?: throw BackupError(uiText(TextKey.BACKUP_SPACE_INVALID, space.id))
        var spaceCounts = (row["counts"] as? Map<String, Any?>) ?: emptyMap()
        val missing = LATER_BACKUP_GROUPS.filter { it in SPACE_GROUPS && it !in rawData }
        val data = LinkedHashMap(rawData).apply { for (g in missing) put(g, emptyList<BackupRow>()) }
        if (missing.isNotEmpty()) spaceCounts = LinkedHashMap(spaceCounts).apply { for (g in missing) put(g, 0L) }
        checkFullBackupData(data, SPACE_GROUPS, external)
        val typed = data as FullBackupData
        checkBackupFinance(typed)
        for (g in SPACE_GROUPS) if (!sameNumber(spaceCounts[g], typed.getValue(g).size)) throw IllegalArgumentException(uiText(TextKey.BACKUP_COUNT_MISMATCH, BACKUP_LABELS.getValue(g)))
        SpaceBackupEntry(space, spaceBackupData(typed), spaceCounts)
    }
    val transfers = (file["spaceTransfers"] as? List<*> ?: throw BackupError(uiText(TextKey.BACKUP_DATA_INVALID))).map {
        it as? Map<String, Any?> ?: throw BackupError(uiText(TextKey.BACKUP_SPACE_TRANSFER_INVALID, "row"))
    }
    if (!sameNumber(counts[SPACE_TRANSFERS_COUNT], transfers.size)) throw IllegalArgumentException(uiText(TextKey.BACKUP_COUNT_MISMATCH, uiText(TextKey.BACKUP_GROUP_SPACE_TRANSFERS)))
    checkSpaceTransferRows(transfers, transactionsBySpace(root, entries))
    return entries to transfers
}

/** بلد من الملف ⇐ بلدها في الحساب + اللي هيتضاف. */
private class SpaceMerge(val entry: SpaceBackupEntry, val target: Space, val isNew: Boolean, val live: FullBackupData, val merge: BackupMerge)

private suspend fun mergeSpaces(port: SpacesBackupPort, file: FullBackupFile, rootExisting: FullBackupData, rootAdditions: FullBackupData, rootRemaps: Map<String, Map<String, String>>): List<SpaceMerge> {
    val registry = port.registry()
    val external = accountIds(rootExisting, rootAdditions)
    val inherited = rootRemaps.filterKeys { it in ACCOUNT_DATA_GROUPS }
    return file.spaces.orEmpty().map { entry ->
        val found = registry.firstOrNull { it.countryCode == entry.space.countryCode }
        val target = found ?: entry.space
        val live = if (found != null) port.dataOf(target.id).read() else emptyBackupData()
        val existing = normalizeBudgetIds(live)
        // فضة قديمة في الملف بتتكتب بالشكل الجديد (§69.9)
        val merge = mergeFullBackupDetailed(normalizeLegacySilverAssets(entry.data), existing, SPACE_GROUPS, inherited)
        val combined = SPACE_GROUPS.associateWith { existing.getValue(it) + merge.additions.getValue(it) }
        checkFullBackupData(combined, SPACE_GROUPS, external)
        checkBackupFinance(combined)
        SpaceMerge(entry, target, found == null, live, merge)
    }
}

/** الأزواج بعد تحويل البلاد والعمليات للي في الحساب — اللي معرّفه موجود أو رجله في زوج تاني بيتساب. */
private suspend fun transfersToAdd(port: SpacesBackupPort, file: FullBackupFile, merges: List<SpaceMerge>, rootRemaps: Map<String, Map<String, String>>): List<BackupRow> {
    val spaceIds = merges.associate { it.entry.space.id to it.target.id } + (DEFAULT_SPACE_ID to DEFAULT_SPACE_ID)
    val txRemaps = merges.associate { it.target.id to it.merge.remaps["transactions"].orEmpty() } + (DEFAULT_SPACE_ID to rootRemaps["transactions"].orEmpty())
    val existing = port.readSpaceTransfers()
    val usedLegs = existing.flatMap { listOf("${it["fromSpaceId"]}|${it["fromTransactionId"]}", "${it["toSpaceId"]}|${it["toTransactionId"]}") }.toMutableSet()
    val ids = existing.map { it["id"] }.toMutableSet()
    val out = mutableListOf<BackupRow>()
    for (row in file.spaceTransfers) {
        val from = spaceIds.getValue(row["fromSpaceId"] as String)
        val to = spaceIds.getValue(row["toSpaceId"] as String)
        val fromTx = txRemaps[from]?.get(row["fromTransactionId"] as String) ?: row["fromTransactionId"] as String
        val toTx = txRemaps[to]?.get(row["toTransactionId"] as String) ?: row["toTransactionId"] as String
        val id = spaceTransferId(from, fromTx)
        if (id in ids || "$from|$fromTx" in usedLegs || "$to|$toTx" in usedLegs) continue
        out += LinkedHashMap(row).apply { put("id", id); put("fromSpaceId", from); put("fromTransactionId", fromTx); put("toSpaceId", to); put("toTransactionId", toTx) }
        ids += id
        usedLegs += "$from|$fromTx"
        usedLegs += "$to|$toTx"
    }
    return out
}

internal suspend fun planSpaces(port: SpacesBackupPort, file: FullBackupFile, rootExisting: FullBackupData, rootAdditions: FullBackupData, rootRemaps: Map<String, Map<String, String>>): SpacesPlan {
    val merges = mergeSpaces(port, file, rootExisting, rootAdditions, rootRemaps)
    val plans = merges.map { m ->
        val lines = SPACE_GROUPS.map { g ->
            val incoming = m.entry.data.getValue(g).size
            val toAdd = m.merge.additions.getValue(g).size
            BackupPlanLine(g, BACKUP_LABELS.getValue(g), incoming, toAdd, incoming - toAdd, null)
        }
        SpaceRestorePlan(m.entry.space, m.target.id, m.isNew, lines, lines.sumOf { it.toAdd } + (if (m.isNew) 1 else 0))
    }
    return SpacesPlan(plans, transfersToAdd(port, file, merges, rootRemaps).size)
}

/** بيكتب البلاد (السجل لو جديدة + الناقص من بياناتها) وبعدين الأزواج. المفاتيح: `<بلد>/<مجموعة>` · `spaces` · `spaceTransfers`. */
internal suspend fun restoreSpaces(port: SpacesBackupPort, file: FullBackupFile, rootExisting: FullBackupData, rootAdditions: FullBackupData, rootRemaps: Map<String, Map<String, String>>): Map<String, Int> {
    val merges = mergeSpaces(port, file, rootExisting, rootAdditions, rootRemaps)
    val out = LinkedHashMap<String, Int>()
    var created = 0
    for (m in merges) {
        if (m.isNew && port.addSpaceIfMissing(m.target)) created++
        val additions = LinkedHashMap(m.merge.additions).apply { put("categoryBudgets", pointLinesAtLiveBudgets(m.merge.additions.getValue("categoryBudgets"), m.live.getValue("budgets"))) }
        for ((g, n) in port.dataOf(m.target.id).addMissing(additions)) if (g in SPACE_GROUPS) out["${m.target.id}/$g"] = n
    }
    out["spaces/registry"] = created
    out[SPACE_TRANSFERS_COUNT] = port.addMissingSpaceTransfers(transfersToAdd(port, file, merges, rootRemaps))
    return out
}
