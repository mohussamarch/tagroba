package app.masroufy.core

/**
 * النسخة الشاملة — نقل `fullBackup.ts` + `backupProfile.ts` + `backupBudgetIds.ts` + `mergeFullBackup.ts`.
 * الصفوف بيانات مرنة (زي JSON): `Map<String, Any?>` وقيمها نص أو رقم (Long/Double) أو منطقي أو null أو قايمة أو خريطة.
 * **لازم نفس بصمة السلامة** عشان نسخة من التطبيق الحالي تترجع هنا والعكس.
 */
typealias BackupRow = Map<String, Any?>
typealias FullBackupData = Map<String, List<BackupRow>>

val BACKUP_GROUPS = listOf(
    "wallets", "categories", "merchants", "rules", "people", "assets", "tags", "budgets", "recurringItems", "importBatches",
    "transactions", "obligations", "allocations", "settlements", "sourceRecords", "transactionTags", "categoryBudgets",
    "assetLots", "assetSales", "assetPrices", "notificationReceipts", "projects", "projectLinks", "projectRules",
    // «المستحقات» — التطبيق الجديد بس (OVERRIDES §50 و§55). التطبيق الحالي بيقبل الملف ويتجاهلها
    "roscas", "roscaEntries", "installmentPlans", "installmentPayments", "debtTerms",
    // «زون التحويلات» (§60) والزكاة (§62) والأحداث ومناسبات الشخص (§64) — التطبيق الجديد بس
    "transferParties", "zakatFacts", "zakatYears", "zakatPayments", "lifeEvents", "eventLinks", "occasions",
    // مصادر الدخل (§48 · §64) — التطبيق الجديد بس
    "incomeSources",
)

/** المجموعات الخمسة بتوع «المستحقات» — مش في نسخ التطبيق الحالي. */
val DUES_BACKUP_GROUPS = listOf("roscas", "roscaEntries", "installmentPlans", "installmentPayments", "debtTerms")

/**
 * اتضافت بعد أول نسخ الإصدار 2: المشاريع (§34) و«المستحقات» (§55) — النسخة الأقدم من غيرها بتتقري فاضية.
 * (نسخة التطبيق الحالي عمرها ما هيبقى فيها «المستحقات».)
 */
/** قرارات «زون التحويلات» (§60) — مش في نسخ التطبيق الحالي، وبتتكتب بس لو فيها حاجة. */
const val TRANSFER_PARTIES_GROUP = "transferParties"

/** الزكاة (§62) — وقائع وسنين ودفعات؛ بتتكتب مع بعض لو أي واحدة فيها حاجة (زي «المستحقات»). */
val ZAKAT_BACKUP_GROUPS = listOf("zakatFacts", "zakatYears", "zakatPayments")

/** كل اللي في التطبيق الجديد بس (مش في ملف التطبيق الحالي) — كل مجموعة منهم بتتكتب بس لو فيها حاجة (`exportedBackupData`). */
/** الأحداث وروابطها ومناسبات الشخص (§64) — بيتكتبوا مع بعض لو أي واحدة فيها حاجة. */
val EVENT_BACKUP_GROUPS = listOf("lifeEvents", "eventLinks", "occasions")

/** مصادر الدخل (§48 · §64) — بتتكتب بس لو فيها حاجة. */
const val INCOME_SOURCES_GROUP = "incomeSources"

val NEW_APP_BACKUP_GROUPS = DUES_BACKUP_GROUPS + TRANSFER_PARTIES_GROUP + ZAKAT_BACKUP_GROUPS + EVENT_BACKUP_GROUPS + INCOME_SOURCES_GROUP

val LATER_BACKUP_GROUPS = listOf("projects", "projectLinks", "projectRules") + NEW_APP_BACKUP_GROUPS

