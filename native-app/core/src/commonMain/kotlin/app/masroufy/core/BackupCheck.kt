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
    // «المستحقات» — نفس الحقول الإجبارية في `DuesCodecs`
    "roscas" to listOf("name", "currency", "contributionMinor", "every", "unit", "firstDueAt", "cycleCount", "myTurns", "payoutMinor", "members", "createdAt"),
    "roscaEntries" to listOf("roscaId", "transactionId", "kind", "amountMinor"),
    "installmentPlans" to listOf("name", "provider", "kind", "currency", "principalMinor", "totalMinor", "installmentMinor", "cycleMonths", "firstDueAt", "createdAt"),
    "installmentPayments" to listOf("planId", "transactionId", "amountMinor"),
    "debtTerms" to listOf("personId", "firstDueAt", "cycleMonths"),
    "transferParties" to listOf("label", "verdict", "decidedAt"),
    "zakatFacts" to listOf("subject", "updatedAt"),
    "zakatYears" to listOf("hawlStart", "dueAt", "currency", "confirmedAt"),
    "zakatPayments" to listOf("yearId", "amountMinor", "lines", "paidAt", "createdAt"),
    "lifeEvents" to listOf("name", "normalizedName", "kind", "date", "mine", "archived", "createdAt"),
    "eventLinks" to listOf("eventId", "transactionId", "role", "createdAt"),
    "occasions" to listOf("kind", "month", "day", "yearly", "createdAt"),
    "incomeSources" to listOf("name", "normalizedName", "kind", "currency", "startedAt", "createdAt"),
    MERCHANT_CATEGORIES_GROUP to listOf("merchantId", "categoryId"),
    "reservations" to listOf("itemType", "sourceId", "occurrenceDate", "amountMinor", "currency", "createdAt"),
    "eventPrep" to listOf("eventId", "name", "order", "done", "createdAt"),
    PERSON_PROFILES_GROUP to listOf("personId", "updatedAt"),
    PERSON_RELATIONS_GROUP to listOf("personAId", "personBId", "createdAt"),
    ALERT_SETTINGS_GROUP to listOf("group", "enabled"),
    SAVINGS_GOALS_GROUP to listOf("name", "targetMinor", "currency", "startedAt", "deadline", "archived", "createdAt", "updatedAt"),
    GOAL_CONTRIBUTIONS_GROUP to listOf("goalId", "date", "amountMinor", "createdAt"),
    // صفحة الإشعارات (§69) — نفس الحقول الإجبارية في `AlertCodecs.alertInbox`. النوع المجهول ما بيترفضش هنا (بيتخطّى في الدمج)
    ALERT_INBOX_GROUP to listOf("threadKey", "eventKey", "kind", "flow", "title", "body", "delivery", "inAppWindow", "factors", "createdAt"),
    // حسابات الورث المحفوظة (§69.4) — نفس الحقول الإجبارية في `InheritanceCodecs`، والمتداخل بيتفحص في `checkInheritanceScenarioRow`
    INHERITANCE_SCENARIOS_GROUP to listOf("name", "estateOf", "countryCode", "heirs", "items", "funeralMinor", "debtsMinor", "createdAt", "updatedAt"),
    // المساعد «مصروفي» (§78) والإعدادات الجديدة والإشعارات الممسوحة — نفس الحقول الإجبارية في `AssistantCodecs`
    USER_SETTINGS_GROUP to listOf("id", "type", "updatedAt"),
    ASSISTANT_CONVERSATIONS_GROUP to listOf("id", "spaceId", "createdAt", "lastMessageAt", "title", "firstReply", "messageCount"),
    ASSISTANT_MESSAGES_GROUP to listOf("id", "conversationId", "createdAt", "from", "kind", "text"),
    ASSISTANT_TOPICS_GROUP to listOf("id", "topic", "askCount", "lastAskedAt"),
    ASSISTANT_FORGOTTEN_GROUP to listOf("id", "factKey", "createdAt"),
    ASSISTANT_UNKNOWN_GROUP to listOf("id", "text", "normalized", "askCount", "firstAskedAt", "lastAskedAt", "screen", "spaceId"),
    ALERT_DISMISSALS_GROUP to listOf("id", "threadKey", "dismissedAt"),
)
private val BOOLEANS = setOf("active", "archived", "enabled", "confirmed", "economicKindConfirmed", "categoryConfirmed", "excludedFromBudget", "isCashTagged", "notifyEnabled", "hasInterest", "mine", "yearly", "saudiCompany", "done", "inAppWindow", "starred")
private val NUMERIC = setOf("order", "priority", "sourceOrder", "cycleMonths", "originalRowIndex", "quantity", "thresholdPercent", "every", "cycleCount", "karat", "fineness", "month", "day", "year", "leadDays", "sharePercent", "expectedDayOfMonth", "payWeekday")
private val DATES = setOf("occurredAt", "openingAt", "periodStart", "periodEnd", "purchasedAt", "soldAt", "asOf", "nextDueAt", "firstDueAt", "hawlStart", "dueAt", "paidAt", "date", "startedAt", "endedAt", "occurrenceDate", "deadline")
private val ENUMS: Map<String, Map<String, List<String>>> = mapOf(
    "wallets" to mapOf("kind" to listOf("bank", "cash", "own_abroad", "digital_wallet")),
    "transactions" to linkedMapOf("reviewState" to listOf("confirmed", "suggested", "needs_review"), "datePrecision" to listOf("day", "minute")),
    "obligations" to mapOf("kind" to listOf("receivable", "loan_payable", "custody_payable")),
    "allocations" to mapOf("allocationKind" to listOf("receivable", "gift")),
    "assets" to mapOf("kind" to listOf("gold", "silver", "stock", "fund", "digital", "other")),
    "assetPrices" to mapOf("source" to listOf("manual", "feed")),
    "recurringItems" to mapOf("kind" to listOf("subscription", "bill")),
    "rules" to mapOf("matchMode" to listOf("contains", "startsWith", "exact")),
    "importBatches" to linkedMapOf("state" to listOf("staged", "committed", "reverted"), "sourceType" to listOf("csv_preview", "csv_legacy", "pdf_alrajhi", "sms", "pdf_qnb")),
    "sourceRecords" to mapOf("matchingState" to listOf("new", "duplicate", "similar", "conflict", "invalid")),
    "roscas" to mapOf("unit" to listOf("week", "month")),
    "roscaEntries" to mapOf("kind" to listOf("contribution", "payout")),
    "installmentPlans" to mapOf("kind" to listOf("purchase_plan", "financing")),
    "transferParties" to mapOf("verdict" to listOf("own_account", "person", "dismissed")),
    "zakatFacts" to mapOf("subject" to listOf("asset", "obligation")),
    "lifeEvents" to mapOf("kind" to LifeEventKind.entries.map { it.wire }),
    "eventLinks" to mapOf("role" to EventRole.entries.map { it.wire }),
    "occasions" to mapOf("kind" to OccasionKind.entries.map { it.wire }),
    "incomeSources" to mapOf("kind" to IncomeSourceKind.entries.map { it.wire }),
    "reservations" to mapOf("itemType" to CalendarItemType.entries.map { it.wire }),
    ALERT_SETTINGS_GROUP to mapOf("group" to AlertGroup.entries.map { it.wire }),
)
/** قيم اختيارية بتتفحص لو موجودة بس (وقائع الزكاة — §62). */
private val OPTIONAL_ENUMS: Map<String, Map<String, List<String>>> = mapOf(
    "zakatFacts" to linkedMapOf("purpose" to listOf("wear", "saving"), "holding" to listOf("trading", "long_term"), "collectability" to listOf("strong", "doubtful")),
    PERSON_PROFILES_GROUP to mapOf("circle" to PersonCircle.entries.map { it.wire }),
)
private val CURRENCY_CODE = Regex("[A-Z]{3}")
private val LAST_FOUR = Regex("[0-9]{4}")

