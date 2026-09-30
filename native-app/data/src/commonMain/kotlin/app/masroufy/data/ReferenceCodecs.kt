package app.masroufy.data

import app.masroufy.core.Budget
import app.masroufy.core.Category
import app.masroufy.core.CategoryBudget
import app.masroufy.core.ClassificationRule
import app.masroufy.core.Currency
import app.masroufy.core.Merchant
import app.masroufy.core.NotificationReceipt
import app.masroufy.core.Person
import app.masroufy.core.RecurringItem
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.Tag
import app.masroufy.core.Wallet

/** المحافظ والتصنيفات والتجار والقواعد والأشخاص والوسوم والميزانيات والاشتراكات والتنبيهات. */
object ReferenceCodecs {
    val wallets: DocCodec<Wallet> = codec(
        "wallets", { it.id },
        { w ->
            doc {
                req("id", w.id); req("name", w.name); req("currency", w.currency.name); req("kind", w.kind)
                req("openingBalanceMinor", w.openingBalanceMinor); req("openingAt", w.openingAt); opt("accountLast4", w.accountLast4)
            }
        },
        { r ->
            Wallet(
                r.str("id"), r.str("name"), r.wire("currency", Currency::valueOf), r.str("kind"), r.long("openingBalanceMinor"),
                r.str("openingAt"), r.strOrNull("accountLast4"),
            )
        },
    )

    val categories: DocCodec<Category> = codec(
        "categories", { it.id },
        { c ->
            doc {
                req("id", c.id); nul("parentId", c.parentId); req("name", c.name); req("iconKey", c.iconKey); req("lightColor", c.lightColor)
                req("darkColor", c.darkColor); req("active", c.active); req("order", c.order); opt("groupKey", c.groupKey)
                opt("requires", c.requires); opt("noCarName", c.noCarName); opt("noCarIconKey", c.noCarIconKey)
            }
        },
        { r ->
            Category(
                r.str("id"), r.strOrNull("parentId"), r.str("name"), r.str("iconKey"), r.str("lightColor"), r.str("darkColor"),
                r.bool("active"), r.int("order"), r.strOrNull("groupKey"), r.strOrNull("requires"), r.strOrNull("noCarName"), r.strOrNull("noCarIconKey"),
            )
        },
    )

    val merchants: DocCodec<Merchant> = codec(
        "merchants", { it.id },
        { m ->
            doc {
                req("id", m.id); req("displayName", m.displayName); req("normalizedName", m.normalizedName); opt("aliases", m.aliases)
                opt("logoAsset", m.logoAsset); opt("logoSource", m.logoSource); opt("verifiedCategoryId", m.verifiedCategoryId)
            }
        },
        { r ->
            Merchant(
                r.str("id"), r.str("displayName"), r.str("normalizedName"), r.strings("aliases"), r.strOrNull("logoAsset"),
                r.strOrNull("logoSource"), r.strOrNull("verifiedCategoryId"),
            )
        },
    )

    val rules: DocCodec<ClassificationRule> = codec(
        "rules", { it.id },
        { c ->
            doc {
                req("id", c.id); req("priority", c.priority); req("matchText", c.matchText); req("matchMode", c.matchMode.wire)
                req("categoryId", c.categoryId); req("enabled", c.enabled)
            }
        },
        { r ->
            ClassificationRule(r.str("id"), r.int("priority"), r.str("matchText"), r.wire("matchMode", RuleMatchMode::fromWire), r.str("categoryId"), r.bool("enabled"))
        },
    )

    val people: DocCodec<Person> = codec(
        "people", { it.id },
        { p -> doc { req("id", p.id); req("name", p.name); req("archived", p.archived) } },
        { r -> Person(r.str("id"), r.str("name"), r.bool("archived")) },
    )

    val tags: DocCodec<Tag> = codec(
        "tags", { it.id },
        { t -> doc { req("id", t.id); req("normalizedName", t.normalizedName); req("displayName", t.displayName) } },
        { r -> Tag(r.str("id"), r.str("normalizedName"), r.str("displayName")) },
    )

    /** معرّف مستند الميزانية = مفتاح الفترة (`budgets/{periodKey}`) — فترة واحدة = ميزانية واحدة. */
    val budgets: DocCodec<Budget> = codec(
        "budgets", { it.periodKey },
        { b ->
            doc {
                req("id", b.id); req("periodKey", b.periodKey); req("periodStart", b.periodStart); req("periodEnd", b.periodEnd)
                nul("totalLimitMinor", b.totalLimitMinor); nul("thresholdPercent", b.thresholdPercent); req("createdAt", b.createdAt); req("updatedAt", b.updatedAt)
            }
        },
        { r ->
            Budget(
                r.str("id"), r.str("periodKey"), r.str("periodStart"), r.str("periodEnd"), r.longOrNull("totalLimitMinor"),
                r.intOrNull("thresholdPercent"), r.str("createdAt"), r.str("updatedAt"),
            )
        },
    )

    val categoryBudgets: DocCodec<CategoryBudget> = codec(
        "categoryBudgets", { it.id },
        { c ->
            doc {
                req("id", c.id); req("budgetId", c.budgetId); req("categoryId", c.categoryId); req("limitMinor", c.limitMinor)
                req("notifyEnabled", c.notifyEnabled); nul("thresholdPercent", c.thresholdPercent)
            }
        },
        { r -> CategoryBudget(r.str("id"), r.str("budgetId"), r.str("categoryId"), r.long("limitMinor"), r.bool("notifyEnabled"), r.intOrNull("thresholdPercent")) },
    )

    val recurringItems: DocCodec<RecurringItem> = codec(
        "recurringItems", { it.id },
        { i ->
            doc {
                req("id", i.id); req("name", i.name); req("merchantKey", i.merchantKey); req("kind", i.kind); req("cycleMonths", i.cycleMonths)
                req("expectedMinor", i.expectedMinor); req("currency", i.currency.name); req("nextDueAt", i.nextDueAt); req("active", i.active)
                req("confirmed", i.confirmed)
            }
        },
        { r ->
            RecurringItem(
                r.str("id"), r.str("name"), r.str("merchantKey"), r.str("kind"), r.int("cycleMonths"), r.long("expectedMinor"),
                r.wire("currency", Currency::valueOf), r.str("nextDueAt"), r.bool("active"), r.bool("confirmed"),
            )
        },
    )

    val notificationReceipts: DocCodec<NotificationReceipt> = codec(
        "notificationReceipts", { receiptDocId(it.eventKey) },
        { n ->
            doc {
                req("eventKey", n.eventKey); nul("threshold", n.threshold); opt("categoryId", n.categoryId); opt("recurringId", n.recurringId)
                req("periodStart", n.periodStart); req("sentAt", n.sentAt)
            }
        },
        { r ->
            NotificationReceipt(r.str("eventKey"), r.intOrNull("threshold"), r.str("periodStart"), r.str("sentAt"), r.strOrNull("categoryId"), r.strOrNull("recurringId"))
        },
    )
}
