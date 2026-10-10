package app.masroufy.core

/**
 * حواجز الصراحة في فهم المساعد (المراجعة العدائية 2026-10-10 على فرع `assistant-engine`): الفهم **ما بيكمّلش بثقة** لو الكلام فيه حاجة
 * القواعد مش فاهماها — بدل ما يرمي الكلمة ويجاوب بالمجموع العام أو بالشهر الحالي (القاعدة 10: لا رقم بلا مصدر).
 *  - **فلوس مش مصروف** (دخل · استرجاع · سلفة · تحويل · سحب) ⇒ ما فيش كارت مصروف، رد صريح بالمكان الصح.
 *  - **«ما تسجلش …»** ⇒ ولا كارت.
 *  - **سؤال عن المستقبل أو «لو …»** ⇒ «غير متاح» (ولا رقم لحاجة ما حصلتش).
 *  - **اسم مش معروف** بعد «على/في/عند» («كم صرفت على البنزين» وما فيش تصنيف بالاسم ده) ⇒ «مش فاهم» (والسؤال بيتحفظ للتعلّم).
 *  - **فترة مش معروفة** («في رمضان» · «آخر سنتين») ⇒ «مش فاهم» بدل الشهر الحالي في صمت.
 *  - **سؤال سعر** («كم سعر الذهب») ⇒ «مش فاهم» — المساعد ما عندوش مصدر أسعار.
 */
private val INCOME_VERBS = vocab(
    "جاني", "جالي", "جاتني", "جاتلي", "استلمت", "استلمنا", "قبضت", "قبضنا", "نزلي", "نزل لي", "وصلني", "وصلتني", "اتحولي", "اتحول لي", "حولولي",
    "ربحت", "كسبت", "received", "got paid", "earned", fuzzy = false,
)
private val INCOME_NOUNS = vocab("راتب", "الراتب", "مرتب", "المرتب", "معاش", "بونص", "مكافاه", "عيديه", "salary", "bonus", "paycheck", fuzzy = false, exact = true)
private val REFUND_WORDS = vocab(
    "رجعلي", "رجع لي", "رجعولي", "رجعوا لي", "رجعتلي", "استرجعت", "استرجاع", "استرداد", "استرديت", "مرتجع", "رد لي", "ردولي", "كاش باك", "كاشباك",
    "refund", "refunded", "cashback", "cash back", fuzzy = false,
)
private val LOAN_WORDS = vocab(
    "سلفت", "اسلفت", "قرضت", "اقرضت", "استلفت", "اتسلفت", "تسلفت", "اقترضت", "استدنت", "دينت", "lent", "loaned", "borrowed", fuzzy = false,
)
private val ON_BEHALF = vocab("دفعت عن", "دفعت بداله", "دفعت بدالها", "دفعت مكان", "on behalf", "paid for him", "paid for her", fuzzy = false)

/** «دفعت عن طريق البطاقة» = وسيلة الدفع، مش «دفعت عن حد». */
private val VIA = vocab("عن طريق", fuzzy = false)

/** سداد لشخص («سددت لأحمد» · «رجعت لخالد») — دين بس لو فيه شخص في الكلام («سددت فاتورة الكهرباء» مصروف). */
private val REPAY_WORDS = vocab("سددت", "رجعت", "paid back", "repaid", fuzzy = false)
private val CASH_MOVE_WORDS = vocab("سحبت", "سحب", "صراف", "الصراف", "اودعت", "ايداع", "atm", "withdrew", "withdrawal", "withdraw", "deposited", "deposit", fuzzy = false)
private val TRANSFER_WORDS = vocab("حولت", "تحويل", "حواله", "transferred", "transfer", fuzzy = false)

/** فعل شراء/دفع صريح ⇒ مصروف حتى لو فيه كلمة دخل أو استرجاع («استلمت الطلب ودفعت 50»). */
private val PURCHASE_VERBS = vocab("دفعت", "اشتريت", "شريت", "شاريت", "bought", "paid", "purchased", fuzzy = false, exact = true)

private val DONT_RECORD = vocab(
    "ما تسجلش", "متسجلش", "ماتسجلش", "لا تسجل", "ما تسجل", "بلاش تسجل", "بلاش تسجيل", "ما تضيفش", "متضيفش", "لا تضف", "لا تضيف", "ما تحفظش", "لا تحفظ",
    "don't record", "dont record", "do not record", "don't add", "dont add", "do not add", "don't save", "do not save", fuzzy = false,
)

