package app.masroufy.device

import android.Manifest
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * محفظة كل بنك على الجوال (OVERRIDES §72 — رد المالك ١): لكل صاحب صندوق ولكل بلد، والمرسل بأي حالة حروف. والمحفظة الواحدة للبلد
 * اللي النسخة الأولى من الجلسة 31 كانت بتحفظها **بتتقري**: بتبقى محفظة كل بنك مفعّل مالوش ربط، ومفتاحها القديم بيتمسح. بيانات وهمية.
 */
@RunWith(AndroidJUnit4::class)
class SmsSenderWalletsOnDeviceTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences("sms-inbox-settings", Context.MODE_PRIVATE)

    @Test fun eachBankKeepsItsWalletPerOwnerAndCountry() = runBlocking<Unit> {
        val uid = "kt-wallets-" + System.nanoTime()
        val inbox = AndroidSmsInbox(context, uid)
        inbox.enable(listOf("5550003", "BANKB"))
        inbox.setSenderWallet("sa", "BankB ", "w-2")
        inbox.setSenderWallet("eg", "BANKB", "w-eg")
        assertEquals(mapOf("bankb" to "w-2"), inbox.senderWallets("sa"))
        assertEquals(mapOf("bankb" to "w-eg"), inbox.senderWallets("eg"), "كل بلد لوحدها")
        assertEquals(emptyMap(), AndroidSmsInbox(context, "kt-other-" + System.nanoTime()).senderWallets("sa"), "كل حساب لوحده")
        inbox.setSenderWallet("sa", "BANKB", null)
        assertEquals(emptyMap(), inbox.senderWallets("sa"))
        inbox.disable()
    }

    @Test fun theOldSingleCountryWalletIsReadAsTheWalletOfEveryEnabledBank() = runBlocking<Unit> {
        val uid = "kt-legacy-" + System.nanoTime()
        val inbox = AndroidSmsInbox(context, uid)
        inbox.enable(listOf("BANKA", "BankB"))
        inbox.setSenderWallet("sa", "BANKA", "w-chosen") // ربط جديد ما بيتلمسش
        check(prefs.edit().putString("autoTarget|$uid|sa", "w-old").commit())
        assertEquals(mapOf("banka" to "w-chosen", "bankb" to "w-old"), inbox.senderWallets("sa"))
        assertEquals(false, prefs.contains("autoTarget|$uid|sa"), "المفتاح القديم اتحوّل واتمسح")
        assertEquals(mapOf("banka" to "w-chosen", "bankb" to "w-old"), inbox.senderWallets("sa"), "مرة واحدة بس")
        inbox.disable()
    }
}