val BACKUP_RELATIONS: Map<String, Map<String, String>> = mapOf(
    "categories" to mapOf("parentId" to "categories"), "merchants" to mapOf("verifiedCategoryId" to "categories"),
    "rules" to mapOf("categoryId" to "categories"),
    "transactions" to linkedMapOf("walletId" to "wallets", "transferToWalletId" to "wallets", "categoryId" to "categories", "merchantId" to "merchants"),
    "obligations" to linkedMapOf("personId" to "people", "originTransactionId" to "transactions"),
    "allocations" to linkedMapOf("personId" to "people", "transactionId" to "transactions"),
    "settlements" to linkedMapOf("obligationId" to "obligations", "transactionId" to "transactions"),
    "sourceRecords" to linkedMapOf("batchId" to "importBatches", "transactionId" to "transactions"),
    "transactionTags" to linkedMapOf("tagId" to "tags", "transactionId" to "transactions"),
    "categoryBudgets" to linkedMapOf("budgetId" to "budgets", "categoryId" to "categories"),
    "assetLots" to linkedMapOf("assetId" to "assets", "transactionId" to "transactions"),
    "assetSales" to linkedMapOf("assetId" to "assets", "transactionId" to "transactions"),
    "assetPrices" to mapOf("assetId" to "assets"),
    "projectLinks" to linkedMapOf("projectId" to "projects", "transactionId" to "transactions"),
    "projectRules" to mapOf("projectId" to "projects"),
    "roscas" to mapOf("organizerPersonId" to "people"),
    "roscaEntries" to linkedMapOf("roscaId" to "roscas", "transactionId" to "transactions"),
    "installmentPlans" to mapOf("receivedTransactionId" to "transactions"),
    "installmentPayments" to linkedMapOf("planId" to "installmentPlans", "transactionId" to "transactions"),
    "debtTerms" to linkedMapOf("obligationId" to "obligations", "personId" to "people"),
    "transferParties" to mapOf("personId" to "people"),
    "zakatFacts" to linkedMapOf("assetId" to "assets", "obligationId" to "obligations"),
    "zakatPayments" to linkedMapOf("yearId" to "zakatYears", "transactionId" to "transactions"),
    "lifeEvents" to mapOf("hostPersonId" to "people"),
    "eventLinks" to linkedMapOf("eventId" to "lifeEvents", "transactionId" to "transactions", "personId" to "people"),
    "occasions" to linkedMapOf("personId" to "people", "sourceEventId" to "lifeEvents"),
)

fun emptyBackupData(): Map<String, MutableList<BackupRow>> = BACKUP_GROUPS.associateWith { mutableListOf() }

/**
 * اللي بيتكتب في الملف (وعليه البصمة): لو «المستحقات» كلها فاضية، مجموعاتها **ما بتتكتبش** ⇒ الملف هو هو حرف بحرف
 * زي ملف التطبيق الحالي (ونفس البصمة). لو فيها أي حاجة، الخمسة بيتكتبوا. القراية بتكمّل الناقص فاضي (`LATER_BACKUP_GROUPS`).
 */
fun exportedBackupData(data: FullBackupData): FullBackupData {
    val dropped = listOf(DUES_BACKUP_GROUPS, listOf(TRANSFER_PARTIES_GROUP), ZAKAT_BACKUP_GROUPS, EVENT_BACKUP_GROUPS, listOf(INCOME_SOURCES_GROUP)).filter { block -> block.all { data[it].isNullOrEmpty() } }.flatten().toSet()
    return if (dropped.isEmpty()) data else data.filterKeys { it !in dropped }
}

fun backupRowId(group: String, row: BackupRow): String {
    val key = when (group) {
        "assetPrices" -> "assetId"
        "notificationReceipts" -> "eventKey"
        "debtTerms" -> "obligationId"
        "transferParties" -> "key"
        else -> "id"
    }
    return row[key]?.let(::jsString) ?: ""
}

