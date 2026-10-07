package app.masroufy.usecase

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.EconomicKind
import app.masroufy.core.Texts
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAppLockSettings
import app.masroufy.memory.MemoryDeviceLock
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.DeviceLockAvailability
import app.masroufy.port.LockResult
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * رسايل حالات الاستخدام اللي اتنقلت من الكود للجدول (OVERRIDES §66) بتتبع نسخة العربي الشغالة —
 * المصري بنص التطبيق الحالي بالحرف (ملفات المرجع بتتأكد كمان)، والفصحى للسعودية.
 */
class UseCaseTextVariantsTest {
    @AfterTest
    fun reset() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val add = AddTransaction(
        AddTransactionDeps(
            MemoryTransactionRepository(),
            MemoryWalletRepository(listOf(Wallet("w-1", "محفظة وهمية", Currency.SAR, "bank", 0, "2026-01-01"))),
            SequentialIdGenerator(),
            FixedClock("2026-10-05T10:00:00.000Z"),
        ),
    )

    private fun zeroAmount() = NewTransactionInput(0, occurredAt = "2026-10-05", walletId = "w-1", economicKind = EconomicKind.PURCHASE, merchantName = "متجر وهمي")

    @Test fun addTransactionErrorFollowsTheVariant() = runBlocking<Unit> {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("المبلغ لازم يكون أكبر من صفر. الاتجاه بيتحدد من نوع العملية.", assertFailsWith<IllegalArgumentException> { add.add(zeroAmount()) }.message)
        Texts.arabicVariant = ArabicVariant.MSA
        assertEquals("يجب أن يكون المبلغ أكبر من صفر. الاتجاه يُحدَّد من نوع العملية.", assertFailsWith<IllegalArgumentException> { add.add(zeroAmount()) }.message)
    }

    /** رسالة الجهاز كانت جدول ثابت بيتبني مرة واحدة — دلوقتي بتتبني وقت الطلب، فالتبديل بيوصلها. */
    @Test fun lockAvailabilityMessageIsBuiltAtCallTime() = runBlocking<Unit> {
        val lock = AppLock(MemoryDeviceLock(DeviceLockAvailability(false, "HW_UNAVAILABLE")), MemoryAppLockSettings(), now = { 0L })
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals(LockChange.Refused("حساس البصمة مش متاح دلوقتي. جرّب تاني بعد شوية."), lock.enable())
        Texts.arabicVariant = ArabicVariant.MSA
        assertEquals(LockChange.Refused("مستشعر البصمة غير متاح الآن. أعد المحاولة بعد قليل."), lock.enable())
        val device = MemoryDeviceLock(results = listOf(LockResult.OK))
        AppLock(device, MemoryAppLockSettings(), now = { 0L }).enable()
        assertEquals(listOf("أكّد لتفعيل قفل مصروفي"), device.prompts)
    }
}
