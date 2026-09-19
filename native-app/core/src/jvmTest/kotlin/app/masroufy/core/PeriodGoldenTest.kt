package app.masroufy.core

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

/** `period.ts` — الشهر المالي من يوم الراتب (native-app/golden/period.json). */
class PeriodGoldenTest {
    private fun check(fn: String, run: (JsonElement) -> Any?) = Golden.check("period", fn) { json(run(it)) }

    private fun Period.toJson() = JsonObject(
        mapOf("key" to JsonPrimitive(key), "start" to JsonPrimitive(start), "end" to JsonPrimitive(end), "days" to JsonPrimitive(days)),
    )
    private fun DateParts.toJson() = JsonObject(mapOf("year" to JsonPrimitive(year), "month" to JsonPrimitive(month), "day" to JsonPrimitive(day)))
    private fun JsonElement.period() = Period(field("key").str, field("start").str, field("end").str, field("days").num.toInt())
    private fun JsonElement.int(name: String) = field(name).num.toInt()

    @Test fun isLeapYear() { check("isLeapYear") { isLeapYear(it.num.toInt()) } }
    @Test fun daysInMonth() { check("daysInMonth") { daysInMonth(it.int("year"), it.int("month")) } }
    @Test fun parseIsoDate() { check("parseIsoDate") { parseIsoDate(it.str).toJson() } }
    @Test fun isValidIsoDate() { check("isValidIsoDate") { isValidIsoDate(it.str) } }
    @Test fun toDayNumber() { check("toDayNumber") { toDayNumber(DateParts(it.int("year"), it.int("month"), it.int("day"))) } }
    @Test fun dayNumberToIso() { check("dayNumberToIso") { dayNumberToIso(it.num.toInt()) } }
    @Test fun daysBetween() { check("daysBetween") { daysBetween(it.field("from").str, it.field("to").str) } }
    @Test fun clampPaydayToMonth() { check("clampPaydayToMonth") { clampPaydayToMonth(it.int("year"), it.int("month"), it.int("payday")) } }
    @Test fun buildPeriod() { check("buildPeriod") { buildPeriod(it.int("year"), it.int("month"), it.int("payday")).toJson() } }
    @Test fun periodForDate() { check("periodForDate") { periodForDate(it.field("date").str, it.int("payday")).toJson() } }
    @Test fun isDateInPeriod() { check("isDateInPeriod") { isDateInPeriod(it.field("date").str, it.field("period").period()) } }
    @Test fun remainingDaysInPeriod() { check("remainingDaysInPeriod") { remainingDaysInPeriod(it.field("today").str, it.field("period").period()) } }
    @Test fun formatPeriodRange() { check("formatPeriodRange") { formatPeriodRange(it.period()) } }
    @Test fun periodIndex() { check("periodIndex") { periodIndex(it.period()) } }

    @Test fun shiftPeriodWithin() {
        check("shiftPeriodWithin") {
            shiftPeriodWithin(it.field("period").period(), it.int("delta"), it.field("latest").period(), it.int("payday")).toJson()
        }
    }
}