/** المفاتيح بترتيب ثابت (وحدات UTF-16 زي `sort()`)، وترتيب الصفوف والأرقام زي ما هي — نص البصمة. */
fun canonicalBackup(value: Any?): String = when (value) {
    is List<*> -> value.joinToString(",", "[", "]") { canonicalBackup(it) }
    is Map<*, *> -> value.keys.map { it.toString() }.sorted().joinToString(",", "{", "}") { key -> JsText.jsonString(key) + ":" + canonicalBackup(value[key]) }
    else -> jsJson(value)
}

/** النسخة القديمة (من غير ملف الحساب) بصمتها على البيانات بس؛ الجديدة بتغطي الملف كمان. */
fun backupChecksumText(data: FullBackupData, profile: Any?, hasProfile: Boolean): String =
    if (!hasProfile) canonicalBackup(data) else canonicalBackup(linkedMapOf("data" to data, "profile" to profile))

fun backupChecksum(text: String): String = Sha256.hex(text)

class BackupError(message: String) : IllegalArgumentException(message)

internal fun isSafeInteger(value: Any?): Boolean = when (value) {
    is Int -> true
    is Long -> value in -MAX_SAFE_HALALAS..MAX_SAFE_HALALAS
    is Double -> value.isFinite() && value % 1.0 == 0.0 && kotlin.math.abs(value) <= MAX_SAFE_HALALAS.toDouble()
    else -> false
}

internal fun numberOf(value: Any?): Double? = when (value) {
    is Int -> value.toDouble()
    is Long -> value.toDouble()
    is Double -> value
    else -> null
}

/** ملف الحساب في النسخة (OVERRIDES §26) — مش جوه المجموعات عن قصد. */
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
    if (row.containsKey("payday")) {
        val payday = row["payday"]
        val n = numberOf(payday)
        if (n == null || n % 1.0 != 0.0 || n < 1 || n > 31) bad("payday")
    }
    if (has("gender") && row["gender"] != "male" && row["gender"] != "female") bad("gender")
    for (field in listOf("supportsDependents", "hasCar", "renter", "domesticWorker", "business", "duesInBudget", "islamicContentVisible", "carToWork")) if (has(field) && row[field] !is Boolean) bad(field)
    if (has("dependentKinds")) {
        val kinds = row["dependentKinds"] as? List<*> ?: bad("dependentKinds")
        if (!kinds.all { it in DEPENDENT_KINDS }) bad("dependentKinds")
    }
    if (has("onboardedAt") && row["onboardedAt"] !is String) bad("onboardedAt")
}

/** الميزانية القديمة بمعرّف عشوائي بتاخد مفتاح فترتها (في النسخة بس)، وسقوف التصنيفات بتتحوّل معاها. */
fun normalizeBudgetIds(data: FullBackupData): FullBackupData {
    val renamed = LinkedHashMap<String, String>()
    val budgets = data.getValue("budgets").map { row ->
        val periodKey = row["periodKey"] as? String
        if (periodKey == null || periodKey.isEmpty() || row["id"] == periodKey) row
        else {
            (row["id"] as? String)?.let { renamed[it] = periodKey }
            LinkedHashMap(row).apply { put("id", periodKey) }
        }
    }
    if (renamed.isEmpty()) return data
    val lines = data.getValue("categoryBudgets").map { row ->
        val id = row["budgetId"] as? String
        if (id != null && id in renamed) LinkedHashMap(row).apply { put("budgetId", renamed.getValue(id)) } else row
    }
    return LinkedHashMap(data).apply { put("budgets", budgets); put("categoryBudgets", lines) }
}

