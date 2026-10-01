package app.masroufy.firestore

import app.masroufy.core.SharedMerchantEntry
import app.masroufy.core.UserProfile
import app.masroufy.core.parseStoredProfile
import app.masroufy.core.sharedMerchantKey
import app.masroufy.data.Doc
import app.masroufy.data.DocCodec
import app.masroufy.data.ReferenceCodecs
import app.masroufy.data.toStore
import app.masroufy.port.ProfileRepository
import app.masroufy.port.ReferenceSeedPort
import app.masroufy.port.SeedSource
import app.masroufy.port.SeedState
import app.masroufy.port.SharedMerchantCatalogPort
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore

/** ملف المستخدم وتجهيز المراجع أول مرة وقاعدة التجار المشتركة — نقل `firestoreProfileRepository.ts` و`referenceSeed.ts` و`sharedMerchantCatalogRepository.ts`. */

/**
 * ملف المستخدم في `users/{uid}/profile/main`. القراية بتعدّي على `parseStoredProfile` (حقل غلط ما يوقعش فتح التطبيق)،
 * والحفظ بيكتب الملف كله (زي `setDoc` في التطبيق الحالي) — بكل حقوله حتى الفاضية (`null` = ما اتجاوبش).
 */
class FirestoreProfileRepository(private val space: FirestoreSpace) : ProfileRepository {
    private fun ref() = space.db.document("${space.root}/profile/main")

    override suspend fun load(): UserProfile? = ref().get().rawData()?.let(::parseStoredProfile)

    override suspend fun save(profile: UserProfile) {
        val doc: Doc = linkedMapOf(
            "displayName" to profile.displayName, "salaryMinor" to profile.salaryMinor, "payday" to profile.payday.toLong(),
            "gender" to profile.gender, "supportsDependents" to profile.supportsDependents, "dependentKinds" to profile.dependentKinds,
            "hasCar" to profile.hasCar, "renter" to profile.renter, "domesticWorker" to profile.domesticWorker, "business" to profile.business,
            "onboardedAt" to profile.onboardedAt,
        )
        space.write { ref().set(doc) }
    }
}

/**
 * علامة «المراجع اتجهزت» (`users/{uid}/initialization/references-v1`) — مش طريقة لاسترجاع افتراضيات اتمسحت.
 * التجهيز بمعاملات: كل صفحة 100 بتتأكد إن العلامة لسه «pending» وبتكتب الناقص بس — جهازين بيجهزوا مع بعض ما يكرروش.
 */
class FirestoreReferenceSeed(private val space: FirestoreSpace) : ReferenceSeedPort {
    private fun marker() = space.db.document("${space.root}/initialization/references-v1")

    private fun stateOf(data: Doc?): SeedState = when (data?.get("state")) {
        "pending" -> SeedState.PENDING
        "complete" -> SeedState.COMPLETE
        else -> throw IllegalStateException("حالة تجهيز المراجع غير سليمة")
    }

    override suspend fun begin(hasExistingCategories: Boolean): SeedState {
        // علامة «خلص» محلية بتسمح بالفتح من غير نت بعد كده
        val cached = marker().get().rawData()
        if (cached != null && stateOf(cached) == SeedState.COMPLETE) return SeedState.COMPLETE
        return space.db.runTransaction {
            val current = get(marker()).rawData()
            if (current != null) return@runTransaction stateOf(current)
            val state = if (hasExistingCategories) SeedState.COMPLETE else SeedState.PENDING
            set(marker(), mapOf("state" to state.name.lowercase(), "version" to 1L))
            state
        }
    }

    override suspend fun insertMissing(source: SeedSource) {
        insert(ReferenceCodecs.categories, source.categories)
        insert(ReferenceCodecs.rules, source.rules)
        insert(ReferenceCodecs.merchants, source.merchants)
    }

    private suspend fun <T> insert(codec: DocCodec<T>, rows: List<T>) {
        for (page in rows.chunked(100)) {
            val written = space.db.runTransaction {
                val current = get(marker()).rawData() ?: throw IllegalStateException("تجهيز المراجع لم يبدأ")
                if (stateOf(current) == SeedState.COMPLETE) return@runTransaction emptyList()
                val refs = page.map { space.collection(codec.group).document(codec.id(it)) }
                val snaps = refs.map { get(it) }
                val fresh = mutableListOf<Pair<String, Doc>>()
                snaps.forEachIndexed { i, snap ->
                    if (!snap.exists) {
                        val doc = codec.toStore(page[i])
                        set(refs[i], doc)
                        fresh += refs[i].id to doc
                    }
                }
                fresh
            }
            space.mirror?.let { m -> written.forEach { (id, doc) -> m.applySet(codec.group, id, doc) } }
        }
    }

    override suspend fun complete() {
        space.db.runTransaction {
            val current = get(marker()).rawData() ?: throw IllegalStateException("تجهيز المراجع لم يبدأ")
            if (stateOf(current) == SeedState.PENDING) set(marker(), mapOf("state" to "complete", "version" to 1L))
        }
    }
}

/**
 * قاعدة التجار المشتركة `sharedMerchants/{key}` (OVERRIDES §25) — **برا مساحة المستخدم**، والحماية في `firestore.rules`
 * (قراية لأي داخل، كتابة مش مؤكد بس بحقول محددة و`updatedAt` = وقت السيرفر، ومفيش مسح). القراية بالتغييرات بس (`updatedAt` بعد آخر مزامنة)
 * عشان حد الـ50,000 قراية في اليوم.
 */
class FirestoreSharedMerchantCatalog(private val db: FirebaseFirestore) : SharedMerchantCatalogPort {
    private fun col() = db.collection(COLLECTION)

    override suspend fun listChangedSince(sinceIso: String?): List<SharedMerchantEntry> {
        val base = if (sinceIso != null) col().where { "updatedAt" greaterThan timestampOf(sinceIso) } else col()
        return base.orderBy("updatedAt").get().documents.mapNotNull { it.rawData()?.let(::entryOf) }
    }

    /** حقل واحد بمساواة ⇒ فهرس تلقائي. */
    override suspend fun listConfirmed(): List<SharedMerchantEntry> =
        col().where { "confirmed" equalTo true }.get().documents.mapNotNull { it.rawData()?.let(::entryOf) }

    override suspend fun get(normalizedName: String): SharedMerchantEntry? = col().document(sharedMerchantKey(normalizedName)).get().rawData()?.let(::entryOf)

    override suspend fun save(entry: SharedMerchantEntry) {
        col().document(sharedMerchantKey(entry.normalizedName)).set(
            mapOf(
                "normalizedName" to entry.normalizedName, "displayName" to entry.displayName, "aliases" to entry.aliases,
                "categoryId" to entry.categoryId, "confirmed" to false, "updatedAt" to FieldValue.serverTimestamp,
            ),
        )
    }

    private companion object {
        const val COLLECTION = "sharedMerchants"

        /** نفس `fromDoc`: من غير اسم موحد ⇒ مستند مش صالح ويتساب · أي `confirmed` غير `true` بالظبط = مش مؤكد. */
        fun entryOf(d: Doc): SharedMerchantEntry? {
            val name = d["normalizedName"] as? String
            if (name.isNullOrEmpty()) return null
            return SharedMerchantEntry(
                normalizedName = name,
                displayName = d["displayName"] as? String ?: name,
                aliases = (d["aliases"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                categoryId = d["categoryId"] as? String,
                confirmed = d["confirmed"] == true,
                updatedAt = d["updatedAt"] as? String,
            )
        }
    }
}
