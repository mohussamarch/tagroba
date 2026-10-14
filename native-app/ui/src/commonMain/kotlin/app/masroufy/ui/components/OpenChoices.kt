package app.masroufy.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * **الضغط برّه بيلغي الاختيار المفتوح** (المالك 2026-10-15 — HANDOVER §6.5 بند 11: «لو اخترت خيار معين في كذا مكان … ليه لما بدوس
 * برا او في مكان مختلف مش بيلغي الخيار؟»). الحالة الصافية للآلية الواحدة اللي كل الشاشات بتستعملها (`OutsideTap.kt` · `overlay/Choice.kt`):
 * - **اختيار مفتوح** (شبكة تاريخ، شرايح اختيار، وضع تعديل، تأكيد صغير…) بيتسجل هنا بمنطقته ([Choice.areas] — الزرار اللي فتحه + اللوحة).
 * - ضغطة **خلصت** برّه منطقته (حتى لو على زرار تاني) ⇒ [Choice] بيتقفل بـ`onClose` — اللي **ما بيحفظش** أي حاجة ما اتأكدتش (الشاشة بترجّع قيمتها).
 *   الضغطة بتكمّل للي تحتها (زرار تاني بيشتغل عادي) — ما بتتاكلش.
 * - الاختيار اللي **فوقه لوحة** اتفتحت بعده ([Choice.covered]) ما بيتقفلش من ضغطات جوه اللوحة دي.
 * - السحب (التمرير) بيقفل بس الاختيارات اللي طالبة ده ([Choice.closeOnScroll] — زي وضع تعديل صف الأشخاص §84)؛ اللوحة اللي جوه المحتوى
 *   بتتحرك مع التمرير ومش بتتقفل (ممكن تكون أطول من الشاشة).
 * - **الكيبورد:** ضغطة برّه أي خانة كتابة ([keeper]) ⇒ الكيبورد بيتقفل بس. **الكلام المكتوب ما بيتمسحش** (الخانة نفسها بتفضل بقيمتها).
 * - اللي اتفتح **بنفس الضغطة** (ضغط مطوّل بيفتح وضع تعديل) ما بيتقفلش لما الصباع يترفع: الإغلاق بس للي كان مفتوح وقت النزول ([pressed]).
 */
class OpenChoices {
    class Choice internal constructor(internal val owner: OpenChoices, onClose: () -> Unit, closeOnScroll: Boolean) {
        internal var onClose: () -> Unit = onClose
        internal var closeOnScroll: Boolean = closeOnScroll
        internal val areas = LinkedHashMap<Any, () -> Rect?>()

        /** فيه لوحة اتفتحت فوقه بعده (`overlay/Choice.kt` بيحسبها من طبقة اللوحات) ⇒ الضغط جوه اللوحة دي مش «برّه». */
        internal var covered: () -> Boolean = { false }

        internal fun holds(p: Offset): Boolean = areas.values.any { it()?.contains(p) == true }
    }

    private val open = mutableListOf<Choice>()
    private val keepers = LinkedHashMap<Any, () -> Rect?>()
    private var atDown: List<Choice> = emptyList()

    val openCount: Int get() = open.size

    /** اختيار اتفتح. [onClose] = إلغاؤه من غير حفظ. */
    fun open(onClose: () -> Unit, closeOnScroll: Boolean = false): Choice = Choice(this, onClose, closeOnScroll).also(::register)

    /** اختيار جديد لسه مش متسجل (الشاشة بتسجّله لما يترسم فعلًا — `overlay/Choice.kt`). */
    internal fun prepare(onClose: () -> Unit, closeOnScroll: Boolean): Choice = Choice(this, onClose, closeOnScroll)

    internal fun register(choice: Choice) {
        if (choice !in open) open += choice
    }

    /** خانات الرجوع اللي الاختيارات حطتها في طبقة اللوحات (مش لوحات حقيقية ⇒ ما بتغطّيش اختيار تاني). */
    internal val backEntries = mutableSetOf<Any>()

    /** الاختيار اتقفل (بأي طريقة) أو الشاشة اتشالت. */
    fun closed(choice: Choice) {
        open -= choice
        atDown = atDown - choice
    }

    /** منطقة من مناطق [choice] (اللوحة · الزرار اللي فتحها). [bounds] بيتقري وقت الضغطة (مكانها بعد أي حركة) — `null` = اتشالت. */
    fun area(choice: Choice, key: Any, bounds: (() -> Rect?)?) {
        if (bounds == null) choice.areas -= key else choice.areas[key] = bounds
    }

    /** خانة كتابة: الضغط جواها ما بيقفلش الكيبورد. `null` = اتشالت. */
    fun keeper(key: Any, bounds: (() -> Rect?)?) {
        if (bounds == null) keepers -= key else keepers[key] = bounds
    }

    /** الصباع نزل: اللي مفتوح دلوقتي بس هو اللي ممكن يتقفل بالضغطة دي. */
    fun pressed() {
        atDown = open.toList()
    }

    /**
     * ضغطة خلصت عند [p] (من غير سحب): كل اختيار كان مفتوح وقت النزول، ومش متغطي، والضغطة برّاه ⇒ بيتلغي.
     * بيرجّع `true` لو الكيبورد يتقفل (الضغطة مش جوه خانة كتابة).
     */
    fun tapped(p: Offset): Boolean {
        closeWhere { !it.covered() && !it.holds(p) }
        return keepers.values.none { it()?.contains(p) == true }
    }

    /** سحب بدأ من [start]: الاختيارات اللي بتتقفل بالتمرير وبداية السحب برّاها. */
    fun dragged(start: Offset) {
        closeWhere { it.closeOnScroll && !it.covered() && !it.holds(start) }
    }

    private fun closeWhere(test: (Choice) -> Boolean) {
        val hit = atDown.filter { it in open && test(it) }
        atDown = emptyList()
        hit.forEach { it.onClose() }
    }
}

/**
 * لوحة فيها خانة كتابة اتكتب فيها ([typed]) ⇒ الضغط على الستارة برّه اللوحة **بيقفل الكيبورد بس** واللوحة بتهتز وتفضل
 * (الكلام ما يضيعش من غير ما المالك يأكد). القفل وقتها: السحب لتحت من المقبض · زرار الرجوع · زرار اللوحة نفسها.
 * اللوحة اللي ما اتكتبش فيها حاجة ⇒ الضغطة برّه بتقفلها زي الأول. `Sheet` بيعمل واحد لكل فتحة، و`TextInput` بيعلّم عليه.
 */
class FormGuard {
    var typed: Boolean by mutableStateOf(false)
        private set

    /** عدد المرات اللي الضغط برّه اتمنع فيها — اللوحة بتهتز مع كل زيادة. */
    var nudges: Int by mutableIntStateOf(0)
        private set

    /** المالك كتب حرف (من الكيبورد — مش قيمة الشاشة حطتها). */
    fun onTyped() {
        typed = true
    }

    /** ضغطة على الستارة برّه اللوحة: `true` = اقفل اللوحة · `false` = فيه كلام مكتوب ⇒ تفضل (وتهتز). */
    fun outsideTap(): Boolean {
        if (!typed) return true
        nudges++
        return false
    }

    fun reset() {
        typed = false
    }
}
