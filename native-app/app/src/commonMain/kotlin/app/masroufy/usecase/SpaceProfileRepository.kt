package app.masroufy.usecase

import app.masroufy.core.UserProfile
import app.masroufy.core.inSpace
import app.masroufy.core.savedFromSpace
import app.masroufy.port.ProfileRepository

/**
 * الملف (على الحساب) زي ما بتشوفه بلد واحدة — OVERRIDES §111-3: `payday` = بداية الشهر المالي **للبلد دي** (`core/CountryPayday.kt`).
 * كل اللي بيقرا `profile.payday` (الرئيسية · الشهر · الميزانية · مصادر الدخل «غيّر بداية الشهر») بياخد يوم بلده من غير تعديل فيه،
 * والحفظ من مصر بيكتب يوم مصر في خانتها وما بيلمسش يوم السعودية. التجميع: `FirestoreContainer.profile`.
 */
class SpaceProfileRepository(private val inner: ProfileRepository, private val spaceId: String) : ProfileRepository {
    override suspend fun load(): UserProfile? = inner.load()?.inSpace(spaceId)

    override suspend fun save(profile: UserProfile) = inner.save(profile.savedFromSpace(spaceId, inner.load()))
}
