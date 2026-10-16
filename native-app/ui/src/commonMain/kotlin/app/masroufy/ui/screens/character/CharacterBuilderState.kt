package app.masroufy.ui.screens.character

import app.masroufy.core.CharacterColorKey
import app.masroufy.core.CharacterLayer
import app.masroufy.core.CharacterLook
import app.masroufy.core.CharacterOption
import app.masroufy.core.CharacterPart
import app.masroufy.core.CharacterSection
import app.masroufy.core.UiKey
import app.masroufy.core.fitGender
import app.masroufy.core.genderIndex
import app.masroufy.core.normalized
import app.masroufy.core.options
import app.masroufy.core.valueOf
import app.masroufy.core.with
import app.masroufy.ui.text.t

/**
 * حالة البنّاء «ابنِ شخصيتك» / «عدّل شخصيتك» كحالة **نقية** (بتتختبر على JVM) — نقل `CharacterBuilder` @2df8134d (character-spec §3).
 * الأقسام بالترتيب: ملامح الوجه (مفتوح أول ما الشاشة تفتح) ⇒ الجسم والشعر ⇒ الملابس، وقسم واحد مفتوح في المرة. النوع من ملفك بس
 * (مش بيتسأل هنا) وكل نوع بيشوف قوايمه. الضغط على اختيار بيغيّر المعاينة بس — مفيش كتابة لحد «احفظ».
 */
data class BuilderState(
    val look: CharacterLook,
    /** فيه شخصية محفوظة ⇒ «عدّل شخصيتك» بدل «ابنِ شخصيتك». */
    val built: Boolean,
    val section: CharacterSection = CharacterSection.FACE,
    /** التبويب المفتوح في كل قسم (بيتفتكر لما ترجع للقسم). */
    val tabs: Map<CharacterSection, Int> = emptyMap(),
    /** «قريب» / «الجسم كله» اللي اختاره بإيده — null = تلقائي. */
    val view: CharacterLayer? = null,
)

/** أول ما الشاشة تفتح: المحفوظ (أو بداية النموذج من غير لحية) على نوع ملفك — ملف من غير نوع ⇒ من غير ظبط (النموذج `pg === null`). */
fun startBuilder(saved: CharacterLook?, profileGender: String?): BuilderState {
    val look = saved?.normalized() ?: CharacterLook.START
    return BuilderState(if (profileGender == null) look else look.fitGender(genderIndex(profileGender)), built = saved != null)
}

fun isWoman(s: BuilderState): Boolean = s.look.gender == 1

/** أجزاء القسم — اللحية للرجالة بس. */
fun partsOf(s: BuilderState, section: CharacterSection): List<CharacterPart> =
    section.parts.filter { !(it == CharacterPart.BEARD && isWoman(s)) }

fun tabIndex(s: BuilderState): Int = (s.tabs[s.section] ?: 0).coerceIn(0, partsOf(s, s.section).lastIndex)

fun openPart(s: BuilderState): CharacterPart = partsOf(s, s.section)[tabIndex(s)]

fun openSection(s: BuilderState, section: CharacterSection): BuilderState = s.copy(section = section)

fun openTab(s: BuilderState, index: Int): BuilderState = s.copy(tabs = s.tabs + (s.section to index))

fun pickView(s: BuilderState, layer: CharacterLayer): BuilderState = s.copy(view = layer)

/** المعاينة الكبيرة: تلقائي «الجسم كله» في تبويب الملابس وهيئة الجسم، وغير كده «قريب» — لحد ما يختار بإيده. */
fun previewLayer(s: BuilderState): CharacterLayer =
    s.view ?: if (openPart(s) == CharacterPart.OUTFIT || openPart(s) == CharacterPart.BUILD) CharacterLayer.FULL else CharacterLayer.BUST

/** الأقسام المقفولة قبل المفتوح وبعده (بتحافظ على الترتيب). */
fun sectionsBefore(s: BuilderState): List<CharacterSection> = CharacterSection.entries.filter { it.ordinal < s.section.ordinal }

fun sectionsAfter(s: BuilderState): List<CharacterSection> = CharacterSection.entries.filter { it.ordinal > s.section.ordinal }

/** غطا الراس بيخبّي الشعر (شماغ · غترة · حجاب · خمار · نقاب — وطاقية الغني بتلغي الغطا). */
fun hairHidden(look: CharacterLook): Boolean = look.head in setOf(1, 2, 4, 5, 6) && look.hat != 1

/** القيمة بعد الاختيار من غير آثار جانبية للصور الصغيرة (النموذج بيغيّر الحقل بس + النقاب أسود). */
private fun preview(look: CharacterLook, part: CharacterPart, id: Int): CharacterLook {
    val l = look.with(part, id).let { if (part == CharacterPart.OUTFIT) it.copy(outfitColor = look.outfitColor) else it }
    return if (part == CharacterPart.HAIR) l.copy(head = 0, hat = 0) else l
}

