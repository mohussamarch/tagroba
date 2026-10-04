package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.PersonAllocation
import app.masroufy.core.Project
import app.masroufy.core.ProjectError
import app.masroufy.core.ProjectKind
import app.masroufy.core.ProjectLink
import app.masroufy.core.ProjectRule
import app.masroufy.core.ProjectSummary
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.arabicCompare
import app.masroufy.core.checkProjectName
import app.masroufy.core.checkProjectRule
import app.masroufy.core.memberIds
import app.masroufy.core.membershipChanges
import app.masroufy.core.planRuleLinks
import app.masroufy.core.planSyncLinks
import app.masroufy.core.ruleCandidates
import app.masroufy.core.summarizeProject
import app.masroufy.core.syncStart
import app.masroufy.core.uiText
import app.masroufy.port.AllocationRepository
import app.masroufy.port.CategoryRepository
import app.masroufy.port.Clock
import app.masroufy.port.IdGenerator
import app.masroufy.port.ProjectLinkRepository
import app.masroufy.port.ProjectRepository
import app.masroufy.port.ProjectRuleRepository
import app.masroufy.port.SyncCursorPort
import app.masroufy.port.TransactionRepository

/**
 * ManageProjects — نقل `manageProjects.ts` (OVERRIDES §34). الحسابات في `core/Projects.kt`؛ هنا قراية وكتابة بس.
 * القاعدة بتضيف لوحدها اللي اتسجل بعدها (بتتراجع وقت فتح المشاريع)، والقديم بسؤال المستخدم وقت عملها.
 */

data class ProjectsDeps(
    val projects: ProjectRepository,
    val links: ProjectLinkRepository,
    val rules: ProjectRuleRepository,
    val txns: TransactionRepository,
    val allocations: AllocationRepository,
    val categories: CategoryRepository,
    val ids: IdGenerator,
    val clock: Clock,
    /** آخر مراجعة للعمليات الجديدة على الجهاز ده (ضياعها = مراجعة تانية من أقدم قاعدة، من غير تكرار). */
    val cursor: SyncCursorPort,
    /** رجول التحويل لنفسك (§64) — ما تدخلش مشروع لا بإيدك ولا بقاعدة. التشغيل الحقيقي بيدّيه. */
    val spaceLegs: app.masroufy.port.SpaceTransferLegs? = null,
)

data class ProjectRow(val project: Project, val summary: ProjectSummary)

data class ProjectLists(val active: List<ProjectRow>, val archived: List<ProjectRow>)

/** `source`: "rule" = قاعدة ضافتها، "manual" = إنت. */
data class ProjectTransaction(val transaction: Transaction, val source: String)

data class ProjectDetail(
    val project: Project,
    val summary: ProjectSummary,
    val rules: List<ProjectRule>,
    /** الأحدث الأول. */
    val transactions: List<ProjectTransaction>,
)

data class ProjectMembership(val project: Project, val member: Boolean)

data class AddedProjectRule(val rule: ProjectRule, val oldMatches: Int)

/** العمليات «القديمة» لقاعدة جديدة: آخر 3 سنين بس — قراية واحدة بطلب المستخدم (ARCHITECTURE §5.6: كل استعلام محدود). */
private const val OLD_YEARS = 3

class ManageProjects(private val deps: ProjectsDeps) {
    private class Loaded(
        val projects: List<Project>,
        val links: List<ProjectLink>,
        val transactions: List<Transaction>,
        val allocations: List<PersonAllocation>,
        val names: Map<String, String>,
    )

    suspend fun syncRules(): Int {
        val rules = deps.rules.listAll()
        val since = syncStart(rules, deps.cursor.read()) ?: return 0
        val fresh = deps.txns.listCreatedAfter(since)
        if (fresh.isEmpty()) return 0
        val planned = withoutLegs(planSyncLinks(rules, fresh, deps.links.listAll(), deps.clock.nowIso()))
        if (planned.isNotEmpty()) deps.links.saveMany(planned)
        deps.cursor.write(fresh.fold(since) { max, t -> if (t.createdAt > max) t.createdAt else max })
        return planned.size
    }

    private suspend fun withoutLegs(planned: List<ProjectLink>): List<ProjectLink> {
        val legs = deps.spaceLegs?.legsAmong(planned.map { it.transactionId }).orEmpty()
        return if (legs.isEmpty()) planned else planned.filter { it.transactionId !in legs }
    }

    private suspend fun load(projectId: Id? = null): Loaded {
        val projects = deps.projects.listAll()
        val links = deps.links.listAll()
        val categories = deps.categories.listAll()
        val ids = links.filter { it.source != "excluded" && (projectId == null || it.projectId == projectId) }
            .map { it.transactionId }.distinct()
        val transactions = deps.txns.findByIds(ids)
        val allocations = if (ids.isNotEmpty()) deps.allocations.listByTransactionIds(ids) else emptyList()
        return Loaded(projects, links, transactions, allocations, categories.associate { it.id to it.name })
    }

    private fun summaryOf(projectId: Id, data: Loaded): ProjectSummary {
        val members = memberIds(data.links, projectId)
        return summarizeProject(
            data.transactions.filter { it.id in members },
            data.allocations.filter { it.transactionId in members },
            data.names,
        )
    }

    private suspend fun findProject(id: Id, projects: List<Project>? = null): Project =
        (projects ?: deps.projects.listAll()).find { it.id == id } ?: throw ProjectError(uiText(TextKey.PROJECT_NOT_FOUND))

    private suspend fun findRule(id: Id): ProjectRule =
        deps.rules.listAll().find { it.id == id } ?: throw ProjectError(uiText(TextKey.PROJECT_RULE_NOT_FOUND))