/** نوع الفلوس لو مش مصروف (null = مصروف عادي). الرسوم دايمًا مصروف («رسوم تحويل 5» · «رسوم سحب»). */
internal fun notExpenseNote(s: AssistSignals): AssistNote? {
    if (s.has(AssistWords.FEE)) return null
    val person = s.entities.any { it.type == AssistEntityType.PERSON }
    if (s.has(LOAN_WORDS) || (s.has(ON_BEHALF) && !s.has(VIA)) || (person && s.has(REPAY_WORDS))) return AssistNote.NOT_EXPENSE_DEBT
    if (s.has(PURCHASE_VERBS)) return null
    return when {
        s.has(REFUND_WORDS) -> AssistNote.NOT_EXPENSE_REFUND
        s.has(CASH_MOVE_WORDS) -> AssistNote.NOT_EXPENSE_CASH_MOVE
        s.has(TRANSFER_WORDS) -> AssistNote.NOT_EXPENSE_TRANSFER
        s.has(INCOME_VERBS) || s.has(INCOME_NOUNS) -> AssistNote.NOT_EXPENSE_IN
        else -> null
    }
}

internal fun saysDontRecord(s: AssistSignals): Boolean = s.has(DONT_RECORD)

// ─── كروت الدخل والسلفة والتحويل بالكتابة (رد المالك §79.2-7) ───

/** السلفة: إنت اللي سلّفت (بتظهر في «لك») ولا اللي استلفت (في «عليك»). */
enum class LoanSide { LENT, BORROWED }

private val LENT_WORDS = vocab("سلفت", "اسلفت", "قرضت", "اقرضت", "lent", "loaned", fuzzy = false)
private val BORROWED_WORDS = vocab("استلفت", "اتسلفت", "تسلفت", "اقترضت", "استدنت", "borrowed", fuzzy = false)

/** «سلفت أحمد ٢٠٠» ⇒ [LoanSide.LENT] · «استلفت من خالد» ⇒ [LoanSide.BORROWED] · «دفعت عن …» و«سددت لـ…» ⇒ null (رد صريح زي الأول). */
fun loanSide(s: AssistSignals): LoanSide? = when {
    s.has(BORROWED_WORDS) -> LoanSide.BORROWED
    s.has(LENT_WORDS) -> LoanSide.LENT
    else -> null
}

/** السحب من الصراف (من البنك للكاش) ولا الإيداع (من الكاش للبنك). */
enum class CashMove { WITHDRAW, DEPOSIT }

private val DEPOSIT_WORDS = vocab("اودعت", "ايداع", "deposited", "deposit", fuzzy = false)

fun cashMoveOf(s: AssistSignals): CashMove = if (s.has(DEPOSIT_WORDS)) CashMove.DEPOSIT else CashMove.WITHDRAW

private val BONUS_WORDS = vocab("بونص", "مكافاه", "مكافأه", "bonus", fuzzy = false, exact = true)
private val GIFT_WORDS = vocab("عيديه", "هديه", "gift", fuzzy = false, exact = true)

/**
 * نوع الدخل اللي كارت «قبضت …» بيسجّله: راتب/مرتب/معاش ⇒ راتب · بونص/مكافأة ⇒ مكافأة · عيدية/هدية ⇒ هدية · غير كده ⇒ «عمل حر»
 * (اختيار Claude — المالك يقدر يغيّره: الدخل اللي من غير اسم ما يتحسبش راتب عشان ما يلخبطش يوم الراتب).
 */
fun incomeKindOf(s: AssistSignals): EconomicKind = when {
    s.has(BONUS_WORDS) -> EconomicKind.BONUS
    s.has(GIFT_WORDS) -> EconomicKind.GIFT_RECEIVED
    s.has(SALARY_WORDS) -> EconomicKind.SALARY
    else -> EconomicKind.FREELANCE
}

private val SALARY_WORDS = vocab("راتب", "الراتب", "مرتب", "المرتب", "معاش", "salary", "paycheck", fuzzy = false, exact = true)

/** كلمة «من» قبل اسم المحفظة («من الكاش للبنك») — المحفظة اللي الفلوس طالعة منها. */
private val FROM_WORDS = setOf("من", "from")

