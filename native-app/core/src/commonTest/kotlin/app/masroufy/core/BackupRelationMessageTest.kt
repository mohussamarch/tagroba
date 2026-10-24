package app.masroufy.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * r06-fix055b: النسخة الشاملة لما تاجر بيشاور على تصنيف ممسوح كانت بتقول «علاقة ناقصة: merchants.verifiedCategoryId» بحروف لاتيني.
 * دلوقتي الرسالة عربي (أسامي المجموعات من `BACKUP_LABELS`) في الفصحى والمصري. البيانات مخترعة.
 */
class BackupRelationMessageTest {
    @AfterTest fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val data = mapOf(
        "categories" to emptyList<Map<String, Any?>>(),
        "merchants" to listOf(mapOf("id" to "m-qa", "displayName" to "تاجر تجربة", "normalizedName" to "تاجر تجربة", "verifiedCategoryId" to "c-gone")),
    )

    private fun messageIn(variant: ArabicVariant): String {
        Texts.language = Language.AR
        Texts.arabicVariant = variant
        return assertFailsWith<BackupError> { checkFullBackupData(data, groups = listOf("categories", "merchants")) }.message.orEmpty()
    }

    @Test fun brokenRelationIsArabicInBothDialects() {
        for (variant in listOf(ArabicVariant.MSA, ArabicVariant.EGYPTIAN)) {
            val msg = messageIn(variant)
            assertTrue(msg.none { it in 'A'..'Z' || it in 'a'..'z' }, "$variant: فيه حروف لاتيني: $msg")
            assertTrue(BACKUP_LABELS.getValue("merchants") in msg && BACKUP_LABELS.getValue("categories") in msg, "$variant: $msg")
        }
        assertTrue(messageIn(ArabicVariant.MSA) != messageIn(ArabicVariant.EGYPTIAN), "النسختين نفس الكلام")
    }
}
