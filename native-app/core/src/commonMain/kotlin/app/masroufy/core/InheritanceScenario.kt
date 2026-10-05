package app.masroufy.core

/**
 * **حسبة ورث محفوظة** (رد المالك §69.3 «تتحفظ على الحساب وتبان على كل الأجهزة» — OVERRIDES §69.4).
 * - **على مستوى الحساب** (زي خطط الادخار §68) — مجموعة [INHERITANCE_SCENARIOS_GROUP]، والقواعد العامة `users/{uid}/{document=**}` بتغطيها.
 * - بتحفظ **المدخلات بس** (مش النتيجة): اسم · تركة مين ([EstateOwner] + شخص اختياري) · البلد · الحاجات · الورثة بالعدد · التجهيز والديون
 *   والوصية · اللي مات قبله (مصر) · ذوو الأرحام · تاريخ الإنشاء والتعديل. النتيجة بتتحسب تاني وقت الفتح (عشان أي تصحيح في المحرك يوصل).
 * - **المسح مسموح** (رد المالك): سيناريو عمله المستخدم بإيده، مش بيانات فلوس.
 * - الحسبة ممكن تتحفظ ناقصة (مسودة) — الفحص هنا على الشكل بس (أعداد في حدودها · مبالغ مش بالسالب)، والحساب هو اللي بيقول لو ناقص.
 */
const val INHERITANCE_SCENARIOS_GROUP = "inheritanceScenarios"

const val INHERIT_SCENARIO_NAME_MAX = 60

/** تركة مين (§69): «تركتي أنا» · «تركة شخص تاني» (من أشخاصه أو اسم جديد). */
enum class EstateOwner(val wire: String) {
    MINE("mine"),
    OTHER("other"),
    ;

    companion object {
        fun fromWire(wire: String): EstateOwner? = entries.firstOrNull { it.wire == wire }
    }
}

/** الاسم المخزن للظروف الخاصة. */
val SpecialCircumstance.wire: String get() = name.lowercase()

fun specialCircumstanceFromWire(wire: String): SpecialCircumstance? = SpecialCircumstance.entries.firstOrNull { it.wire == wire }

/** [personId] للتركة الـ[EstateOwner.OTHER] بس (اختياري — ممكن يكون اسم جديد في [name]). [input] المسألة زي ما اتكتبت. */
data class InheritanceScenario(
    val id: Id,
    val name: String,
    val estateOf: EstateOwner,
    val personId: Id?,
    val input: InheritanceCase,
    val createdAt: String,
    val updatedAt: String,
)

class InheritanceScenarioError(message: String) : IllegalArgumentException(message)

/** فحص قبل التخزين: اسم (لحد [INHERIT_SCENARIO_NAME_MAX]) · «تركتي» من غير شخص · بلد ليها قانون · الأعداد والمبالغ في حدودها. */
fun checkInheritanceScenario(s: InheritanceScenario): InheritanceScenario {
    val name = JsText.collapseWhitespace(JsText.trim(s.name))
    if (name.isEmpty()) throw InheritanceScenarioError(uiText(TextKey.INHERIT_SCENARIO_NAME_REQUIRED))
    if (name.length > INHERIT_SCENARIO_NAME_MAX) throw InheritanceScenarioError(uiText(TextKey.INHERIT_SCENARIO_NAME_LENGTH, INHERIT_SCENARIO_NAME_MAX.toString()))
    if (s.estateOf == EstateOwner.MINE && s.personId != null) throw InheritanceScenarioError(uiText(TextKey.INHERIT_SCENARIO_PERSON_MINE))
    val law = InheritanceLaw.of(s.input.countryCode) ?: throw InheritanceScenarioError(uiText(TextKey.INHERIT_SCENARIO_COUNTRY))
    val c = s.input
    val counts = c.heirs.entries.map { it.key.label to it.value } + c.predeceasedChildren.flatMap { listOf(HeirKind.SON.label to it.sons, HeirKind.DAUGHTER.label to it.daughters) } +
        c.distantBranches.flatMap { listOf(it.parent.label to it.sons, it.parent.label to it.daughters) }
    counts.firstOrNull { (_, n) -> n < 0 || n > INHERIT_MAX_COUNT }?.let { (label, _) ->
        throw InheritanceScenarioError(uiText(TextKey.INHERIT_INVALID_COUNT, label, INHERIT_MAX_COUNT.toString()))
    }
    val amounts = c.items.map { it.valueMinor } + listOf(c.funeralMinor, c.debtsMinor) + listOfNotNull(c.bequest?.amountMinor) + c.predeceasedChildren.map { it.givenInLifeMinor }
    if (amounts.any { it < 0 }) throw InheritanceScenarioError(uiText(TextKey.INHERIT_INVALID_NEGATIVE))
    if (amounts.any { it > MAX_SAFE_HALALAS }) throw InheritanceScenarioError(uiText(TextKey.INHERIT_INVALID_TOO_LARGE))
    return s.copy(name = name, input = c.copy(countryCode = law.code, heirs = c.heirs.filterValues { it > 0 }))
}

