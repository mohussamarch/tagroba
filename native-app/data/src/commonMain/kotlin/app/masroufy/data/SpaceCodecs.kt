package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.MERCHANT_CATEGORIES_GROUP
import app.masroufy.core.MerchantCategory
import app.masroufy.core.SPACES_GROUP
import app.masroufy.core.SPACE_TRANSFERS_GROUP
import app.masroufy.core.Space
import app.masroufy.core.SpaceTransfer

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

    /** التحويل لنفسك (§64) على مستوى الحساب — كل رجل بعملتها ومبلغها زي الكشف، **ومفيش سعر متخزن**. */
    val spaceTransfers: DocCodec<SpaceTransfer> = codec(
        SPACE_TRANSFERS_GROUP, { it.id },
        { t ->
            doc {
                req("id", t.id)
                req("fromSpaceId", t.fromSpaceId); req("fromTransactionId", t.fromTransactionId); req("fromAmountMinor", t.fromAmountMinor); req("fromCurrency", t.fromCurrency.name)
                req("toSpaceId", t.toSpaceId); req("toTransactionId", t.toTransactionId); req("toAmountMinor", t.toAmountMinor); req("toCurrency", t.toCurrency.name)
                req("createdAt", t.createdAt); opt("note", t.note)
            }
        },
        { d ->
            SpaceTransfer(
                d.str("id"), d.str("fromSpaceId"), d.str("fromTransactionId"), d.long("fromAmountMinor"), d.wire("fromCurrency", Currency::valueOf),
                d.str("toSpaceId"), d.str("toTransactionId"), d.long("toAmountMinor"), d.wire("toCurrency", Currency::valueOf), d.str("createdAt"), d.strOrNull("note"),
            )
        },
    )

    val merchantCategories: DocCodec<MerchantCategory> = codec(
        MERCHANT_CATEGORIES_GROUP, { it.merchantId },
        { m -> doc { req("merchantId", m.merchantId); req("categoryId", m.categoryId) } },
        { d -> MerchantCategory(d.str("merchantId"), d.str("categoryId")) },
    )
}
