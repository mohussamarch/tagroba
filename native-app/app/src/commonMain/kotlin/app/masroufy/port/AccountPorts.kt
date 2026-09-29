package app.masroufy.port

import app.masroufy.core.Category
import app.masroufy.core.ClassificationRule
import app.masroufy.core.Merchant
import app.masroufy.core.UserProfile

/** الحساب — نقل `ProfileRepository.ts` و`AccountPort.ts` و`ReferenceSeedPort.ts` (OVERRIDES §26). */

/** ملف المستخدم الواحد. null = لسه ما اتحفظش ولا مرة. */
interface ProfileRepository {
    suspend fun load(): UserProfile?

    suspend fun save(profile: UserProfile)
}

/** الحساب اللي داخل — تغيير كلمة السر بإيميل إعادة تعيين: التطبيق ما بيستلمش كلمة سر ولا بيكتبها. */
interface AccountPort {
    /** إيميل الحساب الداخل؛ null لو مالوش إيميل. */
    fun email(): String?

    /** بيرمي خطأ بلغة المستخدم لو ما اتبعتش. */
    suspend fun sendPasswordReset()
}

data class SeedSource(val categories: List<Category>, val rules: List<ClassificationRule>, val merchants: List<Merchant>)

enum class SeedState { PENDING, COMPLETE }

/** علامة التجهيز الدايمة، منفصلة عن المراجع اللي المستخدم بيعدّلها. */
interface ReferenceSeedPort {
    /** حساب قديم من غير علامة ومعاه تصنيفات بيتعتبر خلصان — ومش بيتصلح ضمنيًا. */
    suspend fun begin(hasExistingCategories: Boolean): SeedState

    /** بيضيف المعرّفات الغايبة بس؛ التنفيذ لازم يتأكد من الوجود ذرّيًا. */
    suspend fun insertMissing(source: SeedSource)

    suspend fun complete()
}