    private suspend fun oldCandidates(rule: ProjectRule, links: List<ProjectLink>): List<Transaction> {
        val today = deps.clock.nowIso().take(10)
        val from = "${today.take(4).toInt() - OLD_YEARS}${today.substring(4)}"
        val old = deps.txns.listByDateRange(from, today).filter { it.createdAt <= rule.createdAt }
        return ruleCandidates(rule, old, links)
    }

    /** كل المشاريع بمجاميعها — الشغالة بالأحدث، والمؤرشفة لوحدها. */
    suspend fun list(): ProjectLists {
        syncRules()
        val data = load()
        val rows = data.projects.sortedByDescending { it.createdAt }.map { ProjectRow(it, summaryOf(it.id, data)) }
        return ProjectLists(rows.filter { !it.project.archived }, rows.filter { it.project.archived })
    }

    /** النوع أول سؤال وقت عمل المشروع (OVERRIDES §47) — التطبيق الحالي مالوش نوع، فالافتراضي شخصي. */
    suspend fun create(name: String, kind: ProjectKind = ProjectKind.PERSONAL): Project {
        val checked = checkProjectName(name, deps.projects.listAll())
        val project = Project(
            id = deps.ids.next("project"),
            name = checked.name,
            normalizedName = checked.normalizedName,
            archived = false,
            createdAt = deps.clock.nowIso(),
            kind = kind,
        )
        deps.projects.save(project)
        return project
    }

    suspend fun rename(id: Id, name: String) {
        val all = deps.projects.listAll()
        val project = findProject(id, all)
        val checked = checkProjectName(name, all, id)
        deps.projects.save(project.copy(name = checked.name, normalizedName = checked.normalizedName))
    }

    /** مفيش مسح للمشروع — أرشفة، وعملياته ومجاميعه بتفضل. */
    suspend fun setArchived(id: Id, archived: Boolean) {
        deps.projects.save(findProject(id).copy(archived = archived))
    }

    suspend fun detail(projectId: Id): ProjectDetail {
        syncRules()
        val data = load(projectId)
        val project = findProject(projectId, data.projects)
        val sources = data.links
            .filter { it.projectId == projectId && it.source != "excluded" }
            .associate { it.transactionId to it.source }
        val transactions = data.transactions
            .filter { it.id in sources }
            .sortedWith { a, b -> if (a.occurredAt != b.occurredAt) b.occurredAt.compareTo(a.occurredAt) else b.sourceOrder - a.sourceOrder }
            .map { ProjectTransaction(it, sources.getValue(it.id)) }
        val rules = deps.rules.listAll().filter { it.projectId == projectId }.sortedBy { it.createdAt }
        return ProjectDetail(project, summaryOf(projectId, data), rules, transactions)
    }

    /** مشاريع عملية (لـ«ضيف لمشروع»). المؤرشف بيظهر بس لو العملية فيه. */
    suspend fun membership(transactionId: Id): List<ProjectMembership> {
        val projects = deps.projects.listAll()
        val member = deps.links.listByTransaction(transactionId).filter { it.source != "excluded" }.map { it.projectId }.toSet()
        return projects
            .filter { !it.archived || it.id in member }
            .sortedWith { a, b -> arabicCompare(a.name, b.name) }
            .map { ProjectMembership(it, it.id in member) }
    }

    /** إضافة أو شيل بإيد المستخدم؛ الشيل بيسيبها «مستبعدة» عشان قاعدة ما ترجعهاش. */
    suspend fun setMember(transactionId: Id, projectId: Id, member: Boolean) {
        findProject(projectId)
        if (member && deps.spaceLegs?.isLeg(transactionId) == true) throw ProjectError(uiText(TextKey.SPACE_TRANSFER_LEG_LOCKED))
        val existing = deps.links.listByTransaction(transactionId).filter { it.projectId == projectId }
        val changes = membershipChanges(existing, projectId, transactionId, member, deps.clock.nowIso())
        if (changes.isNotEmpty()) deps.links.saveMany(changes)
    }

    /** بتحفظ القاعدة وبترجّع كام عملية قديمة بتنطبق عليها — المستخدم بيختار يضيفها ولا لأ (§34). */
    suspend fun addRule(projectId: Id, matchText: String, matchMode: RuleMatchMode, direction: String): AddedProjectRule {
        findProject(projectId)
        val id = deps.ids.next("prule")
        val checked = checkProjectRule(matchText, matchMode, direction)
        val rule = ProjectRule(id, projectId, checked.matchText, checked.matchMode, checked.direction, enabled = true, createdAt = deps.clock.nowIso())
        deps.rules.save(rule)
        return AddedProjectRule(rule, oldCandidates(rule, deps.links.listAll()).size)
    }

    /** «ضيف القديم كمان» — بعد ما المستخدم شاف العدد. بترجّع كام اتضاف. */
    suspend fun applyRuleToOld(ruleId: Id): Int {
        val rule = findRule(ruleId)
        val links = deps.links.listAll()
        val planned = withoutLegs(planRuleLinks(rule, oldCandidates(rule, links), links, deps.clock.nowIso()))
        if (planned.isNotEmpty()) deps.links.saveMany(planned)
        return planned.size
    }

    /** القاعدة ما بتتمسحش — بتتقفل (زي قواعد التصنيف). اللي ضافته بيفضل. */
    suspend fun setRuleEnabled(ruleId: Id, enabled: Boolean) {
        deps.rules.save(findRule(ruleId).copy(enabled = enabled))
    }
}
