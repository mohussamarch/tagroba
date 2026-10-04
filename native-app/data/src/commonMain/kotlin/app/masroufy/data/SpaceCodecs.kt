package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.MERCHANT_CATEGORIES_GROUP
import app.masroufy.core.MerchantCategory
import app.masroufy.core.SPACES_GROUP
import app.masroufy.core.Space

/**
 * «حساب لكل بلد» (OVERRIDES §41 · §64) — مجموعات جديدة في كوتلن بس، على نفس قواعد الباقي.
 * - `spaces` (على مستوى الحساب): مستند لكل بلد تانية بمعرّفها (`eg`). مساحة السعودية **مش هنا** — بتتبني في الكود.
 *   بيانات البلد تحت نفس المستند كمجموعات فرعية (`users/{uid}/spaces/eg/transactions/…`) — والمستمع على `spaces` ما بيشوفهاش.
 * - `merchantCategories` (جوه المساحة): تصنيف التاجر المشترك في البلد دي — معرّف المستند = معرّف التاجر.
 * قواعد فايربيز (`users/{uid}/{document=**}`) بتغطي المسارين ⇒ مش محتاجة نشر.
 */
object SpaceCodecs {
    val spaces: DocCodec<Space> = codec(
        SPACES_GROUP, { it.id },
        { s ->
            doc {
                req("id", s.id); req("name", s.name); req("countryCode", s.countryCode); req("currency", s.currency.name)
                req("createdAt", s.createdAt); req("archived", s.archived)
            }
        },
        { d -> Space(d.str("id"), d.str("name"), d.str("countryCode"), d.wire("currency", Currency::valueOf), d.str("createdAt"), d.bool("archived")) },
    )

    val merchantCategories: DocCodec<MerchantCategory> = codec(
        MERCHANT_CATEGORIES_GROUP, { it.merchantId },
        { m -> doc { req("merchantId", m.merchantId); req("categoryId", m.categoryId) } },
        { d -> MerchantCategory(d.str("merchantId"), d.str("categoryId")) },
    )
}
