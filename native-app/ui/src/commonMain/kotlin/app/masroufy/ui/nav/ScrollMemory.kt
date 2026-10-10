package app.masroufy.ui.nav

/**
 * **الرجوع بيرجّعك لنفس المكان بالظبط** (OVERRIDES §85 — طلب المالك 2026-10-10): مكان التمرير لكل خانة في سجل الرجوع، في الملّاح نفسه
 * ([Navigator.scrolls]) مش في كل شاشة لوحدها. المفتاح = مفتاح الخانة ([Navigator.visibleKey]: `s<id>` للشاشة الداخلية · `tab-<TAB>` للتبويب)
 * + اسم المتمرّر جوه الشاشة ([slot] — `main` للأساسي).
 * - الشاشة بتكتب مكانها وهي بتتقفل (دخلت جوه حاجة أو بدّلت التبويب) — **بس لو خانتها لسه في السجل** ([Navigator.isLive]).
 * - الخانة اللي اتشالت من السجل (رجوع · `replace` · تبديل التبويب بيمسح الشاشات الداخلية · `reset`) مكانها بيتمسح معاها.
 * - التبويبات الأربعة **كل واحد بمكانه** لحد `reset` (الدخول أو تبديل البلد).
 * التطبيق على الشاشة نفسها (اللي بيرجّع المكان لما المحتوى يحمّل) في `EntryScroll.kt`.
 */
class ScrollMemory internal constructor(private val isLive: (String) -> Boolean) {
    /** مكان قايمة (`LazyColumn`): أول عنصر ظاهر وإزاحته. */
    data class ListPos(val index: Int, val offset: Int) {
        val isTop: Boolean get() = index == 0 && offset == 0
    }

    private val lists = HashMap<String, ListPos>()
    private val plains = HashMap<String, Int>()

    private fun id(entry: String, slot: String) = "$entry|$slot"

    fun list(entry: String, slot: String): ListPos? = lists[id(entry, slot)]

    fun plain(entry: String, slot: String): Int? = plains[id(entry, slot)]

    fun saveList(entry: String, slot: String, pos: ListPos) {
        if (isLive(entry)) lists[id(entry, slot)] = pos
    }

    fun savePlain(entry: String, slot: String, value: Int) {
        if (isLive(entry)) plains[id(entry, slot)] = value
    }

    /** بيمسح أماكن الخانات اللي مابقتش في السجل. */
    internal fun prune() {
        lists.keys.removeAll { !isLive(it.substringBefore('|')) }
        plains.keys.removeAll { !isLive(it.substringBefore('|')) }
    }

    internal fun clear() {
        lists.clear()
        plains.clear()
    }

    /** للاختبار: عدد الأماكن المحفوظة. */
    internal val size: Int get() = lists.size + plains.size
}

/**
 * الرجوع للمكان المحفوظ **بيتعاد** كل ما المحتوى يكبر (الشاشة بتحمّل بياناتها بعد ما تتبني — من غيره القايمة الفاضية بترجّع المكان لأول الصفحة)،
 * لحد ما الوقت يخلص ([RESTORE_WINDOW_MS]) أو **أول ما تلمس الشاشة** ([touched]) — زي النموذج («أول ما تلمس الشاشة بيقف»).
 * وهو لسه بيرجّع: لو خرجت من الشاشة، المحفوظ هو **المكان المطلوب** مش اللي القايمة الفاضية وقفت عنده ([toSave]).
 */
class ScrollRestore<P : Any>(private val target: P?) {
    var pending: Boolean = target != null
        private set

    fun touched() {
        pending = false
    }

    fun finish() {
        pending = false
    }

    fun toSave(current: P): P = if (pending && target != null) target else current
}

/** أقصى وقت لإعادة المكان وإنت راجع (المحتوى بيحمّل من الذاكرة — عادة أقل من كده بكتير). */
const val RESTORE_WINDOW_MS = 1_200L
