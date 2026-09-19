package app.masroufy.core

import app.masroufy.core.EntityJson.obj
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test

/** الدفتر والأنواع والاقتراح و«التقريبي» (native-app/golden/ledger.json). */
class LedgerGoldenTest {
    private fun check(fn: String, run: (JsonElement) -> Any?) = Golden.check("ledger", fn) { json(run(it)) }

    private fun PeriodTotals.toJson() = obj(
        "incomeMinor" to incomeMinor, "personalExpenseMinor" to personalExpenseMinor,
        "remainingMinor" to remainingMinor, "excludedExpenseMinor" to excludedExpenseMinor,
    )

    @Test fun economicKindRules() {
        check("economicKindRules") {
            val r = ruleFor(EconomicKind.fromWire(it.str))
            obj(
                "kind" to r.kind.wire, "label" to r.label, "liquidity" to r.liquidity.wire, "countsAsIncome" to r.countsAsIncome,
                "countsAsPersonalExpense" to r.countsAsPersonalExpense, "personEffect" to r.personEffect.wire,
            )
        }
    }

    @Test fun isConsistentWithObservedDirection() {
        check("isConsistentWithObservedDirection") {
            isConsistentWithObservedDirection(EconomicKind.fromWire(it.field("kind").str), Direction.fromWire(it.field("observed").str))
        }
    }

    @Test fun personalShareOf() {
        check("personalShareOf") { personalShareOf(EntityJson.transaction(it.field("transaction")), EntityJson.allocations(it.field("allocations"))) }
    }

    @Test fun computePeriodTotals() {
        check("computePeriodTotals") {
            computePeriodTotals(EntityJson.transactions(it.field("transactions")), EntityJson.allocations(it.field("allocations"))).toJson()
        }
    }

    @Test fun sumByTag() {
        check("sumByTag") {
            sumByTag(EntityJson.transactions(it.field("transactions")), it.field("tagged").let { t -> kotlinx.serialization.json.JsonArray(t as kotlinx.serialization.json.JsonArray).map { x -> x.str } }, EntityJson.allocations(it.field("allocations")))
        }
    }

    @Test fun remainingOfObligation() {
        check("remainingOfObligation") { remainingOfObligation(EntityJson.obligation(it.field("obligation")), EntityJson.settlements(it.field("settlements"))) }
    }

    @Test fun computePersonBalance() {
        check("computePersonBalance") {
            val b = computePersonBalance(it.field("personId").str, EntityJson.obligations(it.field("obligations")), EntityJson.settlements(it.field("settlements")))
            obj("personId" to b.personId, "receivableMinor" to b.receivableMinor, "payableLoanMinor" to b.payableLoanMinor, "payableCustodyMinor" to b.payableCustodyMinor)
        }
    }

    @Test fun checkSettlement() {
        check("checkSettlement") {
            val c = checkSettlement(EntityJson.obligation(it.field("obligation")), EntityJson.settlements(it.field("settlements")), it.field("amount").num)
            obj("allowed" to c.allowed, "reason" to c.reason, "settledMinor" to c.settledMinor, "surplusMinor" to c.surplusMinor)
        }
    }

    @Test fun splitGrossIntoPrincipalAndFee() {
        check("splitGrossIntoPrincipalAndFee") {
            val s = splitGrossIntoPrincipalAndFee(it.field("gross").num, it.field("fee").num)
            obj("principalMinor" to s.principalMinor, "feeMinor" to s.feeMinor)
        }
    }

    @Test fun suggestEconomicKind() {
        check("suggestEconomicKind") {
            val input = it.jsonObject
            fun opt(name: String) = input[name]?.str
            val s = suggestEconomicKind(
                SuggestionInput(
                    direction = Direction.fromWire(it.field("direction").str), sourceCategory = opt("sourceCategory"),
                    categoryName = opt("categoryName"), merchantName = opt("merchantName"), description = opt("description"),
                ),
            )
            obj(
                "kind" to (s.kind?.wire ?: kotlinx.serialization.json.JsonNull), "confidence" to s.confidence.wire, "reason" to s.reason,
                "alternatives" to EntityJson.kinds(s.alternatives), "bulkConfirmable" to isBulkConfirmable(s),
            )
        }
    }

    @Test fun withEstimatedKinds() {
        check("withEstimatedKinds") {
            val names = it.field("categoryNames").jsonObject.mapValues { (_, v) -> v.str }
            val view = withEstimatedKinds(EntityJson.transactions(it.field("transactions")), names)
            obj("kinds" to EntityJson.kinds(view.transactions.map { t -> t.economicKind }), "estimatedCount" to view.estimatedCount, "needsReviewCount" to view.needsReviewCount)
        }
    }
}
