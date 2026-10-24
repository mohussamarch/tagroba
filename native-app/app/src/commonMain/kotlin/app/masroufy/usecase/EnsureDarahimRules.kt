package app.masroufy.usecase

/**
 * fix055 (فحص 0.5.4 ملاحظة 3 — رسالة «لـسلام» اتسجلت من غير تصنيف): قواعد دراهم ([RepairDarahimRules]) كانت بتوصل الحسابات القديمة من
 * **دورة الخلفية بس**، وبعد تسجيل الرسايل كمان. «اقرأ اللي فات» و«أكّد الأشكال» و«رسائل البنك» بيصنّفوا من غير ما الدورة تكون اشتغلت ⇒
 * قاعدة «سلام» (مطابقة تامة) لسه مش في الحساب ⇒ «غير مصنّف».
 *
 * دلوقتي التصنيف في أي طريق رسايل (`buildCategorizeDeps`) بينادي ده الأول: لو أي دفعة لسه ما خلصتش ⇒ الفرعيات الافتراضية الأول ([subs]،
 * عشان قاعدة تصنيفها فرعي جديد ما تتسابش) وبعدين الدفعات بالترتيب. بعد ما الكل يخلص ما بيقراش حاجة تاني في نفس التشغيلة.
 * نفس قواعد §110-2: بيضيف بس، وكلمة المالك تمشي، ومرة واحدة لكل بلد.
 */
class EnsureDarahimRules(private val subs: RepairMissingDefaultSubs?, private val repairs: List<RepairDarahimRules>) {
    private var done = false

    suspend fun run() {
        if (done) return
        if (repairs.all { it.isDone() }) { done = true; return }
        subs?.run()
        repairs.forEach { it.run() }
        done = repairs.all { it.isDone() }
    }
}
