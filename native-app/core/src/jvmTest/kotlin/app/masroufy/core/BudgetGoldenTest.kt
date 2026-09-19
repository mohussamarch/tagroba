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

/** الميزانية والمتوسط والشذوذ والتحليل (native-app/golden/budget.json). */
class BudgetGoldenTest {
    private fun check(fn: String, run: (JsonElement) -> Any?) = Golden.check("budget", fn) { json(run(it)) }
    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)
    private fun JsonElement.longOrNull(): Long? = if (this is JsonNull) null else num
    private fun JsonElement.intOrNull(): Int? = if (this is JsonNull) null else jsonPrimitive.int
    private fun JsonElement.period() = Period(field("key").str, field("start").str, field("end").str, field("days").num.toInt())

    private fun BudgetStatus.toJson() = obj(
        "limitMinor" to limitMinor, "spentMinor" to spentMinor, "remainingMinor" to remainingMinor,
        "usedTenthPercent" to usedTenthPercent, "level" to level.wire, "thresholdCrossed" to thresholdCrossed,
    )

    private fun AnomalyResult.toJson() = JsonObject(
        mapOf("isAnomaly" to nullable(isAnomaly), "medianMinor" to nullable(medianMinor), "deviationMinor" to nullable(deviationMinor), "reason" to json(reason)),
    )

    @Test fun budgetStatus() {
        check("budgetStatus") { budgetStatus(it.field("limit").num, it.field("spent").num, it.field("threshold").intOrNull()).toJson() }
    }

    @Test fun averageCompletedSpend() {
        check("averageCompletedSpend") {
            val r = averageCompletedSpend(
                it.jsonArray.map { p ->
                    CompletedPeriodSpend(p.field("periodKey").str, p.field("spentMinor").num, p.field("reliable").jsonPrimitive.boolean, p.field("transactionCount").jsonPrimitive.int)
                },
            )
            JsonObject(
                mapOf(
                    "averageMinor" to nullable(r.averageMinor), "usedPeriods" to json(r.usedPeriods), "reason" to json(r.reason),
                    "excluded" to json(r.excluded.map { e -> obj("periodKey" to e.periodKey, "reason" to e.reason) }),
                ),
            )
        }
    }

    @Test fun detectAnomaly() {
        check("detectAnomaly") { detectAnomaly(it.field("value").num, it.field("history").jsonArray.map { h -> h.num }).toJson() }
    }

    @Test fun buildCategoryLines() {
        check("buildCategoryLines") {
            val lines = buildCategoryLines(
                it.field("spend").jsonObject.mapValues { (_, v) -> v.num },
                it.field("limits").jsonObject.mapValues { (_, v) -> CategoryLimit(v.field("limitMinor").num, v.field("thresholdPercent").intOrNull()) },
                it.field("averages").jsonObject.mapValues { (_, v) -> v.longOrNull() },
                it.field("histories").jsonObject.mapValues { (_, v) -> v.jsonArray.map { h -> h.num } },
            )
            lines.map { l ->
                JsonObject(
                    mapOf(
                        "categoryId" to json(l.categoryId), "spentMinor" to json(l.spentMinor), "shareTenthPercent" to json(l.shareTenthPercent),
                        "status" to (l.status?.toJson() ?: JsonNull), "averageMinor" to nullable(l.averageMinor), "anomaly" to l.anomaly.toJson(),
                        "noLimitReason" to nullable(l.noLimitReason),
                    ),
                )
            }
        }
    }

    @Test fun relativeTenths() { check("relativeTenths") { relativeTenths(it.jsonArray.map { v -> v.longOrNull() }).map { v -> nullable(v) } } }

    @Test fun categoryDistribution() {
        check("categoryDistribution") {
            val d = categoryDistribution(EntityJson.transactions(it.field("transactions")), EntityJson.allocations(it.field("allocations")))
            obj(
                "totalMinor" to d.totalMinor,
                "slices" to d.slices.map { s ->
                    JsonObject(mapOf("categoryId" to nullable(s.categoryId), "amountMinor" to json(s.amountMinor), "count" to json(s.count), "shareTenthPercent" to json(s.shareTenthPercent)))
                },
            )
        }
    }

    @Test fun dailyAllowance() {
        check("dailyAllowance") {
            val d = dailyAllowance(it.field("limit").longOrNull(), it.field("spent").num, it.field("today").str, it.field("period").period(), it.field("remaining").longOrNull())
            JsonObject(mapOf("amountMinor" to nullable(d.amountMinor), "remainingDays" to json(d.remainingDays), "approximate" to json(d.approximate), "reason" to json(d.reason)))
        }
    }

    @Test fun forecastPeriodSpend() {
        check("forecastPeriodSpend") {
            val f = forecastPeriodSpend(it.field("spent").num, it.field("today").str, it.field("period").period())
            JsonObject(mapOf("projectedMinor" to nullable(f.projectedMinor), "elapsedDays" to json(f.elapsedDays), "totalDays" to json(f.totalDays), "caveat" to json(f.caveat)))
        }
    }

    @Test fun assessCoverage() {
        check("assessCoverage") {
            val c = assessCoverage(EntityJson.transactions(it))
            JsonObject(mapOf("total" to json(c.total), "unclassified" to json(c.unclassified), "totalsReliable" to json(c.totalsReliable), "note" to nullable(c.note)))
        }
    }
}
