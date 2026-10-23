package app.masroufy.ui.screens.dues

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import app.masroufy.ui.theme.PrefStore

/**
 * «تخطّي» / «بعدين» / السحب لتحت على لوحة «ادفع بعدين» (تابي · تمارا).
 * - فحص 0.5.3: كانوا بيتنسوا مع كل تبديل للبلد ⇒ اتنقلوا لحالة عامة طول ما البرنامج مفتوح.
 * - r06-polish: **بيتفتكروا بعد قفل البرنامج كمان، لكل بلد لوحدها** — في تخزين الجهاز (`PrefStore` — نفس ملف المظهر على أندرويد) تحت
 *   المفتاح `bnpl_later|<معرّف البلد>`. ده إعداد جهاز (مش بيانات حساب ومش بيتزامن)، والقيمة معرّفات عمليات بس (مفيش مبالغ ولا أسامي).
 * - التخزين لو مش موجود (الاختبارات) أو وقع ⇒ بيفتكر طول ما البرنامج مفتوح بس (زي الأول). آخر [MAX] معرّف بس بيتحفظوا.
 */
object BnplLater {
    private const val PREFIX = "bnpl_later|"
    internal const val MAX = 200

    private var store: PrefStore? = null
    private val bySpace = mutableMapOf<String, SnapshotStateList<String>>()

    /** بيتنادى مرة عند فتح التطبيق (`MainActivity`) قبل أول رسم. */
    fun attach(store: PrefStore?) {
        this.store = store
        bySpace.clear()
    }

    /** المعرّفات المتأجلة للبلد دي (حالة مراقَبة — اللوحة بتتحدّث لما تتغير). */
    internal fun ids(spaceId: String): List<String> = listFor(spaceId)

    internal fun add(spaceId: String, transactionId: String) {
        val list = listFor(spaceId)
        if (transactionId in list) return
        list += transactionId
        while (list.size > MAX) list.removeAt(0)
        save(spaceId, list)
    }

    internal fun remove(spaceId: String, transactionId: String) {
        val list = listFor(spaceId)
        if (list.remove(transactionId)) save(spaceId, list)
    }

    private fun listFor(spaceId: String): SnapshotStateList<String> = bySpace.getOrPut(spaceId) {
        mutableStateListOf<String>().apply { addAll(decode(runCatching { store?.read(PREFIX + spaceId) }.getOrNull())) }
    }

    private fun save(spaceId: String, list: List<String>) {
        runCatching { store?.write(PREFIX + spaceId, list.joinToString("\n").ifEmpty { null }) }
    }

    private fun decode(raw: String?): List<String> = raw?.split('\n')?.filter { it.isNotBlank() }?.distinct()?.takeLast(MAX).orEmpty()
}
