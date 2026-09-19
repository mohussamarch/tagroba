package app.masroufy.core

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

/** `money.ts` و`formatMoney.ts` — نفس النتايج بالهللة ونفس رسايل الأخطاء (native-app/golden/money.json). */
class MoneyGoldenTest {
    private fun check(fn: String, run: (kotlinx.serialization.json.JsonElement) -> Any?) =
        Golden.check("money", fn) { json(run(it)) }

    @Test fun normalizeDigits() { check("normalizeDigits") { normalizeDigits(it.str) } }
    @Test fun parseMoney() { check("parseMoney") { parseMoney(it.str) } }
    @Test fun tryParseMoney() { check("tryParseMoney") { tryParseMoney(it.str) } }

    @Test fun formatAmount() {
        check("formatAmount") {
            formatAmount(
                it.field("amount").num,
                alwaysSign = it.field("alwaysSign").jsonPrimitive.booleanOrNull ?: false,
                grouping = it.field("grouping").jsonPrimitive.booleanOrNull ?: true,
            )
        }
    }

    @Test fun formatMoney() {
        check("formatMoney") {
            formatMoney(
                it.field("amount").num,
                showCurrency = it.field("showCurrency").jsonPrimitive.booleanOrNull ?: true,
                hidden = it.field("hidden").jsonPrimitive.booleanOrNull ?: false,
            )
        }
    }

    @Test fun formatMoneyOrNA() {
        check("formatMoneyOrNA") { val a = it.field("amount"); formatMoneyOrNA(if (a is JsonNull) null else a.num) }
    }

    @Test fun addMoney() { check("addMoney") { input -> addMoney(*input.jsonArray.map { it.num }.toLongArray()) } }
    @Test fun subtractMoney() { check("subtractMoney") { val (a, b) = it.jsonArray.map { x -> x.num }; subtractMoney(a, b) } }
    @Test fun negateAbs() { check("negateAbs") { listOf(negateMoney(it.num), absMoney(it.num)) } }
    @Test fun multiplyMoneyByInt() { check("multiplyMoneyByInt") { val (a, t) = it.jsonArray.map { x -> x.num }; multiplyMoneyByInt(a, t) } }

    @Test fun rateOfMoney() {
        check("rateOfMoney") { rateOfMoney(it.field("amount").num, it.field("numerator").num, it.field("denominator").num) }
    }

    @Test fun splitMoney() { check("splitMoney") { splitMoney(it.field("amount").num, it.field("parts").num.toInt()) } }
    @Test fun compareMoney() { check("compareMoney") { val (a, b) = it.jsonArray.map { x -> x.num }; compareMoney(a, b) } }

    @Test fun withinRelativeTolerance() {
        check("withinRelativeTolerance") {
            withinRelativeTolerance(it.field("value").num, it.field("target").num, it.field("perThousand").num)
        }
    }

    @Test fun savingsRatePercent() {
        check("savingsRatePercent") { savingsRatePercent(it.field("income").num, it.field("remaining").num) }
    }

    @Test fun formatPercentOrNA() {
        check("formatPercentOrNA") { formatPercentOrNA(if (it is JsonNull) null else it.jsonPrimitive.doubleOrNull) }
    }
}
