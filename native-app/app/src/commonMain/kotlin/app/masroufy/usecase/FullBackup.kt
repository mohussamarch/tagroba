package app.masroufy.usecase

import app.masroufy.core.BACKUP_GROUPS
import app.masroufy.core.BACKUP_LABELS
import app.masroufy.core.BackupRow
import app.masroufy.core.FullBackupData
import app.masroufy.core.DUES_BACKUP_GROUPS
import app.masroufy.core.LATER_BACKUP_GROUPS
import app.masroufy.core.exportedBackupData
import app.masroufy.core.backupChecksum
import app.masroufy.core.canonicalBackup
import app.masroufy.core.checkBackupFinance
import app.masroufy.core.checkBackupProfile
import app.masroufy.core.checkFullBackupData
import app.masroufy.core.jsonStringify
import app.masroufy.core.mergeFullBackup
import app.masroufy.core.normalizeBudgetIds
import app.masroufy.core.pointLinesAtLiveBudgets
import app.masroufy.port.FullBackupPort

/**
 * FullBackup — نقل `fullBackup.ts`: النسخة الشاملة (الإصدار 2) — عمل نسخة، ومعاينة استرجاعها، وتنفيذه **بالدمج**.
 * الموجود ما يتدهسش، وملف الحساب بيتضاف بس لو الحساب مالوش ملف (OVERRIDES §26). البصمة SHA-256 من `core`
 * (التطبيق الحالي بياخدها من المتصفح كمنفذ؛ هنا كود نقي فمش محتاجة منفذ) — ولازم تطابق عشان النسخ تتنقل بين التطبيقين.
 */

