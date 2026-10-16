package app.masroufy.core

/**
 * «شخصيتك» — نموذج البيانات v2 (نقل `CharacterArt.dc.html` + `CharacterBuilder.dc.html` من فرع التصميم @2df8134d حرفيًا).
 * كل قيمة رقم صغير **هو رقم النموذج الأولي نفسه** (هو اللي بيتخزن) ⇒ ممنوع تتغير الأرقام.
 * القيم الافتراضية = `DEFAULT` بتاع الرسم (SAMPLES[0] من غير غطا راس): الحقل الناقص بيترسم بالقيمة دي.
 * المكافآت (طاقية الغني `hat`، نظارة الذهب `glasses = 4`، سلسلة الذهب `bling`، البشت/العباية المطرزة `outfit = 4`) بتترسم بس —
 * مالهاش اختيار في أي قائمة (مؤجلة لقرار المالك).
 */
data class CharacterLook(
    /** 0 راجل · 1 ست — من الملف (`UserProfile.gender`)، عمره ما بيتسأل في شاشة البناء. */
    val gender: Int = 0,
    /** 0 نحيف · 1 متوسط · 2 ممتلئ. */
    val build: Int = 1,
    /** 0 بيضاوي · 1 مدوّر · 2 طويل · 3 مربع. */
    val face: Int = 0,
    val skin: Int = 2,
    /** رجالي 0..5 · حريمي 6..10. */
    val hair: Int = 1,
    /** لون الشعر (والدقن بنفس اللون). */
    val hairColor: Int = 0,
    val eyes: Int = 0,
    val brows: Int = 0,
    val nose: Int = 0,
    val mouth: Int = 1,
    /** للرجالة بس (بيتصفّر للستات). */
    val beard: Int = 2,
    /** 0 من غير · 1 مدوّرة · 2 مربعة · 3 رفيعة · 4 الذهب (مكافأة). */
    val glasses: Int = 0,
    /** 0 من غير · 1 شماغ · 2 غترة · 3 طاقية · 7 كاب (رجالي) — 4 حجاب · 5 خمار · 6 نقاب (حريمي). */
    val head: Int = 0,
    /** لون الحجاب/الخمار/النقاب/الكاب. */
    val wrap: Int = 0,
    /** 0 ثوب · 1 قميص وبنطلون · 2 بدلة (رجالي) — 5 عباية · 3 فستان · 6 تونيك وبنطلون (حريمي) · 4 مكافأة. */
    val outfit: Int = 0,
    /** من ألوان الملابس، أو ألوان العباية لما `outfit = 5`. */
    val outfitColor: Int = 0,
    /** 1 = طاقية الغني (مكافأة) — بتغطي على `head` في الرسم. */
    val hat: Int = 0,
    /** 1 = سلسلة الذهب (مكافأة). */
    val bling: Int = 0,
) {
    companion object {
        /** بداية شاشة البناء لو مفيش شخصية محفوظة (`START` في النموذج): زي الافتراضي بس من غير دقن — وبعدها `fitGender`. */
        val START: CharacterLook = CharacterLook(beard = 0)
        internal val DEFAULT = CharacterLook()
    }
}

/** رقم نسخة الشكل المتخزن (`v` جوه خريطة `character`). */
const val CHARACTER_VERSION = 2

/** أسماء الحقول بالترتيب — هي نفسها مفاتيح التخزين. */
val CHARACTER_FIELDS: List<String> = listOf(
    "gender", "build", "face", "skin", "hair", "hairColor", "eyes", "brows", "nose", "mouth", "beard", "glasses", "head", "wrap", "outfit",
    "outfitColor", "hat", "bling",
)

/** المدى المسموح لكل حقل (بنفس ترتيب [CHARACTER_FIELDS]). */
private val CHARACTER_RANGES: List<IntRange> = listOf(
    0..1, 0..2, 0..3, 0..5, 0..10, 0..5, 0..3, 0..3, 0..2, 0..3, 0..5, 0..4, 0..7, 0..5, 0..6, 0..5, 0..1, 0..1,
)

internal fun CharacterLook.fieldValues(): List<Int> =
    listOf(gender, build, face, skin, hair, hairColor, eyes, brows, nose, mouth, beard, glasses, head, wrap, outfit, outfitColor, hat, bling)

private fun lookOf(v: List<Int>) = CharacterLook(
    gender = v[0], build = v[1], face = v[2], skin = v[3], hair = v[4], hairColor = v[5], eyes = v[6], brows = v[7], nose = v[8], mouth = v[9],
    beard = v[10], glasses = v[11], head = v[12], wrap = v[13], outfit = v[14], outfitColor = v[15], hat = v[16], bling = v[17],
)

/** أي حقل برّه مداه ⇒ قيمته الافتراضية (النموذج بيقصّ الألوان وما بيرسمش الشكل المجهول؛ الرجوع للافتراضي هو المقابل الآمن). */
fun CharacterLook.normalized(): CharacterLook {
    val defaults = CharacterLook.DEFAULT.fieldValues()
    return lookOf(fieldValues().mapIndexed { i, v -> if (v in CHARACTER_RANGES[i]) v else defaults[i] })
}

