package app.masroufy.core

/**
 * فحص النسخة الشاملة قبل ما أي رقم يوصل التخزين — نقل `checkFullBackup.ts` + `checkBackupFinance.ts`.
 * بيرفض الناقص والمكرر والعلاقة المكسورة والمبلغ الغلط، بنفس رسايل التطبيق الحالي.
 */
private val REQUIRED: Map<String, List<String>> = mapOf(
    "wallets" to listOf("name", "currency", "kind", "openingBalanceMinor", "openingAt"),
    "categories" to listOf("name", "iconKey", "lightColor", "darkColor", "active", "order"),
    "merchants" to listOf("displayName", "normalizedName"),
    "rules" to listOf("priority", "matchText", "matchMode", "categoryId", "enabled"),
    "people" to listOf("name", "archived"),
    "assets" to listOf("name", "kind", "unitLabel", "currency", "archived"),
    "tags" to listOf("displayName", "normalizedName"),
    "budgets" to listOf("periodKey", "periodStart", "periodEnd", "totalLimitMinor", "thresholdPercent", "createdAt", "updatedAt"),
    "recurringItems" to listOf("name", "merchantKey", "kind", "cycleMonths", "expectedMinor", "currency", "nextDueAt", "active", "confirmed"),
    "importBatches" to listOf("sourceType", "fileHash", "fileName", "importedAt", "state", "counts"),
    "transactions" to listOf(
        "occurredAt", "datePrecision", "sourceOrder", "economicKind", "economicKindConfirmed", "observedDirection", "amountMinor", "currency",
        "categoryConfirmed", "excludedFromBudget", "reviewState", "isCashTagged", "createdAt", "updatedAt",
    ),
    "obligations" to listOf("personId", "originTransactionId", "kind", "originalMinor", "currency"),
    "allocations" to listOf("transactionId", "personId", "allocationKind", "amountMinor", "currency"),
    "settlements" to listOf("transactionId", "obligationId", "amountMinor"),
    "sourceRecords" to listOf("batchId", "accountIdentity", "sourceHash", "originalRowIndex", "rawLine", "matchingState", "reason"),
    "transactionTags" to listOf("transactionId", "tagId"),
    "categoryBudgets" to listOf("budgetId", "categoryId", "limitMinor", "notifyEnabled", "thresholdPercent"),
    "assetLots" to listOf("assetId", "purchasedAt", "quantity", "principalMinor", "feeMinor"),
    "assetSales" to listOf("assetId", "soldAt", "quantity", "grossProceedsMinor", "feeMinor"),
    "assetPrices" to listOf("assetId", "pricePerUnitMinor", "asOf", "source"),
    "notificationReceipts" to listOf("eventKey", "threshold", "periodStart", "sentAt"),
    "projects" to listOf("name", "normalizedName", "archived", "createdAt"),
    "projectLinks" to listOf("projectId", "transactionId", "source", "createdAt"),
    "projectRules" to listOf("projectId", "matchText", "matchMode", "direction", "enabled", "createdAt"),
)
private val BOOLEANS = setOf("active", "archived", "enabled", "confirmed", "economicKindConfirmed", "categoryConfirmed", "excludedFromBudget", "isCashTagged", "notifyEnabled")
private val NUMERIC = setOf("order", "priority", "sourceOrder", "cycleMonths", "originalRowIndex", "quantity", "thresholdPercent")
private val DATES = setOf("occurredAt", "openingAt", "periodStart", "periodEnd", "purchasedAt", "soldAt", "asOf", "nextDueAt")
private val ENUMS: Map<String, Map<String, List<String>>> = mapOf(
    "wallets" to mapOf("kind" to listOf("bank", "cash", "own_abroad", "digital_wallet")),
    "transactions" to linkedMapOf("reviewState" to listOf("confirmed", "suggested", "needs_review"), "datePrecision" to listOf("day", "minute")),
    "obligations" to mapOf("kind" to listOf("receivable", "loan_payable", "custody_payable")),
    "allocations" to mapOf("allocationKind" to listOf("receivable", "gift")),
    "assets" to mapOf("kind" to listOf("gold", "stock", "fund", "digital", "other")),
    "assetPrices" to mapOf("source" to listOf("manual", "feed")),
    "recurringItems" to mapOf("kind" to listOf("subscription", "bill")),
    "rules" to mapOf("matchMode" to listOf("contains", "startsWith", "exact")),
    "importBatches" to linkedMapOf("state" to listOf("staged", "committed", "reverted"), "sourceType" to listOf("csv_preview", "csv_legacy", "pdf_alrajhi", "sms")),
    "sourceRecords" to mapOf("matchingState" to listOf("new", "duplicate", "similar", "conflict", "invalid")),
)
private val CURRENCY_CODE = Regex("[A-Z]{3}")
private val LAST_FOUR = Regex("[0-9]{4}")

