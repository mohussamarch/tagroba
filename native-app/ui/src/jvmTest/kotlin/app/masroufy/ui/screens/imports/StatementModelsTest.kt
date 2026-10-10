package app.masroufy.ui.screens.imports

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.ImportSourceType
import app.masroufy.core.SchemaId
import app.masroufy.core.Texts
import app.masroufy.usecase.PdfStatementResult
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «كشف الحساب» (`StatementImport`) و«تحديد الأعمدة» (`StatementColumns`): نوع الملف · «قُرئ» · الطلب للمعاينة · اختيار معنى الأعمدة. */
class StatementModelsTest {
    @AfterTest fun reset() = Fx.resetTexts()

    @Test fun theFileKindComesFromItsBytesNotItsName() {
        assertEquals(StatementKind.PDF, statementKind("x.csv", "%PDF-1.7 ...".encodeToByteArray()))
        assertNull(statementKind("كشف.pdf", "date,name".encodeToByteArray()), "اسمه PDF ومحتواه مش PDF ⇒ مش مدعوم")
        assertEquals(StatementKind.CSV, statementKind("حركات.csv", "date,name,amount\n2026-09-01,x,1".encodeToByteArray()))
        assertNull(statementKind("صورة.png", byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 0, 1)), "ملف ثنائي ⇒ مش CSV")
    }

    @Test fun aReadPdfShowsItsBankPagesCountAndPeriod() {
        val rows = listOf(Fx.parsed(1, "أ", 100, date = "2026-09-14"), Fx.parsed(2, "ب", 200, date = "2026-09-01"), Fx.parsed(3, "ج", 300, date = "2026-09-30"))
        val result = PdfStatementResult(rows, emptyList(), pagesRead = 4, content = "c", schema = SchemaId.QNB_PDF, sourceType = ImportSourceType.PDF_QNB)
        val ui = pdfReadyUi("كشف-سبتمبر.pdf", result)
        assertEquals(SchemaId.QNB_PDF, ui.schema)
        assertEquals(4, ui.pages)
        assertEquals(3, ui.operations)
        assertEquals("2026-09-01" to "2026-09-30", ui.from to ui.to, "أول وآخر تاريخ (ترتيب تواريخ، مش حساب)")
        assertEquals("QNB مصر", bankOf(ui.schema))
    }

    @Test fun aCsvIsCountedInTheReviewNotHere() {
        val ui = csvReadyUi("حركات.csv", CsvTable(listOf("date", "name"), emptyList(), lines = 13))
        assertEquals(StatementKind.CSV, ui.kind)
        assertEquals(13, ui.lines)
        assertNull(ui.operations, "الـCSV بيتعد في المراجعة — مش رقم مخترع")
        assertNull(csvReadyUi("x.csv", null).lines, "ملف مش مقروء ⇒ «غير متاح» مش صفر")
    }

    @Test fun onlyPdfReadersAreListedAsSupportedBanks() {
        assertEquals(listOf(SchemaId.ALRAJHI_PDF), pdfSchemas(listOf(SchemaId.PREVIEW, SchemaId.ALRAJHI_PDF, SchemaId.LEGACY)))
    }

    @Test fun requestsCarryTheWalletCurrencyAndIdentity() {
        val wallet = Fx.wallet("w1", "حساب الراتب")
        val pdf = PdfStatementResult(listOf(Fx.parsed(1, "أ", 100)), emptyList(), 1, "content", SchemaId.ALRAJHI_PDF, ImportSourceType.PDF_ALRAJHI)
        val p = pdfRequest("ك.pdf", pdf, wallet)
        assertEquals("content", p.content, "البصمة من محتوى العمليات")
        assertEquals(ImportSourceType.PDF_ALRAJHI, p.sourceType)
        assertEquals("w1", p.walletId)
        val c = csvRequest("ح.csv", "date,name", Fx.wallet("e", "QNB", currency = Currency.EGP))
        assertNull(c.schema, "أول مرة المعاينة بتتعرف عليه")
        assertEquals(Currency.EGP, c.currency)
        assertEquals(ImportSourceType.CSV_LEGACY, csvRequest("ح.csv", "x", wallet, SchemaId.LEGACY).sourceType)
    }

    @Test fun aRoleMovesFromItsOldColumnAndSkipCanRepeat() {
        var roles: List<ColumnRole?> = listOf(null, null, null)
        roles = assignRole(roles, 0, ColumnRole.DATE)
        assertEquals(1, nextUnset(roles, 0))
        roles = assignRole(roles, 2, ColumnRole.DATE)
        assertEquals(listOf(null, null, ColumnRole.DATE), roles, "المعنى الواحد ما يتكررش")
        roles = assignRole(assignRole(roles, 0, ColumnRole.SKIP), 1, ColumnRole.SKIP)
        assertEquals(listOf(ColumnRole.SKIP, ColumnRole.SKIP, ColumnRole.DATE), roles, "«تجاهل» ينفع على أكتر من عمود")
        assertEquals(2, nextUnset(roles, 2), "كله اتحدد ⇒ نفس العمود")
    }

    @Test fun checksListTheChosenColumnsInEachVariant() {
        val roles = listOf(ColumnRole.DATE, ColumnRole.DESC, ColumnRole.DEBIT, ColumnRole.CREDIT, null)
        val checks = columnChecks(roles)
        assertEquals("العمود ١", checks[0].second)
        assertEquals("العمود ٣، العمود ٤", checks[2].second)
        assertTrue(checks[3].second.isEmpty(), "الرصيد مش متحدد ⇒ «لم يُحدَّد» في الشاشة")
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("العمود ٢", columnWord(1))
    }
}
