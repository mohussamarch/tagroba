package app.masroufy.core

/**
 * r06-1e (OVERRIDES §110-1 — رد المالك: «احفظها مع نفسك مش لازم تظهرها لصاحب الحساب»): **ساعة العملية المخفية.**
 * رسالة البنك بتاخد ساعة وصولها المحلية بتوقيت حزمة البلد (§88)، والكشف بياخد الساعة لو السطر فيه ساعة. بتتحفظ في
 * `Transaction.sourceTime` ومعاها `datePrecision = "minute"` — وبيقراها اقتراح الوجبة ([localHourOf]) و«مكرر؟» ([operationSecondOf]) بس.
 * **من غير ساعة = ما بتتكتبش** (القاعدة 10): مفيش ساعة متخمّنة.
 */

/** دقة التاريخ للعملية: «minute» لو فيه ساعة، وإلا «day» زي الأول. */
fun datePrecisionFor(time: String?): String = if (time != null) "minute" else "day"

private fun two(n: Long): String = n.toString().padStart(2, '0')

/**
 * ساعة اللحظة [receivedAt] المحلية «HH:mm:ss» في [timeZone] — **بس لو يومها المحلي = [day]** (رسالة اتأخرت أو تاريخها في النص يوم تاني ⇒
 * الساعة دي مش ساعة العملية ⇒ null). وقت من غير ساعة («2026-10-07») أو منطقة مش معروفة ⇒ null.
 */
fun localTimeOn(timeZone: String, receivedAt: String, day: IsoDate): String? {
    if (receivedAt.length <= 10) return null
    val millis = JsText.parseIsoMillis(receivedAt) ?: return null
    val local = runCatching { localMillisIn(timeZone, millis) }.getOrNull() ?: return null
    if (dayNumberToIso(local.floorDiv(DAY_MS).toInt()) != day) return null
    val sec = local.mod(DAY_MS) / 1_000L
    return two(sec / 3_600L) + ":" + two(sec / 60L % 60L) + ":" + two(sec % 60L)
}

/** قراية الرسالة **ومعاها ساعتها المحلية** بتوقيت [timeZone] (منطقة حزمة البلد). الرفض زي ما هو. */
fun BankSmsReader.parseTimed(message: BankSmsMessage, lineNumber: Int, timeZone: String): SmsParseResult =
    when (val r = parse(message, lineNumber)) {
        is SmsParseResult.Ok -> SmsParseResult.Ok(r.row.copy(time = localTimeOn(timeZone, message.receivedAt, r.row.date)))
        else -> r
    }

private val QNB_CARD_STAMP = Regex("(\\d{2})/(\\d{2})/(\\d{2})\\s+(\\d{2}):(\\d{2})-")

/** ساعة شراء الكارت في كشف QNB («CARD PURCHASE dd/mm/yy hh:mm-…») — بس لو يوم الكارت = يوم السطر [day]، وإلا null. */
fun qnbCardTime(details: String, day: IsoDate): String? {
    val m = QNB_CARD_STAMP.find(details) ?: return null
    val (d, mo, y, h, mi) = m.destructured
    if ("20$y-$mo-$d" != day || h.toInt() > 23 || mi.toInt() > 59) return null
    return "$h:$mi"
}
