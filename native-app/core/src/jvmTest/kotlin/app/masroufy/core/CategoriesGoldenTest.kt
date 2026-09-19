package app.masroufy.core

import app.masroufy.core.EntityJson.obj
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.test.Test

/** التصنيفات والألوان وملف المستخدم (native-app/golden/categories.json). */
class CategoriesGoldenTest {
    private fun check(fn: String, run: (JsonElement) -> Any?) = Golden.check("categories", fn) { json(run(it)) }
    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)
    private fun JsonElement.opt(name: String): String? = jsonObject[name]?.takeIf { it !is JsonNull }?.str

    private fun category(e: JsonElement) = Category(
        e.field("id").str, e.opt("parentId"), e.field("name").str, e.field("iconKey").str, e.field("lightColor").str, e.field("darkColor").str,
        e.field("active").jsonPrimitive.boolean, e.field("order").jsonPrimitive.int, e.opt("groupKey"), e.opt("requires"), e.opt("noCarName"), e.opt("noCarIconKey"),
    )
    private fun categories(e: JsonElement) = e.jsonArray.map(::category)
    private fun Category.toJson() = JsonObject(
        buildMap {
            put("id", json(id)); put("parentId", nullable(parentId)); put("name", json(name)); put("iconKey", json(iconKey))
            put("lightColor", json(lightColor)); put("darkColor", json(darkColor)); put("active", json(active)); put("order", json(order))
            groupKey?.let { put("groupKey", json(it)) }; requires?.let { put("requires", json(it)) }
            noCarName?.let { put("noCarName", json(it)) }; noCarIconKey?.let { put("noCarIconKey", json(it)) }
        },
    )
    private fun CategoryColorPair.toJson() = obj("lightColor" to lightColor, "darkColor" to darkColor)
    private fun Hsl.toJson() = obj("h" to h, "s" to s, "l" to l)
    private fun UserProfile.toJson() = JsonObject(
        mapOf(
            "displayName" to nullable(displayName), "salaryMinor" to nullable(salaryMinor), "payday" to json(payday), "gender" to nullable(gender),
            "supportsDependents" to nullable(supportsDependents), "dependentKinds" to nullable(dependentKinds), "hasCar" to nullable(hasCar),
            "renter" to nullable(renter), "domesticWorker" to nullable(domesticWorker), "business" to nullable(business), "onboardedAt" to nullable(onboardedAt),
        ),
    )
    private fun JsonElement.boolOrNull(name: String) = jsonObject[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.boolean
    private fun profile(e: JsonElement) = UserProfile(
        e.opt("displayName"), jsonOrNull(e.jsonObject["salaryMinor"])?.jsonPrimitive?.longOrNull, e.field("payday").jsonPrimitive.int, e.opt("gender"),
        e.boolOrNull("supportsDependents"), jsonOrNull(e.jsonObject["dependentKinds"])?.jsonArray?.map { it.str },
        e.boolOrNull("hasCar"), e.boolOrNull("renter"), e.boolOrNull("domesticWorker"), e.boolOrNull("business"), e.opt("onboardedAt"),
    )
    private fun jsonOrNull(e: JsonElement?) = e?.takeIf { it !is JsonNull }
    private fun plain(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonArray -> e.map(::plain)
        is JsonObject -> e.mapValues { (_, v) -> plain(v) }
        is JsonPrimitive -> when {
            e.isString -> e.content
            e.booleanOrNull != null -> e.booleanOrNull
            e.longOrNull != null -> e.longOrNull
            else -> e.double
        }
    }
    private fun double(e: JsonElement) = e.jsonPrimitive.double

    @Test fun colors() {
        check("hslToHex") { hslToHex(double(it.jsonArray[0]), double(it.jsonArray[1]), double(it.jsonArray[2])) }
        check("categoryColors") {
            val (h, s, l) = it.jsonArray.take(3).map(::double)
            if (it.jsonArray.size == 3) categoryColors(h, s, l).toJson() else subCategoryColors(h, s, l, it.jsonArray[3].jsonPrimitive.int).toJson()
        }
        check("swatches") {
            val colors = swatchColors(it.str)
            JsonObject(mapOf("colors" to (colors?.toJson() ?: JsonNull), "keyOf" to nullable(swatchKeyOf(colors?.lightColor?.lowercase() ?: "#000000"))))
        }
        check("firstFreeSwatch") { firstFreeSwatch(categories(it)) }
        check("hexToHsl") { hexToHsl(it.str)?.toJson() }
        check("childColors") {
            val p = it.field("parent")
            childColors(CategoryColorPair(p.field("lightColor").str, p.field("darkColor").str), it.field("index").jsonPrimitive.int).toJson()
        }
    }

    @Test fun groupCategoryOptions() {
        check("groupCategoryOptions") {
            groupCategoryOptions(categories(it.field("categories")), it.opt("keepId")).map { g ->
                obj("key" to g.key, "label" to g.label, "options" to g.options.map { o -> obj("id" to o.id, "label" to o.label, "depth" to o.depth) })
            }
        }
    }

    @Test fun manageViews() {
        fun ManagedMain.toJson() = obj("category" to category.toJson(), "subs" to subs.map { it.toJson() })
        check("manageCategoryView") {
            val v = manageCategoryView(categories(it))
            obj("groups" to v.groups.map { g -> obj("key" to g.key, "label" to g.label, "iconKey" to g.iconKey, "mains" to g.mains.map { m -> m.toJson() }) }, "hidden" to v.hidden.map { m -> m.toJson() })
        }
        check("categoryPlaceChoices") {
            val c = categoryPlaceChoices(categories(it.field("categories")), it.opt("editingId"))
            obj(
                "groups" to c.groups.map { (key, label) -> JsonObject(mapOf("key" to nullable(key), "label" to json(label))) },
                "parentGroups" to c.parentGroups.map { g -> obj("key" to g.key, "label" to g.label, "options" to g.options.map { o -> obj("id" to o.id, "label" to o.label) }) },
                "hasSubs" to c.hasSubs,
            )
        }
    }

    @Test fun presentAndFacts() {
        check("presentCategories") {
            val f = it.field("facts")
            val facts = ProfileFacts(f.boolOrNull("hasCar"), f.boolOrNull("familyDependents"), f.boolOrNull("renter"), f.boolOrNull("domesticWorker"), f.boolOrNull("business"))
            presentCategories(categories(it.field("categories")), facts).map { c -> c.toJson() }
        }
        check("factsFromProfile") {
            val f = factsFromProfile(if (it is JsonNull) null else profile(it))
            JsonObject(mapOf("hasCar" to nullable(f.hasCar), "familyDependents" to nullable(f.familyDependents), "renter" to nullable(f.renter), "domesticWorker" to nullable(f.domesticWorker), "business" to nullable(f.business)))
        }
    }

    @Test fun planCategorySave() {
        check("planCategorySave") {
            val i = it.field("input").jsonObject
            fun field(name: String): Field<String?> = if (name in i) Field.Set(i[name]?.takeIf { v -> v !is JsonNull }?.str) else Field.Unset
            val input = CategorySaveInput(
                id = i["id"]?.str, name = i.getValue("name").str, active = i.getValue("active").jsonPrimitive.boolean, iconKey = i["iconKey"]?.str,
                parentId = field("parentId"), groupKey = field("groupKey"), swatchKey = i["swatchKey"]?.str,
            )
            val plan = planCategorySave(categories(it.field("categories")), input) { "new-id" }
            obj("item" to plan.item.toJson(), "recolored" to plan.recolored.map { c -> c.toJson() })
        }
    }

    @Test fun profiles() {
        check("parseStoredProfile") { e -> parseStoredProfile(@Suppress("UNCHECKED_CAST") (plain(e) as Map<String, Any?>?)).toJson() }
        check("checkProfile") {
            when (val c = checkProfile(profile(it))) {
                is ProfileCheck.Ok -> obj("ok" to true, "profile" to c.profile.toJson())
                is ProfileCheck.Invalid -> obj("ok" to false, "field" to c.field, "message" to c.message)
            }
        }
    }

    @Test fun groupDistribution() {
        check("groupDistribution") {
            val slices = it.field("slices").jsonArray.map { s -> CategorySlice(s.opt("categoryId"), s.field("amountMinor").num, s.field("count").jsonPrimitive.int, 0) }
            val d = groupDistribution(slices, categories(it.field("categories")))
            obj(
                "grouped" to d.grouped, "totalMinor" to d.totalMinor,
                "groups" to d.groups.map { g ->
                    obj("key" to g.key, "amountMinor" to g.amountMinor, "count" to g.count, "shareTenthPercent" to g.shareTenthPercent,
                        "mains" to g.mains.map { m -> obj("categoryId" to m.categoryId, "amountMinor" to m.amountMinor, "count" to m.count, "shareTenthPercent" to m.shareTenthPercent) })
                },
            )
        }
    }
}
