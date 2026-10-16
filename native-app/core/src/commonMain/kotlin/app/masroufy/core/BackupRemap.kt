package app.masroufy.core

/**
 * الحقول اللي جواها معرّفات في «أضف الناقص» (`mergeFullBackupDetailed`) غير علاقات [BACKUP_RELATIONS]:
 * - «مش ده» (§75-8 — [remapDismissedTxnIds]).
 * - **رابط الشخص** (R6 — جولة r05): معرّف المستند = معرّف العملية دايمًا (`personLinks/{transactionId}`)، فلما العملية تتطابق مع عملية
 *   موجودة بمعرّف تاني، المعرّف بيتغير معاها — وإلا الرابط بيتكتب تحت المعرّف القديم و«فك الربط» ومسح العملية (بيشيلوا بمعرّف العملية)
 *   عمرهم ما يوصلوله. والسدادات اللي اتطابقت مع سدادات موجودة بمعرّف تاني بتتغير في قايمته.
 */
internal fun remapRowLists(group: String, row: MutableMap<String, Any?>, maps: Map<String, Map<String, String>>) {
    remapDismissedTxnIds(group, row, maps["transactions"])
    if (group != PERSON_LINKS_GROUP) return
    row["transactionId"]?.let { row["id"] = it }
    val settlements = maps["settlements"]
    val list = row["settlementIds"] as? List<*> ?: return
    if (!settlements.isNullOrEmpty()) row["settlementIds"] = list.map { id -> settlements[jsString(id)] ?: id }
}