/**
 * المحفظتين في «حولت ٥٠٠ من الكاش للبنك» بالترتيب (من ⇒ إلى): المحفظة اللي قبلها «من» هي المصدر، والتانية الوجهة. أقل من محفظتين مختلفتين ⇒
 * null. كلمة عامة («البنك») لأكتر من حساب ⇒ أول واحد في ترتيبك (الأساسية لو منهم — بيختارها المستدعي).
 */
fun movePair(s: AssistSignals): Pair<AssistEntity, AssistEntity>? {
    val byPlace = s.entities.ofType(AssistEntityType.WALLET).groupBy { it.start }.toSortedMap().values.map { at -> at.firstOrNull { !it.generic } ?: at.first() }
    if (byPlace.size < 2 || byPlace.map { it.id }.distinct().size < 2) return null
    val from = byPlace.firstOrNull { e -> s.tokens.getOrNull(e.start - 1)?.let { t -> cliticForms(t).any { it in FROM_WORDS } } == true } ?: byPlace.first()
    val to = byPlace.first { it.id != from.id }
    return from to to
}

private val FUTURE_PERIOD = vocab(
    "الشهر الجاي", "الشهر القادم", "الشهر المقبل", "الشهر الياي", "الشهر اللي جاي", "السنه الجايه", "السنه القادمه", "السنه المقبله", "السنه اللي جايه",
    "العام القادم", "العام المقبل", "الاسبوع الجاي", "الاسبوع القادم", "الاسبوع المقبل", "بكره", "بكرا", "بعد بكره", "next month", "next year", "next week",
    "tomorrow", fuzzy = false,
)
private val FUTURE_SPEND = vocab("هصرف", "حصرف", "ساصرف", "سوف اصرف", "راح اصرف", "رح اصرف", "will i spend", "will spend", "going to spend", fuzzy = false)
private val IF_WORDS = setOf("لو", "اذا", "if", "لنفترض", "افرض", "افترض", "suppose")
private val POLITE_AFTER_IF = setOf("سمحت", "سمحتي", "سمحتو", "تكرمت", "تكرمتي", "ممكن", "امكن", "you")
private val PRICE_WORDS = vocab("سعر", "اسعار", "price", "prices", fuzzy = false)

/** «لو صرفت …» · «إذا …» — مش «لو سمحت». */
private fun hypothetical(s: AssistSignals): Boolean =
    s.tokens.withIndex().any { (i, t) -> t in IF_WORDS && s.tokens.getOrNull(i + 1)?.let { it !in POLITE_AFTER_IF } == true }

/** أسئلة عن اللي حصل (أو الشهر الجاري) — المستقبل والافتراض فيها «غير متاح». المواعيد الجاية (راتب · فواتير · أقساط) مش منهم. */
private val PAST_DATA = setOf(
    AssistIntent.SPEND_TOTAL, AssistIntent.SPEND_CATEGORY, AssistIntent.SPEND_MERCHANT, AssistIntent.SPEND_PERSON, AssistIntent.SPEND_BIGGEST,
    AssistIntent.SPEND_COMPARE, AssistIntent.INCOME, AssistIntent.REMAINING, AssistIntent.DAILY_ALLOWANCE, AssistIntent.FORECAST,
    AssistIntent.BUDGET_STATUS, AssistIntent.CATEGORY_BUDGET, AssistIntent.CASH_ON_HAND, AssistIntent.ON_HAND, AssistIntent.LAST_AT_MERCHANT,
    AssistIntent.EVENT_SPEND, AssistIntent.PROJECT_SPEND,
)

/** أسئلة بفترة (الافتراضي = الشهر الحالي) — الفترة اللي ما اتفهمتش ما تتحوّلش للشهر الحالي في صمت. */
private val PERIOD_DATA = setOf(
    AssistIntent.SPEND_TOTAL, AssistIntent.SPEND_CATEGORY, AssistIntent.SPEND_MERCHANT, AssistIntent.SPEND_PERSON, AssistIntent.SPEND_BIGGEST, AssistIntent.INCOME,
)

/** كلمات فترة لو جات من غير ما الفترة تتفهم ⇒ الفترة مش معروفة («آخر سنتين» · «في رمضان» · «الصيف»). */
private val PERIOD_HINTS = setOf(
    "شهرين", "شهور", "اشهر", "اسبوعين", "اسابيع", "سنتين", "سنين", "سنوات", "ايام", "يومين", "رمضان", "الصيف", "صيف", "الشتا", "الشتاء", "الاجازه", "اجازه",
    "months", "weeks", "years", "days", "ramadan", "summer", "winter", "vacation", "holiday",
).map(::assistNormalize).toSet()

