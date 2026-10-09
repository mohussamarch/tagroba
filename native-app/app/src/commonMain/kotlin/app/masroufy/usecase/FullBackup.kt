package app.masroufy.usecase

import app.masroufy.core.BACKUP_GROUPS
import app.masroufy.core.BACKUP_LABELS
import app.masroufy.core.BACKUP_SPACES_VERSION
import app.masroufy.core.BackupRow
import app.masroufy.core.FullBackupData
import app.masroufy.core.NEW_APP_BACKUP_GROUPS
import app.masroufy.core.LATER_BACKUP_GROUPS
import app.masroufy.core.TextKey
import app.masroufy.core.exportedBackupData
import app.masroufy.core.backupChecksum
import app.masroufy.core.canonicalBackup
import app.masroufy.core.checkBackupFinance
import app.masroufy.core.checkBackupProfile
import app.masroufy.core.checkFullBackupData
import app.masroufy.core.jsonStringify
import app.masroufy.core.mergeFullBackupDetailed
import app.masroufy.core.normalizeBudgetIds
import app.masroufy.core.normalizeLegacySilverAssets
import app.masroufy.core.settleMergedReversals
import app.masroufy.core.settleReversalLinks
import app.masroufy.core.pointLinesAtLiveBudgets
import app.masroufy.core.uiText
import app.masroufy.port.FullBackupPort
import app.masroufy.port.SpacesBackupPort

/**
 * FullBackup — نقل `fullBackup.ts`: النسخة الشاملة (الإصدار 2) — عمل نسخة، ومعاينة استرجاعها، وتنفيذه **بالدمج**.
 * الموجود ما يتدهسش، وملف الحساب بيتضاف بس لو الحساب مالوش ملف (OVERRIDES §26). البصمة SHA-256 من `core`
 * (التطبيق الحالي بياخدها من المتصفح كمنفذ؛ هنا كود نقي فمش محتاجة منفذ) — ولازم تطابق عشان النسخ تتنقل بين التطبيقين.
 *
 * **حساب لكل بلد (§41.1 · §64):** ملف واحد للحساب كله. من غير بلد تانية ⇒ **الإصدار 2 بالحرف**؛ ببلد تانية ⇒ الإصدار 3
 * (البلاد في `spaces` والتحويل لنفسك في `spaceTransfers` — `FullBackupSpaces.kt`). الاسترجاع بيربط كل بلد **ببلدها** (بلد واحدة = حساب واحد).
 */

/** `hasProfile` = مفتاح `profile` موجود في الملف (النسخ الأقدم مالهاش، و`null` = الحساب مالوش ملف). */
data class FullBackupFile(
    val exportedAt: String,
    val data: FullBackupData,
    val profile: BackupRow?,
    val hasProfile: Boolean,
    val checksum: String,
    val counts: Map<String, Any?>,
    /** البلاد غير السعودية — `null` = الإصدار 2 (مفيش بلد تانية). */
    val spaces: List<SpaceBackupEntry>? = null,
    val spaceTransfers: List<BackupRow> = emptyList(),
) {
    /** نص الملف زي `JSON.stringify` بالظبط — و«المستحقات» الفاضية ما بتتكتبش (`exportedBackupData`). */
    fun toJsonText(): String {
        val shown = exportedBackupData(data)
        return jsonStringify(
            LinkedHashMap<String, Any?>().apply {
                put("app", "masroufy"); put("schemaVersion", if (spaces == null) 2L else BACKUP_SPACES_VERSION.toLong()); put("exportedAt", exportedAt); put("data", shown)
                if (hasProfile) put("profile", profile)
                if (spaces != null) {
                    put("spaces", spacesAsWritten(spaces))
                    put("spaceTransfers", spaceTransfers)
                }
                put("checksum", checksum); put("counts", counts.filterKeys { it !in NEW_APP_BACKUP_GROUPS || it in shown })
            },
        )
    }
}

data class BackupPlanLine(val key: String, val label: String, val incoming: Int, val toAdd: Int, val skipped: Int, val note: String?)

data class BackupProfilePlan(val incoming: Boolean, val toAdd: Boolean)

data class FullBackupPlan(
    val file: FullBackupFile,
    val lines: List<BackupPlanLine>,
    val profile: BackupProfilePlan,
    val totalToAdd: Int,
    val warnings: List<String>,
    /** البلاد التانية في الملف (الإصدار 3) — كل بلد بتترجع لبلدها. */
    val spaces: List<SpaceRestorePlan> = emptyList(),
    val spaceTransfersToAdd: Int = 0,
)

data class FullBackupOutcome(val added: Map<String, Int>, val totalAdded: Int)

private const val MAX_BACKUP_CHARS = 40_000_000

