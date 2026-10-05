package app.masroufy.core

import app.masroufy.core.EntityJson.obj
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** محلل رسايل البنك والحجب (native-app/golden/sms.json). */
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

    @Test fun parseBankSms() {
        var line = 0
        check("parseBankSms") {
            line++
            val message = BankSmsMessage(it.field("sender").str, it.field("receivedAt").str, it.field("body").str)
            // رقم السطر في الحالات = ترتيب الرسالة (كل رسالة ليها حالتين)
            when (val r = parseBankSms(message, (line + 1) / 2)) {
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
        }
    }

    @Test fun redactSms() { check("redactSms") { redactSms(it.str) } }
}
