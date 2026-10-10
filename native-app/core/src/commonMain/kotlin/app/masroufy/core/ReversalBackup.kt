package app.masroufy.core

/**
 * ربط «العملية اللي رجعت» بالأصلية في النسخة الشاملة (§77-D — الشريحة S3) **لين**: بيتحوّل مع المعرّفات في الدمج، بس الربط المكسور
 * **ما بيرفضش الملف** — بيتصلح بنفس قواعد `RepairReversals` قبل ما النسخة تتعمل وقبل الدمج وبعده.
 *
 * **ليه:** التطبيق القديم (`src/`، متجمد) بيشارك نفس البيانات ومش عارف الحقلين دول — التراجع عن دفعة فيه (`revertImportBatch.ts`
 * `deleteMany`) بيمسح رجل من الزوج والتانية بتفضل بتشاور على عملية مش موجودة. لو الربط كان علاقة إجبارية (`BACKUP_RELATIONS`) كان عمل
 * النسخة هيقف («علاقة ناقصة») لحد ما حد يصلّح، واسترجاع نسخة بترتيب «الرجوع قبل الأصلية» كان بيقف كمان.
 */
val BACKUP_SOFT_RELATIONS: Map<String, Map<String, String>> = mapOf(
    "transactions" to linkedMapOf("reversalOfId" to "transactions", "reversedById" to "transactions"),
)

/** النسخة بعد تصليح أزواج «اللي رجعت» المكسورة — **نفس الكائن** لو مفيش حاجة اتصلحت (نفس الملف ونفس البصمة). */
fun settleReversalLinks(data: FullBackupData): FullBackupData {
    val rows = data["transactions"] ?: return data
    val settled = settleReversalRows(rows, emptyMap())
    return if (settled === rows) data else LinkedHashMap(data).apply { put("transactions", settled) }
}

/**
 * العمليات اللي هتتضاف في الاسترجاع ([added]) قدام الموجود في الحساب ([existing] — **ما بيتكتبش فوقه**): شريك موجود ما بيشاورش عليها ⇒
 * اللي هتتضاف بس هي اللي بتتصلح (الرجوع بيرجع سؤال «نلغي الاتنين؟» أو «استرداد» مقترح · الأصلية بترجع نوعها). نفس القايمة لو مفيش.
 */
fun settleAddedReversalLinks(added: List<BackupRow>, existing: List<BackupRow>): List<BackupRow> =
    settleReversalRows(added, existing.associateBy { jsString(it["id"]) })

/** نتيجة الدمج بعد تصليح العمليات المضافة قدام الموجود ([settleAddedReversalLinks]) — نفس الكائن لو مفيش تغيير. */
fun settleMergedReversals(merge: BackupMerge, existing: FullBackupData): BackupMerge {
    val added = merge.additions["transactions"] ?: return merge
    val settled = settleAddedReversalLinks(added, existing["transactions"].orEmpty())
    return if (settled === added) merge else BackupMerge(LinkedHashMap(merge.additions).apply { put("transactions", settled) }, merge.remaps)
}

private const val INTERNAL = "internal_transfer"

private fun rowId(row: BackupRow) = jsString(row["id"])
private fun text(row: BackupRow, field: String): String? = row[field]?.let(::jsString)
private fun isInternal(row: BackupRow) = row["economicKind"] == INTERNAL
private fun confirmed(row: BackupRow) = row["economicKindConfirmed"] == true

/** مستني سؤال من أسئلة §77-D («ده استرداد؟» · «نلغي الاتنين؟») ومش مربوط. */
private fun pendingReturn(row: BackupRow) =
    row["economicKind"] == EconomicKind.UNCLASSIFIED.wire && !confirmed(row) && row["suggestedKind"] != null && row["reversalOfId"] == null

private fun edit(row: BackupRow, put: Map<String, Any?>, remove: List<String> = emptyList()): BackupRow =
    LinkedHashMap(row).apply { putAll(put); for (k in remove) this.remove(k) }

/** الرجوع اتفك ⇒ «استرداد» مقترح مستني تأكيد. */
private fun reopened(row: BackupRow) = edit(
    row, mapOf("economicKind" to EconomicKind.UNCLASSIFIED.wire, "economicKindConfirmed" to false, "reviewState" to ReviewState.NEEDS_REVIEW.wire,
        "suggestedKind" to EconomicKind.REFUND_RECEIVED.wire), listOf("reversalOfId"),
)

