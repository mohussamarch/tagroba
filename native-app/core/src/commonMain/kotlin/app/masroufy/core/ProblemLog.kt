package app.masroufy.core

/**
 * «سجل المشاكل» (OVERRIDES §90 — اختيار المالك «الاتنين»): سطر لكل مشكلة حصلت **من غير ما البرنامج يقع** (أو وقعة، قبل تقرير الأعطال).
 * **مفيش أي نص من بيانات المستخدم في السطر — بالنوع نفسه:** الكود من قايمة ثابتة ([ProblemCode]) · نوع الخطأ قايمة ثابتة ([ErrorKind]) ·
 * تفاصيل أرقام بس بمفاتيح ثابتة ([ProblemDetail]). الوقت والنسخة والبلد من البرنامج نفسه، وبيتنضفوا تاني وقت الكتابة ([ProblemLogFormat]).
 * ممنوع: مبالغ · أسامي ناس أو تجار · نص رسايل · رموز دخول · رسالة الخطأ الأصلية (ممكن يبقى فيها نص رسالة بنك).
 */
enum class ProblemLevel { INFO, WARN, ERROR }

enum class ProblemArea(val wire: String) { SYNC("sync"), SMS("sms"), IMPORT("import"), BACKUP("backup"), UI("ui"), OTHER("other") }

/** القايمة الثابتة: لكل كود منطقته ودرجته ونصه المقروء في صفحة «سجل المشاكل» (فصحى · مصري · إنجليزي). */
enum class ProblemCode(val area: ProblemArea, val level: ProblemLevel, val label: TextRef) {
    SYNC_LISTEN_FAILED(ProblemArea.SYNC, ProblemLevel.WARN, UiKey.PLOG_C_SYNC_LISTEN),
    SYNC_WRITE_FAILED(ProblemArea.SYNC, ProblemLevel.ERROR, UiKey.PLOG_C_SYNC_WRITE),
    SMS_PERMISSION_LOST(ProblemArea.SMS, ProblemLevel.WARN, UiKey.PLOG_C_SMS_PERMISSION),
    SMS_READ_FAILED(ProblemArea.SMS, ProblemLevel.ERROR, UiKey.PLOG_C_SMS_READ),
    SMS_RECEIVE_FAILED(ProblemArea.SMS, ProblemLevel.ERROR, UiKey.PLOG_C_SMS_RECEIVE),
    SMS_AUTO_RECORD_FAILED(ProblemArea.SMS, ProblemLevel.ERROR, UiKey.PLOG_C_SMS_AUTO),
    BACKGROUND_FAILED(ProblemArea.OTHER, ProblemLevel.ERROR, UiKey.PLOG_C_BACKGROUND),
    BACKGROUND_GAVE_UP(ProblemArea.OTHER, ProblemLevel.WARN, R06Key.PLOG_C_BACKGROUND_GAVE_UP),
    ALERTS_FAILED(ProblemArea.OTHER, ProblemLevel.WARN, UiKey.PLOG_C_ALERTS),
    IMPORT_PDF_UNREADABLE(ProblemArea.IMPORT, ProblemLevel.WARN, UiKey.PLOG_C_PDF_UNREADABLE),
    IMPORT_PDF_LOCKED(ProblemArea.IMPORT, ProblemLevel.INFO, UiKey.PLOG_C_PDF_LOCKED),
    IMPORT_CSV_UNREADABLE(ProblemArea.IMPORT, ProblemLevel.WARN, UiKey.PLOG_C_CSV_UNREADABLE),
    IMPORT_PREVIEW_FAILED(ProblemArea.IMPORT, ProblemLevel.ERROR, UiKey.PLOG_C_IMPORT_PREVIEW),
    IMPORT_SAVE_FAILED(ProblemArea.IMPORT, ProblemLevel.ERROR, UiKey.PLOG_C_IMPORT_SAVE),
    BACKUP_CREATE_FAILED(ProblemArea.BACKUP, ProblemLevel.ERROR, UiKey.PLOG_C_BACKUP_CREATE),
    BACKUP_FILE_SAVE_FAILED(ProblemArea.BACKUP, ProblemLevel.ERROR, UiKey.PLOG_C_BACKUP_SAVE),
    BACKUP_FILE_UNREADABLE(ProblemArea.BACKUP, ProblemLevel.WARN, UiKey.PLOG_C_BACKUP_UNREADABLE),
    BACKUP_RESTORE_FAILED(ProblemArea.BACKUP, ProblemLevel.ERROR, UiKey.PLOG_C_BACKUP_RESTORE),
    BACKUP_DEVICE_COPY_FAILED(ProblemArea.BACKUP, ProblemLevel.ERROR, UiKey.PLOG_C_BACKUP_DEVICE),
    CRASH_MAIN(ProblemArea.UI, ProblemLevel.ERROR, UiKey.PLOG_C_CRASH_MAIN),
    CRASH_BACKGROUND(ProblemArea.OTHER, ProblemLevel.ERROR, UiKey.PLOG_C_CRASH_BACKGROUND),
}

/** نوع الخطأ — من **اسم نوعه** بس ([errorKindOf])، عمر رسالته ما بتتقري. */
enum class ErrorKind { NETWORK, PERMISSION, TIMEOUT, SECURITY, MEMORY, PARSE, IO, FIREBASE, NULL_VALUE, INDEX, ARGUMENT, STATE, OTHER }

/** تفاصيل رقمية بمفاتيح ثابتة (مثلًا رقم المحاولة) — ولا نص. */
enum class ProblemDetail(val wire: String) { COUNT("count"), ATTEMPT("attempt") }

