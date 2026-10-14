package app.masroufy.core

/**
 * فحص حقول «حدّث الرصيد» في النسخة الشاملة (بتتفحص لو موجودة بس — النسخ القديمة مالهاش): `balanceCheckpoints` جوه المحفظة و`explainsCheckpointId`
 * على العملية. بيرجّع اسم الحقل الغلط أو null. الفرق نفسه ممكن يبقى سالب، والرصيد المكتوب لا. الأعداد صحيحة بالهللة.
 * الربط من العملية للنقطة **لين** (النقطة ممكن تكون اتمسحت ⇒ العملية بتتحسب عادي) — بس لازم نص.
 */
fun checkpointBackupProblem(group: String, row: Map<String, Any?>): String? {
    if (group == "transactions") {
        val link = row["explainsCheckpointId"] ?: return null
        return if (link !is String || link.isEmpty()) "explainsCheckpointId" else null
    }
    if (group != "wallets") return null
    val list = row["balanceCheckpoints"] ?: return null
    if (list !is List<*>) return "balanceCheckpoints"
    val seen = HashSet<String>()
    for (raw in list) {
        val cp = raw as? Map<*, *> ?: return "balanceCheckpoints"
        val id = cp["id"] as? String
        val day = cp["day"] as? String
        if (id.isNullOrEmpty() || !seen.add(id) || day == null || !isValidIsoDate(day)) return "balanceCheckpoints"
        if (cp["createdAt"] !is String || (cp["atInstant"] != null && cp["atInstant"] !is String)) return "balanceCheckpoints"
        if (!isSafeInteger(cp["statedMinor"]) || numberOf(cp["statedMinor"])!! < 0) return "balanceCheckpoints"
        if (!isSafeInteger(cp["recordedDiffMinor"])) return "balanceCheckpoints"
        if (cp["keptMinor"] != null && !isSafeInteger(cp["keptMinor"])) return "balanceCheckpoints"
    }
    return null
}
