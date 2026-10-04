package app.masroufy.firestore

import app.masroufy.core.Id
import app.masroufy.core.MerchantCategory
import app.masroufy.core.Space
import app.masroufy.data.SpaceCodecs
import app.masroufy.data.toStore
import app.masroufy.port.MerchantCategoryRepository
import app.masroufy.port.SpaceRegistry

/**
 * سجل البلاد (`users/{uid}/spaces/{id}` — على مستوى الحساب) وتصنيف التاجر جوه البلد (`…/spaces/{id}/merchantCategories`) — OVERRIDES §64.
 * قواعد فايربيز (`users/{uid}/{document=**}`) بتغطي المسارين ⇒ **مفيش نشر** (اتجرب على المحاكي بقواعد المشروع الحقيقية).
 */
class FirestoreSpaceRegistry(private val account: FirestoreSpace) : SpaceRegistry {
    private val codec = SpaceCodecs.spaces

    override suspend fun listAll(): List<Space> = account.select(codec)

    /** معاملة: بيكتب لو المستند مش موجود بس — جهازين بيعملوا نفس البلد مع بعض بيطلعوا بمساحة واحدة. */
    override suspend fun addIfMissing(space: Space): Boolean {
        val ref = account.collection(codec.group).document(space.id)
        val doc = codec.toStore(space)
        val written = account.db.runTransaction {
            if (get(ref).exists) return@runTransaction false
            set(ref, doc)
            true
        }
        if (written) account.mirror?.applySet(codec.group, space.id, doc)
        return written
    }

    override suspend fun save(space: Space) = account.saveAll(codec, listOf(space))
}

class FirestoreMerchantCategoryRepository(private val space: FirestoreSpace) : MerchantCategoryRepository {
    private val codec = SpaceCodecs.merchantCategories

    override suspend fun listAll(): Map<Id, Id> = space.select(codec).associate { it.merchantId to it.categoryId }

    override suspend fun set(merchantId: Id, categoryId: Id?) {
        if (categoryId == null) space.deleteAll(codec.group, listOf(merchantId)) else space.saveAll(codec, listOf(MerchantCategory(merchantId, categoryId)))
    }
}