/** الرجوع يرجع سؤال «نلغي الاتنين؟». */
private fun recheck(row: BackupRow) = edit(
    row, mapOf("economicKind" to EconomicKind.UNCLASSIFIED.wire, "economicKindConfirmed" to false, "reviewState" to ReviewState.NEEDS_REVIEW.wire,
        "suggestedKind" to EconomicKind.INTERNAL_TRANSFER.wire), listOf("reversalOfId"),
)

/** الأصلية بترجع زي ما كانت (`restoredKindOf`): نوع المالك اللي اتحفظ، وإلا «غير محددة» وحالة مراجعة من تصنيفها. */
private fun restored(row: BackupRow): BackupRow {
    val before = row["kindBeforeReversal"]
    val put = if (before != null) mapOf("economicKind" to before, "economicKindConfirmed" to true)
    else mapOf(
        "economicKind" to EconomicKind.UNCLASSIFIED.wire, "economicKindConfirmed" to false,
        "reviewState" to categoryReviewState(row["categoryId"] != null, row["categoryConfirmed"] == true).wire,
    )
    return edit(row, put, listOf("reversedById", "kindBeforeReversal", "suggestedKind"))
}

private fun cancelled(row: BackupRow, partner: String, field: String): BackupRow {
    val put = linkedMapOf<String, Any?>("economicKind" to INTERNAL, "economicKindConfirmed" to true, field to partner)
    if (!confirmed(row)) put["reviewState"] = ReviewState.CONFIRMED.wire else if (field == "reversedById") put["kindBeforeReversal"] = row["economicKind"]
    return edit(row, put, listOf("suggestedKind"))
}

/**
 * قواعد `RepairReversals` على صفوف النسخة. [rows] بيتصلحوا · [readOnly] = شركاء موجودين ما بيتلمسوش (الاسترجاع): الأصلية الموجودة اللي
 * ما اتعلّمتش ⇒ الرجوع بيتسأل بدل ما الأصلية تتلغي. بترجع [rows] نفسها لو مفيش تغيير.
 */
internal fun settleReversalRows(rows: List<BackupRow>, readOnly: Map<String, BackupRow>): List<BackupRow> {
    if (rows.none { it["reversalOfId"] != null || it["reversedById"] != null }) return rows
    val byId = LinkedHashMap<String, BackupRow>().apply { for (r in rows) put(rowId(r), r) }
    var changed = false
    var pass = 0
    var again = true
    // لحد ما مفيش تغيير (كل قاعدة بتقرّب للحالة السليمة) — مرتين تلاتة بالكتير
    while (again && pass++ < 4) {
        again = false
        settlePass(byId, readOnly) { row ->
            byId[rowId(row)] = row
            changed = true
            again = true
        }
    }
    return if (!changed) rows else rows.map { byId.getValue(rowId(it)) }
}

private fun settlePass(byId: Map<String, BackupRow>, readOnly: Map<String, BackupRow>, set: (BackupRow) -> Unit) {
    fun partner(id: String): BackupRow? = byId[id] ?: readOnly[id]
    for (id in byId.keys.toList()) {
        val ret = byId.getValue(id)
        val originalId = text(ret, "reversalOfId")
        if (originalId != null) {
            val original = partner(originalId)
            val editable = originalId in byId
            when {
                !isInternal(ret) -> {
                    set(edit(ret, emptyMap(), listOf("reversalOfId")))
                    if (original != null && editable && text(original, "reversedById") == id && isInternal(original)) set(restored(original))
                }
                original == null || original["reversalOfId"] != null -> set(reopened(ret))
                text(original, "reversedById") == null ->
                    if (editable && !confirmed(original)) set(cancelled(original, id, "reversedById")) else set(recheck(ret))
                text(original, "reversedById") != id -> set(reopened(ret))
                !isInternal(original) -> {
                    if (editable) set(edit(original, emptyMap(), listOf("reversedById", "kindBeforeReversal")))
                    set(reopened(ret))
                }
            }
        }
        val original = byId.getValue(id)
        val returnId = text(original, "reversedById") ?: continue
        val back = partner(returnId)
        if (back != null && text(back, "reversalOfId") == id) continue
        when {
            !isInternal(original) -> set(edit(original, emptyMap(), listOf("reversedById", "kindBeforeReversal")))
            back != null && returnId in byId && pendingReturn(back) -> set(cancelled(back, id, "reversalOfId"))
            else -> set(restored(original))
        }
    }
}
