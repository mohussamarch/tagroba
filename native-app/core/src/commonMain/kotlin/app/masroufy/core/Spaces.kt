package app.masroufy.core

/**
 * «حساب لكل بلد» (OVERRIDES §41 · §64): كل بلد **مساحة** (space) لوحدها — محافظها وعملياتها وميزانيتها وتصنيفاتها وديونها وزكاتها.
 * - **مساحة السعودية = بيانات التطبيق الحالي في مكانها** (`users/{uid}`) من غير أي نقل ولا كتابة على مستنداتها القديمة.
 *   معرّفها [DEFAULT_SPACE_ID] وما بتتخزنش في السجل — بتتبني في الكود.
 * - البلاد التانية في `users/{uid}/spaces/{id}/…` بنفس المجموعات، ومعرّفها من كود البلد ([spaceIdForCountry]).
 * - **بلد واحدة = مساحة واحدة** (رد المالك §64-٢) — والاسترجاع بيربط كل بلد ببلدها.
 * - **مفيش مسح** للمساحة — أرشفة بس (الفلوس اللي اتسجلت ما تختفيش).
 */
const val DEFAULT_SPACE_ID = "default"

/** سجل المساحات (على مستوى الحساب) — مستند لكل بلد تانية في `users/{uid}/spaces/{id}`. */
const val SPACES_GROUP = "spaces"

/** التحويل لنفسك بين بلدين (§64) — زوج مربوط على مستوى الحساب، لأنه بين مساحتين. */
const val SPACE_TRANSFERS_GROUP = "spaceTransfers"

/**
 * تصنيف التاجر **جوه مساحة** غير السعودية (اختيار Claude §64): التاجر نفسه مشترك على مستوى الحساب، وتصنيفه بيختلف بين البلدين،
 * فتصنيفه في مصر بيتخزن هنا ومش بيلمس مستند التاجر المشترك.
 */
const val MERCHANT_CATEGORIES_GROUP = "merchantCategories"

/**
 * مجموعات بيانات **مشتركة** بين البلاد (§41): التجار · الأشخاص · الوسوم · مناسبات الشخص (§64) · دواير الأشخاص والصلات بينهم
 * (جلسة 16 — الشخص مشترك فدايرته وصلاته مشتركة) · المجموعات المقفولة من التنبيهات (جلسة 18 — المحرك واحد للحساب، §61) ·
 * خطط الادخار وإيداعاتها (§68 — الخطة للشخص مش للبلد).
 */
val ACCOUNT_DATA_GROUPS: List<String> = listOf(
    "merchants", "people", "tags", "occasions", PERSON_PROFILES_GROUP, PERSON_RELATIONS_GROUP, ALERT_SETTINGS_GROUP, SAVINGS_GOALS_GROUP,
    GOAL_CONTRIBUTIONS_GROUP,
)

/**
 * كل اللي على مستوى الحساب: البيانات المشتركة + سجل المساحات + أزواج التحويل لنفسك + صفحة الإشعارات وإيصالاتها
 * (بتتزامن ومش في النسخة — §61). (ملف الحساب `profile/main` مستند لوحده.)
 */
val ACCOUNT_GROUPS: List<String> = ACCOUNT_DATA_GROUPS + SPACES_GROUP + SPACE_TRANSFERS_GROUP + ALERT_SYNC_ONLY_GROUPS

/**
 * كل مجموعة تانية **جوه المساحة**. محسوبة من [BACKUP_GROUPS] ناقص [ACCOUNT_GROUPS] — عن قصد:
 * أي مجموعة جديدة بتتضاف للنسخة الشاملة **بتبقى جوه المساحة لوحدها** إلا لو حد قرر غير كده صراحة (الأأمن: بيانات البلد ما تتخلطش).
 */
val SPACE_GROUPS: List<String> get() = BACKUP_GROUPS.filter { it !in ACCOUNT_GROUPS }

data class Space(
    val id: String,
    val name: String,
    /** ISO 3166-1 alpha-2 — من [COUNTRY_PACKS]. */
    val countryCode: String,
    val currency: Currency,
    val createdAt: String,
    val archived: Boolean = false,
)

/** تصنيف تاجر مشترك **جوه مساحة** غير السعودية ([MERCHANT_CATEGORIES_GROUP]) — معرّف المستند = معرّف التاجر. */
data class MerchantCategory(val merchantId: Id, val categoryId: Id)

/** معرّف مساحة البلد: كود البلد صغير (`eg`). ثابت ⇒ جهازين بيعملوا نفس البلد مع بعض بيكتبوا **نفس** المستند، مش مساحتين. */
fun spaceIdForCountry(countryCode: String): String = countryCode.lowercase()

private val SPACE_ID = Regex("^(default|[a-z]{2})$")

/** شكل معرّف مساحة سليم — القيمة البايظة المتخزنة على الجهاز بتتعامل كإنها مش موجودة (⇒ السعودية). */
fun isSpaceId(text: String?): Boolean = text != null && SPACE_ID.matches(text)

/** اسم البلد للعرض جوه التطبيق (مش على شاشة القفل). */
fun countryLabel(countryCode: String): String = when (countryCode.uppercase()) {
    "SA" -> uiText(TextKey.COUNTRY_SA)
    "EG" -> uiText(TextKey.COUNTRY_EG)
    else -> countryCode.uppercase()
}

/** مساحة السعودية = بيانات التطبيق الحالي في `users/{uid}`. بتتبني هنا ومش بتتخزن في السجل. */
fun defaultSpace(): Space = Space(DEFAULT_SPACE_ID, countryLabel(SAUDI_PACK.code), SAUDI_PACK.code, SAUDI_PACK.currency, createdAt = "", archived = false)

class SpaceError(message: String) : IllegalArgumentException(message)

/**
 * مساحة جديدة لبلد: البلد لازم تبقى معروفة، والعملة من حزمتها، و**بلد واحدة = مساحة واحدة** — حتى لو المساحة القديمة مؤرشفة
 * (ترجع من الأرشيف بدل ما تتعمل تانية)، والسعودية موجودة من الأول.
 */
fun newSpaceFor(countryCode: String, existing: List<Space>, nowIso: String): Space {
    val code = countryCode.uppercase()
    val pack = COUNTRY_PACKS[code] ?: throw SpaceError(uiText(TextKey.SPACE_COUNTRY_UNKNOWN))
    val taken = existing.map { it.countryCode.uppercase() }.toSet() + SAUDI_PACK.code
    if (code in taken) throw SpaceError(uiText(TextKey.SPACE_COUNTRY_TAKEN, countryLabel(code)))
    return Space(spaceIdForCountry(code), countryLabel(code), code, pack.currency, nowIso, archived = false)
}

/** كل المساحات للعرض: السعودية الأول، وبعدها السجل بترتيب الإنشاء. */
fun allSpaces(registry: List<Space>): List<Space> = listOf(defaultSpace()) + registry.filter { it.id != DEFAULT_SPACE_ID }.sortedBy { it.createdAt }

/** المساحة الشغالة: المتخزنة على الجهاز لو موجودة ومش مؤرشفة، وإلا السعودية. */
fun activeSpaceOf(storedId: String?, registry: List<Space>): Space {
    if (storedId == null || storedId == DEFAULT_SPACE_ID) return defaultSpace()
    return registry.firstOrNull { it.id == storedId && !it.archived } ?: defaultSpace()
}
