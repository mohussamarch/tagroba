package app.masroufy.ui.screens.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.masroufy.ui.app.BellState
import app.masroufy.ui.app.ShellDeps
import app.masroufy.usecase.DismissedAlert

/**
 * اختيارات **على الجهاز للجلسة دي بس** (زي `sessionStorage` في النموذج) — مش بيانات، ومش بتتحفظ بعد ما التطبيق يتقفل.
 * كل واحدة مكتوب جنبها حالة الاستخدام الناقصة اللي هتحل محلها (CLAUDE.md #15: ميزة ظاهرة مش ميزة مكتملة).
 */

/**
 * الشهر المختار لكل بلد (مفتاح الفترة `YYYY-MM` من `Period.key`). `null` = الشهر الحالي.
 * «اختر الشهر» بيكتب هنا، والعمليات والمراجعة بيقروا من هنا (⚠️ شاشة العمليات بتتوصل وقت الدمج).
 */
@Stable
object PeriodChoice {
    private val keys = mutableStateMapOf<String, String>()

    fun of(spaceId: String): String? = keys[spaceId]

    fun set(spaceId: String, key: String?) {
        if (key == null) keys.remove(spaceId) else keys[spaceId] = key
    }
}

/**
 * «اختر شكلك» (`LookSheet`): الشكل المختار (١–٦) للجلسة. ⚠️ **ناقص في المنطق:** مفيش حقل للشكل في ملفك (`UserProfile`) ولا حالة استخدام
 * تحفظه — لحد ما تتبني، الاختيار بيضيع لما التطبيق يتقفل (`MeInfo.lookIndex` من الهيكل بيرجع للأول).
 */
@Stable
object LookChoice {
    var look by mutableStateOf<Int?>(null)
}

/** الشكل اللي بيتعرض في الدواير: اختيار الجلسة ⇒ اللي في ملفك (`MeInfo.lookIndex`) ⇒ الأول. رقم برّه الستة ⇒ الأول. */
fun avatarLook(choice: Int?, fromProfile: Int?): Int = (choice ?: fromProfile)?.takeIf { it in 1..LOOK_COUNT } ?: 1

/**
 * «×» على الإشعار + «تراجع» ٤ ثواني (قرار المالك 2026-10-09). [gone] = اللي اتمسح (بيستخبى على طول في الجرس والصفحة)، و[undo] = آخر واحد
 * لسه ينفع يرجع. **المسح نفسه على الحساب** من المحرك ([dropAndSave] ⇒ `ShellDeps.dismissAlert` — علامة `alertDismissals` + السطر بيتشال):
 * نقطة التبويب بتروح، وكارته في بداية الشات بيختفي، وما بيرجعش غير لو صعّد لدرجة جديدة (§79.2-1). «تراجع» ⇒ [undoAndSave].
 */
@Stable
class Dismissals {
    private val gone = mutableStateMapOf<String, Boolean>()

    /** سجل المسح اللي المحرك رجّعه لكل موضوع (عشان «تراجع» يرجّع السطر زي ما كان). */
    private val records = mutableMapOf<String, DismissedAlert>()

    /** «×»: يستخبى دلوقتي ويتحفظ على الحساب — لو الحفظ فشل بيرجع ظاهر والغلط بيطلع للشاشة. */
    suspend fun dropAndSave(threadKey: String, shell: ShellDeps) {
        drop(threadKey)
        try {
            records[threadKey] = shell.dismissAlert(threadKey)
        } catch (e: Exception) {
            gone.remove(threadKey)
            if (undo == threadKey) undo = null
            throw e
        }
    }

    /** «تراجع» خلال الأربع ثواني: السطر والعلامة بيرجعوا زي ما كانوا. */
    suspend fun undoAndSave(shell: ShellDeps) {
        val key = undo ?: return
        val record = records.remove(key)
        restore()
        record?.let { shell.undoDismiss(it) }
    }

    var undo by mutableStateOf<String?>(null)
        private set

    fun isGone(threadKey: String): Boolean = gone[threadKey] == true

    val goneKeys: Set<String> get() = gone.keys.toSet()

    fun drop(threadKey: String) {
        gone[threadKey] = true
        undo = threadKey
    }

    /** «تراجع»: آخر واحد اتمسح بيرجع. */
    fun restore() {
        val key = undo ?: return
        gone.remove(key)
        undo = null
    }

    /** عدّت الأربع ثواني ⇒ مفيش تراجع (الممسوح بيفضل ممسوح). */
    fun expire(threadKey: String) {
        if (undo == threadKey) undo = null
    }

    companion object {
        private val bySpace = mutableMapOf<String, Dismissals>()

        /** نفس الحالة في صفحة الإشعارات ونافذة الجرس للبلد دي. */
        fun of(spaceId: String): Dismissals = bySpace.getOrPut(spaceId) { Dismissals() }

        /** مدة «تراجع» (قرار المالك: ٤ ثواني). */
        const val UNDO_MS = 4_000L
    }
}

/**
 * الجرس بعد «×» (قرار المالك 2026-10-09): الممسوح بيختفي من النافذة، وما بيتعدّش في «جديد»، و**بيشيل نقطة تبويبه** لو مفيش غيره
 * (النقطة من السطور اللي لسه ما اتقرتش وليها تبويب — `BellItem.tab`). مفيش ممسوح ⇒ الحالة زي ما هي.
 */
fun BellState.without(gone: Set<String>): BellState {
    if (gone.isEmpty()) return this
    val left = items.filter { it.threadKey !in gone }
    val unread = left.filter { it.unread }
    return BellState(left, unread.size, unread.mapNotNull { it.tab }.toSet())
}
