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
 * التقرير في `device/build/reports/sms-template-coverage/` (`summary.txt` و`report.json`). الاختبار بيفشل لو الأداة نفسها باظت
 * (سطر مالوش متوقع، خانة `{…}` مالهاش قيمة)، **ومن جلسة 32 كمان لو سطر رجع لورا**: كل سطر لازم يتقري صح أو يترفض صح ما عدا
 * [KNOWN_UNSUPPORTED] (معروف ومكتوب سببه)، ومفيش رسالة تتقري في البلدين.
 * **الجولة الرابعة (OVERRIDES §72.1):** كمان بيعد **الشكل** اللي اتفهمت بيه العملية اللي اتسجلت (`known` · `sama-title` بيتسجلوا لوحدهم،
 * `keyword-fallback` بيستنى تأكيد المالك)، وبيفشل لو قالب بحث (مش سطر «كلمات بس») اتقري صح من الكلمات العامة بس.
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
        val main = Fill.expand(fixed).map { (l, t) -> Variant(l, Fill.fill(t, values), spec.expect, values) }
        // نفس القالب بأشكال تاريخ تانية (شكل البنك لسه مفترض) — الاسم «date=<الشكل>»
        val extra = spec.alsoDates.flatMap { s ->
            val v = Fill.values(country, lang, type, s, spec.values)
            Fill.expand(fixed).map { (l, t) -> Variant(if (l == "-") "date=${s.name}" else "$l/date=${s.name}", Fill.fill(t, v), spec.expect, v) }
        }
        return main + extra
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
        // جلسة 32 (تعديل القارئ): من هنا القياس **بيمسك الرجوع لورا** — كل سطر لازم يتقري صح أو يترفض صح، ما عدا القايمة دي
        val notHandled = rows.filter { it.outcome != Outcome.CORRECT && it.outcome != Outcome.CORRECTLY_IGNORED }.map { "${it.file}#${it.index}" }.toSet()
        assertEquals(KNOWN_UNSUPPORTED.keys, notHandled, "rows not handled right (outside the known list) — see build/reports/sms-template-coverage/summary.txt")
        assertTrue(rows.flatMap { it.variants }.none { it.crossLane != null }, "a message read (or kept waiting) by both countries' readers")
        // الجولة التانية من المراجعة: الرسالة اللي مش عملية لازم يوقفها **حارس مقصود** (عرض · رمز · مرفوضة · مش عملية) — في التسجيل
        // التلقائي وفي القراية بطلب المستخدم — مش رفض بالصدفة عشان التاريخ أو المبلغ مش واضح
        val fragile = rows.filter { r -> r.variants.any { it.fragileIgnore } }.map { "${it.file}#${it.index}" }
        assertTrue(fragile.isEmpty(), "must-ignore rows stopped only by accident, not by a guard: $fragile")
        // الجولة الرابعة (§72 «المفهومة» = شكل معروف): كل قالب بحث بيتقري صح لازم يبقى **شكل معروف** في كل نسخه — لو بقى «كلمات
        // عامة» هيستنى تأكيد المالك بدل ما يتسجل لوحده (رجوع لورا). سطور «كلمات بس» في البحث (`keywordOnly`) بتتعد في التقرير بس.
        val fallback = rows.filter { !it.keywordOnly && it.bookedShape == RowResult.FALLBACK }.map { "${it.file}#${it.index}" } - WAIT_BY_DESIGN.keys
        assertTrue(fallback.isEmpty(), "researched templates read only by the keyword fallback (would wait instead of auto-recording): $fallback")
        // وعناوين البنك المركزي لازم تتعرف **كعنوان موحّد** بالعربي والإنجليزي (مش قالب بنك بالصدفة) — ما عدا [WAIT_BY_DESIGN]
        val samaNotTitle = rows.filter { r -> r.isTitle && "${r.file}#${r.index}" !in WAIT_BY_DESIGN && r.variants.any { it.shape != null && it.shape != "sama-title" } }
            .map { "${it.file}#${it.index}" }
        assertTrue(samaNotTitle.isEmpty(), "SAMA standard titles not recognised as SAMA titles: $samaNotTitle")
        // الجولة الخامسة: الشراء/السحب **برّه البلد** بيستنى حتى لو المبلغ بالريال بس (§75-12) — لازم يفضل كده
        val notWaiting = rows.filter { "${it.file}#${it.index}" in WAIT_BY_DESIGN && it.bookedShape != RowResult.FALLBACK }.map { "${it.file}#${it.index}" }
        assertTrue(notWaiting.isEmpty(), "international purchase/withdrawal titles must wait for the owner: $notWaiting")
        val shapes = Report.shapeCounts(rows.filterNot { it.keywordOnly })
        assertTrue(shapes.getValue("known") > 0 && shapes.getValue("sama-title") > 0, "shape counts look empty: $shapes")
    }

    companion object {
        /**
         * الجولة الخامسة (§75-12 — قرار المالك: الشراء الأجنبي ما بيتسجلش لوحده): عنوان شراء أو سحب **دولي** بيستنى تأكيد المالك حتى لو
         * المبلغ المكتوب بالريال بس (`SmsKnownShapesSaudi.kt` — `INTERNATIONAL`). بيتقري صح، بس شكله «مستني» عمدًا.
         */
        val WAIT_BY_DESIGN = mapOf(
            "saudi#164" to "SAMA «International ATM Withdrawal / سحب صراف آلي دولي» with only a SAR amount",
            "saudi#174" to "SAMA «PoS International Purchase / شراء عبر نقاط البيع دولية» with only a SAR amount",
        )

        /** سطور معروف إنها بتترفض (بتستنى المالك) — والسبب. اللي يصلح سطر منهم يشيله من هنا. */
        val KNOWN_UNSUPPORTED = mapOf(
            "saudi#39" to "invented by a test author (research: «do not build on it»); no date in the message",
            "saudi#48" to "probably invented; no date in the message",
            "saudi#73" to "no currency at all («تم قيد مبلغ 87.50 لحسابكم») — riyal can't be assumed (rule 10)",
            "saudi#97" to "STC «Purchase Reversal» template has no date (PennyWise test, likely trimmed)",
            "saudi#103" to "STC «Internal transfer» has no date and no direction word (PennyWise test)",
            "saudi#106" to "STC «Outward SARIE Transfer» template has no date",
            "saudi#120" to "cashback accrual into a card's cashback wallet: no date in the template",
            "saudi#121" to "cashback credited to card: no date in the template",
            // egypt#49 اتشال (مراجعة جلسة 33): فلتر الجهاز بقى ما بيحجبش التاريخ، فرقم المرجع اللي قبله ما بقاش بياكله
        )
    }
}
