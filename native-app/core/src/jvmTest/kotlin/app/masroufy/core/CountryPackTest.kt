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
        // كل المخططات ما عدا كشف QNB مصر (حزمة مصر بس)
        assertEquals(SchemaId.entries.toSet() - SchemaId.QNB_PDF, DEFAULT_COUNTRY_PACK.statementSchemas.toSet())
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
        // قارئ كشف QNB اتعمل 2026-10-01، والشجرة = شجرة السعودية + فروق مصر (رد المالك §64-٣) — مفيش ناقص في الحزمة
        assertEquals(emptyList(), EGYPT_PACK.gaps)
        assertEquals(EGYPT_CATEGORY_DELTA, EGYPT_PACK.categoryDelta)
        assertTrue(SAUDI_PACK.categoryDelta.isEmpty(), "السعودية من غير فروق")
        assertTrue(SchemaId.QNB_PDF in EGYPT_PACK.statementSchemas)
        assertEquals("Africa/Cairo", EGYPT_PACK.timeZone, "حدود اليوم في رسايل البنك بتوقيت مصر")
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
