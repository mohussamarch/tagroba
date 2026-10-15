package app.masroufy.core

/**
 * «معايا كذا دلوقتي» — تحديث الرصيد (قايمة المالك 2026-10-15 البنود 4 و5): المالك بيكتب **رصيده الحقيقي** لمحفظة (بنك · كاش · أي محفظة)
 * دلوقتي أو في يوم فات، والفرق بين المكتوب والمحسوب بيتسجّل **«فرق مش معروف»** يتراجع بعدين.
 *
 * **النقطة بتثبّت الرصيد في لحظتها** (مش مبلغ ثابت): الرصيد بعدها = [BalanceCheckpoint.statedMinor] + الحركات اللي بعدها. والفرق نفسه
 * بيتحسب كل مرة = المكتوب − المحسوب قبلها مباشرة. ليه؟ عشان أي حاجة **أقدم** توصل متأخر (رسالة بنك ليوم فات · كشف · عملية اتسجلت
 * بإيد) **ما تكسرش الرصيد اللي المالك أكده** — بتدخل في الفرق (بتشرحه أو بتكبّره)، والفرق اللي اتغيّر عن وقت التسجيل بيبان للمراجعة
 * ([CheckpointEffect.lateMinor]). ده الاختيار المحافظ (المالك يقدر يغيّره — سؤال مفتوح في التقرير).
 *
 * **الترتيب جوه اليوم** (العمليات تاريخها يوم من غير ساعة):
 * - يوم قبل يوم النقطة ⇒ قبلها · يوم بعده ⇒ بعدها.
 * - نفس اليوم: النقطة من غير ساعة ([BalanceCheckpoint.atInstant] null = «آخر اليوم») ⇒ كل عمليات اليوم قبلها. النقطة بساعة («دلوقتي») ⇒ العملية
 *   اللي **اتسجلت** (`createdAt`) قبل الساعة دي أو معاها قبلها، واللي اتسجلت بعدها بعدها (القهوة اللي اتسجلت بعد ما حدّثت الكاش بتنقص منه).
 *   `createdAt` مش مقروء (بيانات قديمة) ⇒ قبلها.
 * - الجزء اللي المالك فصله من الفرق لتصنيف حقيقي ([Transaction.explainsCheckpointId]) ⇒ قبل نقطته مباشرة، فبيشرح الفرق ومش بيغيّر الرصيد.
 *
 * الفرق **مش مصروف ولا دخل** (برّه مجاميع الشهر والميزانيات والمساعد) — النقطة مش عملية أصلًا. بيتعرض لوحده ويتراجع.
 * المبالغ هللات صحيحة. النقطة قبل يوم بداية المحفظة ما بتتحسبش (التطبيق ما يعرفش رصيدها قبله).
 */
data class BalanceCheckpoint(
    val id: Id,
    /** يوم النقطة بتوقيت البلد (`CountryTime`). */
    val day: IsoDate,
    /** اللحظة اللي الرصيد كان صح فيها (ISO جرينتش) — «دلوقتي» = وقت التسجيل. null = آخر اليوم ده (يوم فات من غير ساعة). */
    val atInstant: String?,
    /** الرصيد اللي المالك كتبه. */
    val statedMinor: Halalas,
    /** الفرق وقت التسجيل (المكتوب − المحسوب، بإشارته) — عشان «اتغيّر من ساعة ما سجّلته» يبان. */
    val recordedDiffMinor: Halalas,
    val createdAt: String,
    /** المالك راجع الفرق وقال «سيبه» وهو بالقيمة دي — لو اتغيّر بعدها بيرجع للمراجعة. null = لسه ما اتراجعش. */
    val keptMinor: Halalas? = null,
)

/**
 * أثر نقطة على رصيد محفظتها دلوقتي. [adjustmentMinor] = «فرق مش معروف» اللي النقطة بتضيفه (موجب = معاك أكتر من المحسوب، سالب = أقل).
 * [explainedMinor] = مجموع الأجزاء اللي المالك فصلها منه (بإشارة أثرها: الصادر سالب). [lateMinor] = التغيير من وقت التسجيل اللي **مش** من
 * الأجزاء دي (حاجة أقدم وصلت متأخر · عملية قديمة اتعدلت أو اتمسحت · رصيد البداية اتغيّر).
 */
