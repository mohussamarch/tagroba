// ART-STUB (replaced by r041-char-art at merge)
package app.masroufy.core

/**
 * «شخصيتك» — أجزاء شاشة البناء وقوايمها (نقل `P` و`SECS` من `CharacterBuilder.dc.html` @2df8134d).
 * النصوص في `TextsCharacterOptions.kt` (التلات جداول). المكافآت مالهاش اختيار هنا (مؤجلة).
 */

/** طبقات الرسم: الراس (الدواير) · الراس والكتاف · الجسم كله (200×440). */
enum class CharacterLayer { HEAD, BUST, FULL }

/** لوحة الألوان اللي الجزء بيختار منها. */
enum class CharacterColorKey { SKIN, HAIR, WRAP, OUTFIT }

/** جزء = تبويب في شاشة البناء: اسمه، شكل الصورة الصغيرة للاختيار (`null` = ألوان بس)، ولوحة ألوانه لو ليه. */
enum class CharacterPart(val title: UiKey, val thumb: CharacterLayer?, val color: CharacterColorKey?) {
    FACE(UiKey.CHAR_PART_FACE, CharacterLayer.HEAD, null),
    EYES(UiKey.CHAR_PART_EYES, CharacterLayer.HEAD, null),
    BROWS(UiKey.CHAR_PART_BROWS, CharacterLayer.HEAD, null),
    NOSE(UiKey.CHAR_PART_NOSE, CharacterLayer.HEAD, null),
    MOUTH(UiKey.CHAR_PART_MOUTH, CharacterLayer.HEAD, null),
    BUILD(UiKey.CHAR_PART_BUILD, CharacterLayer.FULL, null),
    SKIN(UiKey.CHAR_PART_SKIN, null, CharacterColorKey.SKIN),
    HAIR(UiKey.CHAR_PART_HAIR, CharacterLayer.BUST, CharacterColorKey.HAIR),
    BEARD(UiKey.CHAR_PART_BEARD, CharacterLayer.HEAD, CharacterColorKey.HAIR),
    OUTFIT(UiKey.CHAR_PART_OUTFIT, CharacterLayer.FULL, CharacterColorKey.OUTFIT),
    HEAD(UiKey.CHAR_PART_HEAD, CharacterLayer.BUST, CharacterColorKey.WRAP),
    GLASSES(UiKey.CHAR_PART_GLASSES, CharacterLayer.HEAD, null),
}

/** الأقسام بالترتيب (طلب المالك 2026-10-16): ملامح الوش ← الجسم والشعر ← اللبس — وأول ما الشاشة تفتح «ملامح الوش» مفتوح. */
enum class CharacterSection(val title: UiKey, val parts: List<CharacterPart>) {
    FACE(UiKey.CHAR_SEC_FACE, listOf(CharacterPart.FACE, CharacterPart.EYES, CharacterPart.BROWS, CharacterPart.NOSE, CharacterPart.MOUTH)),
    BODY(UiKey.CHAR_SEC_BODY, listOf(CharacterPart.BUILD, CharacterPart.SKIN, CharacterPart.HAIR, CharacterPart.BEARD)),
    WEAR(UiKey.CHAR_SEC_WEAR, listOf(CharacterPart.OUTFIT, CharacterPart.HEAD, CharacterPart.GLASSES)),
}

/** أجزاء القسم للنوع ده (الدقن للرجالة بس). */
fun CharacterSection.partsFor(woman: Boolean): List<CharacterPart> = if (woman) parts.filter { it != CharacterPart.BEARD } else parts

/** اختيار واحد: رقمه (اللي بيتخزن) واسمه. */
data class CharacterOption(val id: Int, val label: UiKey)

private fun opts(vararg pairs: Pair<Int, UiKey>) = pairs.map { (id, key) -> CharacterOption(id, key) }