/** فحص وقت التشغيل: `data` جاية من ملف، فكل حاجة بتتأكد (نفس الترتيب والرسايل). */
fun checkFullBackupData(data: Any?) {
    val value = data as? Map<*, *> ?: throw BackupError(uiText(TextKey.BACKUP_DATA_INVALID))
    for (group in BACKUP_GROUPS) {
        val rows = value[group] as? List<*> ?: throw BackupError(uiText(TextKey.BACKUP_GROUP_MISSING, BACKUP_LABELS.getValue(group)))
        val ids = HashSet<String>()
        for (raw in rows) {
            @Suppress("UNCHECKED_CAST")
            val row = raw as? Map<String, Any?> ?: throw BackupError(uiText(TextKey.BACKUP_ROW_INVALID, group))
            val id = backupRowId(group, row)
            if (id.isEmpty() || id.length > 1000 || (group != "notificationReceipts" && '/' in id) || !ids.add(id)) throw BackupError(uiText(TextKey.BACKUP_ID_INVALID, group))
            for (field in REQUIRED.getValue(group)) if (!row.containsKey(field)) throw BackupError(uiText(TextKey.BACKUP_FIELD_MISSING, group, field))
            validateFields(row, group)
            if (group == "importBatches" && row["state"] == "staged") throw BackupError(uiText(TextKey.BACKUP_IMPORT_UNFINISHED))
            if (group == "budgets" && row["id"] != row["periodKey"]) throw BackupError(uiText(TextKey.BACKUP_BUDGET_ID))
        }
    }
    @Suppress("UNCHECKED_CAST")
    val groups = BACKUP_GROUPS.associateWith { g -> (value[g] as List<Map<String, Any?>>) }
    val ids = groups.mapValues { (g, rows) -> rows.map { backupRowId(g, it) }.toSet() }
    for (group in BACKUP_GROUPS) for (row in groups.getValue(group)) for ((field, target) in BACKUP_RELATIONS[group].orEmpty()) {
        val v = row[field]
        if (v != null && jsString(v) !in ids.getValue(target)) throw BackupError(uiText(TextKey.BACKUP_RELATION_MISSING, group, field))
    }
}

