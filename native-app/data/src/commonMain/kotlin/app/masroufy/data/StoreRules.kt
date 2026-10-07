package app.masroufy.data

/**
 * قواعد الكتابة اللي التطبيق الحالي بيطبقها **قبل** ما المستند يوصل فايربيز — نفسها بالحرف، عشان التطبيقين يكتبوا نفس الشكل.
 */

/**
 * قص أرقام الحسابات (OVERRIDES §2، CLAUDE.md #11): أي 5 أرقام ورا بعض أو أكتر ⇒ `****` + آخر 4.
 * نقل `sanitizeAccountNumbers` (`firestoreRepositories.ts`) — والأرقام العربية والفارسية بتتعد أرقام برضه.
 */
private val LONG_DIGITS = Regex("[0-9٠-٩۰-۹]{5,}")

fun sanitizeAccountNumbers(text: String): String = LONG_DIGITS.replace(text) { "****" + it.value.takeLast(4) }

/** المجموعات اللي التطبيق الحالي بيقص فيها الأرقام: العمليات ومصادرها ودفعات الاستيراد (`sanitize`). */
private val SANITIZED_GROUPS = setOf("transactions", "sourceRecords", "importBatches")

/** المعرّفات ما بتتقصش — `id` وأي حقل آخره `Id` (`isIdentifierField`). */
private fun isIdentifierField(key: String): Boolean = key == "id" || key.endsWith("Id")

/** الشكل اللي بيتكتب فعلًا: النصوص الحرة **في المستوى الأول بس** بتتقص (زي `sanitize` — الحقول المتداخلة أعداد أصلًا). */
fun storeForm(group: String, doc: Doc): Doc {
    if (group !in SANITIZED_GROUPS) return doc
    return doc.mapValues { (key, value) -> if (value is String && !isIdentifierField(key)) sanitizeAccountNumbers(value) else value }
}

/**
 * `encodeURIComponent` بتاعة جافاسكربت: الحروف دي بس بتفضل زي ما هي، وأي حاجة تانية بايتات UTF-8 بـ`%XX` حروف كبيرة.
 */
private const val URI_UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()"

fun jsEncodeUriComponent(text: String): String = buildString {
    for (byte in text.encodeToByteArray()) {
        val c = (byte.toInt() and 0xFF)
        if (c < 0x80 && URI_UNRESERVED.indexOf(c.toChar()) >= 0) {
            append(c.toChar())
        } else {
            append('%')
            append("0123456789ABCDEF"[c shr 4])
            append("0123456789ABCDEF"[c and 0x0F])
        }
    }
}

/** معرّف مستند إيصال التنبيه: المفتاح متشفّر لأن فيه `|` و`:`، والنقطة كمان (`docIdOf` في `notificationRepository.ts`). */
fun receiptDocId(eventKey: String): String = jsEncodeUriComponent(eventKey).replace(".", "%2E")