/** `hasProfile` = مفتاح `profile` موجود في الملف (النسخ الأقدم مالهاش، و`null` = الحساب مالوش ملف). */
data class FullBackupFile(
    val exportedAt: String,
    val data: FullBackupData,
    val profile: BackupRow?,
    val hasProfile: Boolean,
    val checksum: String,
    val counts: Map<String, Any?>,
) {
    /** نص الملف زي `JSON.stringify` بالظبط — و«المستحقات» الفاضية ما بتتكتبش (`exportedBackupData`). */
    fun toJsonText(): String {
        val shown = exportedBackupData(data)
        return jsonStringify(
            LinkedHashMap<String, Any?>().apply {
                put("app", "masroufy"); put("schemaVersion", 2L); put("exportedAt", exportedAt); put("data", shown)
                if (hasProfile) put("profile", profile)
                put("checksum", checksum); put("counts", counts.filterKeys { it !in DUES_BACKUP_GROUPS || it in shown })
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
)

data class FullBackupOutcome(val added: Map<String, Int>, val totalAdded: Int)

private const val MAX_BACKUP_CHARS = 40_000_000

private fun signedText(data: Any?, profile: Any?, hasProfile: Boolean): String =
    if (!hasProfile) canonicalBackup(data) else canonicalBackup(linkedMapOf("data" to data, "profile" to profile))

private fun sameNumber(value: Any?, expected: Int): Boolean = when (value) {
    is Long -> value == expected.toLong()
    is Int -> value == expected
    is Double -> value == expected.toDouble()
    else -> false
}

class FullBackup(private val port: FullBackupPort) {
    @Suppress("UNCHECKED_CAST")
    private fun check(raw: String): FullBackupFile {
        if (raw.length > MAX_BACKUP_CHARS) throw IllegalArgumentException("النسخة أكبر من الحد المدعوم (40 ميجابايت)")
        val parsed = try {
            JsonText.parse(raw)
        } catch (_: JsonText.InvalidJson) {
            throw IllegalArgumentException("الملف مش JSON صالح")
        }
        val file = parsed as? Map<String, Any?>
        val version = file?.get("schemaVersion")
        if (file == null || file["app"] != "masroufy" || !sameNumber(version, 2)) {
            throw IllegalArgumentException("اختر نسخة شاملة بإصدار 2؛ للنسخ القديمة استخدم استعادة النسخة القديمة")
        }
        val hasProfile = file.containsKey("profile")
        val profile = file["profile"]
        val rawData = file["data"]
        // البصمة على اللي اتصدّر فعلًا؛ نسخة أقدم من المشاريع مالهاش مجموعاتها فبتتقري فاضية (OVERRIDES §34)
        val signed = signedText(rawData, profile, hasProfile)
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
            if (!sameNumber(counts[key], typed.getValue(key).size)) throw IllegalArgumentException("عدد السجلات غير مطابق: " + BACKUP_LABELS.getValue(key))
        }
        if (backupChecksum(signed) != file["checksum"]) throw IllegalArgumentException("بصمة سلامة النسخة غير مطابقة؛ الملف اتغير أو اتلف")
        // النسخة اتأكدت؛ المكمّلة بتتبصم تاني (على اللي هيتكتب فعلًا) عشان التطبيق بعد المعاينة يتأكد منها هي
        val exported = exportedBackupData(typed)
        val checksum = if (missing.isNotEmpty() || exported.keys != typed.keys) backupChecksum(signedText(exported, profile, hasProfile)) else file["checksum"] as String
        return FullBackupFile(file["exportedAt"] as? String ?: "", typed, profile as BackupRow?, hasProfile, checksum, counts)
    }

    private fun validateMerge(existing: FullBackupData, additions: FullBackupData) {
        val combined = BACKUP_GROUPS.associateWith { existing.getValue(it) + additions.getValue(it) }
        checkFullBackupData(combined)
        checkBackupFinance(combined)
    }

    suspend fun create(exportedAt: String): FullBackupFile {
        // ميزانيات قديمة بمعرّف عشوائي بتاخد مفتاح فترتها في النسخة بس
        val data = normalizeBudgetIds(port.read())
        checkFullBackupData(data)
        checkBackupFinance(data)
        val profile = port.readProfile()
        checkBackupProfile(profile)
        return FullBackupFile(
            exportedAt, data, profile, hasProfile = true,
            checksum = backupChecksum(signedText(exportedBackupData(data), profile, hasProfile = true)),
            counts = BACKUP_GROUPS.associateWith { data.getValue(it).size.toLong() },
        )
    }

    suspend fun plan(raw: String): FullBackupPlan {
        val file = check(raw)
        val existing = normalizeBudgetIds(port.read())
        val additions = mergeFullBackup(file.data, existing)
        validateMerge(existing, additions)
        val lines = BACKUP_GROUPS.map { key ->
            val incoming = file.data.getValue(key).size
            val toAdd = additions.getValue(key).size
            BackupPlanLine(key, BACKUP_LABELS.getValue(key), incoming, toAdd, incoming - toAdd, null)
        }
        val hasIncomingProfile = file.profile != null
        val profile = BackupProfilePlan(hasIncomingProfile, hasIncomingProfile && port.readProfile() == null)
        return FullBackupPlan(
            file, lines, profile, lines.sumOf { it.toAdd } + (if (profile.toAdd) 1 else 0),
            listOf(
                "الموجود يفضل كما هو. روابط العمليات المتكررة تُنقل لمعرّفات العمليات الموجودة.",
                "ملف الحساب (الاسم والمرتب ويوم الراتب) بيتضاف بس لو الحساب مالوش ملف — ما بيتكتبش فوق الموجود.",
                "الاستعادة على دفعات: لو الاتصال انقطع قد يُحفظ جزء؛ أعد نفس النسخة لاستكمال الناقص دون الكتابة فوق الموجود. تجنب التعديل من جهاز آخر أثناء النسخ والاستعادة.",
                "النسخة تشمل بيانات الحساب؛ أذونات الهاتف ورسائل المراجعة المحلية وإعدادات المظهر لا تُستعاد منها.",
            ),
        )
    }

    /** بيعيد الفحص على نص الملف نفسه (زي `JSON.stringify` في التطبيق الحالي)، وبيعيد الدمج على الموجود **دلوقتي**. */
    suspend fun apply(input: FullBackupFile): FullBackupOutcome {
        val file = check(input.toJsonText())
        val live = port.read()
        val existing = normalizeBudgetIds(live)
        val additions = mergeFullBackup(file.data, existing)
        validateMerge(existing, additions)
        // سقوف التصنيفات المضافة لحساب ميزانيته بالمعرّف القديم بتشاور على معرّفه الحقيقي عشان تبان
        val added = port.addMissing(LinkedHashMap(additions).apply { put("categoryBudgets", pointLinesAtLiveBudgets(additions.getValue("categoryBudgets"), live.getValue("budgets"))) })
        // ملف الحساب آخر حاجة، ولو موجود ما يتكتبش فوقه (OVERRIDES §26)
        val profileAdded = file.profile?.let { port.addProfileIfMissing(it) } ?: false
        val byGroup = LinkedHashMap(added).apply { put("profile", if (profileAdded) 1 else 0) }
        return FullBackupOutcome(byGroup, added.values.sum() + (if (profileAdded) 1 else 0))
    }
}
