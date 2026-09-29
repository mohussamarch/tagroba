package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.EntityJson
import app.masroufy.core.Golden
import app.masroufy.core.Project
import app.masroufy.core.ProjectKind
import app.masroufy.core.ProjectLink
import app.masroufy.core.ProjectRule
import app.masroufy.core.ProjectSummary
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.field
import app.masroufy.core.json
import app.masroufy.core.str
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryProjectLinkRepository
import app.masroufy.memory.MemoryProjectRepository
import app.masroufy.memory.MemoryProjectRuleRepository
import app.masroufy.memory.MemorySyncCursor
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

/** المشاريع وروابطها وقواعدها على `projectsFlow.json`. */
class ProjectsFlowGoldenTest {
    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)

    private fun JsonElement.optStr(name: String): String? = jsonObject[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

    private fun category(e: JsonElement) = Category(
        id = e.field("id").str, parentId = e.optStr("parentId"), name = e.field("name").str,
        iconKey = e.field("iconKey").str, lightColor = e.field("lightColor").str, darkColor = e.field("darkColor").str,
        active = e.field("active").jsonPrimitive.boolean, order = e.field("order").jsonPrimitive.int,
        groupKey = e.optStr("groupKey"), requires = e.optStr("requires"),
    )

    private fun project(e: JsonElement) = Project(
        id = e.field("id").str, name = e.field("name").str, normalizedName = e.field("normalizedName").str,
        archived = e.field("archived").jsonPrimitive.boolean, createdAt = e.field("createdAt").str,
        kind = ProjectKind.fromWire(e.optStr("kind")),
    )

    /** التطبيق الحالي مالوش نوع مشروع ⇒ «شخصي» بيتكتب من غير الحقل، زي المشاريع القديمة بالظبط. */
    private fun projectJson(p: Project) = EntityJson.obj(
        "id" to p.id, "name" to p.name, "normalizedName" to p.normalizedName,
        "archived" to p.archived, "createdAt" to p.createdAt,
        "kind" to p.kind.takeIf { it != ProjectKind.PERSONAL }?.wire,
    )

    private fun link(e: JsonElement) = ProjectLink(
        id = e.field("id").str, projectId = e.field("projectId").str, transactionId = e.field("transactionId").str,
        source = e.field("source").str, createdAt = e.field("createdAt").str,
    )

    private fun linkJson(l: ProjectLink) = JsonObject(
        mapOf(
            "id" to json(l.id), "projectId" to json(l.projectId), "transactionId" to json(l.transactionId),
            "source" to json(l.source), "createdAt" to json(l.createdAt),
        ),
    )

    private fun rule(e: JsonElement) = ProjectRule(
        id = e.field("id").str, projectId = e.field("projectId").str, matchText = e.field("matchText").str,
        matchMode = RuleMatchMode.fromWire(e.field("matchMode").str), direction = e.field("direction").str,
        enabled = e.field("enabled").jsonPrimitive.boolean, createdAt = e.field("createdAt").str,
    )

    private fun ruleJson(r: ProjectRule) = JsonObject(
        mapOf(
            "id" to json(r.id), "projectId" to json(r.projectId), "matchText" to json(r.matchText),
            "matchMode" to json(r.matchMode.wire), "direction" to json(r.direction),
            "enabled" to json(r.enabled), "createdAt" to json(r.createdAt),
        ),
    )

    private fun summaryJson(s: ProjectSummary) = JsonObject(
        mapOf(
            "spentMinor" to json(s.spentMinor), "receivedMinor" to json(s.receivedMinor), "count" to json(s.count),
            "estimatedCount" to json(s.estimatedCount), "needsReviewCount" to json(s.needsReviewCount),
        ),
    )

    private fun rowJson(r: ProjectRow) = JsonObject(mapOf("projectId" to json(r.project.id), "summary" to summaryJson(r.summary)))

    @Test
    fun manageProjects() {
        Golden.check("projectsFlow", "manageProjects") { input ->
            val seed = input.field("seed")
            val projects = MemoryProjectRepository(seed.field("projects").jsonArray.map(::project))
            val links = MemoryProjectLinkRepository(seed.field("links").jsonArray.map(::link))
            val rules = MemoryProjectRuleRepository(seed.field("rules").jsonArray.map(::rule))
            val cursor = MemorySyncCursor(seed.optStr("cursor"))
            val manage = ManageProjects(
                ProjectsDeps(
                    projects = projects, links = links, rules = rules,
                    txns = MemoryTransactionRepository(EntityJson.transactions(seed.field("transactions"))),
                    allocations = MemoryAllocationRepository(EntityJson.allocations(seed.field("allocations"))),
                    categories = MemoryCategoryRepository(input.field("categories").jsonArray.map(::category)),
                    ids = SequentialIdGenerator(),
                    clock = FixedClock("2026-09-22T10:00:00.000Z"),
                    cursor = cursor,
                ),
            )
            val action = input.field("action")

            runBlocking {
                suspend fun stored() = mapOf(
                    "storedProjects" to JsonArray(projects.listAll().map(::projectJson)),
                    "storedLinks" to JsonArray(links.listAll().map(::linkJson)),
                    "storedRules" to JsonArray(rules.listAll().map(::ruleJson)),
                    "cursorAfter" to nullable(cursor.read()),
                )
                when (action.field("kind").str) {
                    "syncOnly" -> JsonObject(mapOf("linked" to json(manage.syncRules())) + stored())
                    "list" -> {
                        val result = manage.list()
                        JsonObject(
                            mapOf(
                                "active" to JsonArray(result.active.map(::rowJson)),
                                "archived" to JsonArray(result.archived.map(::rowJson)),
                            ) + stored(),
                        )
                    }
                    "create" -> JsonObject(mapOf("created" to projectJson(manage.create(action.field("name").str))) + stored())
                    "rename" -> {
                        manage.rename(action.field("id").str, action.field("name").str)
                        JsonObject(stored())
                    }
                    "archive" -> {
                        manage.setArchived(action.field("id").str, action.field("archived").jsonPrimitive.boolean)
                        JsonObject(stored())
                    }
                    "detail" -> {
                        val d = manage.detail(action.field("id").str)
                        JsonObject(
                            mapOf(
                                "projectId" to json(d.project.id),
                                "summary" to summaryJson(d.summary),
                                "rules" to JsonArray(d.rules.map(::ruleJson)),
                                "transactions" to JsonArray(
                                    d.transactions.map { JsonObject(mapOf("transactionId" to json(it.transaction.id), "source" to json(it.source))) },
                                ),
                            ) + stored(),
                        )
                    }
                    "membership" -> JsonObject(
                        mapOf(
                            "rows" to JsonArray(
                                manage.membership(action.field("id").str).map {
                                    JsonObject(mapOf("projectId" to json(it.project.id), "member" to json(it.member)))
                                },
                            ),
                        ),
                    )
                    "setMember" -> {
                        manage.setMember(
                            action.field("transactionId").str, action.field("projectId").str,
                            action.field("member").jsonPrimitive.boolean,
                        )
                        JsonObject(stored())
                    }
                    "addRule" -> {
                        val added = manage.addRule(
                            projectId = action.field("projectId").str,
                            matchText = action.field("matchText").str,
                            matchMode = RuleMatchMode.fromWire(action.field("matchMode").str),
                            direction = action.field("direction").str,
                        )
                        JsonObject(mapOf("rule" to ruleJson(added.rule), "oldMatches" to json(added.oldMatches)) + stored())
                    }
                    "applyRuleToOld" -> JsonObject(mapOf("added" to json(manage.applyRuleToOld(action.field("ruleId").str))) + stored())
                    else -> {
                        manage.setRuleEnabled(action.field("ruleId").str, action.field("enabled").jsonPrimitive.boolean)
                        JsonObject(stored())
                    }
                }
            }
        }
    }
}