/** قوايم كل نوع (أرقام النموذج بنفس ترتيب العرض) — الشعر والغطا واللبس بتختلف بين الراجل والست. */
internal val MEN_HAIR = listOf(0, 1, 2, 3, 4, 5)
internal val WOMEN_HAIR = listOf(6, 7, 8, 9, 10)
internal val MEN_HEAD = listOf(0, 1, 2, 3, 7)
internal val WOMEN_HEAD = listOf(0, 4, 5, 6)
internal val MEN_OUTFIT = listOf(0, 1, 2)
internal val WOMEN_OUTFIT = listOf(5, 3, 6)

/** رقم المكافأة في اللبس (البشت / العباية المطرزة) — بيفضل لما النوع يتغير. */
const val CHARACTER_REWARD_OUTFIT = 4

/**
 * ظبط الشخصية على النوع (`fit` في النموذج): شعر أو غطا أو لبس مش من قايمة النوع ⇒ أول اختيار له (الشعر: 6 للست · 1 للراجل؛
 * الغطا: من غير؛ اللبس: عباية للست · ثوب للراجل، ولونه الأول) — ومكافأة اللبس بتفضل. الست مالهاش دقن.
 */
fun CharacterLook.fitGender(gender: Int): CharacterLook {
    val woman = gender == 1
    var l = copy(gender = if (woman) 1 else 0)
    if (l.hair !in (if (woman) WOMEN_HAIR else MEN_HAIR)) l = l.copy(hair = if (woman) 6 else 1)
    if (l.head !in (if (woman) WOMEN_HEAD else MEN_HEAD)) l = l.copy(head = 0)
    if (l.outfit !in (if (woman) WOMEN_OUTFIT else MEN_OUTFIT) && l.outfit != CHARACTER_REWARD_OUTFIT) {
        l = l.copy(outfit = if (woman) 5 else 0, outfitColor = 0)
    }
    if (woman) l = l.copy(beard = 0)
    return l
}

/** اختيار جزء (`set` في النموذج): النقاب بيبدأ أسود (`wrap = 2`)، وأي تغيير في اللبس بيرجّع لونه للأول. */
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

/** قيمة الجزء الحالية (اللي بيتعلّم عليها الاختيار). */
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

/** اختيار لون (رقم في لوحة الألوان بتاعة المفتاح). */
fun CharacterLook.withColor(key: CharacterColorKey, index: Int): CharacterLook = when (key) {
    CharacterColorKey.SKIN -> copy(skin = index)
    CharacterColorKey.HAIR -> copy(hairColor = index)
    CharacterColorKey.WRAP -> copy(wrap = index)
    CharacterColorKey.OUTFIT -> copy(outfitColor = index)
}

/** اللون المختار حاليًا لمفتاح الألوان. */
fun CharacterLook.colorOf(key: CharacterColorKey): Int = when (key) {
    CharacterColorKey.SKIN -> skin
    CharacterColorKey.HAIR -> hairColor
    CharacterColorKey.WRAP -> wrap
    CharacterColorKey.OUTFIT -> outfitColor
}

/** الشكل المتخزن (خريطة `character` جوه `users/{uid}/profile/main`): `v = 2` + الحقول الـ18 كلها أرقام صحيحة (Long). */
fun CharacterLook.toStored(): Map<String, Any> {
    val out = LinkedHashMap<String, Any>()
    out["v"] = CHARACTER_VERSION.toLong()
    CHARACTER_FIELDS.zip(normalized().fieldValues()).forEach { (name, v) -> out[name] = v.toLong() }
    return out
}

private fun storedInt(value: Any?): Int? = when (value) {
    is Int -> value
    is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
    is Double -> value.takeIf { it % 1.0 == 0.0 && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE }?.toInt()
    else -> null
}

/**
 * قراية `character` المتخزنة: خريطة بـ`v = 2` ⇒ كل حقل معروف بيتقري بتسامح (ناقص أو مش رقم صحيح أو برّه المدى ⇒ الافتراضي).
 * مفيش خريطة أو مش خريطة أو `v` تاني ⇒ `null` (= لسه ما اتبنتش).
 */
fun parseStoredCharacter(raw: Any?): CharacterLook? {
    val map = raw as? Map<*, *> ?: return null
    if (storedInt(map["v"]) != CHARACTER_VERSION) return null
    val defaults = CharacterLook.DEFAULT.fieldValues()
    return lookOf(CHARACTER_FIELDS.mapIndexed { i, name -> storedInt(map[name]) ?: defaults[i] }).normalized()
}

/** نوع الملف ⇒ رقم الشخصية: `"female"` ⇒ 1، أي حاجة تانية (حتى `null`) ⇒ 0 (قوايم الرجالة — سؤال مفتوح للمالك). */
fun genderIndex(profileGender: String?): Int = if (profileGender == "female") 1 else 0
