package app.masroufy.memory

import app.masroufy.core.UserProfile
import app.masroufy.port.AccountPort
import app.masroufy.port.CategoryRepository
import app.masroufy.port.MerchantRepository
import app.masroufy.port.ProfileRepository
import app.masroufy.port.ReferenceSeedPort
import app.masroufy.port.RuleRepository
import app.masroufy.port.SeedSource
import app.masroufy.port.SeedState

/** الحساب في الذاكرة — نقل `memoryProfileRepository.ts` و`memoryAccount.ts` و`referenceSeed.ts`. */
class MemoryProfileRepository(private var stored: UserProfile? = null) : ProfileRepository {
    override suspend fun load(): UserProfile? = stored

    override suspend fun save(profile: UserProfile) {
        stored = profile
    }
}

/** حساب وهمي — بيعدّ إيميلات تغيير كلمة السر بدل ما يبعتها. */
class MemoryAccount(private val address: String? = "demo@example.com") : AccountPort {
    var resetsSent = 0
        private set

    override fun email(): String? = address

    override suspend fun sendPasswordReset() {
        if (address == null) throw IllegalStateException("الحساب ده مالوش إيميل نبعتله عليه")
        resetsSent++
    }
}

/** نفس النسخة بتتعاد في المحاولات، زي ما فايربيز بيحتفظ بالعلامة بين مرات الفتح. */
class MemoryReferenceSeed(
    private val categories: CategoryRepository,
    private val rules: RuleRepository,
    private val merchants: MerchantRepository,
) : ReferenceSeedPort {
    private var state: SeedState? = null

    override suspend fun begin(hasExistingCategories: Boolean): SeedState {
        val current = state ?: (if (hasExistingCategories) SeedState.COMPLETE else SeedState.PENDING)
        state = current
        return current
    }

    override suspend fun insertMissing(source: SeedSource) {
        if (state == SeedState.COMPLETE) return
        if (state == null) throw IllegalStateException("تجهيز المراجع لم يبدأ")
        val knownCategories = categories.listAll().map { it.id }.toSet()
        for (row in source.categories) if (row.id !in knownCategories) categories.save(row)
        val knownRules = rules.listAll().map { it.id }.toSet()
        rules.saveMany(source.rules.filter { it.id !in knownRules })
        val knownMerchants = merchants.listAll().map { it.id }.toSet()
        merchants.saveMany(source.merchants.filter { it.id !in knownMerchants })
    }

    override suspend fun complete() {
        if (state == null) throw IllegalStateException("تجهيز المراجع لم يبدأ")
        state = SeedState.COMPLETE
    }
}
