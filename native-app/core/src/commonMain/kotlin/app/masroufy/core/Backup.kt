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
)

/** اتضافت بعد أول نسخ الإصدار 2 (المشاريع §34): النسخة القديمة من غيرها بتتقري فاضية. */
val LATER_BACKUP_GROUPS = listOf("projects", "projectLinks", "projectRules")

/** أسماء المجموعات للعرض — بتتقرا وقت العرض عشان تتغير مع اللغة (Texts.kt). */
val BACKUP_LABELS: Map<String, String>
    get() = mapOf(
        "wallets" to uiText(TextKey.BACKUP_GROUP_WALLETS),
        "categories" to uiText(TextKey.BACKUP_GROUP_CATEGORIES),
        "merchants" to uiText(TextKey.BACKUP_GROUP_MERCHANTS),
        "rules" to uiText(TextKey.BACKUP_GROUP_RULES),
        "people" to uiText(TextKey.BACKUP_GROUP_PEOPLE),
        "assets" to uiText(TextKey.BACKUP_GROUP_ASSETS),
        "tags" to uiText(TextKey.BACKUP_GROUP_TAGS),
        "budgets" to uiText(TextKey.BACKUP_GROUP_BUDGETS),
        "recurringItems" to uiText(TextKey.BACKUP_GROUP_RECURRING_ITEMS),
        "importBatches" to uiText(TextKey.BACKUP_GROUP_IMPORT_BATCHES),
        "transactions" to uiText(TextKey.BACKUP_GROUP_TRANSACTIONS),
        "obligations" to uiText(TextKey.BACKUP_GROUP_OBLIGATIONS),
        "allocations" to uiText(TextKey.BACKUP_GROUP_ALLOCATIONS),
        "settlements" to uiText(TextKey.BACKUP_GROUP_SETTLEMENTS),
        "sourceRecords" to uiText(TextKey.BACKUP_GROUP_SOURCE_RECORDS),
        "transactionTags" to uiText(TextKey.BACKUP_GROUP_TRANSACTION_TAGS),
        "categoryBudgets" to uiText(TextKey.BACKUP_GROUP_CATEGORY_BUDGETS),
        "assetLots" to uiText(TextKey.BACKUP_GROUP_ASSET_LOTS),
        "assetSales" to uiText(TextKey.BACKUP_GROUP_ASSET_SALES),
        "assetPrices" to uiText(TextKey.BACKUP_GROUP_ASSET_PRICES),
        "notificationReceipts" to uiText(TextKey.BACKUP_GROUP_NOTIFICATION_RECEIPTS),
        "projects" to uiText(TextKey.BACKUP_GROUP_PROJECTS),
        "projectLinks" to uiText(TextKey.BACKUP_GROUP_PROJECT_LINKS),
        "projectRules" to uiText(TextKey.BACKUP_GROUP_PROJECT_RULES),
    )

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
)

fun emptyBackupData(): Map<String, MutableList<BackupRow>> = BACKUP_GROUPS.associateWith { mutableListOf() }

/** نفس `String(x)` في جافاسكربت للقيم اللي بتيجي من JSON. */
internal fun jsString(value: Any?): String = when (value) {
    null -> "null"
    is String -> value
    is Boolean -> value.toString()
    is Int, is Long -> value.toString()
    is Double -> jsNumber(value)
    is List<*> -> value.joinToString(",") { if (it == null) "" else jsString(it) }
    is Map<*, *> -> "[object Object]"
    else -> value.toString()
}

/** نفس `Number.prototype.toString()` (الأرقام في النسخ أعداد صحيحة؛ الكسور نادرة). */
internal fun jsNumber(d: Double): String {
    if (d.isNaN()) return "NaN"
    if (d.isInfinite()) return if (d > 0) "Infinity" else "-Infinity"
    if (d == 0.0) return "0"
    if (d % 1.0 == 0.0 && kotlin.math.abs(d) < 1e21) return d.toLong().toString()
    // أقصر أرقام من toString، وبعدين شكل جافاسكربت (أُس لو ≥ 1e21 أو < 1e-6)
    val raw = kotlin.math.abs(d).toString().lowercase()
    val mantissa = raw.substringBefore('e')
    val exp = raw.substringAfter('e', "0").toInt()
    val intPart = mantissa.substringBefore('.')
    val fracPart = mantissa.substringAfter('.', "")
    var digits = (intPart + fracPart).trimStart('0')
    val lead = (intPart + fracPart).length - (intPart + fracPart).trimStart('0').length
    var n = intPart.length + exp - lead
    digits = digits.trimEnd('0').ifEmpty { "0" }
    val k = digits.length
    val body = when {
        n in k..21 -> digits + "0".repeat(n - k)
        n in 1..21 -> digits.substring(0, n) + "." + digits.substring(n)
        n in -5..0 -> "0." + "0".repeat(-n) + digits
        else -> {
            val e = n - 1
            (if (k == 1) digits else digits[0] + "." + digits.substring(1)) + "e" + (if (e >= 0) "+" else "-") + kotlin.math.abs(e)
        }
    }
    return if (d < 0) "-$body" else body
}

/** نفس `JSON.stringify` لقيمة بسيطة أو مركبة (المفاتيح بترتيبها). */
internal fun jsJson(value: Any?): String = when (value) {
    null -> "null"
    is String -> JsText.jsonString(value)
    is Boolean -> value.toString()
    is Int, is Long -> value.toString()
    is Double -> if (value.isFinite()) jsNumber(value) else "null"
    is List<*> -> value.joinToString(",", "[", "]") { jsJson(it) }
    is Map<*, *> -> value.entries.joinToString(",", "{", "}") { (k, v) -> JsText.jsonString(k.toString()) + ":" + jsJson(v) }
    else -> JsText.jsonString(value.toString())
}

fun backupRowId(group: String, row: BackupRow): String {
    val key = when (group) {
        "assetPrices" -> "assetId"
        "notificationReceipts" -> "eventKey"
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
    for (field in listOf("supportsDependents", "hasCar", "renter", "domesticWorker", "business")) if (has(field) && row[field] !is Boolean) bad(field)
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