/** اختيار في الشبكة: [look] = شكل الصورة الصغيرة (الحالي والاختيار ده بس). */
data class OptionUi(val id: Int, val label: String, val selected: Boolean, val look: CharacterLook, val layer: CharacterLayer)

fun optionsFor(s: BuilderState, part: CharacterPart = openPart(s)): List<OptionUi> {
    val layer = part.thumb ?: return emptyList()
    return part.options(isWoman(s)).map { o: CharacterOption ->
        OptionUi(o.id, t(o.label), s.look.valueOf(part) == o.id, preview(s.look, part, o.id), layer)
    }
}

/** الضغط على اختيار: النقاب ⇒ أسود · تغيير اللبس ⇒ أول لون. */
fun pick(s: BuilderState, part: CharacterPart, id: Int): BuilderState = s.copy(look = s.look.with(part, id))

/** صف الألوان ظاهر؟ غطا الراس بس للحجاب والخمار والنقاب والكاب · الشعر المستخبي من غير لون. */
fun showColors(s: BuilderState, part: CharacterPart = openPart(s)): Boolean {
    if (part.color == null) return false
    if (part == CharacterPart.HEAD && s.look.head !in setOf(4, 5, 6, 7)) return false
    return !(part == CharacterPart.HAIR && hairHidden(s.look))
}

/** ملاحظة «الشعر مخفي …» في تبويب الشعر. */
fun hairNote(s: BuilderState): Boolean = openPart(s) == CharacterPart.HAIR && hairHidden(s.look)

fun colorIndex(look: CharacterLook, key: CharacterColorKey): Int = when (key) {
    CharacterColorKey.SKIN -> look.skin
    CharacterColorKey.HAIR -> look.hairColor
    CharacterColorKey.WRAP -> look.wrap
    CharacterColorKey.OUTFIT -> look.outfitColor
}

fun pickColor(s: BuilderState, key: CharacterColorKey, index: Int): BuilderState = s.copy(
    look = when (key) {
        CharacterColorKey.SKIN -> s.look.copy(skin = index)
        CharacterColorKey.HAIR -> s.look.copy(hairColor = index)
        CharacterColorKey.WRAP -> s.look.copy(wrap = index)
        CharacterColorKey.OUTFIT -> s.look.copy(outfitColor = index)
    },
)

/** اسم صف الألوان (النموذج: الغطا باسمه · لون الدقن «نفس الشعر» · العباية باسمها). */
fun colorLabel(s: BuilderState, part: CharacterPart = openPart(s)): String = t(
    when (part.color) {
        CharacterColorKey.SKIN -> UiKey.CHAR_COLOR_SKIN
        CharacterColorKey.HAIR -> if (part == CharacterPart.BEARD) UiKey.CHAR_COLOR_BEARD else UiKey.CHAR_COLOR_HAIR
        CharacterColorKey.WRAP -> when (s.look.head) {
            4 -> UiKey.CHAR_COLOR_HIJAB
            5 -> UiKey.CHAR_COLOR_KHIMAR
            6 -> UiKey.CHAR_COLOR_NIQAB
            else -> UiKey.CHAR_COLOR_CAP
        }
        CharacterColorKey.OUTFIT, null -> if (s.look.outfit == 5) UiKey.CHAR_COLOR_ABAYA else UiKey.CHAR_COLOR_CLOTHES
    },
)

private fun labelOf(s: BuilderState, part: CharacterPart): String =
    part.options(isWoman(s)).firstOrNull { it.id == s.look.valueOf(part) }?.let { t(it.label) }.orEmpty()

/** سطر القسم المقفول: الوش · العينين · البق — الجسم · الشعر · الدقن (رجالة) — اللبس · الغطا · النضارة (لو فيه). */
fun summary(s: BuilderState, section: CharacterSection): String = when (section) {
    CharacterSection.FACE -> listOf(labelOf(s, CharacterPart.FACE), labelOf(s, CharacterPart.EYES), labelOf(s, CharacterPart.MOUTH))
    CharacterSection.BODY -> listOf(labelOf(s, CharacterPart.BUILD), labelOf(s, CharacterPart.HAIR), if (isWoman(s)) "" else labelOf(s, CharacterPart.BEARD))
    CharacterSection.WEAR -> listOf(
        labelOf(s, CharacterPart.OUTFIT), labelOf(s, CharacterPart.HEAD), if (s.look.glasses != 0) labelOf(s, CharacterPart.GLASSES) else "",
    )
}.filter { it.isNotEmpty() }.joinToString(" · ")

/** عنوان الشاشة وزرار «ملفك»: قبل أول حفظ «ابنِ شخصيتك» وبعده «عدّل شخصيتك». */
fun builderTitle(built: Boolean): String = t(if (built) UiKey.CHAR_EDIT else UiKey.CHAR_BUILD)