/** حرف الجر قبل اسم الموضوع: «على» للصرف وكمان «من» للدخل («دخلي من الإيجار»). */
private val SPEND_PREPS = setOf("علي", "في", "عند", "on", "at", "in", "for")
private val INCOME_PREPS = SPEND_PREPS + setOf("من", "from")

/** كلمات بعد حرف الجر ومش اسم موضوع («في الشهر» · «على إيه» · «في المجمل» · «on this»). */
private val NOT_A_SUBJECT = setOf(
    "الشهر", "شهر", "الشهور", "الاسبوع", "اسبوع", "السنه", "سنه", "العام", "اليوم", "يوم", "الايام", "النهارده", "امبارح", "امس", "اخر", "اول", "خلال",
    "هذا", "هذه", "هذي", "هاذا", "ده", "دي", "دا", "الفتره", "فتره", "بدايه", "نهايه", "كل", "كله", "كلها", "ايه", "وش", "ايش", "شو", "كده", "كذا", "المجموع",
    "مجموع", "المجمل", "الاجمالي", "اجمالي", "الاجمال", "حاجه", "حاجات", "الحاجات", "نفسي", "مصاريف", "مصروفات", "المصاريف", "المصروفات", "الحساب", "حسابي",
    "this", "that", "last", "past", "the", "my", "total", "all", "a", "month", "week", "year", "day", "today", "general",
).map(::assistNormalize).toSet()

/**
 * أول كلمة بعد «على/في/عند» (أو «عالـ…») مش متفهمة اسم ولا فترة ولا رقم — يعني الموضوع اللي اتسأل عنه مش معروف. [named] = الاسم المعروف
 * بيعدّي (في الصرف)؛ في الدخل الإجابة ما بتفصلش بالمصدر ⇒ أي اسم بعد «من» = موضوع ما نقدرش نجاوبه.
 */
internal fun unknownSubjectWord(s: AssistSignals, preps: Set<String>, named: Boolean = true): String? {
    for ((i, t) in s.tokens.withIndex()) {
        val (at, word) = when {
            t in preps || (t.length > 1 && t[0] == 'و' && t.drop(1) in preps) -> (i + 1) to s.tokens.getOrNull(i + 1)
            t.startsWith("عال") && t.length > 4 -> i to t
            else -> continue
        }
        if (word == null || word.any { it.isDigit() }) continue
        if (named && s.entities.any { at in it.start until it.end }) continue
        if (cliticForms(word).any { it in NOT_A_SUBJECT } || detectAssistPeriod(word, listOf(word)) != null) continue
        return word
    }
    return null
}

/**
 * حاجز سؤال البيانات بعد ما القواعد اختارت النية: null = الإجابة تمشي. غير كده ⇒ «مش فاهم» بالملاحظة دي ([AssistNote.NO_FUTURE] = «غير
 * متاح» للمستقبل والافتراض، و null جوه = «مش فاهم» عادي).
 */
internal class DataBlock(val note: AssistNote?)

internal fun dataBlock(s: AssistSignals, intent: AssistIntent, subject: AssistEntity?): DataBlock? {
    if (intent !in PAST_DATA && intent != AssistIntent.ASSETS) return null
    // «كم هصرف …» عن الصرف نفسه = مستقبل؛ «فاضلي/أقدر أصرف كام» بطبيعتها عن باقي الشهر الجاري (مش محجوبة بالفعل لوحده)
    val futureVerb = s.has(FUTURE_SPEND) && intent in PERIOD_DATA
    if (intent in PAST_DATA && (s.has(FUTURE_PERIOD) || hypothetical(s) || futureVerb)) return DataBlock(AssistNote.NO_FUTURE)
    if (s.has(PRICE_WORDS)) return DataBlock(null)
    if (intent in PERIOD_DATA && s.period == null) {
        val hint = s.tokens.withIndex().any { (i, t) -> cliticForms(t).any { it in PERIOD_HINTS } && s.entities.none { i in it.start until it.end } }
        if (hint) return DataBlock(null)
    }
    val unknown = when {
        subject != null -> null
        intent == AssistIntent.SPEND_TOTAL -> unknownSubjectWord(s, SPEND_PREPS)
        intent == AssistIntent.INCOME -> unknownSubjectWord(s, INCOME_PREPS, named = false)
        else -> null
    }
    return if (unknown != null) DataBlock(null) else null
}