private val FACE_OPTIONS = opts(0 to UiKey.CHAR_FACE_OVAL, 1 to UiKey.CHAR_FACE_ROUND, 2 to UiKey.CHAR_FACE_LONG, 3 to UiKey.CHAR_FACE_SQUARE)
private val EYES_OPTIONS = opts(0 to UiKey.CHAR_EYES_DOTS, 1 to UiKey.CHAR_EYES_ALMOND, 2 to UiKey.CHAR_EYES_CALM, 3 to UiKey.CHAR_EYES_WIDE)
private val BROWS_OPTIONS = opts(0 to UiKey.CHAR_BROWS_STRAIGHT, 1 to UiKey.CHAR_BROWS_ARCHED, 2 to UiKey.CHAR_BROWS_THICK, 3 to UiKey.CHAR_BROWS_THIN)
private val NOSE_OPTIONS = opts(0 to UiKey.CHAR_NOSE_SMALL, 1 to UiKey.CHAR_NOSE_STRAIGHT, 2 to UiKey.CHAR_NOSE_WIDE)
private val MOUTH_OPTIONS = opts(0 to UiKey.CHAR_MOUTH_NEUTRAL, 1 to UiKey.CHAR_MOUTH_SLIGHT, 2 to UiKey.CHAR_MOUTH_SMILE, 3 to UiKey.CHAR_MOUTH_SMALL)
private val BUILD_OPTIONS = opts(0 to UiKey.CHAR_BUILD_SLIM, 1 to UiKey.CHAR_BUILD_MEDIUM, 2 to UiKey.CHAR_BUILD_FULL)
private val MEN_HAIR_OPTIONS = opts(
    0 to UiKey.CHAR_HAIR_NONE, 1 to UiKey.CHAR_HAIR_SHORT, 2 to UiKey.CHAR_HAIR_SIDE, 3 to UiKey.CHAR_HAIR_LONG, 4 to UiKey.CHAR_HAIR_CURLY,
    5 to UiKey.CHAR_HAIR_THIN,
)
private val WOMEN_HAIR_OPTIONS = opts(
    6 to UiKey.CHAR_HAIR_W_LONG, 7 to UiKey.CHAR_HAIR_W_PONY, 8 to UiKey.CHAR_HAIR_W_CURLY, 9 to UiKey.CHAR_HAIR_W_SHORT, 10 to UiKey.CHAR_HAIR_W_BUN,
)
private val BEARD_OPTIONS = opts(
    0 to UiKey.CHAR_BEARD_NONE, 1 to UiKey.CHAR_BEARD_STUBBLE, 2 to UiKey.CHAR_BEARD_SHORT, 3 to UiKey.CHAR_BEARD_FULL, 4 to UiKey.CHAR_BEARD_GOATEE,
    5 to UiKey.CHAR_BEARD_MOUSTACHE,
)
private val MEN_OUTFIT_OPTIONS = opts(0 to UiKey.CHAR_OUTFIT_THOBE, 1 to UiKey.CHAR_OUTFIT_SHIRT, 2 to UiKey.CHAR_OUTFIT_SUIT)
private val WOMEN_OUTFIT_OPTIONS = opts(5 to UiKey.CHAR_OUTFIT_ABAYA, 3 to UiKey.CHAR_OUTFIT_DRESS, 6 to UiKey.CHAR_OUTFIT_TUNIC)
private val MEN_HEAD_OPTIONS = opts(
    0 to UiKey.CHAR_HEAD_NONE, 1 to UiKey.CHAR_HEAD_SHEMAGH, 2 to UiKey.CHAR_HEAD_GHUTRA, 3 to UiKey.CHAR_HEAD_TAQIYAH, 7 to UiKey.CHAR_HEAD_CAP,
)
private val WOMEN_HEAD_OPTIONS = opts(0 to UiKey.CHAR_HEAD_NONE, 4 to UiKey.CHAR_HEAD_HIJAB, 5 to UiKey.CHAR_HEAD_KHIMAR, 6 to UiKey.CHAR_HEAD_NIQAB)
private val GLASSES_OPTIONS = opts(
    0 to UiKey.CHAR_GLASSES_NONE, 1 to UiKey.CHAR_GLASSES_ROUND, 2 to UiKey.CHAR_GLASSES_SQUARE, 3 to UiKey.CHAR_GLASSES_THIN,
)

/** اختيارات الجزء للنوع ده بترتيب العرض — من غير أي رقم مكافأة. البشرة ألوان بس ⇒ قايمة فاضية. الدقن للست ⇒ فاضية. */
fun CharacterPart.options(woman: Boolean): List<CharacterOption> = when (this) {
    CharacterPart.FACE -> FACE_OPTIONS
    CharacterPart.EYES -> EYES_OPTIONS
    CharacterPart.BROWS -> BROWS_OPTIONS
    CharacterPart.NOSE -> NOSE_OPTIONS
    CharacterPart.MOUTH -> MOUTH_OPTIONS
    CharacterPart.BUILD -> BUILD_OPTIONS
    CharacterPart.SKIN -> emptyList()
    CharacterPart.HAIR -> if (woman) WOMEN_HAIR_OPTIONS else MEN_HAIR_OPTIONS
    CharacterPart.BEARD -> if (woman) emptyList() else BEARD_OPTIONS
    CharacterPart.OUTFIT -> if (woman) WOMEN_OUTFIT_OPTIONS else MEN_OUTFIT_OPTIONS
    CharacterPart.HEAD -> if (woman) WOMEN_HEAD_OPTIONS else MEN_HEAD_OPTIONS
    CharacterPart.GLASSES -> GLASSES_OPTIONS
}
