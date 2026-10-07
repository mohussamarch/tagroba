package app.masroufy.data

/**
 * محوّل مجموعة: الكيان ⇄ المستند، ومعرّف المستند. الشكل مطابق للي التطبيق الحالي بيكتبه (`documents.json`).
 * [encode] بيطلّع الكيان زي ما هو؛ القص بتاع أرقام الحسابات بيحصل وقت الكتابة في [storeForm] — زي التطبيق الحالي بالظبط.
 */
interface DocCodec<T> {
    /** اسم المجموعة تحت `users/{uid}/` (أو تحت المساحة — OVERRIDES §41). */
    val group: String

    fun id(value: T): String

    fun encode(value: T): Doc

    fun decode(doc: Doc): T
}

fun <T> codec(group: String, id: (T) -> String, encode: (T) -> Doc, decode: (DocReader) -> T): DocCodec<T> = object : DocCodec<T> {
    override val group = group

    override fun id(value: T) = id(value)

    override fun encode(value: T) = encode(value)

    override fun decode(doc: Doc) = decode(DocReader(group, doc))
}

/** المستند اللي بيتكتب فعلًا (بعد القص) — ده اللي المستودع يبعته لفايربيز. */
fun <T> DocCodec<T>.toStore(value: T): Doc = storeForm(group, encode(value))

/** الحقول الاختيارية الفاضية في [value] — اللي المستودع لازم يمسحها لو كانت موجودة قبل كده. */
fun <T> DocCodec<T>.omittedFields(value: T): Set<String> = encode(value).omittedFields

/**
 * نفس المحوّل، بس المستند اللي ما يتقريش ([DocumentError]) بيرجع `null` بدل ما يوقف القراية كلها — للمجموعات اللي **بتتولد تاني**
 * ومالهاش فلوس (صفحة الإشعارات · إيصالاتها · إعداداتها — §61): جوال بنسخة أقدم من التطبيق بيشوف سطر بنوع تنبيه جديد ما يعرفوش
 * ⇒ بيتخطاه بدل ما الصفحة كلها تقع. **مش للبيانات المالية** — هناك المستند الغلط لازم يبان (اختيار Claude، جلسة 18).
 */
fun <T : Any> DocCodec<T>.skippingUnreadable(): DocCodec<T?> {
    val base = this
    return object : DocCodec<T?> {
        override val group = base.group

        override fun id(value: T?) = base.id(value!!)

        override fun encode(value: T?) = base.encode(value!!)

        override fun decode(doc: Doc): T? = try {
            base.decode(doc)
        } catch (e: DocumentError) {
            null
        }
    }
}
