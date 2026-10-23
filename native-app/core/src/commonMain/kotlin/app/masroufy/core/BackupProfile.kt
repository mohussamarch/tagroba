package app.masroufy.core

/**
 * ملف الحساب في النسخة (OVERRIDES §26) — مش جوه المجموعات عن قصد. (اتنقل من `Backup.kt` عشان حد الـ300 سطر.)
 * **المرتب (قرار المالك 2026-10-16):** `salaryCurrency` لازم عملة معروفة و`salaryDeclined` صح/غلط. مفتاح `salaryRange` القديم
 * (النطاقات اللي اتلغت) **مقبول بأي قيمة** — النسخة القديمة بترجع زي ما هي، والنطاق بيتقري «ما اتجاوبش» (`parseStoredProfile`).
 */
fun checkBackupProfile(profile: Any?) {
    if (profile == null) return
    val row = profile as? Map<*, *> ?: throw BackupError(uiText(TextKey.BACKUP_PROFILE_INVALID))
    fun bad(field: String): Nothing = throw BackupError(uiText(TextKey.BACKUP_PROFILE_INVALID_FIELD, field))
    fun has(field: String) = row.containsKey(field) && row[field] != null
    if (has("displayName")) {
        val name = row["displayName"] as? String ?: bad("displayName")
        if (JsText.trim(name).length > MAX_NAME_LENGTH) bad("displayName")
    }
    if (has("salaryMinor") && (!isSafeInteger(row["salaryMinor"]) || numberOf(row["salaryMinor"])!! < 0)) bad("salaryMinor")
    if (has("salaries")) {
        val m = row["salaries"] as? Map<*, *> ?: bad("salaries")
        if (!m.all { (k, v) -> Currency.entries.any { it.name == k } && isSafeInteger(v) && numberOf(v)!! >= 0 }) bad("salaries")
    }
    if (has("salaryDeclinedIn") && !((row["salaryDeclinedIn"] as? List<*>) ?: bad("salaryDeclinedIn")).all { c -> Currency.entries.any { it.name == c } }) bad("salaryDeclinedIn")
    if (has("paydays") && !((row["paydays"] as? Map<*, *>) ?: bad("paydays")).all { (k, v) -> isSpaceId(k as? String) && isSafeInteger(v) && numberOf(v)!! in 1.0..31.0 }) bad("paydays")
    if (has("salaryCurrency") && Currency.entries.none { it.name == row["salaryCurrency"] }) bad("salaryCurrency")
    if (row.containsKey("payday")) {
        val payday = row["payday"]
        val n = numberOf(payday)
        if (n == null || n % 1.0 != 0.0 || n < 1 || n > 31) bad("payday")
    }
    if (has("gender") && row["gender"] != "male" && row["gender"] != "female") bad("gender")
    val flags = listOf("supportsDependents", "hasCar", "renter", "domesticWorker", "business", "duesInBudget", "islamicContentVisible", "carToWork", "salaryDeclined")
    for (field in flags) if (has(field) && row[field] !is Boolean) bad(field)
    if (has("dependentKinds")) {
        val kinds = row["dependentKinds"] as? List<*> ?: bad("dependentKinds")
        if (!kinds.all { it in DEPENDENT_KINDS }) bad("dependentKinds")
    }
    if (has("onboardedAt") && row["onboardedAt"] !is String) bad("onboardedAt")
    if (has("lookIndex")) {
        val look = numberOf(row["lookIndex"])
        if (look == null || look % 1.0 != 0.0 || look < 1 || look > LOOK_COUNT) bad("lookIndex")
    }
    if (has("character") && row["character"] !is Map<*, *>) bad("character") // «شخصيتك»: خريطة بس — حقولها بتتقصّ وقت القراية
}
