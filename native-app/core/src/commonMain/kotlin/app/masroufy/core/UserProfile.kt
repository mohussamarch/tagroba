package app.masroufy.core

/**
 * ملف المستخدم — نقل `src/domain/userProfile.ts` (OVERRIDES §26 و§28.1).
 * يوم الراتب لكل مستخدم (من غيره 28). الاختياري `null` = ما اتجاوبش، ومش «لأ» (قاعدة 10).
 */
data class UserProfile(
    val displayName: String?,
    val salaryMinor: Halalas?,
    val payday: Int,
    /** "male" أو "female". */
    val gender: String?,
    val supportsDependents: Boolean?,
    /** "spouse" / "children" / "parents" بترتيب ثابت. */
    val dependentKinds: List<String>?,
    val hasCar: Boolean?,
    val renter: Boolean?,
    val domesticWorker: Boolean?,
    val business: Boolean?,
    /** null = لسه ما خلّصش أسئلة البداية. */
    val onboardedAt: String?,
)

val DEPENDENT_KINDS = listOf("spouse", "children", "parents")
const val MAX_NAME_LENGTH = 60

fun emptyProfile() = UserProfile(null, null, DEFAULT_PAYDAY, null, null, null, null, null, null, null, null)

private fun safeInteger(value: Any?): Long? = when (value) {
    is Int -> value.toLong()
    is Long -> value.takeIf { it in -MAX_SAFE_HALALAS..MAX_SAFE_HALALAS }
    is Double -> value.takeIf { it % 1.0 == 0.0 && kotlin.math.abs(it) <= MAX_SAFE_HALALAS.toDouble() }?.toLong()
    else -> null
}

/** قراية ملف متخزن بأمان: الحقل الناقص أو الغلط بيبقى «ما اتجاوبش» (أو 28 ليوم الراتب) — مش بيكتب حاجة. */
fun parseStoredProfile(raw: Map<String, Any?>?): UserProfile {
    val data = raw ?: emptyMap()
    val name = (data["displayName"] as? String)?.let(JsText::trim)
    val salary = safeInteger(data["salaryMinor"])
    val payday = safeInteger(data["payday"])
    val kinds = data["dependentKinds"] as? List<*>
    fun bool(field: String) = data[field] as? Boolean
    return UserProfile(
        displayName = name?.takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH },
        salaryMinor = salary?.takeIf { it >= 0 },
        payday = payday?.takeIf { it in 1..31 }?.toInt() ?: DEFAULT_PAYDAY,
        gender = (data["gender"] as? String)?.takeIf { it == "male" || it == "female" },
        supportsDependents = bool("supportsDependents"),
        dependentKinds = kinds?.takeIf { list -> list.all { it is String && it in DEPENDENT_KINDS } }?.let { list -> DEPENDENT_KINDS.filter { it in list } },
        hasCar = bool("hasCar"), renter = bool("renter"), domesticWorker = bool("domesticWorker"), business = bool("business"),
        onboardedAt = (data["onboardedAt"] as? String)?.takeIf { it.isNotEmpty() },
    )
}

sealed interface ProfileCheck {
    data class Ok(val profile: UserProfile) : ProfileCheck
    data class Invalid(val field: String, val message: String) : ProfileCheck
}

/** تأكيد قيم الملف قبل الحفظ — الخطأ جنب حقله (spec/04). */
fun checkProfile(input: UserProfile): ProfileCheck {
    val name = input.displayName?.let(JsText::trim)
    if (name != null && name.length > MAX_NAME_LENGTH) return ProfileCheck.Invalid("displayName", "الاسم أطول من $MAX_NAME_LENGTH حرف")
    if (input.salaryMinor != null && (input.salaryMinor > MAX_SAFE_HALALAS || input.salaryMinor < 0)) {
        return ProfileCheck.Invalid("salaryMinor", "المرتب لازم يكون مبلغ صحيح مش سالب")
    }
    if (input.payday < 1 || input.payday > 31) return ProfileCheck.Invalid("payday", "يوم الراتب لازم يكون من 1 لـ 31")
    if (input.gender != null && input.gender != "male" && input.gender != "female") {
        return ProfileCheck.Invalid("gender", "اختار ذكر أو أنثى، أو سيبها فاضية")
    }
    if (input.dependentKinds != null && !input.dependentKinds.all { it in DEPENDENT_KINDS }) {
        return ProfileCheck.Invalid("dependentKinds", "اختار الزوج أو الزوجة أو الأولاد أو الأهل، أو سيبها فاضية")
    }
    // «لأ» على بيعول حد ⇒ مفيش حد بيعوله؛ الترتيب ثابت ومن غير تكرار
    val kinds = if (input.supportsDependents == false || input.dependentKinds == null) null else DEPENDENT_KINDS.filter { it in input.dependentKinds }
    return ProfileCheck.Ok(input.copy(displayName = name?.ifEmpty { null }, dependentKinds = kinds))
}
