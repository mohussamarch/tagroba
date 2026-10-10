package app.masroufy.core

import app.masroufy.core.EntityJson.obj
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * محلل رسايل البنك والحجب (native-app/golden/sms.json). ملف المرجع **ما بيتعدّلش**: الحالات اللي القارئ بقى يقراها غير التطبيق الحالي
 * **بقرار مالك** مكتوبة بالاسم في [KNOWN_DIVERGENCE] ومعاها القرار، والاختبار بيتأكد إن الفرق هو ده بالظبط (ولا حالة زيادة ولا ناقصة).
 */
class SmsGoldenTest {
    // النص المتوقع هنا = نص التطبيق الحالي = النسخة المصرية (OVERRIDES §66)
    @BeforeTest
    fun egyptianText() {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
    }

    @AfterTest
    fun defaultText() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private fun check(fn: String, run: (JsonElement) -> Any?) = Golden.check("sms", fn) { json(run(it)) }

    private fun resultJson(r: SmsParseResult): JsonElement = when (r) {
        is SmsParseResult.Rejected -> obj("ok" to false, "reason" to r.reason)
        is SmsParseResult.Ok -> JsonObject(
            mapOf(
                "ok" to json(true),
                "row" to obj(
                    "lineNumber" to r.row.lineNumber, "date" to r.row.date, "amountMinor" to r.row.amountMinor,
                    "direction" to r.row.direction.wire, "merchantName" to r.row.merchantName, "reference" to r.row.reference,
                    "sourceName" to r.row.sourceName, "description" to r.row.description, "raw" to r.row.raw,
                ),
            ),
        )
    }

    /** المتوقع بعد تبسيط الكلمات المعتمد (نفس `Golden.check`). */
    private fun plain(e: JsonElement): JsonElement = when (e) {
        is JsonArray -> JsonArray(e.map(::plain))
        is JsonObject -> JsonObject(e.mapValues { plain(it.value) })
        is JsonPrimitive -> if (e.isString) JsonPrimitive(plainEgyptian(e.content)) else e
        else -> e
    }

    @Test fun parseBankSms() {
        val cases = Golden.cases("sms", "parseBankSms")
        assertTrue(cases.isNotEmpty())
        val unexpected = mutableListOf<String>()
        val seen = mutableSetOf<Pair<String, String>>()
        cases.forEachIndexed { i, case ->
            val input = case["in"] ?: JsonNull
            val message = BankSmsMessage(input.field("sender").str, input.field("receivedAt").str, input.field("body").str)
            // رقم السطر في الحالات = ترتيب الرسالة (كل رسالة ليها حالتين)
            val r = parseBankSms(message, (i + 2) / 2)
            val diff = Golden.firstDiff(plain(case["out"] ?: JsonNull), resultJson(r), "") ?: return@forEachIndexed
            val known = KNOWN_DIVERGENCE[message.body]
            if (known == null) {
                unexpected += "${message.body.replace("\n", "\\n")} @ ${message.receivedAt}: $diff"
                return@forEachIndexed
            }
            // الفرق المسموح بس: كان «التاريخ مش واضح» وبقى بيتقري بيوم الوصول بتوقيت الرياض (§77-C)
            val old = case["out"]?.field("reason")?.str?.let(::plainEgyptian)
            assertEquals(uiText(TextKey.SMS_DATE_UNCLEAR), old, "${message.body}: only a dateless rejection may change (${known.reason})")
            val row = (r as? SmsParseResult.Ok)?.row
            // مراجعة S1: اليوم المتوقع **مكتوب بالحرف** لكل وقت وصول — مش محسوب بنفس الدالة اللي بنختبرها
            assertEquals(known.days[message.receivedAt], row?.date, "${message.body} @ ${message.receivedAt}: arrival day (${known.reason})")
            assertEquals(known.clear, row!!.shape.clear, "${message.body}: shape (${known.reason})")
            seen += message.body to message.receivedAt
        }
        assertTrue(unexpected.isEmpty(), "golden sms.json differences outside KNOWN_DIVERGENCE:\n" + unexpected.joinToString("\n"))
        val listed = KNOWN_DIVERGENCE.flatMap { (body, known) -> known.days.keys.map { body to it } }.toSet()
        assertEquals(listed, seen, "KNOWN_DIVERGENCE must list exactly the changed entries (body and arrival time)")
    }

    @Test fun redactSms() { check("redactSms") { redactSms(it.str) } }

    /** حالة اتغيّرت بقرار مالك: السبب · اليوم المتوقع لكل وقت وصول في ملف المرجع (بالحرف) · الشكل واضح ولا بيستنى. */
    class Divergence(val reason: String, val days: Map<String, String>, val clear: Boolean)

    companion object {
        /**
         * حالات ملف المرجع اللي **قرار مالك** غيّر نتيجتها — النص بالحرف ⇒ السبب واليوم المتوقع. ملف المرجع نفسه ما اتلمسش.
         * §77-C (2026-10-09): «الرسالة من غير تاريخ ⇒ ياخد يوم وصول الرسالة بتوقيت البلد — لكل الأشكال اللي مفيهاش تاريخ»: التطبيق
         * الحالي كان بيرفضها «التاريخ مش واضح»، ودلوقتي الشكل المعروف بيتقري بيوم الوصول.
         */
        val KNOWN_DIVERGENCE: Map<String, Divergence> = mapOf(
            // قالب الراجحي «حوالة محلية واردة» من غير تاريخ: وصول 10:00 جرينتش = 13:00 الرياض يوم 18 · 23:30 جرينتش = 02:30 الرياض يوم 19
            "حوالة محلية واردة\nSR 7\nإلى: 9999\nرسوم: 2 SAR" to Divergence(
                "§77-C: a known layout with no date takes the arrival day (Riyadh)",
                mapOf("2026-09-18T10:00:00Z" to "2026-09-18", "2026-09-18T23:30:00.123Z" to "2026-09-19"),
                clear = true,
            ),
        )
    }
}
