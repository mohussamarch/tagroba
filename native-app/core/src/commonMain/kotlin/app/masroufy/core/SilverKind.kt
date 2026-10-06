package app.masroufy.core

/**
 * **الفضة نوع لوحده في كوتلن بس** (رد المالك §69.9 — زي العقار §69.7). التطبيق الحالي (`src/`، نفس مستندات `users/{uid}`)
 * بيرفض أي نوع أصل مش في قايمته وهو بيعمل النسخة الشاملة أو بيرجّعها (`src/domain/checkFullBackup.ts` سطر 71 و76) ⇒
 * **النوع المتخزن "other" + علامة `silver: true`** (بتتكتب لو true بس ⇒ مستند الأصل التاني ونسخته هي هي بالحرف).
 *
 * **الفرق عن العقار (قرار جلسة 28):** العقار مالوش `kind` في كوتلن (النوع المتخزن هو هو، والعقار بيتعرف بـ[isRealEstate]).
 * الفضة ليها `kind` = "silver" في كوتلن من §62، والزكاة والأسماء ووحدة القياس والتوقّع بيقروه ⇒ **التحويل في المحوّل بس**
 * (`AssetProjectCodecs.assets` — طبقة التخزين): [Asset.kind] في الدومين بيفضل "silver"، والمستند بيتكتب "other" + العلامة.
 * يعني **أي حفظ** (إضافة · أرشفة · ربط بالأسعار · حقول التوقّع) بيكتب الشكل الجديد — مفيش مسار بيكتب "silver" تاني.
 *
 * **القديم:** مستند متخزن قبل كده بنوع "silver" بيتقري فضة، وأول حفظ ليه بيكتبه بالشكل الجديد. ونسخة شاملة فيها "silver"
 * بتتقبل وبتتحوّل ([normalizeLegacySilverAssets]) قبل ما تتكتب أو تتصدّر.
 */
const val ASSET_KIND_SILVER = "silver"

/** اسم علامة الفضة في المستند والنسخة الشاملة. */
const val SILVER_MARKER_FIELD = "silver"

/** نوع كوتلن ⇒ (النوع المتخزن، علامة الفضة): الفضة ⇒ ("other"، true) · غيرها زي ما هو. */
fun storedSilverForm(kind: String): Pair<String, Boolean> = if (kind == ASSET_KIND_SILVER) "other" to true else kind to false

/**
 * المتخزن ⇒ نوع كوتلن: "other" + العلامة ⇒ فضة · غير كده النوع المتخزن زي ما هو — يعني "silver" القديم بيتقري فضة لوحده.
 * العلامة على نوع تاني (مستند مش متسق، والنسخة الشاملة بترفضه) ما بتغيرش النوع، وأول حفظ بيشيلها.
 */
fun kindFromStored(storedKind: String, silverMarker: Boolean): String =
    if (storedKind == "other" && silverMarker) ASSET_KIND_SILVER else storedKind

/**
 * سطور الأصول بنوع "silver" القديم ⇒ "other" + `silver: true` (نفس مكان الحقل، والعلامة في الآخر). مفيش سطر فضة قديم ⇒
 * **نفس الكائن بالظبط** (النسخة اللي من غير فضة هي هي بالحرف ونفس البصمة).
 */
fun normalizeLegacySilverAssets(data: FullBackupData): FullBackupData {
    val assets = data["assets"] ?: return data
    if (assets.none { it["kind"] == ASSET_KIND_SILVER }) return data
    val rows = assets.map { row ->
        if (row["kind"] != ASSET_KIND_SILVER) row else LinkedHashMap(row).apply { put("kind", "other"); put(SILVER_MARKER_FIELD, true) }
    }
    return LinkedHashMap(data).apply { put("assets", rows) }
}