/**
 * فحص وقت التشغيل: `data` جاية من ملف، فكل حاجة بتتأكد (نفس الترتيب والرسايل).
 * بلد غير السعودية في الإصدار 3 (§64): [groups] = مجموعات البلد بس، والعلاقات لمجموعات الحساب (الأشخاص · التجار · الوسوم · …)
 * بتتفحص على معرّفات الحساب [external] (من بيانات الجذر في نفس الملف).
 */
fun checkFullBackupData(data: Any?, groups: List<String> = BACKUP_GROUPS, external: Map<String, Set<String>> = emptyMap()) {
    val value = data as? Map<*, *> ?: throw BackupError(uiText(TextKey.BACKUP_DATA_INVALID))
    for (group in groups) {
        val rows = value[group] as? List<*> ?: throw BackupError(uiText(TextKey.BACKUP_GROUP_MISSING, BACKUP_LABELS.getValue(group)))
        val ids = HashSet<String>()
        for (raw in rows) {
            @Suppress("UNCHECKED_CAST")
            val row = raw as? Map<String, Any?> ?: throw BackupError(uiText(TextKey.BACKUP_ROW_INVALID, group))
            val id = backupRowId(group, row)
            // مفاتيح الإيصالات وصفحة الإشعارات ممكن يبقى فيها «/» (أسامي أطراف) — معرّف المستند بيتشفّر (`receiptDocId`)
            val slashOk = group == "notificationReceipts" || group == ALERT_INBOX_GROUP
            if (id.isEmpty() || id.length > 1000 || (!slashOk && '/' in id) || !ids.add(id)) throw BackupError(uiText(TextKey.BACKUP_ID_INVALID, group))
            for (field in REQUIRED.getValue(group)) if (!row.containsKey(field)) throw BackupError(uiText(TextKey.BACKUP_FIELD_MISSING, group, field))
            validateFields(row, group)
            if (group == "importBatches" && row["state"] == "staged") throw BackupError(uiText(TextKey.BACKUP_IMPORT_UNFINISHED))
            if (group == "budgets" && row["id"] != row["periodKey"]) throw BackupError(uiText(TextKey.BACKUP_BUDGET_ID))
        }
    }
    @Suppress("UNCHECKED_CAST")
    val rowsByGroup = groups.associateWith { g -> (value[g] as List<Map<String, Any?>>) }
    val ids = rowsByGroup.mapValues { (g, rows) -> rows.map { backupRowId(g, it) }.toSet() }
    for (group in groups) for (row in rowsByGroup.getValue(group)) for ((field, target) in BACKUP_RELATIONS[group].orEmpty()) {
        val v = row[field]
        if (v != null && jsString(v) !in (ids[target] ?: external[target].orEmpty())) throw BackupError(uiText(TextKey.BACKUP_RELATION_MISSING, group, field))
    }
}