data class CheckpointEffect(
    val walletId: Id,
    val checkpoint: BalanceCheckpoint,
    /** الرصيد المحسوب قبل النقطة مباشرة (البداية + الحركات قبلها + فروق النقط اللي قبلها). */
    val beforeMinor: Halalas,
    val adjustmentMinor: Halalas,
    val explainedMinor: Halalas,
    val lateMinor: Halalas,
) {
    /** محتاج مراجعة: فيه فرق، والمالك ما قالش «سيبه» على القيمة دي بالظبط. */
    val needsReview: Boolean get() = adjustmentMinor != 0L && checkpoint.keptMinor != adjustmentMinor
}

private const val END_OF_DAY = Long.MAX_VALUE

/** لحظة النقطة جوه يومها — من غير ساعة (أو ساعة مش مقروءة) ⇒ آخر اليوم. */
private fun slotOf(cp: BalanceCheckpoint): Long = cp.atInstant?.let { JsText.parseIsoMillis(it) } ?: END_OF_DAY

/** ترتيب النقط: اليوم ⇒ اللحظة ⇒ وقت التسجيل ⇒ المعرّف (ثابت دايمًا). */
val CHECKPOINT_ORDER: Comparator<BalanceCheckpoint> = compareBy({ it.day }, { slotOf(it) }, { it.createdAt }, { it.id })

/** أثر العملية على رصيد المحفظة [walletId]: الطرف الداخل للتحويل الداخلي وارد دايمًا · المحفظة نفسها بالاتجاه · غير كده null. */
fun walletDeltaOf(t: Transaction, walletId: Id): Halalas? = when {
    t.transferToWalletId == walletId -> t.amountMinor
    t.walletId == walletId -> if (t.observedDirection == Direction.OUT) -t.amountMinor else t.amountMinor
    else -> null
}

/** النقط اللي بتتحسب للمحفظة (من يوم بدايتها) بالترتيب. */
fun activeCheckpoints(wallet: Wallet): List<BalanceCheckpoint> = wallet.checkpoints.filter { it.day >= wallet.openingAt }.sortedWith(CHECKPOINT_ORDER)

/** العملية [t] قبل النقطة رقم [index] في [ordered]؟ (القاعدة في أول الملف.) */
private fun isBefore(t: Transaction, ordered: List<BalanceCheckpoint>, index: Int, rank: Map<Id, Int>): Boolean {
    val cp = ordered[index]
    if (t.occurredAt != cp.day) return t.occurredAt < cp.day
    val own = t.explainsCheckpointId?.let { rank[it] }
    if (own != null && ordered[own].day == t.occurredAt) return own <= index
    val slot = slotOf(cp)
    if (slot == END_OF_DAY) return true
    val recorded = JsText.parseIsoMillis(t.createdAt) ?: return true
    return recorded <= slot
}

/**
 * أثر كل نقطة على المحفظة [wallet] بالترتيب. [transactions] لازم تبقى **كل** عمليات المحفظة من يوم بدايتها لحد آخر نقطة على الأقل
 * (زي أي حساب رصيد — `walletBalancesOn`).
 */
fun checkpointEffects(wallet: Wallet, transactions: List<Transaction>): List<CheckpointEffect> {
    val ordered = activeCheckpoints(wallet)
    if (ordered.isEmpty()) return emptyList()
    val rank = ordered.withIndex().associate { (i, cp) -> cp.id to i }
    val moves = transactions.mapNotNull { t -> if (t.occurredAt < wallet.openingAt) null else walletDeltaOf(t, wallet.id)?.let { t to it } }
    val out = ArrayList<CheckpointEffect>(ordered.size)
    var earlier = 0L
    for ((index, cp) in ordered.withIndex()) {
        var before = addMoney(wallet.openingBalanceMinor, earlier)
        var explained = 0L
        for ((t, delta) in moves) {
            if (isBefore(t, ordered, index, rank)) before = addMoney(before, delta)
            if (t.explainsCheckpointId == cp.id) explained = addMoney(explained, delta)
        }
        val adjustment = subtractMoney(cp.statedMinor, before)
        val late = subtractMoney(adjustment, subtractMoney(cp.recordedDiffMinor, explained))
        out += CheckpointEffect(wallet.id, cp, before, adjustment, explained, late)
        earlier = addMoney(earlier, adjustment)
    }
    return out
}

