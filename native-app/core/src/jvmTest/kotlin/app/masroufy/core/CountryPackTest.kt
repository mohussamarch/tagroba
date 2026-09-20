package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** حزمة البلد (OVERRIDES §40) — المكان جاهز والسعودية بس هي اللي فيها محتوى حقيقي. */
class CountryPackTest {
    @Test
    fun `الحزمة السعودية هي الافتراضية ومعاها كل مخططات الاستيراد`() {
        assertEquals("SA", DEFAULT_COUNTRY_PACK.code)
        assertEquals(Currency.SAR, DEFAULT_COUNTRY_PACK.currency)
        assertEquals(SchemaId.entries.toSet(), DEFAULT_COUNTRY_PACK.statementSchemas.toSet())
    }

    @Test
    fun `البلد المش مدعومة بترجع الافتراضية مش استثناء`() {
        assertSame(SAUDI_PACK, countryPack("sa"))
        assertSame(EGYPT_PACK, countryPack("eg"))
        assertSame(SAUDI_PACK, countryPack("XX"))
        assertSame(SAUDI_PACK, countryPack(null))
    }

    @Test
    fun `أي حزمة ناقصة لازم تقول ناقصها إيه`() {
        for (pack in COUNTRY_PACKS.values) {
            assertTrue(pack.categoryTreeAsset.isNotBlank(), "حزمة ${pack.code} من غير شجرة تصنيفات")
            assertTrue(pack.statementSchemas.isNotEmpty(), "حزمة ${pack.code} من غير مخطط كشوف")
            if (!pack.ready) assertTrue(pack.gaps.isNotEmpty())
        }
        // مصر: قارئ الرسايل خلص، والشجرة وقارئ الكشف لسه
        assertEquals(listOf("categoryTree", "statementReader"), EGYPT_PACK.gaps)
    }

    @Test
    fun `قارئ الرسايل في الحزمة هو نفسه القارئ الحالي بالحرف`() {
        val reader = assertNotNull(SAUDI_PACK.smsReader)
        val message = BankSmsMessage(
            sender = "AlRajhiBank",
            receivedAt = "2026-09-04T10:15:00.000Z",
            body = "شراء بطاقة\nمن: جاري *1234\nبمبلغ: SAR 96.47\nلدى: بقالة\nفي: 26/09/04",
        )
        assertEquals(parseBankSms(message, 1), reader.parse(message, 1))
    }
}
