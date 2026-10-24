package app.masroufy.data

import app.masroufy.core.ALERT_INBOX_GROUP
import app.masroufy.core.redactSms

/**
 * PRIVACY-3 — شكل السطر اللي **الاسترجاع** بيكتبه في المجموعات المحمية (فيها نص بنك):
 * - العمليات ومصادرها ودفعات الاستيراد: السطر بيتقرا بالمحوّل ويتكتب تاني بيه (= نفس اللي التطبيق بيكتبه عادي) ⇒ أي مفتاح مش
 *   معروف بيتشال (قايمة المفاتيح المسموحة = اللي المحوّل بيكتبه)، وبعدها [storeForm] على كل النصوص، و[redactSms] على نصوص البنك.
 * - صفحة الإشعارات: العنوان والتفاصيل بيتقصوا (زي ما المحوّل بيعمل).
 * - باقي المجموعات زي ما هي (مفيهاش نص بنك).
 * المحوّل لو رفض السطر بيرمي — الاسترجاع بيقف قبل ما يكتب حاجة غلط.
 */
private val RESTORE_CODECS: Map<String, DocCodec<*>> = listOf(LedgerCodecs.transactions, LedgerCodecs.sourceRecords, LedgerCodecs.importBatches).associateBy { it.group }

/** نصوص البنك الخام — بتتحجب بـ[redactSms] (أرقام مقسّمة بمسافات وشرط · IBAN · تليفونات) مش القص العادي بس. */
private val BANK_TEXT_FIELDS = setOf("accountIdentity", "rawLine", "rawDescription", "rawMerchantName")

fun restoreForm(group: String, row: Doc): Doc {
    // المفتاح نفسه (معرّف المستند متشفّر منه) ما بيتلمسش — النص اللي بيتعرض بس
    if (group == ALERT_INBOX_GROUP) return row.mapValues { (k, v) -> if (v is String && (k == "title" || k == "body")) sanitizeAccountNumbers(v) else v }
    @Suppress("UNCHECKED_CAST")
    val codec = RESTORE_CODECS[group] as DocCodec<Any?>? ?: return row
    val canonical = storeForm(group, codec.encode(codec.decode(row)))
    return canonical.mapValues { (k, v) -> if (v is String && k in BANK_TEXT_FIELDS) redactSms(v) else v }
}
