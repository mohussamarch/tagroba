package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.ClassificationRule
import app.masroufy.core.CountryPack
import app.masroufy.core.UserSetting
import app.masroufy.core.normalizeText
import app.masroufy.core.repairDoneKey
import app.masroufy.port.CategoryRepository
import app.masroufy.port.Clock
import app.masroufy.port.RuleRepository
import app.masroufy.port.SpaceSeedSourceLoader
import app.masroufy.port.UserSettingsStore

/** أول قاعدة من قايمة محلات دراهم في `rule-reference.json` (§109-6 — اتضافت بعد 228 بند قديم ⇒ معرّفها من `rule-0229`). */
const val DARAHIM_RULES_FROM_PRIORITY = 229

/** اسم التصليح الثابت في علامة «خلص» (`repairDone.darahimRules.<spaceId>`). */
const val DARAHIM_RULES_REPAIR = "darahimRules"

/**
 * r06-1e (OVERRIDES §110-2 — رد المالك: قواعد محلات دراهم «بتتضاف للحسابات الموجودة كمان»): **تصليح مرة واحدة لكل بلد** في دورة الخلفية
 * (بعد `RepairMissingDefaultSubs` — عشان فرعيات زي «عصير» تبقى موجودة).
 * - بيضيف قواعد دراهم من بذرة البلد ([DARAHIM_RULES_FROM_PRIORITY] وبعدها) **بس**: ولا قاعدة موجودة بتتغير ولا بتتمسح.
 * - القاعدة بتتساب لو فيه قاعدة بنفس المعرّف أو بنفس الكلمة (بعد [normalizeText]) — كلمة المالك هي اللي تمشي — أو تصنيفها مش عنده.
 * - الأولوية **بعد آخر قاعدة عنده** (زي «قاعدة جديدة» في `ManageRules`) ⇒ عمرها ما بتسبق قاعدة المالك ولا بتغيّر تصنيف اختاره.
 * - بعد ما يخلص بيكتب علامة «خلص» للبلد ([UserSetting.RepairDone]) ⇒ القاعدة اللي المالك يمسحها بعد كده ما بترجعش.
 * - حساب لسه ما اتزرعش (من غير تصنيفات) ⇒ ما بيعملش حاجة وما بيعلّمش (الزرع نفسه بيجيب القواعد).
 */
class RepairDarahimRules(
    private val spaceId: String,
    private val categories: CategoryRepository,
    private val rules: RuleRepository,
    private val seeds: SpaceSeedSourceLoader,
    private val pack: CountryPack,
    private val settings: UserSettingsStore,
    private val clock: Clock,
) {
    /** عدد القواعد اللي اتضافت، أو null لو اتعمل قبل كده (أو الحساب مش متزرع). */
    suspend fun run(): Int? {
        val key = repairDoneKey(DARAHIM_RULES_REPAIR, spaceId)
        if (settings.listAll().any { it.key == key }) return null
        val mine = categories.listAll()
        if (mine.isEmpty()) return null
        val added = missingDarahimRules(rules.listAll(), seeds.load(pack).rules, mine)
        if (added.isNotEmpty()) rules.saveMany(added)
        settings.save(UserSetting.RepairDone(spaceId, DARAHIM_RULES_REPAIR, clock.nowIso()))
        return added.size
    }
}

/** قواعد دراهم الناقصة من [existing] (نقية — للاختبار): من غير تكرار معرّف أو كلمة، وتصنيفها موجود، وأولويتها بعد آخر قاعدة. */
fun missingDarahimRules(existing: List<ClassificationRule>, seeded: List<ClassificationRule>, categories: List<Category>): List<ClassificationRule> {
    val ids = existing.mapTo(HashSet()) { it.id }
    val words = existing.mapTo(HashSet()) { normalizeText(it.matchText) }
    val categoryIds = categories.mapTo(HashSet()) { it.id }
    var priority = (existing.maxOfOrNull { it.priority } ?: 0) + 10
    return seeded.filter { it.priority >= DARAHIM_RULES_FROM_PRIORITY }
        .filter { it.id !in ids && it.categoryId in categoryIds && words.add(normalizeText(it.matchText)) }
        .map { it.copy(priority = priority++) }
}
