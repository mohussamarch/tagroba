package app.masroufy.device.smscoverage

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * **قياس** قارئ رسايل البنك على كل أشكال الرسايل اللي في البحث (`research/banks/saudi-sms-formats.json` و`egypt-sms-formats.json`).
 *
 * لكل سطر: القالب بيتملى بقيم **مخترعة** مناسبة لنوعه ([Fill] — المستودع عام: ممنوع اسم أو رقم أو محل من المصادر)، وبيعدّي على
 * نفس خط التطبيق ([Evaluate]: فلتر الأمان ⇒ قارئ البلد ⇒ قارئ الطرف التاني)، ويتصنف:
 * - `correct` — عملية واتقرت صح (المبلغ والاتجاه والتاريخ والمحل/الطرف زي ما القالب معناه، والعملة = عملة البلد).
 * - `wrong` — اتقرت بس فيه خانة غلط (أخطر حاجة: بتتسجل لوحدها غلط).
 * - `rejected` — عملية والقارئ (أو فلتر الأمان) رفضها ⇒ بتستنى المالك أو بتضيع.
 * - `wronglyAccepted` — مش عملية (رمز · عرض · مرفوض · حجز · طلب · معلومة) والقارئ سجلها.
 * - `correctlyIgnored` — مش عملية واترفضت.
 * المتوقع لكل سطر مكتوب باليد في [SaudiSpecs] و[EgyptSpecs] و[SamaTitles] (عناوين البنك المركزي بتتجرب كأول سطر في رسالة عامة).
 *
 * ⚠️ **الاختبار ده بيقيس وما بيفشلش على نتيجة القارئ** — التقرير في `device/build/reports/sms-template-coverage/`
 * (`summary.txt` و`report.json`). بيفشل بس لو الأداة نفسها باظت: سطر في ملفات البحث مالوش متوقع، أو خانة `{…}` مالهاش قيمة.
 * اللي هيعدّل القارئ يقدر يقلب أي سطر لـ«لازم يبقى صح» بعد ما يصلحه.
 */
class SmsTemplateCoverageTest {
    private fun researchDir(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            File(dir, "research/banks").takeIf { it.isDirectory }?.let { return it }
            dir = dir.parentFile
        }
        fail("research/banks not found above ${System.getProperty("user.dir")}")
    }

    @Suppress("UNCHECKED_CAST")
    private fun load(name: String): List<Map<String, Any?>> = MiniJson.parse(File(researchDir(), name).readText()) as List<Map<String, Any?>>

    private fun senderOf(bank: String) = bank.substringBefore(' ').take(11)

    private fun saudiRow(index: Int, row: Map<String, Any?>): RowResult {
        val bank = row["bank"] as String
        val type = row["type"] as String
        val lang = row["lang"] as String
        val template = row["template"] as String
        if (type == "standard-title") {
            val expect = SamaTitles.rows[index] ?: fail("saudi #$index: no SAMA title expectation")
            val values = mapOf("amount" to SamaTitles.AMOUNT)
            val ar = Fill.expand(template).map { (l, t) -> Variant(if (l == "-") "ar" else "ar-$l", SamaTitles.arabicBody(t), expect, values) }
            val en = Variant("en", SamaTitles.englishBody(SamaTitles.englishTitle(row["notes"] as String)), expect, values)
            return result("saudi", index, row, (ar + en).map { Evaluate.run("SA", "SAMA", it) }, isTitle = true, keywordOnly = false)
        }
        val spec = SaudiSpecs.rows[index] ?: fail("saudi #$index ($bank · $type): no expectation in SaudiSpecs")
        return result("saudi", index, row, variants("SA", index, bank, type, lang, template, spec).map { Evaluate.run("SA", senderOf(bank), it) }, false, spec.custom.isNotEmpty())
    }

    private fun egyptRow(index: Int, row: Map<String, Any?>): RowResult {
        val bank = row["bank"] as String
        val spec = EgyptSpecs.rows[index] ?: fail("egypt #$index ($bank · ${row["type"]}): no expectation in EgyptSpecs")
        val vs = variants("EG", index, bank, row["type"] as String, row["lang"] as String, row["template"] as String, spec)
        return result("egypt", index, row, vs.map { Evaluate.run("EG", senderOf(bank), it) }, false, spec.custom.isNotEmpty())
    }

    private fun variants(country: String, index: Int, bank: String, type: String, lang: String, template: String, spec: RowSpec): List<Variant> {
        val style = spec.date ?: Fill.defaultStyle(country, bank, index, lang)
        val values = Fill.values(country, lang, type, style, spec.values)
        if (spec.custom.isNotEmpty()) {
            return spec.custom.flatMap { c ->
                Fill.expand(c.template).map { (l, t) -> Variant(if (l == "-") c.label else "${c.label}/$l", Fill.fill(t, values), c.expect, values) }
            }
        }
        val fixed = spec.fix?.invoke(template) ?: template
        if (spec.fix != null) assertTrue(fixed != template, "$country #$index: template fix did not apply")
        return Fill.expand(fixed).map { (l, t) -> Variant(l, Fill.fill(t, values), spec.expect, values) }
    }

    private fun result(file: String, index: Int, row: Map<String, Any?>, results: List<VariantResult>, isTitle: Boolean, keywordOnly: Boolean) =
        RowResult(
            file, index, row["bank"] as String, row["type"] as String, row["lang"] as String, row["verified"] as Boolean?,
            isTitle, keywordOnly, results,
        )

    @Test fun measureEveryResearchedTemplate() {
        val saudi = load("saudi-sms-formats.json")
        val egypt = load("egypt-sms-formats.json")
        val rows = saudi.mapIndexed(::saudiRow) + egypt.mapIndexed(::egyptRow)
        assertEquals(saudi.size + egypt.size, rows.size)
        // كل متوقع مكتوب لسطر موجود فعلًا (لو ملف البحث اتغير ترتيبه، الأداة لازم تتحدث)
        assertTrue(SaudiSpecs.rows.keys.all { it in saudi.indices && saudi[it]["type"] != "standard-title" }, "SaudiSpecs index out of range")
        assertTrue(SamaTitles.rows.keys.all { it in saudi.indices && saudi[it]["type"] == "standard-title" }, "SamaTitles index mismatch")
        assertTrue(EgyptSpecs.rows.keys.all { it in egypt.indices }, "EgyptSpecs index out of range")
        val out = File(System.getProperty("user.dir"), "build/reports/sms-template-coverage")
        val report = Report.write(out, rows)
        assertTrue(report.isFile)
        println(Report.summaryText(rows))
        println("report: ${report.absolutePath}")
    }
}