private fun signedText(data: Any?, profile: Any?, hasProfile: Boolean): String =
    if (!hasProfile) canonicalBackup(data) else canonicalBackup(linkedMapOf("data" to data, "profile" to profile))

internal fun sameNumber(value: Any?, expected: Int): Boolean = when (value) {
    is Long -> value == expected.toLong()
    is Int -> value == expected
    is Double -> value == expected.toDouble()
    else -> false
}

class FullBackup(private val port: FullBackupPort, private val spaces: SpacesBackupPort? = null) {
    @Suppress("UNCHECKED_CAST")
    private fun check(raw: String): FullBackupFile {
        if (raw.length > MAX_BACKUP_CHARS) throw IllegalArgumentException(uiText(TextKey.BACKUP_TOO_LARGE))
        val parsed = try {
            JsonText.parse(raw)
        } catch (_: JsonText.InvalidJson) {
            throw IllegalArgumentException(uiText(TextKey.BACKUP_NOT_JSON))
        }
        val file = parsed as? Map<String, Any?>
        val version = file?.get("schemaVersion")
        val v3 = sameNumber(version, BACKUP_SPACES_VERSION)
        if (file == null || file["app"] != "masroufy" || !(sameNumber(version, 2) || v3)) {
            throw IllegalArgumentException(uiText(TextKey.BACKUP_NEEDS_V2))
        }
        val hasProfile = file.containsKey("profile")
        val profile = file["profile"]
        val rawData = file["data"]
        // البصمة على اللي اتصدّر فعلًا؛ نسخة أقدم من المشاريع مالهاش مجموعاتها فبتتقري فاضية (OVERRIDES §34)
        val signed = if (v3) signedV3(rawData, profile, file["spaces"], file["spaceTransfers"]) else signedText(rawData, profile, hasProfile)
        var counts: Map<String, Any?> = (file["counts"] as? Map<String, Any?>) ?: emptyMap()
        val missing = (rawData as? Map<String, Any?>)?.let { d -> LATER_BACKUP_GROUPS.filter { it !in d } }.orEmpty()
        val data: Any? = if (missing.isEmpty()) rawData else {
            counts = LinkedHashMap(counts).apply { for (key in missing) put(key, 0L) }
            LinkedHashMap(rawData as Map<String, Any?>).apply { for (key in missing) put(key, emptyList<BackupRow>()) }
        }
        checkFullBackupData(data)
        val typed = data as FullBackupData
        checkBackupFinance(typed)
        checkBackupProfile(profile)
        for (key in BACKUP_GROUPS) {
            if (!sameNumber(counts[key], typed.getValue(key).size)) throw IllegalArgumentException(uiText(TextKey.BACKUP_COUNT_MISMATCH, BACKUP_LABELS.getValue(key)))
        }
        val spaceParts = if (v3) checkSpacesPart(file, typed, counts) else null
        if (backupChecksum(signed) != file["checksum"]) throw IllegalArgumentException(uiText(TextKey.BACKUP_CHECKSUM_MISMATCH))
        // النسخة اتأكدت؛ المكمّلة بتتبصم تاني (على اللي هيتكتب فعلًا) عشان التطبيق بعد المعاينة يتأكد منها هي
        val exported = exportedBackupData(typed)
        if (spaceParts != null) {
            val (entries, transfers) = spaceParts
            val checksum = backupChecksum(signedV3(exported, profile, spacesAsWritten(entries), transfers))
            return FullBackupFile(file["exportedAt"] as? String ?: "", typed, profile as BackupRow?, hasProfile, checksum, counts, entries, transfers)
        }
        val checksum = if (missing.isNotEmpty() || exported.keys != typed.keys) backupChecksum(signedText(exported, profile, hasProfile)) else file["checksum"] as String
        return FullBackupFile(file["exportedAt"] as? String ?: "", typed, profile as BackupRow?, hasProfile, checksum, counts)
    }

    private fun validateMerge(existing: FullBackupData, additions: FullBackupData) {
        val combined = BACKUP_GROUPS.associateWith { existing.getValue(it) + additions.getValue(it) }
        checkFullBackupData(combined)
        checkBackupFinance(combined)
    }

