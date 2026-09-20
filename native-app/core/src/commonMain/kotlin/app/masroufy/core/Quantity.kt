package app.masroufy.core

import kotlin.math.abs

/**
 * الكميات — نقل `src/domain/quantity.ts` (spec/02: تمثيل عشري دقيق للكميات).
 * الكمية عدد صحيح بمقياس 100,000,000 (ثمانية أرقام عشرية — الساتوشي).
 * الضرب كمية × سعر ممكن يعدّي 64 بت، فبيتعمل بـ128 بت (`Wide`) ويتقرّب **مرة واحدة** (نص لفوق بعيد عن الصفر).
 */
typealias Quantity = Long

const val QUANTITY_SCALE = 100_000_000L
const val QUANTITY_DECIMALS = 8

class QuantityError(message: String) : IllegalArgumentException(message)

fun assertQuantity(value: Long, context: String = uiText(TextKey.QUANTITY_CONTEXT_DEFAULT)) {
    if (value > MAX_SAFE_HALALAS || value < -MAX_SAFE_HALALAS) throw QuantityError(uiText(TextKey.QUANTITY_OUT_OF_RANGE_CONTEXT, context))
}

/** بيقرا كمية مكتوبة (أرقام عربية وفواصل)، وبيرفض أكتر من 8 أرقام عشرية **بدل ما يقرّب بصمت**. */
fun parseQuantity(raw: String): Quantity {
    var text = JsText.trim(normalizeDigits(raw)).filterNot { JsText.isWhitespace(it) || it == ',' || it.code == 0x066C }
    if (text.isEmpty()) throw QuantityError(uiText(TextKey.QUANTITY_EMPTY))
    var negative = false
    if (text.startsWith('-')) {
        negative = true
        text = text.substring(1)
    } else if (text.startsWith('+')) {
        text = text.substring(1)
    }
    val parts = text.split('.', '٫')
    if (parts.size > 2) throw QuantityError(uiText(TextKey.QUANTITY_TWO_POINTS))
    val intPart = parts[0].ifEmpty { "0" }
    val fracRaw = parts.getOrElse(1) { "" }
    val shown = JsText.trim(raw)
    if (!intPart.all(JsText::isAsciiDigit) || !fracRaw.all(JsText::isAsciiDigit)) throw QuantityError(uiText(TextKey.QUANTITY_INVALID, shown))
    if (fracRaw.length > QUANTITY_DECIMALS) {
        throw QuantityError(uiText(TextKey.QUANTITY_TOO_MANY_DECIMALS, QUANTITY_DECIMALS.toString()))
    }
    val digits = (intPart + fracRaw.padEnd(QUANTITY_DECIMALS, '0')).trimStart('0')
    // نفس رسايل جافاسكربت: رقم بحجم ما ينفعش ⇒ «مش رقم صالح»، وأكبر من الحد الآمن ⇒ «أكبر من الحد»
    if (digits.length > 309) throw QuantityError(uiText(TextKey.QUANTITY_NOT_A_NUMBER, shown))
    if (digits.length > 16 || (digits.isNotEmpty() && digits.toLong() > MAX_SAFE_HALALAS)) throw QuantityError(uiText(TextKey.QUANTITY_OUT_OF_RANGE, shown))
    val value = if (digits.isEmpty()) 0L else digits.toLong()
    return if (negative) -value else value
}

/** من غير أصفار زيادة على اليمين — للعرض بس. */
fun formatQuantity(quantity: Quantity): String {
    assertQuantity(quantity)
    val digits = abs(quantity).toString().padStart(QUANTITY_DECIMALS + 1, '0')
    val intPart = digits.substring(0, digits.length - QUANTITY_DECIMALS)
    val frac = digits.substring(digits.length - QUANTITY_DECIMALS).trimEnd('0')
    val body = if (frac.isNotEmpty()) "$intPart.$frac" else intPart
    return if (quantity < 0) "-$body" else body
}

fun addQuantity(vararg values: Quantity): Quantity {
    var total = 0L
    for (v in values) {
        assertQuantity(v)
        total += v
    }
    assertQuantity(total, uiText(TextKey.QUANTITY_CONTEXT_TOTAL))
    return total
}

fun subtractQuantity(a: Quantity, b: Quantity): Quantity {
    assertQuantity(a)
    assertQuantity(b)
    return a - b
}