private fun validateFields(row: Map<String, Any?>, group: String) {
    val special = NUMERIC + BOOLEANS + setOf("counts", "parentId", "threshold")
    for (key in REQUIRED.getValue(group)) {
        // دين قديم من غير عملية (OVERRIDES §27)
        if (group == "obligations" && key == "originTransactionId" && row[key] == null) continue
        if (key !in special && !key.endsWith("Minor") && row[key] !is String) throw BackupError(uiText(TextKey.BACKUP_TEXT_INVALID, group, key))
    }
    for ((key, value) in row) {
        if (key.endsWith("Minor") || key in NUMERIC) {
            if (value == null && key in listOf("totalLimitMinor", "thresholdPercent")) continue
            if (!isSafeInteger(value)) throw BackupError(uiText(TextKey.BACKUP_NUMBER_INVALID, key))
            if (key.endsWith("Minor") && key !in listOf("openingBalanceMinor", "statedBalanceMinor") && numberOf(value)!! < 0) throw BackupError(uiText(TextKey.BACKUP_NEGATIVE_AMOUNT, key))
        } else if (key in BOOLEANS && value !is Boolean) {
            throw BackupError(uiText(TextKey.BACKUP_BOOLEAN_INVALID, key))
        } else if (key in DATES && (value !is String || !isValidIsoDate(value))) {
            throw BackupError(uiText(TextKey.BACKUP_DATE_INVALID, key))
        }
    }
    if (row.containsKey("currency") && (row["currency"] !is String || !CURRENCY_CODE.matches(row["currency"] as String))) throw BackupError(uiText(TextKey.BACKUP_CURRENCY_INVALID))
    if (row.containsKey("accountLast4") && (row["accountLast4"] !is String || !LAST_FOUR.matches(row["accountLast4"] as String))) throw BackupError(uiText(TextKey.BACKUP_LAST_FOUR_ONLY))
    if (group == "transactions" && jsString(row["observedDirection"]) !in listOf("in", "out")) throw BackupError(uiText(TextKey.BACKUP_DIRECTION_INVALID))
    if (group == "transactions" && (numberOf(row["amountMinor"]) ?: Double.NaN) <= 0) throw BackupError(uiText(TextKey.BACKUP_AMOUNT_POSITIVE))
    if (group == "recurringItems" && numberOf(row["cycleMonths"]) !in listOf(1.0, 3.0, 12.0)) throw BackupError(uiText(TextKey.BACKUP_CYCLE_INVALID))
    if (group == "transactions" && ALL_ECONOMIC_KINDS.none { it.wire == row["economicKind"] }) throw BackupError(uiText(TextKey.BACKUP_KIND_INVALID))
    for ((field, allowed) in ENUMS[group].orEmpty()) if (jsString(row[field]) !in allowed) throw BackupError(uiText(TextKey.BACKUP_VALUE_UNSUPPORTED, group, field))
    if (group == "importBatches") {
        val counts = row["counts"] as? Map<*, *>
        for (key in listOf("total", "imported", "duplicates", "similar", "conflicts", "invalid")) {
            val v = counts?.get(key)
            if (!isSafeInteger(v) || numberOf(v)!! < 0) throw BackupError(uiText(TextKey.BACKUP_COUNTER_INVALID))
        }
    }
}

/** علاقات الفلوس، ومنها اللي بتتكوّن من الدمج: التخصيص والدين والتسوية ما يعدّوش العملية أو الأصل. */
fun checkBackupFinance(data: FullBackupData) {
    val transactions = data.getValue("transactions").associateBy { it["id"] }
    val obligations = data.getValue("obligations").associateBy { it["id"] }
    val allocations = HashMap<Any?, Double>()
    val settlements = HashMap<Any?, Double>()
    fun num(v: Any?) = numberOf(v) ?: Double.NaN
    for (row in data.getValue("allocations")) {
        val txn = transactions[row["transactionId"]]
        if (txn?.get("currency") != row["currency"]) throw BackupError(uiText(TextKey.BACKUP_ALLOCATION_CURRENCY))
        val sum = (allocations[row["transactionId"]] ?: 0.0) + num(row["amountMinor"])
        if (!isSafeInteger(sum) || sum > num(txn?.get("amountMinor"))) throw BackupError(uiText(TextKey.BACKUP_ALLOCATIONS_EXCEED))
        allocations[row["transactionId"]] = sum
    }
    for (row in data.getValue("obligations")) {
        if (row.containsKey("originTransactionId") && row["originTransactionId"] == null) {
            if (!(num(row["originalMinor"]) > 0)) throw BackupError(uiText(TextKey.BACKUP_OBLIGATION_POSITIVE))
            continue
        }
        val txn = transactions[row["originTransactionId"]]
        if (txn?.get("currency") != row["currency"] || num(row["originalMinor"]) > num(txn?.get("amountMinor"))) throw BackupError(uiText(TextKey.BACKUP_OBLIGATION_MISMATCH))
    }
    for (row in data.getValue("settlements")) {
        val obligation = obligations[row["obligationId"]]
        val txn = transactions[row["transactionId"]]
        if (txn?.get("currency") != obligation?.get("currency") || num(row["amountMinor"]) > num(txn?.get("amountMinor"))) throw BackupError(uiText(TextKey.BACKUP_SETTLEMENT_MISMATCH))
        val sum = (settlements[row["obligationId"]] ?: 0.0) + num(row["amountMinor"])
        if (!isSafeInteger(sum) || sum > num(obligation?.get("originalMinor"))) throw BackupError(uiText(TextKey.BACKUP_SETTLEMENTS_EXCEED))
        settlements[row["obligationId"]] = sum
    }
}
