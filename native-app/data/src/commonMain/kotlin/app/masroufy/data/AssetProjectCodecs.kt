package app.masroufy.data

import app.masroufy.core.Asset
import app.masroufy.core.AssetLot
import app.masroufy.core.AssetPrice
import app.masroufy.core.AssetSale
import app.masroufy.core.Currency
import app.masroufy.core.Project
import app.masroufy.core.ProjectKind
import app.masroufy.core.ProjectLink
import app.masroufy.core.ProjectRule
import app.masroufy.core.RuleMatchMode

/** الأصول والمشاريع. */
object AssetProjectCodecs {
    val assets: DocCodec<Asset> = codec(
        "assets", { it.id },
        { a ->
            doc {
                req("id", a.id); req("name", a.name); req("kind", a.kind); req("unitLabel", a.unitLabel); req("currency", a.currency.name)
                opt("feedSymbol", a.feedSymbol); req("archived", a.archived); opt("note", a.note)
            }
        },
        { r ->
            Asset(
                r.str("id"), r.str("name"), r.str("kind"), r.str("unitLabel"), r.wire("currency", Currency::valueOf), r.bool("archived"),
                r.strOrNull("feedSymbol"), r.strOrNull("note"),
            )
        },
    )

    /** الكمية عدد صحيح مضروب في 10⁸ (`Quantity`) — زي التطبيق الحالي، مش عشري. */
    val assetLots: DocCodec<AssetLot> = codec(
        "assetLots", { it.id },
        { l ->
            doc {
                req("id", l.id); req("assetId", l.assetId); req("purchasedAt", l.purchasedAt); req("quantity", l.quantity)
                req("principalMinor", l.principalMinor); req("feeMinor", l.feeMinor); opt("transactionId", l.transactionId)
            }
        },
        { r ->
            AssetLot(r.str("id"), r.str("assetId"), r.str("purchasedAt"), r.long("quantity"), r.long("principalMinor"), r.long("feeMinor"), r.strOrNull("transactionId"))
        },
    )

    val assetSales: DocCodec<AssetSale> = codec(
        "assetSales", { it.id },
        { s ->
            doc {
                req("id", s.id); req("assetId", s.assetId); req("soldAt", s.soldAt); req("quantity", s.quantity)
                req("grossProceedsMinor", s.grossProceedsMinor); req("feeMinor", s.feeMinor); opt("transactionId", s.transactionId)
            }
        },
        { r ->
            AssetSale(r.str("id"), r.str("assetId"), r.str("soldAt"), r.long("quantity"), r.long("grossProceedsMinor"), r.long("feeMinor"), r.strOrNull("transactionId"))
        },
    )

    /** سعر واحد لكل أصل ⇒ معرّف المستند = معرّف الأصل. */
    val assetPrices: DocCodec<AssetPrice> = codec(
        "assetPrices", { it.assetId },
        { p -> doc { req("assetId", p.assetId); req("pricePerUnitMinor", p.pricePerUnitMinor); req("asOf", p.asOf); req("source", p.source) } },
        { r -> AssetPrice(r.str("assetId"), r.long("pricePerUnitMinor"), r.str("asOf"), r.str("source")) },
    )

    /**
     * `kind` حقل كوتلن بس (OVERRIDES §47). **الشخصي ما بيتكتبش** — ده نفس شكل مشاريع التطبيق الحالي، فالمستند
     * بيفضل زي ما هو لو اتحفظ من الجديد، والمشروع من غير نوع بيتقرا شخصي.
     */
    val projects: DocCodec<Project> = codec(
        "projects", { it.id },
        { p ->
            doc {
                req("id", p.id); req("name", p.name); req("normalizedName", p.normalizedName); req("archived", p.archived); req("createdAt", p.createdAt)
                opt("kind", p.kind.takeIf { it != ProjectKind.PERSONAL }?.wire)
            }
        },
        { r -> Project(r.str("id"), r.str("name"), r.str("normalizedName"), r.bool("archived"), r.str("createdAt"), ProjectKind.fromWire(r.strOrNull("kind"))) },
    )

    val projectLinks: DocCodec<ProjectLink> = codec(
        "projectLinks", { it.id },
        { l -> doc { req("id", l.id); req("projectId", l.projectId); req("transactionId", l.transactionId); req("source", l.source); req("createdAt", l.createdAt) } },
        { r -> ProjectLink(r.str("id"), r.str("projectId"), r.str("transactionId"), r.str("source"), r.str("createdAt")) },
    )

    val projectRules: DocCodec<ProjectRule> = codec(
        "projectRules", { it.id },
        { p ->
            doc {
                req("id", p.id); req("projectId", p.projectId); req("matchText", p.matchText); req("matchMode", p.matchMode.wire)
                req("direction", p.direction); req("enabled", p.enabled); req("createdAt", p.createdAt)
            }
        },
        { r ->
            ProjectRule(
                r.str("id"), r.str("projectId"), r.str("matchText"), r.wire("matchMode", RuleMatchMode::fromWire), r.str("direction"),
                r.bool("enabled"), r.str("createdAt"),
            )
        },
    )
}
