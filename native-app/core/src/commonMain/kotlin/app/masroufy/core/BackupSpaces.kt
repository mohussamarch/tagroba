package app.masroufy.core

/**
 * النسخة الشاملة **الإصدار 3** (OVERRIDES §41.1 «ملف واحد للحساب كله» · §64): لما يبقى فيه بلد تانية غير السعودية.
 * من غير بلد تانية الملف بيفضل **الإصدار 2 بالحرف** (التطبيق الحالي بيقراه زي ما هو). الإصدار 3:
 * `{app, schemaVersion: 3, exportedAt, data (الحساب + السعودية — زي الإصدار 2), profile, spaces: [{id, name, countryCode, currency, createdAt,
 * archived, data, counts}], spaceTransfers, checksum, counts}` — والبصمة على `{data, profile, spaces, spaceTransfers}`.
 * التطبيق الحالي بيرفض أي إصدار غير 2 برسالة واضحة (مش بيضيّع حاجة في صمت).
 */
const val BACKUP_SPACES_VERSION = 3

/** بيانات البلد في الملف = مجموعاتها بس (من غير مجموعات الحساب). */
fun spaceBackupData(data: FullBackupData): FullBackupData = SPACE_GROUPS.associateWith { data[it].orEmpty() }

/** اللي بيتكتب من بيانات البلد: زي الإصدار 2 — مجموعات التطبيق الجديد الفاضية ما بتتكتبش. */
fun exportedSpaceData(data: FullBackupData): FullBackupData = exportedBackupData(spaceBackupData(data))

fun spaceBackupHeader(space: Space): LinkedHashMap<String, Any?> = linkedMapOf(
    "id" to space.id, "name" to space.name, "countryCode" to space.countryCode, "currency" to space.currency.name,
    "createdAt" to space.createdAt, "archived" to space.archived,
)

/** البلد من رأسها في الملف — أي حقل غلط ⇒ رفض برسالة (البلد لازم تبقى من الحزم ومش السعودية). */
fun spaceFromBackup(row: Map<*, *>): Space {
    fun bad(): Nothing = throw BackupError(uiText(TextKey.BACKUP_SPACE_INVALID, jsString(row["id"])))
    val id = row["id"] as? String ?: bad()
    val code = row["countryCode"] as? String ?: bad()
    val pack = COUNTRY_PACKS[code] ?: bad()
    if (code == SAUDI_PACK.code || id != spaceIdForCountry(code)) bad()
    if (row["currency"] != pack.currency.name) bad()
    val name = row["name"] as? String ?: bad()
    if (name.isEmpty() || name.length > MAX_NAME_LENGTH) bad()
    val createdAt = row["createdAt"] as? String ?: bad()
    val archived = row["archived"] as? Boolean ?: bad()
    return Space(id, name, code, pack.currency, createdAt, archived)
}

private val SPACE_TRANSFER_FIELDS = listOf(
    "id", "fromSpaceId", "fromTransactionId", "fromAmountMinor", "fromCurrency", "toSpaceId", "toTransactionId", "toAmountMinor", "toCurrency", "createdAt",
)

/**
 * أزواج التحويل لنفسك في الملف: الحقول كاملة · البلدين موجودين ومختلفين · كل رجل عملية موجودة في بلدها بنفس المبلغ والعملة وطالعة/داخلة ·
 * المعرّف من الرجل الطالعة · العملية في زوج واحد بس. [transactions] = بلد ⇐ معرّف العملية ⇐ صفها.
 */
fun checkSpaceTransferRows(rows: List<BackupRow>, transactions: Map<String, Map<String, BackupRow>>) {
    val used = HashSet<Pair<String, String>>()
    val ids = HashSet<String>()
    for (row in rows) {
        fun bad(field: String): Nothing = throw BackupError(uiText(TextKey.BACKUP_SPACE_TRANSFER_INVALID, field))
        for (f in SPACE_TRANSFER_FIELDS) if (row[f] == null) bad(f)
        for (f in listOf("id", "fromSpaceId", "fromTransactionId", "toSpaceId", "toTransactionId", "createdAt")) if (row[f] !is String) bad(f)
        if (!ids.add(row["id"] as String)) bad("id")
        if (row["fromSpaceId"] == row["toSpaceId"]) bad("toSpaceId")
        if (row["id"] != spaceTransferId(row["fromSpaceId"] as String, row["fromTransactionId"] as String)) bad("id")
        if (row["note"] != null && (row["note"] !is String || (row["note"] as String).length > 1000)) bad("note")
        for ((side, direction) in listOf("from" to "out", "to" to "in")) {
            val spaceId = row["${side}SpaceId"] as String
            val txId = row["${side}TransactionId"] as String
            val amount = row["${side}AmountMinor"]
            if (!isSafeInteger(amount) || numberOf(amount)!! <= 0) bad("${side}AmountMinor")
            val txn = transactions[spaceId]?.get(txId) ?: bad("${side}TransactionId")
            if (txn["observedDirection"] != direction) bad("${side}TransactionId")
            if (numberOf(txn["amountMinor"]) != numberOf(amount) || txn["currency"] != row["${side}Currency"]) bad("${side}AmountMinor")
            if (!used.add(spaceId to txId)) bad("${side}TransactionId")
        }
    }
}
