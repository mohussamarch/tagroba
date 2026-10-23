package app.masroufy.usecase

import app.masroufy.core.Asset
import app.masroufy.core.Space

/** بلد مفتوحة ومعاها أصولها وطريقة الإضافة فيها (الأصول لكل بلد بعملتها — `FirestoreContainer.assets`). */
data class CountryAssets(val space: Space, val manage: ManageAssets, val repo: app.masroufy.port.AssetRepository)

/** أصل ومعاه بلده (للعرض في «الاستثمار» بعلامة البلد). */
data class CountryAsset(val space: Space, val asset: Asset)

/**
 * §111-6 «أسئلة الملف واحدة للحساب، والحاجة اللي بتضيفها بتحدد بلدها»: عربية في مصر وعربية في السعودية ممكن مع بعض.
 * - أسئلة الملف (عربية · إيجار · عمالة) على ملف الحساب (`UserProfile.hasCar`…) — **واحدة للبلدين** زي ما هي (مفيش تغيير).
 * - الإضافة ([add]) بتروح لبلد الحاجة (اللي اختارها المالك في الفورم) **بعملة البلد دي** — مش البلد الشغالة.
 * - [list] بيرجع أصول كل البلاد المفتوحة، كل أصل ببلده — من غير جمع بين عملتين.
 */
class AssetsAcrossCountries(private val countries: List<CountryAssets>) {
    suspend fun list(): List<CountryAsset> = countries.flatMap { c -> c.repo.listAll().filter { !it.archived }.map { CountryAsset(c.space, it) } }

    suspend fun add(spaceId: String, input: NewAsset): CountryAsset {
        val c = countries.firstOrNull { it.space.id == spaceId } ?: throw IllegalArgumentException(app.masroufy.core.uiText(app.masroufy.core.TextKey.SPACE_NOT_FOUND))
        return CountryAsset(c.space, c.manage.addAsset(input.copy(currency = c.space.currency)))
    }
}
