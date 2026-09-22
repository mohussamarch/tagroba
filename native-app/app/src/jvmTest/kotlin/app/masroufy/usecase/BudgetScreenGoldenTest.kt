package app.masroufy.usecase

import app.masroufy.core.AnomalyResult
import app.masroufy.core.Budget
import app.masroufy.core.BudgetStatus
import app.masroufy.core.Category
import app.masroufy.core.CategoryBudget
import app.masroufy.core.EntityJson
import app.masroufy.core.Golden
import app.masroufy.core.buildPeriod
import app.masroufy.core.field
import app.masroufy.core.json
import app.masroufy.core.str
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryBudgetRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryTransactionRepository
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

/**
 * شاشة الميزانية — تالت حالة استخدام بتتطابق بملف مرجع (`budgetScreen.json`).
 * المدخل فيه الفترة الجارية **وست فترات سابقة** — المتوسط والشذوذ بيتحسبوا منهم.
 */
class BudgetScreenGoldenTest {
    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)

    private fun JsonElement.optStr(name: String): String? =
        jsonObject[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

    private fun JsonElement.longOrNull(): Long? =
        (this as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toLongOrNull()

    private fun category(e: JsonElement) = Category(
        id = e.field("id").str,
        parentId = e.optStr("parentId"),
        name = e.field("name").str,
        iconKey = e.field("iconKey").str,
        lightColor = e.field("lightColor").str,
        darkColor = e.field("darkColor").str,
        active = e.field("active").jsonPrimitive.boolean,
        order = e.field("order").jsonPrimitive.int,
        groupKey = e.optStr("groupKey"),
        requires = e.optStr("requires"),
    )

    private fun budget(e: JsonElement) = Budget(
        id = e.field("id").str,
        periodKey = e.field("periodKey").str,
        periodStart = e.field("periodStart").str,
        periodEnd = e.field("periodEnd").str,
        totalLimitMinor = e.field("totalLimitMinor").longOrNull(),
        thresholdPercent = e.field("thresholdPercent").longOrNull()?.toInt(),
        createdAt = e.field("createdAt").str,
        updatedAt = e.field("updatedAt").str,
    )

    private fun categoryBudget(e: JsonElement) = CategoryBudget(
        id = e.field("id").str,
        budgetId = e.field("budgetId").str,
        categoryId = e.field("categoryId").str,
        limitMinor = e.field("limitMinor").jsonPrimitive.content.toLong(),
        notifyEnabled = e.field("notifyEnabled").jsonPrimitive.boolean,
        thresholdPercent = e.field("thresholdPercent").longOrNull()?.toInt(),
    )

    private fun BudgetStatus.toJson() = JsonObject(
        mapOf(
            "limitMinor" to json(limitMinor),
            "spentMinor" to json(spentMinor),
            "remainingMinor" to json(remainingMinor),
            "usedTenthPercent" to json(usedTenthPercent),
            "level" to json(level.wire),
            "thresholdCrossed" to json(thresholdCrossed),
        ),
    )

    private fun AnomalyResult.toJson() = JsonObject(
        mapOf(
            "isAnomaly" to nullable(isAnomaly),
            "medianMinor" to nullable(medianMinor),
            "deviationMinor" to nullable(deviationMinor),
            "reason" to json(reason),
        ),
    )

    @Test
    fun loadBudgetScreen() {
        Golden.check("budgetScreen", "loadBudgetScreen") { input ->
            val options = input.field("options")
            val deps = LoadBudgetScreenDeps(
                txns = MemoryTransactionRepository(EntityJson.transactions(input.field("transactions"))),
                categories = MemoryCategoryRepository(input.field("categories").jsonArray.map(::category)),
                allocations = MemoryAllocationRepository(EntityJson.allocations(input.field("allocations"))),
                budgets = MemoryBudgetRepository(
                    input.field("budgets").jsonArray.map(::budget),
                    input.field("categoryBudgets").jsonArray.map(::categoryBudget),
                ),
            )
            val payday = options.field("payday").jsonPrimitive.int
            val request = LoadBudgetScreenRequest(
                period = buildPeriod(options.field("year").jsonPrimitive.int, options.field("month").jsonPrimitive.int, payday),
                today = options.field("today").str,
                payday = payday,
            )

            val data = runBlocking { LoadBudgetScreen(deps).load(request) }
            JsonObject(
                mapOf(
                    "periodKey" to json(data.period.key),
                    "budgetId" to nullable(data.budget?.id),
                    "totalStatus" to (data.totalStatus?.toJson() ?: JsonNull),
                    "spentMinor" to json(data.spentMinor),
                    "spentReliable" to json(data.spentReliable),
                    "spentNote" to nullable(data.spentNote),
                    "spentKnown" to json(data.spentKnown),
                    "average" to JsonObject(
                        mapOf(
                            "averageMinor" to nullable(data.average.averageMinor),
                            "usedPeriods" to json(data.average.usedPeriods),
                            "excluded" to JsonArray(
                                data.average.excluded.map {
                                    JsonObject(mapOf("periodKey" to json(it.periodKey), "reason" to json(it.reason)))
                                },
                            ),
                            "reason" to json(data.average.reason),
                        ),
                    ),
                    "anomaly" to data.anomaly.toJson(),
                    "allowance" to JsonObject(
                        mapOf(
                            "amountMinor" to nullable(data.allowance.amountMinor),
                            "remainingDays" to json(data.allowance.remainingDays),
                            "approximate" to json(data.allowance.approximate),
                            "reason" to json(data.allowance.reason),
                        ),
                    ),
                    "categoryIds" to JsonArray(data.categories.map { json(it.id) }),
                    "lines" to JsonArray(
                        data.lines.map { line ->
                            JsonObject(
                                mapOf(
                                    "categoryId" to json(line.categoryId),
                                    "status" to (line.status?.toJson() ?: JsonNull),
                                    "spentMinor" to json(line.spentMinor),
                                    "shareTenthPercent" to json(line.shareTenthPercent),
                                    "averageMinor" to nullable(line.averageMinor),
                                    "anomaly" to line.anomaly.toJson(),
                                    "noLimitReason" to nullable(line.noLimitReason),
                                ),
                            )
                        },
                    ),
                    "categoryBudgetIds" to JsonArray(data.categoryBudgets.map { json(it.id) }),
                ),
            )
        }
    }
}
