package app.masroufy.device.smscoverage

import app.masroufy.core.SmsKind

/**
 * نموذج قياس تغطية أشكال رسايل البنوك (`research/banks (saudi|egypt)-sms-formats.json`) — الوصف الكامل في [SmsTemplateCoverageTest].
 *
 * لكل سطر في ملفات البحث: **إيه اللي الشكل ده معناه** ([Expect]) — عملية ولا لأ، اتجاهها، أنهي مبلغ هو مبلغ العملية،
 * وهل فيه محل أو طرف تاني لازم يتقري. القيم كلها **مخترعة** (المستودع عام): ولا اسم ولا رقم ولا محل من المصادر.
 */
internal enum class Dir { IN, OUT, ANY }

internal enum class Outcome(val wire: String, val severity: Int) {
    CORRECT("correct", 0),
    CORRECTLY_IGNORED("correctlyIgnored", 0),
    REJECTED("rejected", 1),
    WRONG("wrong", 2),
    WRONGLY_ACCEPTED("wronglyAccepted", 2),
}

/**
 * المتوقع من رسالة.
 * - [tx] = حركة فلوس لازم تتسجل (حتى لو محتاجة تأكيد بعدين — §72). `false` = رمز تحقق · عرض · مرفوض · حجز · طلب لسه ما اتنفذش · معلومة.
 * - [amountKey] = اسم الخانة اللي فيها مبلغ العملية (`amount` عادةً، و`total` لما الرسالة فيها رسوم وضريبة و«إجمالي المستحق»).
 * - [merchant] = الرسالة فيها `{merchant}` واسم المحل لازم يطلع زي ما هو.
 * - [partyKeys] = الخانات اللي بتعرّف الطرف التاني في التحويل (اسم أو أرقام). الطرف صح لو الاسم طلع، **أو** آخر 4 أرقام طلعوا
 *   (§39/§60: الاسم + آخر 4). الخانة اللي مش موجودة في نسخة الرسالة دي (جزء اختياري اتشال) ما بتتحسبش. فاضية = مفيش طرف ولازم ما يطلعش طرف.
 * - [foreign] = المبلغ بعملة أجنبية بس (من غير مقابل محلي) — قرار §75-12: يتسجل ويسأل عن المبلغ المحلي. **الصح** هنا = القارئ
 *   رفضها بسبب العملة **ومعاها** المبلغ الأجنبي والاتجاه والتاريخ ([app.masroufy.core.SmsForeignPending]) عشان السؤال يتعمل.
 * - [kind] = نوع العملية اللي لازم القارئ يعرفه (سحب صرّاف §75-4 · استرداد §75-6 · بين حساباتك §75-11) — null = ما بيتفحصش.
 */
internal data class Expect(
    val tx: Boolean,
    val dir: Dir = Dir.ANY,
    val amountKey: String = "amount",
    val merchant: Boolean = false,
    val partyKeys: List<String> = emptyList(),
    val foreign: Boolean = false,
    val what: String = "",
    val kind: SmsKind? = null,
)

/** رسالة مكتوبة باليد لسطر «كلمات بس» (مفيهوش رسالة كاملة) — بقوالب `{…}` زي ملفات البحث. */
internal data class CustomBody(val label: String, val template: String, val expect: Expect)

/** شكل التاريخ والساعة في رسايل البنك (اليوم المخترع ثابت: [Fill.TX_DATE] الساعة 14:22 بتوقيت البلد). */
internal enum class DateStyle(val date: String, val time: String) {
    YY_MM_DD_DASH("26-09-14", "14:22"),
    YY_M_D_SLASH("26/9/14", "14:22"),
    YY_MM_DD_BACKSLASH("26\\09\\14", "14:22"),
    DD_MM_YY_SLASH("14/09/26", "14:22"),
    D_M_YY("14/9/26", "14:22"),
    YYYY_MM_DD("2026-09-14", "14:22"),
    YYYY_MM_DD_SEC("2026-09-14", "14:22:05"),
    DD_MM_YYYY_SLASH("14/09/2026", "14:22"),
    DD_MM_YYYY_DASH("14-09-2026", "14:22"),
    DD_MM_YYYY_DASH_SEC("14-09-2026", "14:22:05"),
    DD_DOT_MM_DOT_YY("14.09.26", "14:22"),
    MM_DD("09-14", "14:22"),
    DD_MM("14/09", "14:22"),
    MON_D_YYYY("Sep 14, 2026", "2:22:05 PM"),
}