private fun validateFields(row: Map<String, Any?>, group: String) {
    val special = NUMERIC + BOOLEANS + setOf("counts", "parentId", "threshold", "myTurns", "members", "lines", "factors", "heirs", "items")
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
    // الحجز (§65) والبند اللي ليه مبلغ: أكبر من صفر (البند من غير مبلغ = الحقل مش مكتوب)
    if (group == "reservations" && (numberOf(row["amountMinor"]) ?: Double.NaN) <= 0) throw BackupError(uiText(TextKey.BACKUP_AMOUNT_POSITIVE))
    if (group == "eventPrep" && row["plannedMinor"] != null && numberOf(row["plannedMinor"])!! <= 0) throw BackupError(uiText(TextKey.BACKUP_AMOUNT_POSITIVE))
    if (group == "recurringItems" && numberOf(row["cycleMonths"]) !in listOf(1.0, 3.0, 12.0)) throw BackupError(uiText(TextKey.BACKUP_CYCLE_INVALID))
    // نسبة الحدث (§64): 1..100، والنقطة العملية كلها — من غير الحقل = 100 (الروابط القديمة)
    if (group == "eventLinks" && row["sharePercent"] != null) {
        val p = numberOf(row["sharePercent"])!!
        if (p < 1 || p > EVENT_SHARE_WHOLE || (row["role"] != EventRole.SPEND.wire && p != EVENT_SHARE_WHOLE.toDouble())) {
            throw BackupError(uiText(TextKey.BACKUP_VALUE_UNSUPPORTED, group, "sharePercent"))
        }
    }
    if (group == "incomeSources") checkIncomeSourceRow(row)
    // حقول «هتوصل لكام؟» على الأصل (§69.6) — بتتفحص لو موجودة بس
    if (group == "assets") checkAssetGrowthRow(row)?.let { throw BackupError(uiText(TextKey.BACKUP_VALUE_UNSUPPORTED, group, it)) }
    if (group == PERSON_PROFILES_GROUP || group == PERSON_RELATIONS_GROUP) checkPeopleRow(group, row)
    if (group == SAVINGS_GOALS_GROUP || group == GOAL_CONTRIBUTIONS_GROUP) checkGoalRow(group, row)
    if (group == INHERITANCE_SCENARIOS_GROUP) checkInheritanceScenarioRow(row)
    if (group == ALERT_INBOX_GROUP && (row["factors"] as? List<*>)?.all { it is String } != true) throw BackupError(uiText(TextKey.BACKUP_TEXT_INVALID, group, "factors"))
    if (group == "transactions" && ALL_ECONOMIC_KINDS.none { it.wire == row["economicKind"] }) throw BackupError(uiText(TextKey.BACKUP_KIND_INVALID))
    for ((field, allowed) in ENUMS[group].orEmpty()) if (jsString(row[field]) !in allowed) throw BackupError(uiText(TextKey.BACKUP_VALUE_UNSUPPORTED, group, field))
    for ((field, allowed) in OPTIONAL_ENUMS[group].orEmpty()) if (row[field] != null && jsString(row[field]) !in allowed) throw BackupError(uiText(TextKey.BACKUP_VALUE_UNSUPPORTED, group, field))
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

/** خطة الادخار (§68): الهدف والإيداع أكبر من صفر، وتاريخ الهدف بعد البداية، والمحفظة والبلد مع بعض. */
private fun checkGoalRow(group: String, row: Map<String, Any?>) {
    val amountField = if (group == SAVINGS_GOALS_GROUP) "targetMinor" else "amountMinor"
    if ((numberOf(row[amountField]) ?: 0.0) <= 0) throw BackupError(uiText(TextKey.BACKUP_VALUE_UNSUPPORTED, group, amountField))
    if (group != SAVINGS_GOALS_GROUP) return
    if (jsString(row["deadline"]) <= jsString(row["startedAt"])) throw BackupError(uiText(TextKey.BACKUP_VALUE_UNSUPPORTED, group, "deadline"))
    if ((row["linkedWalletId"] == null) != (row["linkedSpaceId"] == null)) throw BackupError(uiText(TextKey.BACKUP_VALUE_UNSUPPORTED, group, "linkedWalletId"))
}

/** الدايرة والصلة (جلسة 16): نص الصلة نص لحد [RELATION_LABEL_MAX]، ومفيش صلة لنفس الشخص. */
private fun checkPeopleRow(group: String, row: Map<String, Any?>) {
    val labelField = if (group == PERSON_PROFILES_GROUP) "relationLabel" else "label"
    row[labelField]?.let { if (it !is String || it.isEmpty() || it.length > RELATION_LABEL_MAX) throw BackupError(uiText(TextKey.BACKUP_VALUE_UNSUPPORTED, group, labelField)) }
    if (group == PERSON_RELATIONS_GROUP && row["personAId"] == row["personBId"]) throw BackupError(uiText(TextKey.BACKUP_VALUE_UNSUPPORTED, group, "personBId"))
}

/**
 * مصدر الدخل (§48 · §64 · §65): اليوم المتوقع من 1 لـ31، ويوم القبض الأسبوعي من 1 لـ7، ومفاتيح الأطراف قايمة نصوص (اسم + آخر 4 بس —
 * مفيش رقم حساب كامل). الدورية اختيارية (من غيرها = شهري)، ولو مكتوبة لازم تبقى قيمة معروفة.
 */
private fun checkIncomeSourceRow(row: Map<String, Any?>) {
    row["payFrequency"]?.let { f ->
        if (PayFrequency.entries.none { it.wire == f }) throw BackupError(uiText(TextKey.BACKUP_VALUE_UNSUPPORTED, "incomeSources", "payFrequency"))
    }
    row["expectedDayOfMonth"]?.let { if ((numberOf(it) ?: 0.0) !in 1.0..31.0) throw BackupError(uiText(TextKey.BACKUP_NUMBER_INVALID, "expectedDayOfMonth")) }
    row["payWeekday"]?.let { if ((numberOf(it) ?: 0.0) !in 1.0..7.0) throw BackupError(uiText(TextKey.BACKUP_NUMBER_INVALID, "payWeekday")) }
    for (field in listOf("payerKeys", "declinedPayerKeys")) {
        val list = row[field] ?: continue
        if (list !is List<*> || list.any { it !is String }) throw BackupError(uiText(TextKey.BACKUP_TEXT_INVALID, "incomeSources", field))
    }
}
