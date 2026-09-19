package app.masroufy.core

import app.masroufy.core.EntityJson.obj
import app.masroufy.core.EntityJson.transactionJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

/** الكاش والأيام والبحث والفصل والتسويات والمشاريع والاشتراكات (native-app/golden/misc.json). */
class MiscGoldenTest {
    private fun check(fn: String, run: (JsonElement) -> Any?) = Golden.check("misc", fn) { json(run(it)) }
    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)
    private fun JsonElement.opt(name: String): String? = jsonObject[name]?.takeIf { it !is JsonNull }?.str
    private val cash = Wallet("w-cash", "كاش", Currency.SAR, "cash", 100_000, "2026-08-15")

    private fun rule(e: JsonElement) = ProjectRule(
        e.field("id").str, e.field("projectId").str, e.field("matchText").str, RuleMatchMode.fromWire(e.field("matchMode").str),
        e.field("direction").str, e.field("enabled").jsonPrimitive.boolean, e.field("createdAt").str,
    )
    private fun link(e: JsonElement) = ProjectLink(e.field("id").str, e.field("projectId").str, e.field("transactionId").str, e.field("source").str, e.field("createdAt").str)
    private fun ProjectLink.toJson() = obj("id" to id, "projectId" to projectId, "transactionId" to transactionId, "source" to source, "createdAt" to createdAt)
    private fun item(e: JsonElement) = RecurringItem(
        e.field("id").str, e.field("name").str, e.field("merchantKey").str, e.field("kind").str, e.field("cycleMonths").jsonPrimitive.int,
        e.field("expectedMinor").num, Currency.valueOf(e.field("currency").str), e.field("nextDueAt").str,
        e.field("active").jsonPrimitive.boolean, e.field("confirmed").jsonPrimitive.boolean,
    )

    @Test fun cashAndDays() {
        check("summarizeCash") {
            val s = summarizeCash(cash, EntityJson.transactions(it.field("transactions")), emptyList(), "2026-08-28", "2026-09-27")
            obj(
                "wallet" to obj("id" to s.wallet.id, "name" to s.wallet.name, "openingBalanceMinor" to s.wallet.openingBalanceMinor, "openingAt" to s.wallet.openingAt),
                "balanceMinor" to s.balanceMinor, "inSinceOpeningMinor" to s.inSinceOpeningMinor, "outSinceOpeningMinor" to s.outSinceOpeningMinor,
                "spentInPeriodMinor" to s.spentInPeriodMinor, "periodTransactions" to s.periodTransactions.map(::transactionJson),
            )
        }
        check("groupByDay") {
            groupByDay(EntityJson.transactions(it)).map { g ->
                obj("date" to g.date, "outgoingMinor" to g.outgoingMinor, "incomingMinor" to g.incomingMinor, "transactions" to g.transactions.map(::transactionJson))
            }
        }
    }

    @Test fun search() {
        check("parseQuery") {
            val q = parseQuery(it.str)
            JsonObject(mapOf("text" to json(q.text), "amount" to (q.amount?.let { a -> obj("targetMinor" to a.targetMinor, "tolerancePerThousand" to a.tolerancePerThousand) } ?: JsonNull)))
        }
        check("searchTransactions") {
            val items = it.field("items").jsonArray.map { i ->
                SearchableTransaction(
                    EntityJson.transaction(i.field("transaction")), i.opt("categoryName"),
                    i.jsonObject["tagNames"]?.jsonArray?.map { t -> t.str }, i.jsonObject["merchantNames"]?.jsonArray?.map { m -> m.str },
                )
            }
            searchTransactions(items, parseQuery(it.field("query").str)).map { h -> obj("id" to h.transaction.id, "matchedFields" to h.matchedFields) }
        }
    }

    @Test fun expenseAndSettlement() {
        check("computeExpenseBreakdown") {
            val b = computeExpenseBreakdown(it.jsonArray.map { r -> ClassifiableRow(r.field("debitMinor").num, r.field("creditMinor").num, r.opt("sourceCategory")) })
            obj(
                "totalDebitMinor" to b.totalDebitMinor, "totalCreditMinor" to b.totalCreditMinor, "nonExpenseMinor" to b.nonExpenseMinor,
                "realExpenseMinor" to b.realExpenseMinor, "nonExpenseByCategory" to obj(*b.nonExpenseByCategory.map { (k, v) -> k to v }.toTypedArray()),
            )
        }
        check("isNonExpenseSourceCategory") { isNonExpenseSourceCategory(if (it is JsonNull) null else it.str) }
        check("settlement") {
            if (it is kotlinx.serialization.json.JsonArray) suggestedSettlementMinor(it[0].num, it[1].num)
            else settleableKinds(Direction.fromWire(it.str)).map { k -> k.wire }
        }
        check("prepareSettlement") {
            val i = it.field("input")
            val o = it.field("obligation")
            val s = prepareSettlement(
                Settlement(i.field("id").str, i.field("transactionId").str, i.field("obligationId").str, i.field("amountMinor").num), i.field("personId").str,
                Obligation(o.field("id").str, o.field("personId").str, null, ObligationKind.fromWire(o.field("kind").str), o.field("originalMinor").num, Currency.SAR),
                EntityJson.settlements(it.field("settlements")),
            )
            obj("id" to s.id, "obligationId" to s.obligationId, "transactionId" to s.transactionId, "amountMinor" to s.amountMinor)
        }
    }

    @Test fun projects() {
        val existing = listOf(Project("p1", "ماكت", "ماكت", false, "2026-09-01"))
        check("checkProjectName") { val c = checkProjectName(it.str, existing); obj("name" to c.name, "normalizedName" to c.normalizedName) }
        check("checkProjectRule") {
            val c = checkProjectRule(it.field("matchText").str, RuleMatchMode.fromWire(it.field("matchMode").str), it.field("direction").str)
            obj("matchText" to c.matchText, "matchMode" to c.matchMode.wire, "direction" to c.direction)
        }
        check("ruleCandidates") {
            ruleCandidates(rule(it.field("rule")), EntityJson.transactions(it.field("transactions")), it.field("links").jsonArray.map(::link)).map { t -> t.id }
        }
        check("planSyncLinks") {
            planSyncLinks(it.field("rules").jsonArray.map(::rule), EntityJson.transactions(it.field("transactions")), it.field("links").jsonArray.map(::link), "NOW").map { l -> l.toJson() }
        }
        check("syncStart") {
            if (it is JsonNull || it.str != "no-rules") {
                val rules = Golden.cases("misc", "planSyncLinks").first()["in"]!!.field("rules").jsonArray.map(::rule)
                nullable(syncStart(rules, if (it is JsonNull) null else it.str))
            } else nullable(syncStart(emptyList(), "x"))
        }
        check("membership") {
            val existingLinks = it.field("existing").jsonArray.map(::link)
            obj(
                "changes" to membershipChanges(existingLinks, "p1", "t-0", it.field("member").jsonPrimitive.boolean, "NOW").map { l -> l.toJson() },
                "members" to memberIds(existingLinks, "p1").toList(),
            )
        }
        check("summarizeProject") {
            val s = summarizeProject(EntityJson.transactions(it), emptyList(), mapOf("subs" to "اشتراكات", "bills" to "فواتير"))
            obj("spentMinor" to s.spentMinor, "receivedMinor" to s.receivedMinor, "count" to s.count, "estimatedCount" to s.estimatedCount, "needsReviewCount" to s.needsReviewCount)
        }
    }

    @Test fun recurring() {
        val categories = listOf(
            Category("subs", null, "اشتراكات", "tv", "#000", "#fff", true, 1),
            Category("bills", null, "فواتير ومرافق", "zap", "#000", "#fff", true, 2),
        )
        check("shiftMonths") { shiftMonths(it.field("date").str, it.field("delta").jsonPrimitive.int) }
        check("detectRecurring") {
            detectRecurring(EntityJson.transactions(it.field("rows")), categories).map { c ->
                obj(
                    "name" to c.name, "merchantKey" to c.merchantKey, "currency" to c.currency.name, "expectedMinor" to c.expectedMinor,
                    "nextDueAt" to c.nextDueAt, "cycleMonths" to c.cycleMonths, "transactionIds" to c.transactionIds, "reason" to c.reason,
                )
            }
        }
        check("recurringSummary") {
            val s = recurringSummary(item(it.field("item")), EntityJson.transactions(it.field("rows")), it.field("today").str)
            JsonObject(mapOf("paidMinor" to nullable(s.paidMinor), "paidCount" to json(s.paidCount), "annualMinor" to json(s.annualMinor), "overdue" to json(s.overdue)))
        }
        check("validateRecurring") { validateRecurring(item(it)); true }
    }
}
