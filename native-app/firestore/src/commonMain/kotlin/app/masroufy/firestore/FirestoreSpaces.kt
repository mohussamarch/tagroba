package app.masroufy.firestore

import app.masroufy.core.Id
import app.masroufy.core.MerchantCategory
import app.masroufy.core.Space
import app.masroufy.core.SpaceTransfer
import app.masroufy.data.LedgerCodecs
import app.masroufy.data.SpaceCodecs
import app.masroufy.data.toStore
import app.masroufy.port.MerchantCategoryRepository
import app.masroufy.port.SpaceLegWrite
import app.masroufy.port.SpaceRegistry
import app.masroufy.port.SpaceTransferRepository
import app.masroufy.port.SpaceTransferWriter

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

class FirestoreSpaceTransferRepository(private val account: FirestoreSpace) : SpaceTransferRepository {
    override suspend fun listAll(): List<SpaceTransfer> = account.select(SpaceCodecs.spaceTransfers)
}

/**
 * التحويل لنفسك **ذرّيًا**: الزوج (في الحساب) والرجلين (كل واحدة في بلدها) في **دفعة كتابة واحدة** — يا الكل يا ولا حاجة
 * حتى لو النت قطع في النص. [spaceOf] بيرجّع مكان البلد **بذاكرته** (من الجلسة) عشان الكتابة تبان لحظتها.
 */
class FirestoreSpaceTransferWriter(private val account: FirestoreSpace, private val spaceOf: (String) -> FirestoreSpace) : SpaceTransferWriter {
    private val pairs = SpaceCodecs.spaceTransfers
    private val transactions = LedgerCodecs.transactions

    override suspend fun link(pair: SpaceTransfer, legs: List<SpaceLegWrite>) = commit(pair, delete = false, legs)

    override suspend fun unlink(pair: SpaceTransfer, legs: List<SpaceLegWrite>) = commit(pair, delete = true, legs)

    private suspend fun commit(pair: SpaceTransfer, delete: Boolean, legs: List<SpaceLegWrite>) {
        val batch = account.db.batch()
        val pairRef = account.collection(pairs.group).document(pair.id)
        val pairForm = pairs.mergeForm(pair)
        if (delete) batch.delete(pairRef) else batch.set(pairRef, pairForm, merge = true)
        val legForms = legs.map { leg -> Triple(spaceOf(leg.spaceId), leg.transaction.id, transactions.mergeForm(leg.transaction)) }
        for ((space, id, form) in legForms) batch.set(space.collection(transactions.group).document(id), form, merge = true)
        account.write { batch.commit() }
        if (delete) account.mirror?.applyDelete(pairs.group, listOf(pair.id)) else account.mirror?.applySet(pairs.group, pair.id, pairForm)
        for ((space, id, form) in legForms) space.mirror?.applySet(transactions.group, id, form)
    }
}

class FirestoreMerchantCategoryRepository(private val space: FirestoreSpace) : MerchantCategoryRepository {
    private val codec = SpaceCodecs.merchantCategories

    override suspend fun listAll(): Map<Id, Id> = space.select(codec).associate { it.merchantId to it.categoryId }

    override suspend fun set(merchantId: Id, categoryId: Id?) {
        if (categoryId == null) space.deleteAll(codec.group, listOf(merchantId)) else space.saveAll(codec, listOf(MerchantCategory(merchantId, categoryId)))
    }
}
