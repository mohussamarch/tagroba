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
  /** «الكاش اللي معايا دلوقتي»؛ null = اتخطى السؤال فمحفظة الكاش ما تتلمسش. */
  cashMinor: Halalas | null
  debts: OpeningDebtInput[]
}

export type OnboardingStep = 'profile' | 'cash' | 'debts'
export type OnboardingResult = { ok: true } | { ok: false; step: OnboardingStep; message: string }

/**
 * OnboardNewAccount — أسئلة البداية للحساب الجديد (OVERRIDES §26–27).
 *
 * - **كل القيم بتتأكد الأول، قبل أي كتابة.** غلطة في أي خطوة ⇒ مفيش حاجة اتكتبت، والخطأ بيقول الخطوة.
 * - الكاش = **رصيد افتتاح لمحفظة الكاش** بتاريخ النهارده (مش عملية، فمش دخل).
 * - الديون = شخص (بيتعاد استعماله لو موجود بنفس الاسم) + **دين قديم من غير عملية**.
 * - تسجيل انتهاء الأسئلة **آخر حاجة**: لو اتقطع في النص الأسئلة بترجع، والتكرار ما بيضاعفش شخص ولا دين.
 */
export function makeOnboardNewAccount(deps: {
  profile: ReturnType<typeof makeManageProfile>
  people: ReturnType<typeof makeManagePeople>
  wallets: WalletRepository
  clock: Clock
}) {
  return async function onboard(input: OnboardingInput): Promise<OnboardingResult> {
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
      cashWallet = (await deps.wallets.listAll()).find((wallet) => wallet.kind === 'cash') ?? null
      if (!cashWallet) return { ok: false, step: 'cash', message: 'مفيش محفظة كاش في الحساب' }
    }

    if (cashWallet && input.cashMinor !== null) {
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
}
