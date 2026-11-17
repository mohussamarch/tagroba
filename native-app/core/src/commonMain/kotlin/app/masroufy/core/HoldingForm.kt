package app.masroufy.core

/**
 * لوحة «ضيف حاجة» — الخطوة التانية (مواصفة r05 §1.10 · §2.6): الخانات المكتوبة زي ما هي ⇒ [NewHolding] بأعداد صحيحة بس
 * («1234.5» ⇒ 123450 هللة · الجرامات والمساحة ⇒ `Quantity` بمقياس 10^8 · النسبة ⇒ نقاط أساس). مفيش أي كسر عائم (CLAUDE.md #1).
 */
enum class HoldingField { GRAMS, PRICE, MAKING, VALUE, AREA, PLACE, RENT, NAME, QTY, SHARE }

/** سبايك ولا مشغولات (المشغولات = «للبس» في وقائع الزكاة، وغرضها الافتراضي «شخصي» — Q2). */
enum class MetalForm { BARS, JEWEL }

/** عيارات الدهب في الملف (سطر لكل عيار) والافتراضي 21 · نقاوة الفضة والافتراضي 999. */
val GOLD_KARATS = listOf(24, 22, 21, 18)
val SILVER_PURITIES = listOf(999, 925)

/** اللي المستخدم كتبه واختاره. [karat] = العيار للدهب أو النقاوة للفضة. */
data class HoldingFormText(
    val type: HoldingType,
    val form: MetalForm = MetalForm.BARS,
    val karat: Int = if (type == HoldingType.SILVER) 999 else 21,
    val origin: HoldingOrigin = HoldingOrigin.BUY,
    val purpose: HoldingPurpose = type.defaultPurpose,
    val rented: Boolean = false,
    /** §120-14 «امتلكتها إمتى؟» — null = اتخطّى السؤال (من زمان). */
    val ownedSince: IsoDate? = null,
    val fields: Map<HoldingField, String> = emptyMap(),
) {
    fun with(field: HoldingField, value: String): HoldingFormText = copy(fields = fields + (field to value))
    fun text(field: HoldingField): String = fields[field].orEmpty()

    /** تغيير نوع المعدن بيرجّع الغرض للافتراضي (المشغولات ⇒ شخصي) — زي النموذج. */
    fun withForm(next: MetalForm): HoldingFormText =
        copy(form = next, purpose = if (next == MetalForm.JEWEL) HoldingPurpose.PERSONAL else type.defaultPurpose)
}

/** الخانة المطلوبة للنوع: الوزن للمعادن · العدد للأسهم · القيمة لأي حاجة تانية. */
fun requiredField(type: HoldingType): HoldingField = when {
    type.metal -> HoldingField.GRAMS
    type == HoldingType.STK -> HoldingField.QTY
    else -> HoldingField.VALUE
}

/** الخانات بالترتيب لكل نوع (النموذج): المصنعية للمشغولات بس، والإيجار لو «مؤجّرة» (مش للأرض). */
fun fieldsFor(type: HoldingType, form: MetalForm = MetalForm.BARS, rented: Boolean = false): List<HoldingField> = when {
    type.metal -> listOfNotNull(HoldingField.GRAMS, HoldingField.PRICE, HoldingField.MAKING.takeIf { form == MetalForm.JEWEL })
    type.property -> listOfNotNull(HoldingField.VALUE, HoldingField.AREA, HoldingField.PLACE,
        HoldingField.RENT.takeIf { rented && type != HoldingType.LAND })
    type == HoldingType.STK -> listOf(HoldingField.NAME, HoldingField.QTY, HoldingField.PRICE)
    type == HoldingType.CO -> listOf(HoldingField.NAME, HoldingField.SHARE, HoldingField.VALUE)
    else -> listOf(HoldingField.NAME, HoldingField.VALUE)
}

