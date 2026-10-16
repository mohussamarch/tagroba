// ART-STUB (replaced by r041-char-art at merge)
package app.masroufy.core

/**
 * «شخصيتك» v2 (character-spec §1.1): كل قيمة رقم صغير = رقم النموذج بالظبط (هو اللي بيتخزن ⇒ ما يتغيرش ترقيمه).
 * الافتراضي = `DEFAULT` في النموذج. نسخة مؤقتة لحد ما فرع الرسم (r041-char-art) يتدمج — نفس العقد بالحرف.
 */
data class CharacterLook(
    val gender: Int = 0, val build: Int = 1, val face: Int = 0, val skin: Int = 2, val hair: Int = 1, val hairColor: Int = 0,
    val eyes: Int = 0, val brows: Int = 0, val nose: Int = 0, val mouth: Int = 1, val beard: Int = 2, val glasses: Int = 0,
    val head: Int = 0, val wrap: Int = 0, val outfit: Int = 0, val outfitColor: Int = 0, val hat: Int = 0, val bling: Int = 0,
) {
    companion object {
        /** بداية البنّاء من غير شكل محفوظ (`START` في النموذج): الافتراضي بس من غير لحية. */
        val START: CharacterLook = CharacterLook(beard = 0)
    }
}

const val CHARACTER_VERSION = 2

private val MEN_HAIR = setOf(0, 1, 2, 3, 4, 5)
private val WOMEN_HAIR = setOf(6, 7, 8, 9, 10)
private val MEN_HEAD = setOf(0, 1, 2, 3, 7)
private val WOMEN_HEAD = setOf(0, 4, 5, 6)
private val MEN_OUTFIT = setOf(0, 1, 2)
private val WOMEN_OUTFIT = setOf(5, 3, 6)

fun CharacterLook.normalized(): CharacterLook {
    val d = CharacterLook()
    fun Int.within(range: IntRange, def: Int) = if (this in range) this else def
    return CharacterLook(
        gender = gender.within(0..1, d.gender), build = build.within(0..2, d.build), face = face.within(0..3, d.face),
        skin = skin.within(0..5, d.skin), hair = hair.within(0..10, d.hair), hairColor = hairColor.within(0..5, d.hairColor),
        eyes = eyes.within(0..3, d.eyes), brows = brows.within(0..3, d.brows), nose = nose.within(0..2, d.nose),
        mouth = mouth.within(0..3, d.mouth), beard = beard.within(0..5, d.beard), glasses = glasses.within(0..4, d.glasses),
        head = head.within(0..7, d.head), wrap = wrap.within(0..5, d.wrap), outfit = outfit.within(0..6, d.outfit),
        outfitColor = outfitColor.within(0..5, d.outfitColor), hat = hat.within(0..1, d.hat), bling = bling.within(0..1, d.bling),
    )
}

fun CharacterLook.fitGender(gender: Int): CharacterLook {
    val woman = gender == 1
    var l = copy(gender = if (woman) 1 else 0)
    if (l.hair !in (if (woman) WOMEN_HAIR else MEN_HAIR)) l = l.copy(hair = if (woman) 6 else 1)
    if (l.head !in (if (woman) WOMEN_HEAD else MEN_HEAD)) l = l.copy(head = 0)
    if (l.outfit !in (if (woman) WOMEN_OUTFIT else MEN_OUTFIT) && l.outfit != 4) l = l.copy(outfit = if (woman) 5 else 0, outfitColor = 0)
    if (woman) l = l.copy(beard = 0)
    return l
}

fun CharacterLook.with(part: CharacterPart, id: Int): CharacterLook = when (part) {
    CharacterPart.FACE -> copy(face = id)
    CharacterPart.EYES -> copy(eyes = id)
    CharacterPart.BROWS -> copy(brows = id)
    CharacterPart.NOSE -> copy(nose = id)
    CharacterPart.MOUTH -> copy(mouth = id)
    CharacterPart.BUILD -> copy(build = id)
    CharacterPart.SKIN -> copy(skin = id)
    CharacterPart.HAIR -> copy(hair = id)
    CharacterPart.BEARD -> copy(beard = id)
    CharacterPart.OUTFIT -> copy(outfit = id, outfitColor = 0)
    CharacterPart.HEAD -> if (id == 6) copy(head = id, wrap = 2) else copy(head = id)
    CharacterPart.GLASSES -> copy(glasses = id)
}

fun CharacterLook.valueOf(part: CharacterPart): Int = when (part) {
    CharacterPart.FACE -> face
    CharacterPart.EYES -> eyes
    CharacterPart.BROWS -> brows
    CharacterPart.NOSE -> nose
    CharacterPart.MOUTH -> mouth
    CharacterPart.BUILD -> build
    CharacterPart.SKIN -> skin
    CharacterPart.HAIR -> hair
    CharacterPart.BEARD -> beard
    CharacterPart.OUTFIT -> outfit
    CharacterPart.HEAD -> head
    CharacterPart.GLASSES -> glasses
}

private val FIELDS: List<Pair<String, (CharacterLook) -> Int>> = listOf(
    "gender" to { it.gender }, "build" to { it.build }, "face" to { it.face }, "skin" to { it.skin }, "hair" to { it.hair },
    "hairColor" to { it.hairColor }, "eyes" to { it.eyes }, "brows" to { it.brows }, "nose" to { it.nose }, "mouth" to { it.mouth },
    "beard" to { it.beard }, "glasses" to { it.glasses }, "head" to { it.head }, "wrap" to { it.wrap }, "outfit" to { it.outfit },
    "outfitColor" to { it.outfitColor }, "hat" to { it.hat }, "bling" to { it.bling },
)

fun CharacterLook.toStored(): Map<String, Any> =
    linkedMapOf<String, Any>("v" to CHARACTER_VERSION.toLong()).apply { for ((name, get) in FIELDS) put(name, get(this@toStored).toLong()) }

private fun storedInt(value: Any?): Int? = when (value) {
    is Int -> value
    is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
    is Double -> value.takeIf { it % 1.0 == 0.0 && kotlin.math.abs(it) < 1000.0 }?.toInt()
    else -> null
}

fun parseStoredCharacter(raw: Any?): CharacterLook? {
    val map = raw as? Map<*, *> ?: return null
    if (storedInt(map["v"]) != CHARACTER_VERSION) return null
    val d = CharacterLook()
    fun f(name: String, def: Int) = storedInt(map[name]) ?: def
    return CharacterLook(
        gender = f("gender", d.gender), build = f("build", d.build), face = f("face", d.face), skin = f("skin", d.skin),
        hair = f("hair", d.hair), hairColor = f("hairColor", d.hairColor), eyes = f("eyes", d.eyes), brows = f("brows", d.brows),
        nose = f("nose", d.nose), mouth = f("mouth", d.mouth), beard = f("beard", d.beard), glasses = f("glasses", d.glasses),
        head = f("head", d.head), wrap = f("wrap", d.wrap), outfit = f("outfit", d.outfit), outfitColor = f("outfitColor", d.outfitColor),
        hat = f("hat", d.hat), bling = f("bling", d.bling),
    ).normalized()
}

fun genderIndex(profileGender: String?): Int = if (profileGender == "female") 1 else 0