/**
 * سطر واحد: المتوقع + قيم خاصة بيه + شكل تاريخ خاص (لو مختلف عن باقي البنك) + تعديل للقالب قبل الملء
 * (مثلًا `{amount}` مكتوبة مرتين في القالب وهي مبلغين مختلفين) + رسايل مكتوبة باليد بدل القالب.
 */
internal data class RowSpec(
    val expect: Expect,
    val values: Map<String, String> = emptyMap(),
    val date: DateStyle? = null,
    val fix: ((String) -> String)? = null,
    val custom: List<CustomBody> = emptyList(),
    /** أشكال تاريخ زيادة بتتجرب كمان على نفس القالب (شكل تاريخ البنك لسه مفترض — مثلًا الإنماء بشَرطة بدل «/»). */
    val alsoDates: List<DateStyle> = emptyList(),
)

internal fun out(
    merchant: Boolean = false, party: List<String> = emptyList(), amountKey: String = "amount", foreign: Boolean = false,
    values: Map<String, String> = emptyMap(), date: DateStyle? = null, fix: ((String) -> String)? = null, kind: SmsKind? = null,
) = RowSpec(Expect(true, Dir.OUT, amountKey, merchant, party, foreign, kind = kind), values, date, fix)

internal fun inn(
    merchant: Boolean = false, party: List<String> = emptyList(), amountKey: String = "amount",
    values: Map<String, String> = emptyMap(), date: DateStyle? = null, kind: SmsKind? = null,
) = RowSpec(Expect(true, Dir.IN, amountKey, merchant, party, kind = kind), values, date)

/** حركة فلوس اتجاهها ملتبس حتى للإنسان (سداد بطاقة ائتمانية مثلًا) — أي اتجاه مقبول، المهم تتسجل. */
internal fun anyDir(values: Map<String, String> = emptyMap(), kind: SmsKind? = null) = RowSpec(Expect(true, Dir.ANY, kind = kind), values)

internal fun ignore(what: String, values: Map<String, String> = emptyMap(), date: DateStyle? = null) =
    RowSpec(Expect(false, what = what), values, date)

internal fun custom(vararg bodies: CustomBody, values: Map<String, String> = emptyMap(), date: DateStyle? = null) =
    RowSpec(bodies.first().expect, values, date, custom = bodies.toList())

/** نسخة واحدة من رسالة بعد ما اتحسبت: النص المملوء والمتوقع منها. */
internal data class Variant(val label: String, val body: String, val expect: Expect, val values: Map<String, String>)

/** نتيجة نسخة واحدة. */
internal data class VariantResult(
    val variant: Variant,
    val outcome: Outcome,
    val problems: List<String>,
    /** سبب الرفض أو «فلتر الأمان»، أو ملخص اللي اتقري لو اتقبلت. */
    val detail: String,
    /** النتيجة لو الرسالة اتقرت بطلب المستخدم (`ReadBankSms` — من غير فلتر الأمان). */
    val manualOutcome: Outcome,
    /** قارئ البلد التانية قبلها (الصندوق بيتقري بقارئ كل بلد — `AutoRecordSms`) ⇒ هتتسجل في البلدين. */
    val crossLane: String?,
    /** رسالة مش عملية اترفضت بالصدفة (مبلغ أو تاريخ أو اتجاه مش واضح) مش بحارس مقصود. */
    val fragileIgnore: Boolean,
    /**
     * القالب مفيهوش تاريخ والقارئ رفضه عشان كده بس ⇒ نفس الرسالة + سطر تاريخ: هتتقري صح ولا فيه غلط مستخبي ورا الرفض؟
     * (تشخيص بس — مش داخل في النتيجة.)
     */
    val latent: String? = null,
)

internal data class RowResult(
    val file: String,
    val index: Int,
    val bank: String,
    val type: String,
    val lang: String,
    val verified: Boolean?,
    val isTitle: Boolean,
    val keywordOnly: Boolean,
    val variants: List<VariantResult>,
) {
    /** أسوأ نسخة هي نتيجة السطر. */
    val outcome: Outcome get() = variants.maxBy { it.outcome.severity }.outcome

    val problems: List<String>
        get() = variants.filter { it.outcome == outcome && it.problems.isNotEmpty() }
            .map { v -> (if (variants.size > 1) "[${v.variant.label}] " else "") + v.problems.joinToString("; ") }
}
