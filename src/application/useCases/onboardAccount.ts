import type { Halalas } from '../../domain/money'
import { checkProfile, type UserProfile } from '../../domain/userProfile'
import type { Clock, WalletRepository } from '../ports/repositories'
import type { makeManagePeople } from './managePeople'
import type { makeManageProfile } from './manageProfile'

export interface OpeningDebtInput {
  name: string
  /** «ليا عنده» = receivable، «عليا ليه» = loan_payable (OVERRIDES §27). */
  kind: 'receivable' | 'loan_payable'
  amountMinor: Halalas
}

export interface OnboardingInput {
  profile: UserProfile
  /**
   * «الكاش اللي معايا دلوقتي». null = اتخطى السؤال، أو نفس رصيد البداية الحالي بالظبط
   * ⇒ محفظة الكاش ما تتلمسش.
   */
  cashMinor: Halalas | null
  debts: OpeningDebtInput[]
}

/** اللي الأسئلة بتبدأ بيه: بيانات الملف المتحفظة، ورصيد بداية محفظة الكاش لو اتحدد قبل كده. */
export interface OnboardingStart {
  profile: UserProfile
  /** null = مفيش محفظة كاش، أو رصيد بدايتها لسه صفر (حساب جديد). */
  cashOpening: { amountMinor: Halalas; openingAt: string } | null
}

export type OnboardingStep = 'profile' | 'cash' | 'debts'
export type OnboardingResult = { ok: true } | { ok: false; step: OnboardingStep; message: string }

/**
 * أسئلة البداية — لأي حساب ما خلصهاش قبل كده، جديد أو قديم (OVERRIDES §26–27).
 *
 * - `start` بيرجّع الموجود فعلًا عشان يتكتب في الخانات: حساب قديم ما يتكتبش فوق بياناته بقيم فاضية.
 * - **كل القيم بتتأكد الأول، قبل أي كتابة.** غلطة في أي خطوة ⇒ مفيش حاجة اتكتبت، والخطأ بيقول الخطوة.
 * - الكاش = **رصيد افتتاح لمحفظة الكاش** بتاريخ النهارده (مش عملية، فمش دخل).
 *   **نفس الرصيد الحالي بالظبط ⇒ ما بيتغيرش حاجة** (رد المالك: «يوريه الرصيد ويسيبه يغيّر»).
 * - الديون = شخص (بيتعاد استعماله لو موجود بنفس الاسم) + **دين قديم من غير عملية**.
 * - تسجيل انتهاء الأسئلة **آخر حاجة**: لو اتقطع في النص الأسئلة بترجع، والتكرار ما بيضاعفش شخص ولا دين.
 */
export function makeOnboardAccount(deps: {
  profile: ReturnType<typeof makeManageProfile>
  people: ReturnType<typeof makeManagePeople>
  wallets: WalletRepository
  clock: Clock
}) {
  const findCashWallet = async () => (await deps.wallets.listAll()).find((wallet) => wallet.kind === 'cash') ?? null

  async function start(): Promise<OnboardingStart> {
    const [profile, cash] = await Promise.all([deps.profile.load(), findCashWallet()])
    const cashOpening = cash && cash.openingBalanceMinor !== 0
      ? { amountMinor: cash.openingBalanceMinor, openingAt: cash.openingAt }
      : null
    return { profile, cashOpening }
  }

  async function finish(input: OnboardingInput): Promise<OnboardingResult> {
    // A stale setup screen must not reset cash/profile or recreate a settled opening debt.
    if (!deps.profile.needsOnboarding(await deps.profile.load())) return { ok: true }
    const check = checkProfile(input.profile)
    if (!check.ok) return { ok: false, step: 'profile', message: check.message }
    if (input.cashMinor !== null && (!Number.isSafeInteger(input.cashMinor) || input.cashMinor < 0)) {
      return { ok: false, step: 'cash', message: 'مبلغ الكاش لازم يكون رقم مش سالب' }
    }
    for (const debt of input.debts) {
      if (!debt.name.trim()) return { ok: false, step: 'debts', message: 'اكتب اسم كل شخص' }
      if (debt.name.trim().length > 80) return { ok: false, step: 'debts', message: 'الاسم أطول من 80 حرف' }
      if (!Number.isSafeInteger(debt.amountMinor) || debt.amountMinor <= 0) {
        return { ok: false, step: 'debts', message: `مبلغ دين «${debt.name.trim()}» لازم يكون أكبر من صفر` }
      }
    }

    let cashWallet = null
    if (input.cashMinor !== null) {
      cashWallet = await findCashWallet()
      if (!cashWallet) return { ok: false, step: 'cash', message: 'مفيش محفظة كاش في الحساب' }
    }

    if (cashWallet && input.cashMinor !== null && input.cashMinor !== cashWallet.openingBalanceMinor) {
      await deps.wallets.save({ ...cashWallet, openingBalanceMinor: input.cashMinor, openingAt: deps.clock.nowIso().slice(0, 10) })
    }

    if (input.debts.length > 0) {
      const rows = await deps.people.listWithBalances()
      const idByName = new Map(rows.map((row) => [row.person.name.trim(), row.person.id]))
      for (const debt of input.debts) {
        const name = debt.name.trim()
        let personId = idByName.get(name)
        if (!personId) {
          personId = (await deps.people.addPerson(name)).id
          idByName.set(name, personId)
        }
        // إعادة المحاولة بعد انقطاع: نفس الدين القديم موجود ⇒ ما يتكررش
        const already = rows.find((row) => row.person.id === personId)?.obligations.some(({ obligation }) =>
          obligation.originTransactionId === null && obligation.kind === debt.kind && obligation.originalMinor === debt.amountMinor)
        if (!already) await deps.people.addOpeningDebt({ personId, kind: debt.kind, amountMinor: debt.amountMinor })
      }
    }

    const done = await deps.profile.completeOnboarding(check.profile)
    return done.ok ? { ok: true } : { ok: false, step: 'profile', message: done.message }
  }

  return { start, finish }
}
