package app.masroufy.usecase

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Budget
import app.masroufy.core.Category
import app.masroufy.core.CategoryBudget
import app.masroufy.core.CategorySaveInput
import app.masroufy.core.EntityJson
import app.masroufy.core.EstimatePolicy
import app.masroufy.core.Field
import app.masroufy.core.Golden
import app.masroufy.core.Period
import app.masroufy.core.Texts
import app.masroufy.core.buildPeriod
import app.masroufy.core.field
import app.masroufy.core.json
import app.masroufy.core.str
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryBudgetRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
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
import kotlinx.serialization.json.long
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** ضبط الميزانية + إدارة التصنيفات + تاريخ الرئيسية على `budgetEdit.json`. */
class BudgetEditGoldenTest {
    // النص المتوقع هنا = نص التطبيق الحالي = النسخة المصرية (OVERRIDES §66)
    @BeforeTest
    fun egyptianText() {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        // ملف المرجع من التطبيق القديم: الداخل المجهول «راتب» تقديري وكل عملية في شهر تاريخها ⇒ السياسة القديمة صراحةً (§75-1 · §75-3)
        EstimatePolicy.current = EstimatePolicy.LEGACY
    }

    @AfterTest
    fun defaultText() {
        Texts.arabicVariant = ArabicVariant.MSA
        EstimatePolicy.current = EstimatePolicy.OWNER_2026_10
    }

    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)

    private fun JsonElement.optStr(name: String): String? = jsonObject[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

    private fun JsonElement.optInt(name: String): Int? = jsonObject[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.int

    private fun budget(e: JsonElement) = Budget(
        id = e.field("id").str, periodKey = e.field("periodKey").str,
        periodStart = e.field("periodStart").str, periodEnd = e.field("periodEnd").str,
        totalLimitMinor = e.jsonObject["totalLimitMinor"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.long,
        thresholdPercent = e.optInt("thresholdPercent"),
        createdAt = e.field("createdAt").str, updatedAt = e.field("updatedAt").str,
    )

    private fun budgetJson(b: Budget) = JsonObject(
        mapOf(
            "id" to json(b.id), "periodKey" to json(b.periodKey), "periodStart" to json(b.periodStart), "periodEnd" to json(b.periodEnd),
            "totalLimitMinor" to nullable(b.totalLimitMinor), "thresholdPercent" to nullable(b.thresholdPercent),
            "createdAt" to json(b.createdAt), "updatedAt" to json(b.updatedAt),
        ),
    )

    private fun line(e: JsonElement) = CategoryBudget(
        id = e.field("id").str, budgetId = e.field("budgetId").str, categoryId = e.field("categoryId").str,
        limitMinor = e.field("limitMinor").jsonPrimitive.long, notifyEnabled = e.field("notifyEnabled").jsonPrimitive.boolean,
        thresholdPercent = e.optInt("thresholdPercent"),
    )

    private fun lineJson(l: CategoryBudget) = JsonObject(
        mapOf(
            "id" to json(l.id), "budgetId" to json(l.budgetId), "categoryId" to json(l.categoryId), "limitMinor" to json(l.limitMinor),
            "notifyEnabled" to json(l.notifyEnabled), "thresholdPercent" to nullable(l.thresholdPercent),
        ),
    )

    private fun category(e: JsonElement) = Category(
        id = e.field("id").str, parentId = e.optStr("parentId"), name = e.field("name").str,
        iconKey = e.field("iconKey").str, lightColor = e.field("lightColor").str, darkColor = e.field("darkColor").str,
        active = e.field("active").jsonPrimitive.boolean, order = e.field("order").jsonPrimitive.int,
        groupKey = e.optStr("groupKey"), requires = e.optStr("requires"),
        noCarName = e.optStr("noCarName"), noCarIconKey = e.optStr("noCarIconKey"),
    )

    /** الحقول الاختيارية الغايبة في التطبيق الحالي ما بتتكتبش (مش null). */
    private fun categoryJson(c: Category) = JsonObject(
        buildMap {
            put("id", json(c.id)); put("parentId", nullable(c.parentId)); put("name", json(c.name)); put("iconKey", json(c.iconKey))
            put("lightColor", json(c.lightColor)); put("darkColor", json(c.darkColor)); put("active", json(c.active)); put("order", json(c.order))
            c.groupKey?.let { put("groupKey", json(it)) }; c.requires?.let { put("requires", json(it)) }
            c.noCarName?.let { put("noCarName", json(it)) }; c.noCarIconKey?.let { put("noCarIconKey", json(it)) }
        },
    )

    private fun periodJson(p: Period) = JsonObject(mapOf("key" to json(p.key), "start" to json(p.start), "end" to json(p.end), "days" to json(p.days)))

    @Test
    fun setBudget() {
        Golden.check("budgetEdit", "setBudget") { input ->
            val repo = MemoryBudgetRepository(input.field("budgets").jsonArray.map(::budget), input.field("lines").jsonArray.map(::line))
            val set = SetBudget(SetBudgetDeps(repo, MemoryUnitOfWork(listOf(repo)), SequentialIdGenerator(), FixedClock("2026-09-22T10:00:00.000Z")))
            val payday = input.field("payday").jsonPrimitive.int
            val action = input.field("action")
            val period = buildPeriod(2026, action.field("month").jsonPrimitive.int, payday)

            runBlocking {
                val result: JsonElement = when (action.field("kind").str) {
                    "setTotal" -> budgetJson(
                        set.setTotalLimit(period, action.field("limitMinor").jsonPrimitive.long, action.optInt("thresholdPercent")),
                    )
                    "clearTotal" -> { set.clearTotalLimit(period); JsonNull }
                    "clearAll" -> { set.clearAll(period); JsonNull }
                    "setCategory" -> lineJson(
                        set.setCategoryLimit(
                            period, action.field("categoryId").str, action.field("limitMinor").jsonPrimitive.long,
                            notifyEnabled = action.jsonObject["notifyEnabled"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.boolean,
                            thresholdPercent = action.optInt("thresholdPercent"),
                        ),
                    )
                    "clearCategory" -> { set.clearCategoryLimit(period, action.field("categoryId").str); JsonNull }
                    else -> json(set.copyFrom(action.field("sourceKey").str, period))
                }
                JsonObject(
                    mapOf(
                        "result" to result,
                        "storedBudgets" to JsonArray(repo.allBudgets().map(::budgetJson)),
                        "storedLines" to JsonArray(repo.allLines().map(::lineJson)),
                    ),
                )
            }
        }
    }

    @Test
    fun manageCategories() {
        Golden.check("budgetEdit", "manageCategories") { input ->
            val repo = MemoryCategoryRepository(input.field("categories").jsonArray.map(::category))
            val manage = ManageCategories(ManageCategoriesDeps(repo, SequentialIdGenerator()))
            val action = input.field("action")

            runBlocking {
                if (action.field("kind").str == "list") {
                    JsonObject(mapOf("listed" to JsonArray(manage.list().map(::categoryJson))))
                } else {
                    val i = action.field("input").jsonObject
                    fun optional(name: String): Field<String?> = if (name in i) Field.Set(i[name]?.takeIf { it !is JsonNull }?.str) else Field.Unset
                    val saved = manage.save(
                        CategorySaveInput(
                            id = i["id"]?.str, name = i.getValue("name").str, active = i.getValue("active").jsonPrimitive.boolean,
                            iconKey = i["iconKey"]?.str, parentId = optional("parentId"), groupKey = optional("groupKey"), swatchKey = i["swatchKey"]?.str,
                        ),
                    )
                    JsonObject(mapOf("saved" to categoryJson(saved), "storedCategories" to JsonArray(repo.listAll().map(::categoryJson))))
                }
            }
        }
    }

    @Test
    fun loadHomeHistory() {
        Golden.check("budgetEdit", "loadHomeHistory") { input ->
            val txns = MemoryTransactionRepository(EntityJson.transactions(input.field("transactions")))
            val allocations = MemoryAllocationRepository(EntityJson.allocations(input.field("allocations")))
            val categories = MemoryCategoryRepository(input.field("categories").jsonArray.map(::category))
            val payday = input.field("payday").jsonPrimitive.int
            val period = buildPeriod(2026, input.field("month").jsonPrimitive.int, payday)
            val today = input.field("today").str

            runBlocking {
                val current = LoadHomeScreen(LoadHomeScreenDeps(txns = txns, categories = categories, allocations = allocations, budgets = null))
                    .load(LoadHomeScreenRequest(period = period, today = today, payday = payday, budgetLimitMinor = null, includeHistory = false))
                val history = LoadHomeHistory(
                    LoadHomeHistoryDeps(
                        txns = txns, allocations = allocations,
                        categories = if (input.field("withCategoriesRepo").jsonPrimitive.boolean) categories else null,
                    ),
                ).load(period, payday, current)
                JsonArray(
                    history.map {
                        JsonObject(
                            mapOf(
                                "period" to periodJson(it.period), "expenseMinor" to nullable(it.expenseMinor),
                                "incomeMinor" to nullable(it.incomeMinor), "transactionCount" to json(it.transactionCount),
                            ),
                        )
                    },
                )
            }
        }
    }
}
