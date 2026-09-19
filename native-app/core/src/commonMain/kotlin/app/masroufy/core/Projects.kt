package app.masroufy.core

/**
 * المشاريع — نقل `src/domain/projects.ts` + الكيانات (OVERRIDES §34). **علامة إضافية مش تصنيف**.
 * «صرفت» = نصيبك + المستبعد من الميزانية؛ «جالك» = الدخل؛ غير المؤكد «تقريبي» زي الرئيسية.
 * القاعدة بتضيف لوحدها اللي **اتسجل بعدها** بس؛ القديم بسؤال وقت عملها.
 */
data class Project(val id: Id, val name: String, val normalizedName: String, val archived: Boolean, val createdAt: String)

/** "manual" / "rule" / "excluded" (المستخدم شالها — بتفضل عشان قاعدة ما ترجعهاش). */
data class ProjectLink(val id: Id, val projectId: Id, val transactionId: Id, val source: String, val createdAt: String)

data class ProjectRule(
    val id: Id,
    val projectId: Id,
    val matchText: String,
    val matchMode: RuleMatchMode,
    /** "out" / "in" / "any". */
    val direction: String,
    val enabled: Boolean,
    val createdAt: String,
)

const val PROJECT_NAME_MAX = 60
const val PROJECT_RULE_TEXT_MAX = 60

class ProjectError(message: String) : IllegalArgumentException(message)

data class CheckedName(val name: String, val normalizedName: String)

fun checkProjectName(name: String, projects: List<Project>, selfId: Id? = null): CheckedName {
    val clean = JsText.collapseWhitespace(JsText.trim(name))
    if (clean.isEmpty() || clean.length > PROJECT_NAME_MAX) throw ProjectError("اكتب اسم المشروع بحد أقصى $PROJECT_NAME_MAX حرف.")
    val normalized = normalizeText(clean)
    if (projects.any { it.id != selfId && it.normalizedName == normalized }) throw ProjectError("فيه مشروع بنفس الاسم.")
    return CheckedName(clean, normalized)
}

data class CheckedRule(val matchText: String, val matchMode: RuleMatchMode, val direction: String)

fun checkProjectRule(matchText: String, matchMode: RuleMatchMode, direction: String): CheckedRule {
    val text = JsText.collapseWhitespace(JsText.trim(matchText))
    if (text.length < 2 || text.length > PROJECT_RULE_TEXT_MAX) throw ProjectError("اكتب نص القاعدة من حرفين لـ $PROJECT_RULE_TEXT_MAX حرف.")
    if (direction !in listOf("out", "in", "any")) throw ProjectError("الاتجاه مش معروف.")
    return CheckedRule(text, matchMode, direction)
}

/** معرّف ثابت ⇒ ربط واحد بس لنفس العملية في نفس المشروع. */
fun projectLinkId(projectId: Id, transactionId: Id) = "plink-$projectId-$transactionId"

/** العمليات اللي في المشروع فعلًا (المستبعدة لأ). */
fun memberIds(links: List<ProjectLink>, projectId: Id): Set<Id> =
    links.filter { it.projectId == projectId && it.source != "excluded" }.map { it.transactionId }.toCollection(LinkedHashSet())

fun ruleMatchesTransaction(matchText: String, matchMode: RuleMatchMode, direction: String, t: Transaction): Boolean {
    if (direction != "any" && t.observedDirection.wire != direction) return false
    return listOf(t.rawMerchantName, t.rawDescription).any { !it.isNullOrEmpty() && matchesText(matchText, matchMode, it) }
}

/** المطابقة ومش مربوطة بمشروعها بأي شكل (المستبعدة بإيد المستخدم ما بتتلمسش). */
fun ruleCandidates(rule: ProjectRule, transactions: List<Transaction>, links: List<ProjectLink>): List<Transaction> {
    val known = links.filter { it.projectId == rule.projectId }.map { it.transactionId }.toSet()
    return transactions.filter { it.id !in known && ruleMatchesTransaction(rule.matchText, rule.matchMode, rule.direction, it) }
}

fun planRuleLinks(rule: ProjectRule, transactions: List<Transaction>, links: List<ProjectLink>, now: String): List<ProjectLink> =
    ruleCandidates(rule, transactions, links).map { ProjectLink(projectLinkId(rule.projectId, it.id), rule.projectId, it.id, "rule", now) }

/** كل قاعدة شغالة بتاخد اللي **اتسجل بعدها** بس؛ قاعدتين على نفس العملية في نفس المشروع ⇒ ربط واحد. */
fun planSyncLinks(rules: List<ProjectRule>, transactions: List<Transaction>, links: List<ProjectLink>, now: String): List<ProjectLink> {
    val planned = mutableListOf<ProjectLink>()
    for (rule in rules) {
        if (!rule.enabled) continue
        planned += planRuleLinks(rule, transactions.filter { it.createdAt > rule.createdAt }, links + planned, now)
    }
    return planned
}

/** من إمتى نراجع الجديد: آخر مراجعة، ومش قبل أقدم قاعدة شغالة. null = مفيش قواعد. */
fun syncStart(rules: List<ProjectRule>, cursor: String?): String? {
    val oldest = rules.filter { it.enabled }.map { it.createdAt }.minOrNull() ?: return null
    return if (!cursor.isNullOrEmpty() && cursor > oldest) cursor else oldest
}

/** إضافة أو شيل بإيد المستخدم — الروابط اللي هتتحفظ (فاضية = مفيش تغيير). الشيل بيسيبها «مستبعدة». */
fun membershipChanges(existing: List<ProjectLink>, projectId: Id, transactionId: Id, member: Boolean, now: String): List<ProjectLink> {
    if (!member) return existing.filter { it.source != "excluded" }.map { it.copy(source = "excluded") }
    if (existing.any { it.source != "excluded" }) return emptyList()
    val first = existing.firstOrNull()
    return listOf(ProjectLink(first?.id ?: projectLinkId(projectId, transactionId), projectId, transactionId, "manual", first?.createdAt ?: now))
}

data class ProjectSummary(val spentMinor: Halalas, val receivedMinor: Halalas, val count: Int, val estimatedCount: Int, val needsReviewCount: Int)

fun summarizeProject(transactions: List<Transaction>, allocations: List<PersonAllocation>, categoryNameById: Map<String, String>): ProjectSummary {
    val view = withEstimatedKinds(transactions, categoryNameById)
    val totals = computePeriodTotals(view.transactions, allocations)
    return ProjectSummary(addMoney(totals.personalExpenseMinor, totals.excludedExpenseMinor), totals.incomeMinor, transactions.size, view.estimatedCount, view.needsReviewCount)
}