data class ProblemEntry(
    /** وقت الجهاز ISO بفرق التوقيت (`2026-10-15T04:20:03+03:00`). */
    val at: String,
    val code: ProblemCode,
    val kind: ErrorKind? = null,
    val details: Map<ProblemDetail, Long> = emptyMap(),
    val appVersion: String = "",
    /** بلد الحساب الشغال (`SA` · `EG`) — null قبل الدخول. */
    val country: String? = null,
) {
    val level: ProblemLevel get() = code.level
    val area: ProblemArea get() = code.area
}

/** آخر كام سطر بيتحفظوا (حلقة — الأقدم بيتشال). */
const val PROBLEM_LOG_LIMIT = 300

/**
 * نوع الخطأ من اسم نوعه (`::class.simpleName`) — من غير `message` خالص. الترتيب مهم: «إذن» قبل «حالة» (`SmsPermissionError` نوعه
 * `IllegalStateException`).
 */
fun errorKindOf(error: Throwable?): ErrorKind? {
    if (error == null) return null
    val name = error::class.simpleName.orEmpty()
    return when {
        "Security" in name -> ErrorKind.SECURITY
        "Permission" in name -> ErrorKind.PERMISSION
        "Timeout" in name -> ErrorKind.TIMEOUT
        "UnknownHost" in name || "Socket" in name || "Connect" in name || "Network" in name -> ErrorKind.NETWORK
        "OutOfMemory" in name -> ErrorKind.MEMORY
        "NumberFormat" in name || "Parse" in name || "Serialization" in name || "Json" in name || "PdfRead" in name -> ErrorKind.PARSE
        "IOException" in name || "FileNotFound" in name || "EOF" in name -> ErrorKind.IO
        "Firebase" in name || "Firestore" in name -> ErrorKind.FIREBASE
        "NullPointer" in name -> ErrorKind.NULL_VALUE
        "IndexOutOfBounds" in name || "NoSuchElement" in name -> ErrorKind.INDEX
        "IllegalArgument" in name -> ErrorKind.ARGUMENT
        "IllegalState" in name -> ErrorKind.STATE
        else -> ErrorKind.OTHER
    }
}

/**
 * شكل السطر (الملف على الجوال والنص اللي بيتبعت): `الوقت|الدرجة|المنطقة|الكود|kind=…|count=…|v=النسخة|c=البلد`.
 * كل حقل بيتنضف وقت الكتابة: الوقت لازم ISO · النسخة حروف وأرقام و`.-_` بس (32 بالكتير) · البلد حرفين كبار بس — أي حاجة تانية ⇒ `-`.
 */
object ProblemLogFormat {
    private val ISO = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,9})?)?(Z|[+-]\d{2}:\d{2})""")
    private val VERSION_CHAR = Regex("[A-Za-z0-9._-]")
    private val COUNTRY = Regex("[A-Z]{2}")

    fun safeTime(at: String): String = if (ISO.matches(at)) at else "-"

    /** رقم طويل (7 خانات أو أكتر — جوال/حساب/كارت) ⇒ `-` كله: النسخة الحقيقية (`kotlin-0.3-fast`) عمرها ما فيها كده. */
    fun safeVersion(version: String): String =
        version.filter { VERSION_CHAR.matches(it.toString()) }.take(32).takeIf { it.isNotEmpty() && !LONG_NUMBER.containsMatchIn(it) } ?: "-"

    private val LONG_NUMBER = Regex("""\d{7,}""")

    fun safeCountry(country: String?): String = country?.uppercase()?.takeIf { COUNTRY.matches(it) } ?: "-"

    fun line(e: ProblemEntry): String = buildList {
        add(safeTime(e.at))
        add(e.level.name)
        add(e.area.wire)
        add(e.code.name)
        e.kind?.let { add("kind=${it.name}") }
        for ((key, value) in e.details.entries.sortedBy { it.key.ordinal }) add("${key.wire}=$value")
        add("v=${safeVersion(e.appVersion)}")
        add("c=${safeCountry(e.country)}")
    }.joinToString("|")

    /** سطر من الملف ⇒ سطر (أو null لو مش مفهوم — كود اتشال في نسخة أحدث مثلًا). */
    fun parse(text: String): ProblemEntry? {
        val parts = text.trim().split('|')
        if (parts.size < 4) return null
        val code = ProblemCode.entries.firstOrNull { it.name == parts[3] } ?: return null
        val at = safeTime(parts[0])
        var kind: ErrorKind? = null
        val details = mutableMapOf<ProblemDetail, Long>()
        var version = ""
        var country: String? = null
        for (field in parts.drop(4)) {
            val key = field.substringBefore('=')
            val value = field.substringAfter('=', "")
            when (key) {
                "kind" -> kind = ErrorKind.entries.firstOrNull { it.name == value }
                "v" -> version = safeVersion(value)
                "c" -> country = value.takeIf { it != "-" }?.let(::safeCountry)?.takeIf { it != "-" }
                else -> ProblemDetail.entries.firstOrNull { it.wire == key }?.let { d -> value.toLongOrNull()?.let { details[d] = it } }
            }
        }
        return ProblemEntry(at, code, kind, details, version, country)
    }

    /** النص اللي بيتبعت بـ«ابعته»: رأس فيه وقت التصدير والنسخة والعدد، وبعده السطور من الأقدم للأحدث. */
    fun export(entries: List<ProblemEntry>, exportedAt: String, appVersion: String): String = buildString {
        append("Masroufy problem log\n")
        append("exported=${safeTime(exportedAt)} v=${safeVersion(appVersion)} entries=${entries.size}\n")
        append("---\n")
        for (e in entries) append(line(e)).append('\n')
    }
}
