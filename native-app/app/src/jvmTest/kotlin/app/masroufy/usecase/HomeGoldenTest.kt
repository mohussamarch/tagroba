package app.masroufy.usecase

import app.masroufy.core.Budget
import app.masroufy.core.Category
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
 * الشاشة الرئيسية — **أول حالة استخدام** بتتطابق مع التطبيق الحالي بملف مرجع (`home.json`).
 * المدخل فيه محتوى المستودعات كامل، فبنبني نفس المستودعات في الذاكرة ونقارن الناتج بالحرف.
 * الحقل اللي قيمته `null` **بيفضل موجود** (مش بيتشال زي `EntityJson.obj`)، عشان «غير متاح» تتقارن.
 */
class HomeGoldenTest {
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

    @Test
    fun loadHomeScreen() {
        Golden.check("home", "loadHomeScreen") { input ->
            val options = input.field("options")
            val deps = LoadHomeScreenDeps(
                txns = MemoryTransactionRepository(EntityJson.transactions(input.field("transactions"))),
                categories = MemoryCategoryRepository(input.field("categories").jsonArray.map(::category)),
                allocations = MemoryAllocationRepository(EntityJson.allocations(input.field("allocations"))),
                budgets = if (options.field("useBudgetRepository").jsonPrimitive.boolean) {
                    MemoryBudgetRepository(input.field("budgets").jsonArray.map(::budget))
                } else {
                    null
                },
            )
            val payday = options.field("payday").jsonPrimitive.int
            val request = LoadHomeScreenRequest(
                period = buildPeriod(options.field("year").jsonPrimitive.int, options.field("month").jsonPrimitive.int, payday),
                today = options.field("today").str,
                payday = payday,
                budgetLimitMinor = options.field("budgetLimitMinor").longOrNull(),
                includeHistory = options.field("includeHistory").jsonPrimitive.boolean,
            )

            val data = runBlocking { LoadHomeScreen(deps).load(request) }
            JsonObject(
                mapOf(
                    "periodKey" to json(data.period.key),
                    "periodRange" to json(data.periodRange),
                    "expenseMinor" to nullable(data.expenseMinor),
                    "incomeMinor" to nullable(data.incomeMinor),
                    "remainingMinor" to nullable(data.remainingMinor),
                    "savingsRatePercent" to nullable(data.savingsRatePercent),
                    "excludedExpenseMinor" to nullable(data.excludedExpenseMinor),
                    "partial" to json(data.partial),
                    "estimatedCount" to json(data.estimatedCount),
                    "needsReviewCount" to json(data.needsReviewCount),
                    "distribution" to JsonArray(
                        data.distribution.map {
                            JsonObject(
                                mapOf(
                                    "categoryId" to nullable(it.categoryId),
                                    "amountMinor" to json(it.amountMinor),
                                    "count" to json(it.count),
                                    "shareTenthPercent" to json(it.shareTenthPercent),
                                ),
                            )
                        },
                    ),
                    "categoryIds" to JsonArray(data.categories.map { json(it.id) }),
                    "latestIds" to JsonArray(data.latest.map { json(it.id) }),
                    "transactionCount" to json(data.transactionCount),
                    "allowance" to JsonObject(
                        mapOf(
                            "amountMinor" to nullable(data.allowance.amountMinor),
                            "remainingDays" to json(data.allowance.remainingDays),
                            "approximate" to json(data.allowance.approximate),
                            "reason" to json(data.allowance.reason),
                        ),
                    ),
                    "forecast" to JsonObject(
                        mapOf(
                            "projectedMinor" to nullable(data.forecast.projectedMinor),
                            "elapsedDays" to json(data.forecast.elapsedDays),
                            "totalDays" to json(data.forecast.totalDays),
                            "caveat" to json(data.forecast.caveat),
                        ),
                    ),
                    "coverage" to JsonObject(
                        mapOf(
                            "total" to json(data.coverage.total),
                            "unclassified" to json(data.coverage.unclassified),
                            "totalsReliable" to json(data.coverage.totalsReliable),
                            "note" to nullable(data.coverage.note),
                        ),
                    ),
                    "recentPeriods" to JsonArray(
                        data.recentPeriods.map {
                            JsonObject(
                                mapOf(
                                    "periodKey" to json(it.period.key),
                                    "expenseMinor" to nullable(it.expenseMinor),
                                    "incomeMinor" to nullable(it.incomeMinor),
                                    "transactionCount" to json(it.transactionCount),
                                ),
                            )
                        },
                    ),
                ),
            )
        }
    }
}
