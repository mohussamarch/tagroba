package app.masroufy.core

/**
 * حاسبة الورث (OVERRIDES §69) — حساب الكسور بالظبط (القاعدة #1: ممنوع floating point).
 *
 * الأنصبة **كسور صحيحة** ([Frac]: بسط ومقام Long، دايمًا مختصرة والمقام موجب). كل ضرب/جمع متفحوص من الطفح (overflow) —
 * لو حصل بيرمي [InheritanceOverflow] والحاسبة بترجّع «الأرقام أكبر من المسموح» بدل رقم غلط.
 * تحويل الكسر لهللات في `InheritanceAllocation.kt` (أكبر باقي — largest remainder) عشان المجموع يطلع بالظبط.
 */
class InheritanceOverflow : ArithmeticException("inheritance arithmetic overflow")

internal fun gcdOf(a: Long, b: Long): Long {
    var x = if (a < 0) -a else a
    var y = if (b < 0) -b else b
    while (y != 0L) {
        val t = x % y
        x = y
        y = t
    }
    return x
}

internal fun mulChecked(a: Long, b: Long): Long {
    if (a == 0L || b == 0L) return 0L
    if (a == Long.MIN_VALUE || b == Long.MIN_VALUE) throw InheritanceOverflow()
    val r = a * b
    if (r / b != a) throw InheritanceOverflow()
    return r
}

internal fun addChecked(a: Long, b: Long): Long {
    val r = a + b
    // طفح لو الاتنين نفس الإشارة والنتيجة إشارتها اتقلبت
    if ((a xor r) and (b xor r) < 0) throw InheritanceOverflow()
    return r
}

internal fun lcmOf(a: Long, b: Long): Long = if (a == 0L || b == 0L) 0L else mulChecked(a / gcdOf(a, b), b)

/** كسر مختصر بالظبط. المقام موجب دايمًا. */
class Frac private constructor(val num: Long, val den: Long) : Comparable<Frac> {
    companion object {
        val ZERO = Frac(0, 1)
        val ONE = Frac(1, 1)

        fun of(num: Long, den: Long = 1): Frac {
            require(den != 0L) { "denominator 0" }
            if (num == 0L) return ZERO
            val g = gcdOf(num, den)
            val sign = if (den < 0) -1 else 1
            return Frac(sign * (num / g), sign * (den / g))
        }
    }

    operator fun plus(o: Frac): Frac {
        val g = gcdOf(den, o.den)
        val d = mulChecked(den / g, o.den)
        return of(addChecked(mulChecked(num, o.den / g), mulChecked(o.num, den / g)), d)
    }

    operator fun minus(o: Frac): Frac = this + Frac(-o.num, o.den)

    operator fun times(o: Frac): Frac {
        // اختصار قبل الضرب يقلل فرصة الطفح
        val g1 = gcdOf(num, o.den).coerceAtLeast(1)
        val g2 = gcdOf(o.num, den).coerceAtLeast(1)
        return of(mulChecked(num / g1, o.num / g2), mulChecked(den / g2, o.den / g1))
    }

    operator fun times(n: Int): Frac = this * of(n.toLong())

    operator fun div(o: Frac): Frac {
        require(o.num != 0L) { "division by zero" }
        return this * of(o.den, o.num)
    }

    operator fun div(n: Int): Frac = this / of(n.toLong())

    val isZero: Boolean get() = num == 0L
    val isPositive: Boolean get() = num > 0L

    override fun compareTo(other: Frac): Int {
        val l = mulChecked(num, other.den)
        val r = mulChecked(other.num, den)
        return l.compareTo(r)
    }

    override fun equals(other: Any?): Boolean = other is Frac && other.num == num && other.den == den
    override fun hashCode(): Int = 31 * num.hashCode() + den.hashCode()
    override fun toString(): String = if (den == 1L) "$num" else "$num/$den"
}

fun minFrac(a: Frac, b: Frac): Frac = if (a <= b) a else b

fun Iterable<Frac>.sumFrac(): Frac = fold(Frac.ZERO) { acc, f -> acc + f }

/**
 * (a × b) ÷ d بالظبط: القسمة الصحيحة والباقي، من غير طفح في الضرب (حاصل ضرب 128 بت).
 * a وb موجبين أو صفر، d موجب. القسمة لازم تدخل في Long (وده مضمون لما b ≤ d).
 */
internal fun mulDivRem(a: Long, b: Long, d: Long): Pair<Long, Long> {
    require(a >= 0 && b >= 0 && d > 0)
    val mask = 0xFFFF_FFFFuL
    val ua = a.toULong()
    val ub = b.toULong()
    val a0 = ua and mask
    val a1 = ua shr 32
    val b0 = ub and mask
    val b1 = ub shr 32
    val p00 = a0 * b0
    val p01 = a0 * b1
    val p10 = a1 * b0
    val p11 = a1 * b1
    val mid = (p00 shr 32) + (p01 and mask) + (p10 and mask)
    val lo = (p00 and mask) or (mid shl 32)
    val hi = p11 + (p01 shr 32) + (p10 shr 32) + (mid shr 32)
    val ud = d.toULong()
    var rem = 0uL
    var qHi = 0uL
    var qLo = 0uL
    for (i in 127 downTo 0) {
        val bit = if (i >= 64) (hi shr (i - 64)) and 1uL else (lo shr i) and 1uL
        rem = (rem shl 1) or bit
        if (rem >= ud) {
            rem -= ud
            if (i >= 64) qHi = qHi or (1uL shl (i - 64)) else qLo = qLo or (1uL shl i)
        }
    }
    if (qHi != 0uL || qLo > Long.MAX_VALUE.toULong()) throw InheritanceOverflow()
    return qLo.toLong() to rem.toLong()
}