/** قبل الكتابة على حساب ميزانيته لسه بالمعرّف القديم: السطور المضافة بتشاور على المعرّف الحقيقي المتخزن. */
fun pointLinesAtLiveBudgets(additions: List<BackupRow>, liveBudgets: List<BackupRow>): List<BackupRow> {
    val liveIdByPeriod = LinkedHashMap<String, String>()
    for (b in liveBudgets) {
        val period = b["periodKey"] as? String
        val id = b["id"] as? String
        if (period != null && id != null && id != period) liveIdByPeriod[period] = id
    }
    if (liveIdByPeriod.isEmpty()) return additions.toList()
    return additions.map { row ->
        val id = row["budgetId"] as? String
        if (id != null && id in liveIdByPeriod) LinkedHashMap(row).apply { put("budgetId", liveIdByPeriod.getValue(id)) } else row
    }
}

/** مفتاح محتوى العملية (زي `mergeBackup.transactionContentKey`): العملة + تفاصيل منع التكرار + المحفظة. */
fun transactionRowContentKey(row: BackupRow): String {
    val amount = row["originalAmountMinor"] ?: row["amountMinor"]
    val merchant = row["merchantId"] ?: row["rawMerchantName"] ?: ""
    val detail = listOf(jsString(row["occurredAt"]), jsString(amount), jsString(row["observedDirection"]), normalizeText(jsString(merchant))).joinToString("|")
    return listOf(jsString(row["currency"]), detail, row["walletId"]?.let(::jsString) ?: "").joinToString("|")
}

private val NATURAL_KEYS = mapOf(
    "budgets" to listOf("periodKey"), "categoryBudgets" to listOf("budgetId", "categoryId"),
    "obligations" to listOf("personId", "originTransactionId", "kind", "currency"),
    "allocations" to listOf("personId", "transactionId", "allocationKind", "currency"),
    "settlements" to listOf("obligationId", "transactionId"), "transactionTags" to listOf("transactionId", "tagId"),
    "projectLinks" to listOf("projectId", "transactionId"),
    // العملية الواحدة ليها ربط واحد بحدث (§64) ⇒ نفس العملية على جهاز تاني = نفس الربط
    "eventLinks" to listOf("transactionId"),
)

private fun semantic(group: String, row: BackupRow): String? {
    if (group == "transactions") return transactionRowContentKey(row)
    val keys = NATURAL_KEYS[group] ?: return null
    return jsJson(keys.map { row[it] })
}

/**
 * الدمج: الموجود ما يتدهسش — نفس المعرّف أو نفس المحتوى ⇒ يتخطى، والروابط بتتحوّل لمعرّف الموجود
 * (نفس العملية ممكن تكون متخزنة بمعرّف تاني على جهاز تاني).
 */
fun mergeFullBackup(incoming: FullBackupData, existing: FullBackupData): Map<String, List<BackupRow>> {
    val additions = emptyBackupData()
    val maps = HashMap<String, HashMap<String, String>>()
    for (group in BACKUP_GROUPS) {
        val remap = HashMap<String, String>().also { maps[group] = it }
        val byId = LinkedHashMap<String, BackupRow>().apply { existing.getValue(group).forEach { put(backupRowId(group, it), it) } }
        val byContent = LinkedHashMap<String, MutableList<BackupRow>>()
        val consumed = HashSet<String>()
        for (row in existing.getValue(group)) semantic(group, row)?.let { byContent.getOrPut(it) { mutableListOf() }.add(row) }
        for (original in incoming.getValue(group)) {
            val row = LinkedHashMap(original)
            val originalId = backupRowId(group, original)
            for ((field, target) in BACKUP_RELATIONS[group].orEmpty()) {
                val value = row[field] ?: continue
                maps[target]?.get(jsString(value))?.let { row[field] = it }
            }
            val id = backupRowId(group, row)
            val key = semantic(group, row)
            val found = byId[id] ?: key?.let { k -> byContent[k]?.firstOrNull { backupRowId(group, it) !in consumed } }
            if (found != null) {
                val foundId = backupRowId(group, found)
                consumed += foundId
                remap[originalId] = foundId
                continue
            }
            additions.getValue(group).add(row)
            remap[originalId] = id
        }
    }
    return additions
}
