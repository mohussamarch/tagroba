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

private val FACE_OPTIONS = opts(0 to UiKey.CHAR_OPT_FACE_0, 1 to UiKey.CHAR_OPT_FACE_1, 2 to UiKey.CHAR_OPT_FACE_2, 3 to UiKey.CHAR_OPT_FACE_3)
private val EYES_OPTIONS = opts(0 to UiKey.CHAR_OPT_EYES_0, 1 to UiKey.CHAR_OPT_EYES_1, 2 to UiKey.CHAR_OPT_EYES_2, 3 to UiKey.CHAR_OPT_EYES_3)
private val BROWS_OPTIONS = opts(0 to UiKey.CHAR_OPT_BROWS_0, 1 to UiKey.CHAR_OPT_BROWS_1, 2 to UiKey.CHAR_OPT_BROWS_2, 3 to UiKey.CHAR_OPT_BROWS_3)
private val NOSE_OPTIONS = opts(0 to UiKey.CHAR_OPT_NOSE_0, 1 to UiKey.CHAR_OPT_NOSE_1, 2 to UiKey.CHAR_OPT_NOSE_2)
private val MOUTH_OPTIONS = opts(0 to UiKey.CHAR_OPT_MOUTH_0, 1 to UiKey.CHAR_OPT_MOUTH_1, 2 to UiKey.CHAR_OPT_MOUTH_2, 3 to UiKey.CHAR_OPT_MOUTH_3)
private val BUILD_OPTIONS = opts(0 to UiKey.CHAR_OPT_BUILD_0, 1 to UiKey.CHAR_OPT_BUILD_1, 2 to UiKey.CHAR_OPT_BUILD_2)
private val MEN_HAIR_OPTIONS = opts(
    0 to UiKey.CHAR_OPT_HAIR_0, 1 to UiKey.CHAR_OPT_HAIR_1, 2 to UiKey.CHAR_OPT_HAIR_2, 3 to UiKey.CHAR_OPT_HAIR_3, 4 to UiKey.CHAR_OPT_HAIR_4,
    5 to UiKey.CHAR_OPT_HAIR_5,
)
private val WOMEN_HAIR_OPTIONS = opts(
    6 to UiKey.CHAR_OPT_HAIR_6, 7 to UiKey.CHAR_OPT_HAIR_7, 8 to UiKey.CHAR_OPT_HAIR_8, 9 to UiKey.CHAR_OPT_HAIR_9, 10 to UiKey.CHAR_OPT_HAIR_10,
)
private val BEARD_OPTIONS = opts(
    0 to UiKey.CHAR_OPT_BEARD_0, 1 to UiKey.CHAR_OPT_BEARD_1, 2 to UiKey.CHAR_OPT_BEARD_2, 3 to UiKey.CHAR_OPT_BEARD_3, 4 to UiKey.CHAR_OPT_BEARD_4,
    5 to UiKey.CHAR_OPT_BEARD_5,
)
private val MEN_OUTFIT_OPTIONS = opts(0 to UiKey.CHAR_OPT_OUTFIT_0, 1 to UiKey.CHAR_OPT_OUTFIT_1, 2 to UiKey.CHAR_OPT_OUTFIT_2)
private val WOMEN_OUTFIT_OPTIONS = opts(5 to UiKey.CHAR_OPT_OUTFIT_5, 3 to UiKey.CHAR_OPT_OUTFIT_3, 6 to UiKey.CHAR_OPT_OUTFIT_6)
private val MEN_HEAD_OPTIONS = opts(
    0 to UiKey.CHAR_OPT_HEAD_0, 1 to UiKey.CHAR_OPT_HEAD_1, 2 to UiKey.CHAR_OPT_HEAD_2, 3 to UiKey.CHAR_OPT_HEAD_3, 7 to UiKey.CHAR_OPT_HEAD_7,
)
private val WOMEN_HEAD_OPTIONS = opts(0 to UiKey.CHAR_OPT_HEAD_0, 4 to UiKey.CHAR_OPT_HEAD_4, 5 to UiKey.CHAR_OPT_HEAD_5, 6 to UiKey.CHAR_OPT_HEAD_6)
private val GLASSES_OPTIONS = opts(
    0 to UiKey.CHAR_OPT_GLASSES_0, 1 to UiKey.CHAR_OPT_GLASSES_1, 2 to UiKey.CHAR_OPT_GLASSES_2, 3 to UiKey.CHAR_OPT_GLASSES_3,
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