/** خطوة في سلسلة رصيد محفظة: عملية، أو نقطة «حدّث الرصيد» بفرقها. */
sealed interface LedgerStep {
    data class Txn(val transaction: Transaction) : LedgerStep

    data class Checkpoint(val effect: CheckpointEffect) : LedgerStep
}

/**
 * العمليات [ordered] (مترتبة باليوم ثم ترتيب المصدر — زي المطابقة) ومعاها نقط [wallet] في مكانها: كل نقطة بعد العمليات اللي قبلها (القاعدة في
 * أول الملف) وقبل اللي بعدها. ترتيب العمليات جوه كل جزء ما بيتغيّرش.
 */
fun withCheckpointSteps(wallet: Wallet, ordered: List<Transaction>): List<LedgerStep> {
    val effects = checkpointEffects(wallet, ordered)
    if (effects.isEmpty()) return ordered.map { LedgerStep.Txn(it) }
    val cps = effects.map { it.checkpoint }
    val rank = cps.withIndex().associate { (i, cp) -> cp.id to i }
    // الجزء = أول نقطة العملية قبلها (أو بعد آخر نقطة)
    val keyed = ordered.withIndex().map { (seq, t) ->
        val bucket = cps.indices.firstOrNull { isBefore(t, cps, it, rank) } ?: cps.size
        Triple(Pair(t.occurredAt, bucket * 2 + 1), seq, LedgerStep.Txn(t) as LedgerStep)
    } + effects.withIndex().map { (k, e) -> Triple(Pair(e.checkpoint.day, (k + 1) * 2), -1, LedgerStep.Checkpoint(e) as LedgerStep) }
    return keyed.sortedWith(compareBy({ it.first.first }, { it.first.second }, { it.second })).map { it.third }
}

/**
 * **للشرح «افهم رصيدك»** (فرع `r04-balance-why`): فروق النقط كمصدر لوحده («تعديلات» — مش عمليات) في الفترة من [from] لـ[to] (الاتنين داخلين،
 * null = من غير حد). مجموع [CheckpointEffect.adjustmentMinor] = اللي النقط زوّدته أو نقّصته من الرصيد في الفترة.
 */
fun balanceAdjustments(wallet: Wallet, transactions: List<Transaction>, from: IsoDate? = null, to: IsoDate? = null): List<CheckpointEffect> =
    checkpointEffects(wallet, transactions).filter { (from == null || it.checkpoint.day >= from) && (to == null || it.checkpoint.day <= to) }

/** مجموع فروق النقط في الفترة (بإشارتها) — صفر لو مفيش نقط. */
fun balanceAdjustmentsMinor(wallet: Wallet, transactions: List<Transaction>, from: IsoDate? = null, to: IsoDate? = null): Halalas =
    sumMoney(balanceAdjustments(wallet, transactions, from, to).map { it.adjustmentMinor })

/**
 * الرصيد المحسوب في لحظة نقطة جديدة **قبل** ما تتسجل (اللي هيتقارن بالمكتوب): نفس الحساب بنقطة تجريبية في مكانها، فالنقط اللي قبلها
 * داخلة بفروقها. null لو اليوم قبل بداية المحفظة (الرصيد مش معروف).
 */
fun balanceAtCheckpoint(wallet: Wallet, transactions: List<Transaction>, probe: BalanceCheckpoint): Halalas? {
    if (probe.day < wallet.openingAt) return null
    val trial = wallet.copy(checkpoints = wallet.checkpoints.filter { it.id != probe.id } + probe)
    return checkpointEffects(trial, transactions).firstOrNull { it.checkpoint.id == probe.id }?.beforeMinor
}