/** «مؤجّرة؟» للشقة والمحل بس. */
fun canBeRented(type: HoldingType): Boolean = type == HoldingType.FLAT || type == HoldingType.SHOP

/**
 * الحاجة الجديدة جاهزة لـ`AddHolding`. [quantity] الجرامات · عدد الأسهم · وحدة واحدة لغيرهم. [priceMinor] سعر الشرا كله (أو قيمتها يوم
 * ما وصلت) · [valueMinor] القيمة اللي المستخدم كتبها النهارده (العقار والشركة والباقي). null = مش مكتوب (مش صفر).
 */
data class NewHolding(
    val type: HoldingType,
    val name: String,
    val purpose: HoldingPurpose,
    val origin: HoldingOrigin,
    val quantity: Quantity,
    val priceMinor: Halalas? = null,
    val makingMinor: Halalas? = null,
    val karat: Int? = null,
    val fineness: Int? = null,
    val jewellery: Boolean = false,
    val valueMinor: Halalas? = null,
    val areaSqm: Quantity? = null,
    val place: String? = null,
    val monthlyRentMinor: Halalas? = null,
    val sharePercentBp: Int? = null,
    /** §120-14 «امتلكتها إمتى؟» (اختياري) — null = «من زمان» ([OWNED_LONG_AGO]). */
    val ownedSince: IsoDate? = null,
)

sealed interface HoldingFormCheck {
    data class Ok(val holding: NewHolding) : HoldingFormCheck

    /** الخانة المطلوبة فاضية أو مش رقم موجب ⇒ حد أحمر + رسالتها. */
    data class Missing(val field: HoldingField) : HoldingFormCheck

    /** خانة اختيارية مكتوب فيها حاجة مش رقم ⇒ «اكتب رقمًا صحيحًا». */
    data class Invalid(val field: HoldingField) : HoldingFormCheck
}

private class FormStop(val result: HoldingFormCheck) : RuntimeException()

/** [label] اسم النوع (لو الاسم فاضي — زي النموذج). */
fun checkHoldingForm(text: HoldingFormText, currency: Currency, label: String): HoldingFormCheck = try {
    HoldingFormCheck.Ok(build(text, currency, label))
} catch (stop: FormStop) {
    stop.result
}

private fun build(text: HoldingFormText, currency: Currency, label: String): NewHolding {
    val type = text.type
    val need = requiredField(type)
    val fields = fieldsFor(type, text.form, text.rented)
    fun raw(f: HoldingField): String? = text.text(f).trim().takeIf { it.isNotEmpty() && f in fields }
    fun money(f: HoldingField): Halalas? = raw(f)?.let { s ->
        val v = runCatching { parseMoney(s, currency) }.getOrNull()
        when {
            v != null && v > 0L -> v
            f == need -> throw FormStop(HoldingFormCheck.Missing(f))
            v == 0L -> null
            else -> throw FormStop(HoldingFormCheck.Invalid(f))
        }
    } ?: if (f == need) throw FormStop(HoldingFormCheck.Missing(f)) else null
    fun quantity(f: HoldingField): Quantity? = raw(f)?.let { s ->
        val v = runCatching { parseQuantity(s) }.getOrNull()
        when {
            v != null && v > 0L -> v
            f == need -> throw FormStop(HoldingFormCheck.Missing(f))
            else -> throw FormStop(HoldingFormCheck.Invalid(f))
        }
    } ?: if (f == need) throw FormStop(HoldingFormCheck.Missing(f)) else null
    val name = raw(HoldingField.NAME)?.let(JsText::trim) ?: label
    val built = when {
        type.metal -> NewHolding(
            type, name, text.purpose, text.origin, quantity = quantity(HoldingField.GRAMS)!!,
            priceMinor = money(HoldingField.PRICE), makingMinor = money(HoldingField.MAKING),
            karat = text.karat.takeIf { type == HoldingType.GOLD }, fineness = text.karat.takeIf { type == HoldingType.SILVER },
            jewellery = text.form == MetalForm.JEWEL,
        )
        type.property -> NewHolding(
            type, name, text.purpose, text.origin, quantity = QUANTITY_SCALE, valueMinor = money(HoldingField.VALUE),
            areaSqm = quantity(HoldingField.AREA), place = raw(HoldingField.PLACE)?.take(MAX_HOLDING_PLACE),
            monthlyRentMinor = money(HoldingField.RENT),
        )
        type == HoldingType.STK -> NewHolding(
            type, name, text.purpose, text.origin, quantity = quantity(HoldingField.QTY)!!, priceMinor = money(HoldingField.PRICE),
        )
        type == HoldingType.CO -> NewHolding(
            type, name, text.purpose, text.origin, quantity = QUANTITY_SCALE, valueMinor = money(HoldingField.VALUE),
            sharePercentBp = raw(HoldingField.SHARE)?.let { s ->
                // «12.5» ⇒ 1250 نقطة أساس — نفس قراية المبلغ بخانتين عشريتين
                runCatching { parseMoney(s, currency) }.getOrNull()?.takeIf { it in 0L..10_000L }?.toInt()
                    ?: throw FormStop(HoldingFormCheck.Invalid(HoldingField.SHARE))
            },
        )
        else -> NewHolding(type, name, text.purpose, text.origin, quantity = QUANTITY_SCALE, valueMinor = money(HoldingField.VALUE))
    }
    return built.copy(ownedSince = text.ownedSince)
}