    suspend fun create(exportedAt: String): FullBackupFile {
        // ميزانيات قديمة بمعرّف عشوائي بتاخد مفتاح فترتها في النسخة بس · وفضة قديمة بتتصدّر «other» + العلامة (§69.9)
        // §77-D: ربط «اللي رجع» المكسور (تراجع التطبيق القديم) بيتصلح في الملف بقواعد `RepairReversals` بدل ما النسخة تقف
        val data = settleReversalLinks(normalizeLegacySilverAssets(normalizeBudgetIds(port.read())))
        checkFullBackupData(data)
        checkBackupFinance(data)
        val profile = port.readProfile()
        checkBackupProfile(profile)
        val counts = BACKUP_GROUPS.associateWith { data.getValue(it).size.toLong() }
        val extra = spaces?.let { readSpaces(it, data) }
        if (extra == null) {
            // مفيش بلد تانية ⇒ الإصدار 2 بالحرف (التطبيق الحالي بيقراه)
            return FullBackupFile(exportedAt, data, profile, hasProfile = true, checksum = backupChecksum(signedText(exportedBackupData(data), profile, hasProfile = true)), counts = counts)
        }
        val (entries, transfers) = extra
        val checksum = backupChecksum(signedV3(exportedBackupData(data), profile, spacesAsWritten(entries), transfers))
        return FullBackupFile(exportedAt, data, profile, hasProfile = true, checksum, counts + (SPACE_TRANSFERS_COUNT to transfers.size.toLong()), entries, transfers)
    }

    suspend fun plan(raw: String): FullBackupPlan {
        val file = check(raw)
        val existing = normalizeBudgetIds(port.read())
        val merge = settleMergedReversals(mergeFullBackupDetailed(settleReversalLinks(normalizeLegacySilverAssets(file.data)), existing), existing)
        val additions = merge.additions
        validateMerge(existing, additions)
        val lines = BACKUP_GROUPS.map { key ->
            val incoming = file.data.getValue(key).size
            val toAdd = additions.getValue(key).size
            BackupPlanLine(key, BACKUP_LABELS.getValue(key), incoming, toAdd, incoming - toAdd, null)
        }
        val hasIncomingProfile = file.profile != null
        val profile = BackupProfilePlan(hasIncomingProfile, hasIncomingProfile && port.readProfile() == null)
        val spacePlans = if (file.spaces != null) planSpaces(requireSpaces(), file, existing, additions, merge.remaps) else null
        return FullBackupPlan(
            file, lines, profile, lines.sumOf { it.toAdd } + (if (profile.toAdd) 1 else 0) + (spacePlans?.total ?: 0),
            listOf(
                uiText(TextKey.BACKUP_NOTE_KEEP_EXISTING),
                uiText(TextKey.BACKUP_NOTE_PROFILE),
                uiText(TextKey.BACKUP_NOTE_BATCHES),
                uiText(TextKey.BACKUP_NOTE_SCOPE),
            ),
            spacePlans?.spaces.orEmpty(), spacePlans?.transfersToAdd ?: 0,
        )
    }

    /** بيعيد الفحص على نص الملف نفسه (زي `JSON.stringify` في التطبيق الحالي)، وبيعيد الدمج على الموجود **دلوقتي**. */
    suspend fun apply(input: FullBackupFile): FullBackupOutcome {
        val file = check(input.toJsonText())
        val live = port.read()
        val existing = normalizeBudgetIds(live)
        // فضة قديمة في الملف («silver» من غير علامة) بتتقبل، وبتتكتب «other» + `silver: true` (§69.9). الملف نفسه وبصمته زي ما هما
        val merge = settleMergedReversals(mergeFullBackupDetailed(settleReversalLinks(normalizeLegacySilverAssets(file.data)), existing), existing)
        val additions = merge.additions
        validateMerge(existing, additions)
        // سقوف التصنيفات المضافة لحساب ميزانيته بالمعرّف القديم بتشاور على معرّفه الحقيقي عشان تبان
        val added = port.addMissing(LinkedHashMap(additions).apply { put("categoryBudgets", pointLinesAtLiveBudgets(additions.getValue("categoryBudgets"), live.getValue("budgets"))) })
        // ملف الحساب آخر حاجة، ولو موجود ما يتكتبش فوقه (OVERRIDES §26)
        val profileAdded = file.profile?.let { port.addProfileIfMissing(it) } ?: false
        val byGroup = LinkedHashMap(added).apply { put("profile", if (profileAdded) 1 else 0) }
        // البلاد التانية بعد الجذر (بتورث تحويلات معرّفات الأشخاص والتجار والعمليات)، والتحويل لنفسك آخر حاجة
        if (file.spaces != null) byGroup += restoreSpaces(requireSpaces(), file, existing, additions, merge.remaps)
        return FullBackupOutcome(byGroup, added.values.sum() + (if (profileAdded) 1 else 0) + byGroup.filterKeys { '/' in it || it == SPACE_TRANSFERS_COUNT }.values.sum())
    }

    private fun requireSpaces(): SpacesBackupPort = spaces ?: throw IllegalStateException(uiText(TextKey.BACKUP_NEEDS_SPACES))
}
