package app.masroufy.core

import app.masroufy.core.EntityJson.obj
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

/** التنبيهات والقفل والشعارات وقاعدة التجار المشتركة (native-app/golden/notice.json). */
class NoticeGoldenTest {
    private fun check(fn: String, run: (JsonElement) -> Any?) = Golden.check("notice", fn) { json(run(it)) }
    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)
    private fun JsonElement.orNull() = takeIf { it !is JsonNull }
    private fun JsonElement.opt(name: String): String? = jsonObject[name]?.orNull()?.str
    private fun JsonElement.intOrNull(name: String): Int? = jsonObject[name]?.orNull()?.jsonPrimitive?.int

    private fun status(e: JsonElement) = BudgetStatus(
        e.field("limitMinor").num, e.field("spentMinor").num, e.field("remainingMinor").num, e.field("usedTenthPercent").num,
        BudgetLevel.entries.first { it.wire == e.field("level").str }, e.field("thresholdCrossed").jsonPrimitive.boolean,
    )
    private fun events(input: JsonElement) = buildBudgetNotifications(
        input.field("periodStart").str, input.field("totalStatus").orNull()?.let(::status), input.intOrNull("totalThresholdPercent"),
        input.field("categories").jsonArray.map { CategoryBudgetForNotice(it.field("categoryId").str, it.field("categoryName").str, status(it.field("status")), it.intOrNull("thresholdPercent")) },
        input.field("spentKnown").jsonPrimitive.boolean,
    )
    private fun NotificationEvent.toJson() = obj(
        "eventKey" to eventKey, "kind" to kind, "severity" to severity, "title" to title, "body" to body, "periodStart" to periodStart,
        "categoryId" to categoryId, "recurringId" to recurringId, "threshold" to threshold,
    )
    private fun entry(e: JsonElement) = SharedMerchantEntry(
        e.field("normalizedName").str, e.field("displayName").str, e.field("aliases").jsonArray.map { it.str }, e.opt("categoryId"),
        e.field("confirmed").jsonPrimitive.boolean, e.opt("updatedAt"),
    )
    private fun SharedMerchantEntry.toJson() = JsonObject(
        buildMap {
            put("normalizedName", json(normalizedName)); put("displayName", json(displayName)); put("aliases", json(aliases))
            put("categoryId", nullable(categoryId)); put("confirmed", json(confirmed)); updatedAt?.let { put("updatedAt", json(it)) }
        },
    )
    private fun merchant(e: JsonElement) = Merchant(e.field("id").str, e.field("displayName").str, e.field("normalizedName").str, e.jsonObject["aliases"]?.jsonArray?.map { it.str }, verifiedCategoryId = e.opt("verifiedCategoryId"))
    private fun Merchant.toJson() = obj("id" to id, "displayName" to displayName, "normalizedName" to normalizedName, "aliases" to aliases, "verifiedCategoryId" to verifiedCategoryId)
    private val tree = setOf("cat-food", "cat-fuel")
    private val merchants by lazy { Golden.cases("notice", "planAccountMerchantSync").first()["in"]!!.field("merchants").jsonArray.map(::merchant) }
    private val entries by lazy { Golden.cases("notice", "planAccountMerchantSync").first()["in"]!!.field("entries").jsonArray.map(::entry) }

    @Test fun notifications() {
        check("buildBudgetNotifications") { events(it).map { e -> e.toJson() } }
        val receipts = listOf(NotificationReceipt("2026-08-28|total|100", 100, "2026-08-28", "x"), NotificationReceipt("old", null, "2026-07-28", "x"))
        check("filterAndReceipts") {
            val list = events(it)
            obj(
                "unseen" to filterUnseen(list, receipts).map { e -> e.eventKey },
                "receipts" to list.map { e -> receiptFor(e, "NOW").let { r -> JsonObject(mapOf("eventKey" to json(r.eventKey), "threshold" to nullable(r.threshold), "periodStart" to json(r.periodStart), "sentAt" to json(r.sentAt)) + listOfNotNull(r.categoryId?.let { c -> "categoryId" to json(c) }, r.recurringId?.let { c -> "recurringId" to json(c) })) } },
                "stale" to staleReceipts(receipts, "2026-08-28").map { r -> r.eventKey },
            )
        }
    }

    @Test fun lockAndLogos() {
        check("shouldLock") { shouldLock(it.field("enabled").jsonPrimitive.boolean, it.field("hiddenAt").orNull()?.num, it.field("now").num) }
        val logos = listOf(
            MerchantLogoEntry(listOf("TEST MART", "test-mart"), "mart.png", "testmart.com"), MerchantLogoEntry(listOf("NEW CAFE"), domain = " NewCafe.SA "),
            MerchantLogoEntry(listOf("Bad"), domain = "not a domain"), MerchantLogoEntry(listOf("TEST MART"), file = "dup.png"), MerchantLogoEntry(listOf("", "  ")),
        )
        check("logoSourceFor") {
            when (val s = logoSourceFor(it.field("name").str, buildLogoIndex(logos), it.field("online").jsonPrimitive.boolean, it.field("files").jsonArray.map { f -> f.str }.toSet())) {
                null -> JsonNull
                is LogoSource.Bundled -> obj("kind" to "bundled", "file" to s.file)
                is LogoSource.Online -> obj("kind" to "online", "domain" to s.domain)
            }
        }
    }

    @Test fun sharedMerchants() {
        check("shareableName") {
            val name = it.orNull()?.str
            JsonObject(mapOf("name" to nullable(shareableName(name)), "key" to nullable(if (name.isNullOrEmpty()) null else sharedMerchantKey(name))))
        }
        check("baselineCatalog") { obj(*baselineCatalog(merchants).map { (k, v) -> k to v.toJson() }.toTypedArray()) }
        check("effectiveEntry") { effectiveEntry(it.field("baseline").orNull()?.let(::entry), it.field("remote").orNull()?.let(::entry))?.toJson() }
        check("contributionFor") {
            contributionFor(
                EconomicKind.fromWire(it.field("kind").str), Direction.fromWire(it.field("dir").str), it.field("name").orNull()?.str,
                it.field("cat").str, tree, it.field("current").orNull()?.let(::entry),
            )?.toJson()
        }
        check("planAccountMerchantSync") {
            val plan = planAccountMerchantSync(entries, merchants, tree)
            obj("add" to plan.add.map { m -> m.toJson() }, "fill" to plan.fill.map { m -> m.toJson() })
        }
        check("latestUpdate") { nullable(latestUpdate(entries, it.orNull()?.str)) }
    }
}