/**
 * §120-14 (قرار المالك — مراجعة todo-assets-2): الحاجة اللي عندك من غير ما تقول «امتلكتها إمتى؟» بتتسجل من زمان ⇒ أي تاريخ بيع بيتقبل.
 * تاريخ ثابت قديم بدل «النهارده» (اللي كان بيرفض بيعة امبارح). الشاشات بتعرضه «من زمان» ([isOwnedLongAgo]) مش السنة.
 */
const val OWNED_LONG_AGO: IsoDate = "1900-01-01"

fun isOwnedLongAgo(date: IsoDate?): Boolean = date != null && date.take(10) == OWNED_LONG_AGO

/** تاريخ أول دفعة للحاجة: اللي اتكتب (مش بعد [today]) أو [OWNED_LONG_AGO]. */
fun openingLotDate(ownedSince: IsoDate?, today: IsoDate): IsoDate = ownedSince?.take(10)?.takeIf { it <= today } ?: OWNED_LONG_AGO

/** §121-6: حاجة من «حاجاتي» اتضافت قبل خانة «امتلكتها إمتى؟» (أول دفعة اتسجلت بيوم الإضافة مش بيوم الامتلاك). */
val Asset.isLegacyHolding: Boolean get() = holdingType != null && ownedSinceAsked != true

/**
 * §121-6 (قرار المالك: «تتعلّم كلها من زمان») — قراية من غير ما نكتب في المستند: أول دفعة (الأقدم) للحاجة القديمة لو مش مربوطة
 * بعملية بتتقري [OWNED_LONG_AGO] ⇒ أي تاريخ بيع بيتقبل. الدفعات اللي بعدها والأصول اللي مش من «حاجاتي» زي ما هي.
 */
fun withLegacyOwnedLongAgo(assets: List<Asset>, lots: List<AssetLot>): List<AssetLot> {
    val legacy = assets.filter { it.isLegacyHolding }.map { it.id }.toSet()
    if (legacy.isEmpty()) return lots
    val opening = lots.filter { it.assetId in legacy }.groupBy { it.assetId }
        .mapNotNull { (_, mine) -> mine.minByOrNull { it.purchasedAt }?.takeIf { it.transactionId == null && !isOwnedLongAgo(it.purchasedAt) }?.id }
        .toSet()
    if (opening.isEmpty()) return lots
    return lots.map { if (it.id in opening) it.copy(purchasedAt = OWNED_LONG_AGO) else it }
}
