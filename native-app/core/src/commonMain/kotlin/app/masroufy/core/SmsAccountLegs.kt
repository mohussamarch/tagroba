package app.masroufy.core

/**
 * sms-fix (HANDOVER §6.5-3 — السبب اللي خلّى رصيد البنك غلط): **اتجاه التحويل ومحفظته من سطور «من:/الى:/لـ» في الرسالة نفسها، مش من اتجاه
 * ثابت للشكل.** «حوالة بين حساباتك» كانت متثبتة صادر (`SmsKnownShapesSaudi.kt` · `SmsSaudiTitles.kt`)، فالتحويل من حساب المالك التاني لحسابه
 * ده اتسجل خارج بدل داخل؛ والرسالة الواردة لحسابه التاني راحت لمحفظة البنك الوحيدة لأن المحفظة مالهاش آخر 4 أرقام.
 *
 * القاعدة (لكل أشكال التحويل — [LEG_KINDS] — السعودية ومصر): [own] = آخر 4 أرقام كل محافظ المالك في البلد.
 * - «الى:» محفظة من محافظه و«من:» محفظة تانية من محافظه ⇒ [SmsLegVerdict.Between]: تحويل داخلي برجلين (خارج من دي وداخل في دي).
 * - «الى:» محفظة من محافظه ⇒ [SmsLegVerdict.Into] (داخل) · «من:» محفظة من محافظه ⇒ [SmsLegVerdict.OutOf] (خارج).
 * - ولا رقم منهم محفظة من محافظه ⇒ [SmsLegVerdict.Unknown]: الرسالة تستنى بسؤال «الحساب ده بتاعك؟» — **عمرها ما تتحط في محفظة البنك الوحيدة**.
 * - مفيش سطور «من/الى» خالص ⇒ [SmsLegVerdict.Keep] (السلوك القديم).
 * وضع التعلّم (§77-A) ما بيغيرش ده: تأكيد مثال من الشكل بيسمح بالتسجيل التلقائي بس، وأرقام كل رسالة هي اللي بتقرر.
 */
sealed interface SmsLegVerdict {
    data object Keep : SmsLegVerdict

    data class Into(val last4: String) : SmsLegVerdict

    data class OutOf(val last4: String) : SmsLegVerdict

    data class Between(val fromLast4: String, val toLast4: String) : SmsLegVerdict

    data class Unknown(val last4: String) : SmsLegVerdict
}

/** أشكال التحويل اللي اتجاهها بيتقرر من سطور الحساب (مش من اتجاه الشكل). */
val LEG_KINDS: Set<SmsKind> = setOf(SmsKind.OWN_TRANSFER, SmsKind.TRANSFER_IN, SmsKind.TRANSFER_OUT)

fun smsLegVerdict(row: SmsRow, own: Set<String>): SmsLegVerdict {
    if (row.kind !in LEG_KINDS) return SmsLegVerdict.Keep
    val legs = smsLegsOf(row.raw)
    val from = legs.fromLast4
    val to = legs.toLast4
    if (from == null && to == null) return SmsLegVerdict.Keep
    val fromOwn = from != null && from in own
    val toOwn = to != null && to in own
    return when {
        // مراجعة r04 (الشكوى ٥): «الى:» فيها اسم ⇒ مش رِجلين — الطرف ممكن يكون شخص تاني أرقامه زي محفظتك بالصدفة، فبيتسأل (`OwnAccountAskSource`)
        fromOwn && toOwn && from != to && !legs.toNamed -> SmsLegVerdict.Between(from!!, to!!)
        toOwn && !fromOwn -> SmsLegVerdict.Into(to!!)
        fromOwn && !toOwn -> SmsLegVerdict.OutOf(from!!)
        fromOwn -> SmsLegVerdict.Keep // نفس الأرقام في الناحيتين — ما بنخمّنش
        // ولا رقم بتاعه: نسأل عن رقم ناحيته حسب الشكل (الصادر «من:» · الوارد «الى:») ولو مش مكتوب الناحية التانية
        else -> SmsLegVerdict.Unknown((if (row.direction == Direction.OUT) from ?: to else to ?: from)!!)
    }
}

/**
 * صفوف الرسالة بعد القاعدة: كل صف رايح محفظة أرقامها في [SmsRow.accountLast4] (التوزيع §75-11 بيوديه هناك) واتجاهه الصح. [Between] ⇒ صفين:
 * خارج من «من» وداخل في «الى» (مرجع الداخل بعلامة [IN_LEG_SUFFIX] عشان منع التكرار ما يعتبرهوش نفس الرسالة). الباقي ⇒ الصف زي ما هو.
 */
fun smsLegRows(row: SmsRow, verdict: SmsLegVerdict): List<SmsRow> = when (verdict) {
    is SmsLegVerdict.Into -> listOf(row.onLeg(verdict.last4, Direction.IN))
    is SmsLegVerdict.OutOf -> listOf(row.onLeg(verdict.last4, Direction.OUT))
    is SmsLegVerdict.Between -> listOf(
        row.onLeg(verdict.fromLast4, Direction.OUT),
        row.onLeg(verdict.toLast4, Direction.IN).let { it.copy(reference = it.reference?.plus(IN_LEG_SUFFIX)) },
    )
    else -> listOf(row)
}

/** مرجع رِجل الداخل في تحويل بين محفظتين من محافظ المالك. */
const val IN_LEG_SUFFIX = "#in"

private fun SmsRow.onLeg(last4: String, direction: Direction) = copy(direction = direction, accountLast4 = last4, ownLast4 = last4)
