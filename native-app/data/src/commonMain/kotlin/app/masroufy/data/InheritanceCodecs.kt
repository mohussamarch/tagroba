package app.masroufy.data

import app.masroufy.core.Bequest
import app.masroufy.core.DistantBranch
import app.masroufy.core.EstateItem
import app.masroufy.core.EstateOwner
import app.masroufy.core.HeirKind
import app.masroufy.core.INHERITANCE_SCENARIOS_GROUP
import app.masroufy.core.InheritanceCase
import app.masroufy.core.InheritanceScenario
import app.masroufy.core.PredeceasedChild
import app.masroufy.core.TextKey
import app.masroufy.core.specialCircumstanceFromWire
import app.masroufy.core.uiText
import app.masroufy.core.wire

/**
 * حسابات الورث المحفوظة (§69.4) — مجموعة جديدة **على مستوى الحساب**، في كوتلن بس (التطبيق الحالي ما يعرفهاش).
 * الشكل: الورثة قايمة `{kind, count}` (الصفر ما بيتكتبش — قايمة مش خريطة عشان الحفظ بـmerge ما يدمجش القديم) · الأسامي قايمة
 * `{kind, values}` · الحاجات قايمة `{name, valueMinor}` · الوصية واللي مات قبله وأولاد كل شخص
 * من ذوي الأرحام والظروف الخاصة والأسامي **بيتكتبوا بس لو فيهم حاجة** · `distantRelatives` بس لو اتجاوب.
 * الاسم وأسامي الحاجات والورثة بيتقصوا لو فيهم رقم طويل (القاعدة #11 — آخر 4 بس)، والقواعد العامة `users/{uid}/{document=**}` بتغطيها ⇒ مفيش نشر.
 */
object InheritanceCodecs {
    val scenarios: DocCodec<InheritanceScenario> = codec(
        INHERITANCE_SCENARIOS_GROUP, { it.id },
        { s ->
            val c = s.input
            doc {
                req("id", s.id); req("name", sanitizeAccountNumbers(s.name)); req("estateOf", s.estateOf.wire); opt("personId", s.personId)
                req("countryCode", c.countryCode)
                // قايمة مش خريطة: الحفظ بـmerge بيدمج الخرايط المتداخلة (نوع اتشال كان هيفضل بعدده القديم)، والقايمة بتتكتب كلها من جديد
                req("heirs", HeirKind.entries.filter { (c.heirs[it] ?: 0) > 0 }.map { mapOf("kind" to it.wire, "count" to c.heirs.getValue(it).toLong()) })
                req("items", c.items.map { mapOf("name" to sanitizeAccountNumbers(it.name), "valueMinor" to it.valueMinor) })
                req("funeralMinor", c.funeralMinor); req("debtsMinor", c.debtsMinor)
                opt("bequest", c.bequest?.let { mapOf("amountMinor" to it.amountMinor, "toHeir" to it.toHeir, "heirsConsent" to it.heirsConsent) })
                opt(
                    "predeceased",
                    c.predeceasedChildren.takeIf { it.isNotEmpty() }?.map {
                        mapOf("isSon" to it.isSon, "sons" to it.sons.toLong(), "daughters" to it.daughters.toLong(), "givenInLifeMinor" to it.givenInLifeMinor)
                    },
                )
                opt("distantRelatives", c.distantRelatives)
                opt(
                    "distantBranches",
                    c.distantBranches.takeIf { it.isNotEmpty() }?.map { mapOf("parent" to it.parent.wire, "sons" to it.sons.toLong(), "daughters" to it.daughters.toLong()) },
                )
                opt("special", c.special.takeIf { it.isNotEmpty() }?.map { it.wire }?.sorted())
                opt(
                    "names",
                    HeirKind.entries.filter { !c.names[it].isNullOrEmpty() }.takeIf { it.isNotEmpty() }
                        ?.map { mapOf("kind" to it.wire, "values" to c.names.getValue(it).map(::sanitizeAccountNumbers)) },
                )
                req("createdAt", s.createdAt); req("updatedAt", s.updatedAt)
            }
        },
        { d ->
            val id = d.str("id")
            fun bad(field: String, raw: String): Nothing = throw DocumentError(uiText(TextKey.DOC_FIELD_VALUE, "$INHERITANCE_SCENARIOS_GROUP/$id", field, raw))
            fun kind(wire: String, field: String): HeirKind = HeirKind.fromWire(wire) ?: bad(field, wire)
            if (!d.has("heirs")) d.str("heirs") // إجباري — `maps` بترجّع فاضي لو ناقص، فده بيرمي «الحقل ناقص» بالرسالة المعروفة
            val heirs = d.maps("heirs").associate { kind(it.str("kind"), "heirs") to it.int("count") }
            val names = d.maps("names").associate { kind(it.str("kind"), "names") to (it.strings("values") ?: emptyList()) }
            val case = InheritanceCase(
                countryCode = d.str("countryCode"),
                heirs = heirs,
                items = d.maps("items").map { EstateItem(it.str("name"), it.long("valueMinor")) },
                funeralMinor = d.long("funeralMinor"),
                debtsMinor = d.long("debtsMinor"),
                bequest = if (d.has("bequest")) d.map("bequest").let { Bequest(it.long("amountMinor"), it.bool("toHeir"), it.bool("heirsConsent")) } else null,
                predeceasedChildren = d.maps("predeceased").map { PredeceasedChild(it.bool("isSon"), it.int("sons"), it.int("daughters"), it.long("givenInLifeMinor")) },
                distantRelatives = d.boolOrNull("distantRelatives"),
                special = (d.strings("special") ?: emptyList()).map { w -> specialCircumstanceFromWire(w) ?: bad("special", w) }.toSet(),
                names = names,
                distantBranches = d.maps("distantBranches").map { b -> DistantBranch(kind(b.str("parent"), "distantBranches"), b.int("sons"), b.int("daughters")) },
            )
            InheritanceScenario(
                id = id,
                name = d.str("name"),
                estateOf = d.wire("estateOf") { EstateOwner.fromWire(it) ?: error(it) },
                personId = d.strOrNull("personId"),
                input = case,
                createdAt = d.str("createdAt"),
                updatedAt = d.str("updatedAt"),
            )
        },
    )
}
