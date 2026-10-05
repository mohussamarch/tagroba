package app.masroufy.usecase

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Budget
import app.masroufy.core.CategoryBudget
import app.masroufy.core.EconomicKind
import app.masroufy.core.EntityJson
import app.masroufy.core.Golden
import app.masroufy.core.NotificationEvent
import app.masroufy.core.NotificationReceipt
import app.masroufy.core.Texts
import app.masroufy.core.buildPeriod
import app.masroufy.core.field
import app.masroufy.core.json
import app.masroufy.core.str
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryBudgetRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryNotificationReceiptRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
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

/** التنبيهات جوه التطبيق + مراجعة السجل القديم على `reviewFlow.json`. */
class ReviewFlowGoldenTest {
    // النص المتوقع هنا = نص التطبيق الحالي = النسخة المصرية (OVERRIDES §66)
    @BeforeTest
    fun egyptianText() {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
    }

    @AfterTest
    fun defaultText() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val now = "2026-09-22T10:00:00.000Z"

    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)

    private fun JsonElement.orNull(name: String): JsonElement? = jsonObject[name]?.takeIf { it !is JsonNull }

    private fun budget(e: JsonElement) = Budget(
        id = e.field("id").str, periodKey = e.field("periodKey").str, periodStart = e.field("periodStart").str, periodEnd = e.field("periodEnd").str,
        totalLimitMinor = e.orNull("totalLimitMinor")?.jsonPrimitive?.long, thresholdPercent = e.orNull("thresholdPercent")?.jsonPrimitive?.int,
        createdAt = e.field("createdAt").str, updatedAt = e.field("updatedAt").str,
    )

    private fun line(e: JsonElement) = CategoryBudget(
        id = e.field("id").str, budgetId = e.field("budgetId").str, categoryId = e.field("categoryId").str,
        limitMinor = e.field("limitMinor").jsonPrimitive.long, notifyEnabled = e.field("notifyEnabled").jsonPrimitive.boolean,
        thresholdPercent = e.orNull("thresholdPercent")?.jsonPrimitive?.int,
    )

    private fun receipt(e: JsonElement) = NotificationReceipt(
        e.field("eventKey").str, e.orNull("threshold")?.jsonPrimitive?.int, e.field("periodStart").str, e.field("sentAt").str,
        e.orNull("categoryId")?.str, e.orNull("recurringId")?.str,
    )

    private fun receiptJson(r: NotificationReceipt) = EntityJson.obj(
        "eventKey" to r.eventKey, "threshold" to r.threshold, "periodStart" to r.periodStart, "sentAt" to r.sentAt,
        "categoryId" to r.categoryId, "recurringId" to r.recurringId,
    )

    private fun eventJson(e: NotificationEvent) = EntityJson.obj(
        "eventKey" to e.eventKey, "kind" to e.kind, "severity" to e.severity, "title" to e.title, "body" to e.body,
        "periodStart" to e.periodStart, "categoryId" to e.categoryId, "recurringId" to e.recurringId, "threshold" to e.threshold,
    )

    private fun reportJson(r: CategorizationReport) = JsonObject(
        mapOf(
            "changed" to JsonArray(
                r.changed.map {
                    EntityJson.obj(
                        "transactionId" to it.transactionId, "fromCategoryId" to it.fromCategoryId,
                        "toCategoryId" to it.toCategoryId, "reason" to it.reason, "source" to it.source,
                    )
                },
            ),
            "skippedConfirmed" to json(r.skippedConfirmed),
            "stillNeedsReview" to json(r.stillNeedsReview),
        ),
    )

    private fun report(e: JsonElement) = CategorizationReport(
        changed = e.field("changed").jsonArray.map {
            CategorizationChange(it.field("transactionId").str, it.orNull("fromCategoryId")?.str, it.orNull("toCategoryId")?.str, it.field("reason").str, it.field("source").str)
        },
        skippedConfirmed = e.field("skippedConfirmed").jsonArray.map { it.str },
        stillNeedsReview = e.field("stillNeedsReview").jsonArray.map { it.str },
    )

    @Test
    fun loadNotifications() {
        Golden.check("reviewFlow", "loadNotifications") { input ->
            val p = input.field("period")
            val period = buildPeriod(p.field("year").jsonPrimitive.int, p.field("month").jsonPrimitive.int, p.field("payday").jsonPrimitive.int)
            val txns = MemoryTransactionRepository(EntityJson.transactions(input.field("transactions")))
            val budgets = MemoryBudgetRepository(input.field("budgets").jsonArray.map(::budget), input.field("lines").jsonArray.map(::line))
            val receipts = MemoryNotificationReceiptRepository(input.field("receipts").jsonArray.map(::receipt))
            runBlocking {
                val screen = LoadBudgetScreen(
                    LoadBudgetScreenDeps(txns, MemoryCategoryRepository(input.field("categories").jsonArray.map(ReferenceJson::category)), MemoryAllocationRepository(), budgets),
                ).load(LoadBudgetScreenRequest(period, input.field("today").str, p.field("payday").jsonPrimitive.int))
                val notices = LoadNotifications(receipts, FixedClock(now))
                var last: NotificationsView? = null
                val out = input.field("steps").jsonArray.map { step ->
                    when (step.field("kind").str) {
                        "load" -> notices.load(screen).also { last = it }.let {
                            JsonObject(mapOf("all" to JsonArray(it.all.map(::eventJson)), "unseen" to JsonArray(it.unseen.map(::eventJson))))
                        }
                        "markSeen" -> {
                            notices.markSeen(last?.unseen.orEmpty().take(step.field("count").jsonPrimitive.int))
                            JsonNull
                        }
                        else -> JsonObject(mapOf("pruned" to json(notices.pruneOldReceipts(step.field("keepFrom").str))))
                    }
                }
                JsonObject(mapOf("steps" to JsonArray(out), "storedReceipts" to JsonArray(receipts.listAll().map(::receiptJson))))
            }
        }
    }

    @Test
    fun reviewHistory() {
        Golden.check("reviewFlow", "reviewHistory") { input ->
            val txns = MemoryTransactionRepository(EntityJson.transactions(input.field("transactions")))
            val rules = MemoryRuleRepository(input.field("rules").jsonArray.map(ReferenceJson::rule))
            val review = ReviewHistory(
                CategorizeTransactionsDeps(
                    txns = txns, merchants = MemoryMerchantRepository(input.field("merchants").jsonArray.map(ReferenceJson::merchant)),
                    categories = MemoryCategoryRepository(input.field("categories").jsonArray.map(ReferenceJson::category)),
                    rules = rules, uow = MemoryUnitOfWork(listOf(txns)), clock = FixedClock(now),
                ),
            )
            runBlocking {
                var plan: CategorizationReport? = null
                var previewIds = emptyList<String>()
                val out = input.field("steps").jsonArray.map { step ->
                    when (step.field("kind").str) {
                        "preview" -> {
                            val p = review.preview(step.field("from").str, step.field("to").str)
                            plan = p.categoryPlan
                            previewIds = p.rows.map { it.id }
                            JsonObject(
                                mapOf(
                                    "rowIds" to json(p.rows.map { it.id }),
                                    "categoryPlan" to reportJson(p.categoryPlan),
                                    "groups" to JsonArray(p.groups.map { g -> json(g.map { it.id }) }),
                                ),
                            )
                        }
                        "applyCategories" -> {
                            if (step.orNull("staleRule")?.jsonPrimitive?.boolean == true) {
                                rules.saveMany(listOf(rules.listAll().first().copy(categoryId = "rent")))
                            }
                            val ids = if (step.orNull("allFromPreview")?.jsonPrimitive?.boolean == true) previewIds + previewIds.first() else step.field("ids").jsonArray.map { it.str }
                            // الخطة بترجع من ملف المرجع زي ما الشاشة بترجّعها — مش من الذاكرة
                            reportJson(review.applyCategories(ids, report(reportJson(plan!!))))
                        }
                        else -> {
                            val r = review.setGroup(step.field("ids").jsonArray.map { it.str }, EconomicKind.fromWire(step.field("economicKind").str))
                            JsonObject(mapOf("applied" to json(r.applied), "skipped" to json(r.skipped)))
                        }
                    }
                }
                JsonObject(
                    mapOf(
                        "steps" to JsonArray(out),
                        "storedTransactions" to JsonArray(
                            txns.all().map { t ->
                                JsonObject(
                                    mapOf(
                                        "id" to json(t.id), "economicKind" to json(t.economicKind.wire), "economicKindConfirmed" to json(t.economicKindConfirmed),
                                        "categoryId" to nullable(t.categoryId), "categoryConfirmed" to json(t.categoryConfirmed), "reviewState" to json(t.reviewState.wire),
                                    ),
                                )
                            },
                        ),
                    ),
                )
            }
        }
    }
}