/**
 * فحص سطر الحسبة في النسخة الشاملة (`BackupCheck`): الحقول المتداخلة (الورثة · الحاجات · الوصية · اللي مات قبله · ذوو الأرحام)
 * بنفس شروط المحوّل (`InheritanceCodecs`) — عشان النسخة ما ترجعش سطر المحوّل يقع وهو بيقراه.
 */
internal fun checkInheritanceScenarioRow(row: Map<String, Any?>) {
    val g = INHERITANCE_SCENARIOS_GROUP
    fun bad(field: String): Nothing = throw BackupError(uiText(TextKey.BACKUP_VALUE_UNSUPPORTED, g, field))
    fun count(v: Any?, field: String) = if (!isSafeInteger(v) || numberOf(v)!! < 0 || numberOf(v)!! > INHERIT_MAX_COUNT) bad(field) else Unit
    fun money(v: Any?, field: String) = if (!isSafeInteger(v) || numberOf(v)!! < 0) bad(field) else Unit
    fun maps(field: String): List<Map<*, *>> = when (val v = row[field]) {
        null -> emptyList()
        is List<*> -> v.map { it as? Map<*, *> ?: bad(field) }
        else -> bad(field)
    }
    if (EstateOwner.fromWire(jsString(row["estateOf"])) == null) bad("estateOf")
    if (row["estateOf"] == EstateOwner.MINE.wire && row["personId"] != null) bad("personId")
    fun kind(m: Map<*, *>, field: String) = if ((m["kind"] as? String)?.let(HeirKind::fromWire) == null) bad(field) else Unit
    if (row["heirs"] !is List<*>) bad("heirs")
    for (h in maps("heirs")) {
        kind(h, "heirs")
        count(h["count"], "heirs")
    }
    if (row["items"] !is List<*>) bad("items")
    for (m in maps("items")) {
        if (m["name"] !is String) bad("items")
        money(m["valueMinor"], "items")
    }
    for (n in maps("names")) {
        kind(n, "names")
        val values = n["values"]
        if (values !is List<*> || values.any { it !is String }) bad("names")
    }
    row["bequest"]?.let { b ->
        val m = b as? Map<*, *> ?: bad("bequest")
        money(m["amountMinor"], "bequest")
        if (m["toHeir"] !is Boolean || m["heirsConsent"] !is Boolean) bad("bequest")
    }
    for (p in maps("predeceased")) {
        if (p["isSon"] !is Boolean) bad("predeceased")
        count(p["sons"], "predeceased")
        count(p["daughters"], "predeceased")
        money(p["givenInLifeMinor"], "predeceased")
    }
    for (b in maps("distantBranches")) {
        val parent = (b["parent"] as? String)?.let(HeirKind::fromWire)
        if (parent == null || parent !in DISTANT_BRANCH_CHILDREN) bad("distantBranches")
        count(b["sons"], "distantBranches")
        count(b["daughters"], "distantBranches")
    }
    row["special"]?.let { s -> if (s !is List<*> || s.any { it !is String || specialCircumstanceFromWire(it) == null }) bad("special") }
    if (row["distantRelatives"] != null && row["distantRelatives"] !is Boolean) bad("distantRelatives")
}
