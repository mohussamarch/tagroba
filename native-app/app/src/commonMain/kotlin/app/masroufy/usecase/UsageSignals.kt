package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.SmsParseResult
import app.masroufy.core.Transaction
import app.masroufy.core.UsageCategoryBefore
import app.masroufy.core.UsageEvents
import app.masroufy.core.rememberableMerchantName
import app.masroufy.problems.Usage
import app.masroufy.seed.BundledSeeds

/**
 * بيانات الاستخدام من حالات الاستخدام (PLAN-0.6 §16 «التصنيف» و«الرسايل»). **أعداد ومعرّفات ثابتة بس** — اسم التاجر بيتقري هنا عشان نعرف
 * أنهي قاعدة جاهزة اتطبقت، والمعرّف بس (`rule-0001`) هو اللي بيطلع. `Usage.port` = null (الاختبارات) ⇒ ولا حاجة.
 */
internal fun usageOfCategorySet(before: Transaction, categoryId: Id?) {
    if (Usage.port == null) return
    val was = when {
        before.categoryId == null -> UsageCategoryBefore.NONE
        before.categoryConfirmed -> UsageCategoryBefore.USER
        else -> UsageCategoryBefore.AUTO
    }
    Usage.log(UsageEvents.categorySet(was))
    if (was != UsageCategoryBefore.AUTO || categoryId == before.categoryId) return
    val rule = rememberableMerchantName(before)?.let(BundledSeeds::builtInRuleFor) ?: return
    Usage.log(UsageEvents.builtInRuleOverridden(rule))
}

/**
 * لقطة صندوق رسايل البنك بعد تشغيلة كاملة: لكل مرسل كام رسالة اتقرت وكام ما اتقريتش، وكام «شكل جديد مستني». المرسل بيتبعت بس لو في دليل
 * البنوك (`UsageValue.sender` — غير كده «other»). ⚠️ لقطة مش عدّاد: نفس الرسالة بتتعد في كل تشغيلة لحد ما تخرج من الصندوق — النسبة هي المهمة.
 */
internal fun usageOfSmsRun(state: WaitingState) {
    if (Usage.port == null) return
    val parsed = HashMap<String, IntArray>()
    val seen = HashSet<String>()
    for (view in state.views.values) for (item in view.items) {
        if (!seen.add(item.id)) continue
        val tally = parsed.getOrPut(item.sender) { IntArray(2) }
        if (item.parsed is SmsParseResult.Ok) tally[0]++ else tally[1]++
    }
    for ((sender, t) in parsed) Usage.log(UsageEvents.smsParse(sender, t[0], t[1]))
    val newShape = state.waiting.newShape.toSet()
    if (newShape.isEmpty()) return
    val bySender = state.box.messages.filter { it.id in newShape }.groupingBy { it.sender }.eachCount()
    for ((sender, n) in bySender) Usage.log(UsageEvents.smsNewShapeWaiting(sender, n))
}