/** حساب 128 بت من غير مكتبة — للضرب اللي ممكن يعدّي 64 بت ثم قسمة بتقريب واحد. */
private object Wide {
    /** a × b لعددين موجبين أقل من 2^63 ⇒ (الأعلى، الأدنى). */
    fun multiply(a: Long, b: Long): Pair<ULong, ULong> {
        val a0 = a.toULong() and 0xFFFFFFFFu
        val a1 = a.toULong() shr 32
        val b0 = b.toULong() and 0xFFFFFFFFu
        val b1 = b.toULong() shr 32
        val p00 = a0 * b0
        val p01 = a0 * b1
        val p10 = a1 * b0
        val p11 = a1 * b1
        val middle = (p00 shr 32) + (p01 and 0xFFFFFFFFu) + (p10 and 0xFFFFFFFFu)
        val lo = (middle shl 32) or (p00 and 0xFFFFFFFFu)
        val hi = p11 + (p01 shr 32) + (p10 shr 32) + (middle shr 32)
        return hi to lo
    }

    /** (2n + d) ÷ (2d) = n÷d مقرّبة نص لفوق. null لو الناتج أكبر من 64 بت. */
    fun divideRounded(hi0: ULong, lo0: ULong, d: ULong): ULong? {
        // 2n + d
        var hi = (hi0 shl 1) or (lo0 shr 63)
        var lo = lo0 shl 1
        val sum = lo + d
        if (sum < lo) hi += 1u
        lo = sum
        val divisor = d shl 1
        var remainder = 0uL
        var qHi = 0uL
        var qLo = 0uL
        for (i in 127 downTo 0) {
            val bit = if (i >= 64) (hi shr (i - 64)) and 1u else (lo shr i) and 1u
            remainder = (remainder shl 1) or bit
            if (remainder >= divisor) {
                remainder -= divisor
                if (i >= 64) qHi = qHi or (1uL shl (i - 64)) else qLo = qLo or (1uL shl i)
            }
        }
        return if (qHi != 0uL) null else qLo
    }
}

/** قسمة عددين بتقريب نص لفوق بعيد عن الصفر **مرة واحدة** — الحد الوحيد للتقريب في الاستثمار. */
private fun divideRounded(a: Long, b: Long, denominator: Long): Long {
    if (denominator == 0L) throw QuantityError(uiText(TextKey.QUANTITY_DIVIDE_BY_ZERO))
    val negative = ((a < 0) != (b < 0) && a != 0L && b != 0L) != (denominator < 0)
    val (hi, lo) = Wide.multiply(abs(a), abs(b))
    val q = Wide.divideRounded(hi, lo, abs(denominator).toULong())
    if (q == null || q > MAX_SAFE_HALALAS.toULong()) throw QuantityError(uiText(TextKey.QUANTITY_RESULT_OUT_OF_RANGE))
    val result = q.toLong()
    return if (negative) -result else result
}

/** قيمة كمية بسعر الوحدة = الكمية × السعر ÷ المقياس (السعر بالهللة للوحدة الكاملة). */
fun valueOfQuantity(quantity: Quantity, pricePerUnitMinor: Halalas): Halalas {
    assertQuantity(quantity)
    return divideRounded(quantity, pricePerUnitMinor, QUANTITY_SCALE)
}

/** حصة من مبلغ بنسبة كمية لكمية — تكلفة الجزء المباع. المتبقي بيتحسب بالطرح (مفيش هللة بتضيع). */
fun shareOfAmount(amountMinor: Halalas, partQuantity: Quantity, wholeQuantity: Quantity): Halalas {
    assertQuantity(partQuantity, uiText(TextKey.QUANTITY_CONTEXT_PART))
    assertQuantity(wholeQuantity, uiText(TextKey.QUANTITY_CONTEXT_WHOLE))
    if (wholeQuantity <= 0) throw QuantityError(uiText(TextKey.QUANTITY_WHOLE_POSITIVE))
    if (partQuantity < 0) throw QuantityError(uiText(TextKey.QUANTITY_PART_NEGATIVE))
    if (partQuantity > wholeQuantity) {
        throw QuantityError(uiText(TextKey.QUANTITY_PART_OVER_WHOLE, formatQuantity(partQuantity), formatQuantity(wholeQuantity)))
    }
    if (partQuantity == wholeQuantity) return amountMinor
    return divideRounded(amountMinor, partQuantity, wholeQuantity)
}

/** سعر الوحدة المستنتج — للعرض والمقارنة بس، مش أساس تكلفة. */
fun unitPriceOf(amountMinor: Halalas, quantity: Quantity): Halalas? {
    assertQuantity(quantity)
    if (quantity <= 0) return null
    return divideRounded(amountMinor, QUANTITY_SCALE, quantity)
}
