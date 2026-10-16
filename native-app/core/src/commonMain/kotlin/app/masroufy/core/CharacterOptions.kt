// ART-STUB (replaced by r041-char-art at merge)
package app.masroufy.core

/** الطبقات التلاتة للرسم (character-spec §2.1): الراس · الراس والكتاف · الجسم كله. */
enum class CharacterLayer { HEAD, BUST, FULL }

/** مفتاح صف الألوان تحت الاختيارات. */
enum class CharacterColorKey { SKIN, HAIR, WRAP, OUTFIT }

/** أجزاء البنّاء (§3): العنوان · شكل الصورة الصغيرة (null = ألوان بس) · صف الألوان. */
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

/** الأقسام بالترتيب (طلب المالك 2026-10-16): ملامح الوجه ⇒ الجسم والشعر ⇒ الملابس. اللحية بتتشال للستات في الواجهة. */
enum class CharacterSection(val title: UiKey, val parts: List<CharacterPart>) {
    FACE(UiKey.CHAR_SEC_FACE, listOf(CharacterPart.FACE, CharacterPart.EYES, CharacterPart.BROWS, CharacterPart.NOSE, CharacterPart.MOUTH)),
    BODY(UiKey.CHAR_SEC_BODY, listOf(CharacterPart.BUILD, CharacterPart.SKIN, CharacterPart.HAIR, CharacterPart.BEARD)),
    WEAR(UiKey.CHAR_SEC_WEAR, listOf(CharacterPart.OUTFIT, CharacterPart.HEAD, CharacterPart.GLASSES)),
}

data class CharacterOption(val id: Int, val label: UiKey)

private fun opts(vararg pairs: Pair<Int, UiKey>) = pairs.map { CharacterOption(it.first, it.second) }

/** اختيارات الجزء لكل نوع — من غير المكافآت (مؤجلة · §3-5). */
fun CharacterPart.options(woman: Boolean): List<CharacterOption> = when (this) {
    CharacterPart.FACE -> opts(0 to UiKey.CHAR_OPT_FACE_0, 1 to UiKey.CHAR_OPT_FACE_1, 2 to UiKey.CHAR_OPT_FACE_2, 3 to UiKey.CHAR_OPT_FACE_3)
    CharacterPart.EYES -> opts(0 to UiKey.CHAR_OPT_EYES_0, 1 to UiKey.CHAR_OPT_EYES_1, 2 to UiKey.CHAR_OPT_EYES_2, 3 to UiKey.CHAR_OPT_EYES_3)
    CharacterPart.BROWS -> opts(0 to UiKey.CHAR_OPT_BROWS_0, 1 to UiKey.CHAR_OPT_BROWS_1, 2 to UiKey.CHAR_OPT_BROWS_2, 3 to UiKey.CHAR_OPT_BROWS_3)
    CharacterPart.NOSE -> opts(0 to UiKey.CHAR_OPT_NOSE_0, 1 to UiKey.CHAR_OPT_NOSE_1, 2 to UiKey.CHAR_OPT_NOSE_2)
    CharacterPart.MOUTH -> opts(0 to UiKey.CHAR_OPT_MOUTH_0, 1 to UiKey.CHAR_OPT_MOUTH_1, 2 to UiKey.CHAR_OPT_MOUTH_2, 3 to UiKey.CHAR_OPT_MOUTH_3)
    CharacterPart.BUILD -> opts(0 to UiKey.CHAR_OPT_BUILD_0, 1 to UiKey.CHAR_OPT_BUILD_1, 2 to UiKey.CHAR_OPT_BUILD_2)
    CharacterPart.SKIN -> emptyList()
    CharacterPart.HAIR -> if (woman) {
        opts(6 to UiKey.CHAR_OPT_HAIR_6, 7 to UiKey.CHAR_OPT_HAIR_7, 8 to UiKey.CHAR_OPT_HAIR_8, 9 to UiKey.CHAR_OPT_HAIR_9, 10 to UiKey.CHAR_OPT_HAIR_10)
    } else {
        opts(
            0 to UiKey.CHAR_OPT_HAIR_0, 1 to UiKey.CHAR_OPT_HAIR_1, 2 to UiKey.CHAR_OPT_HAIR_2, 3 to UiKey.CHAR_OPT_HAIR_3,
            4 to UiKey.CHAR_OPT_HAIR_4, 5 to UiKey.CHAR_OPT_HAIR_5,
        )
    }
    CharacterPart.BEARD -> if (woman) emptyList() else opts(
        0 to UiKey.CHAR_OPT_BEARD_0, 1 to UiKey.CHAR_OPT_BEARD_1, 2 to UiKey.CHAR_OPT_BEARD_2, 3 to UiKey.CHAR_OPT_BEARD_3,
        4 to UiKey.CHAR_OPT_BEARD_4, 5 to UiKey.CHAR_OPT_BEARD_5,
    )
    CharacterPart.OUTFIT -> if (woman) opts(5 to UiKey.CHAR_OPT_OUTFIT_5, 3 to UiKey.CHAR_OPT_OUTFIT_3, 6 to UiKey.CHAR_OPT_OUTFIT_6)
    else opts(0 to UiKey.CHAR_OPT_OUTFIT_0, 1 to UiKey.CHAR_OPT_OUTFIT_1, 2 to UiKey.CHAR_OPT_OUTFIT_2)
    CharacterPart.HEAD -> if (woman) opts(0 to UiKey.CHAR_OPT_HEAD_0, 4 to UiKey.CHAR_OPT_HEAD_4, 5 to UiKey.CHAR_OPT_HEAD_5, 6 to UiKey.CHAR_OPT_HEAD_6)
    else opts(0 to UiKey.CHAR_OPT_HEAD_0, 1 to UiKey.CHAR_OPT_HEAD_1, 2 to UiKey.CHAR_OPT_HEAD_2, 3 to UiKey.CHAR_OPT_HEAD_3, 7 to UiKey.CHAR_OPT_HEAD_7)
    CharacterPart.GLASSES -> opts(0 to UiKey.CHAR_OPT_GLASSES_0, 1 to UiKey.CHAR_OPT_GLASSES_1, 2 to UiKey.CHAR_OPT_GLASSES_2, 3 to UiKey.CHAR_OPT_GLASSES_3)
}
