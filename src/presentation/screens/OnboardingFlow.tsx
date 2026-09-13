import { useCallback, useEffect, useState } from 'react'
import type { UserContainer } from '../../app/container'
import type { OnboardingStart, OnboardingStep, OpeningDebtInput } from '../../application/useCases/onboardAccount'
import { formatAmount } from '../../domain/formatMoney'
import { tryParseMoney } from '../../domain/money'
import { emptyProfile, type Gender, type UserProfile } from '../../domain/userProfile'
import './OnboardingFlow.css'

type DebtRow = { name: string; amount: string; kind: OpeningDebtInput['kind'] }
const STEPS = ['name', 'salary', 'about', 'cash', 'debts'] as const
type Step = typeof STEPS[number]
const DAYS = Array.from({ length: 31 }, (_, i) => i + 1)
const FOR_RESULT: Record<OnboardingStep, Step> = { profile: 'salary', cash: 'cash', debts: 'debts' }

const asInput = (minor: number) => formatAmount(minor, 'SAR', { grouping: false })

/**
 * أسئلة البداية لأي حساب ما خلصهاش — OVERRIDES §26–27. شاشة كاملة بخطوات؛ الاختياري ممكن يتساب فاضي.
 * الخانات بتبدأ بالموجود فعلًا (`onboarding.start`)، فحساب قديم ما يتكتبش فوق بياناته بقيم فاضية.
 * الشاشة ما بتحسبش ولا بتكتب: بتجمع الإجابات وتنادي `onboarding.finish` مرة واحدة في الآخر.
 */
export function OnboardingFlow({ user, onDone }: { user: UserContainer; onDone: () => void }) {
  const [start, setStart] = useState<OnboardingStart | null>(null)
  const [step, setStep] = useState<Step>('name')
  const [profile, setProfile] = useState<UserProfile>(emptyProfile())
  const [salaryText, setSalaryText] = useState('')
  const [cashText, setCashText] = useState('')
  const [debts, setDebts] = useState<DebtRow[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    setError(null)
    user.onboarding.start().then((loaded) => {
      setProfile(loaded.profile)
      setSalaryText(loaded.profile.salaryMinor === null ? '' : asInput(loaded.profile.salaryMinor))
      setCashText(loaded.cashOpening ? asInput(loaded.cashOpening.amountMinor) : '')
      setStart(loaded)
    }).catch((cause) => setError('مقدرناش نقرا بياناتك: ' + (cause instanceof Error ? cause.message : String(cause))))
  }, [user])
  useEffect(load, [load])

  const index = STEPS.indexOf(step)
  const go = (next: Step) => { setError(null); setStep(next) }
  const update = (patch: Partial<UserProfile>) => setProfile((old) => ({ ...old, ...patch }))

  function parseOptional(text: string, label: string): number | null | undefined {
    if (!text.trim()) return null
    const minor = tryParseMoney(text.trim())
    if (minor === null || minor < 0) { setError(`اكتب ${label} رقم، زي 1500 أو 1500.50`); return undefined }
    return minor
  }

  function next() {
    if (step === 'salary' && parseOptional(salaryText, 'المرتب') === undefined) return
    if (step === 'cash' && parseOptional(cashText, 'الكاش') === undefined) return
    go(STEPS[index + 1])
  }

  async function finish() {
    const salaryMinor = parseOptional(salaryText, 'المرتب')
    const cashMinor = parseOptional(cashText, 'الكاش')
    if (salaryMinor === undefined) return go('salary')
    if (cashMinor === undefined) return go('cash')
    const parsed: OpeningDebtInput[] = []
    for (const row of debts) {
      const amountMinor = tryParseMoney(row.amount.trim())
      if (!row.name.trim() || amountMinor === null || amountMinor <= 0) {
        setError('كل دين محتاج اسم ومبلغ أكبر من صفر — أو امسح السطر'); return
      }
      parsed.push({ name: row.name, kind: row.kind, amountMinor })
    }
    setBusy(true); setError(null)
    try {
      const result = await user.onboarding.finish({ profile: { ...profile, salaryMinor }, cashMinor, debts: parsed })
      if (result.ok) { onDone(); return }
      setStep(FOR_RESULT[result.step]); setError(result.message)
    } catch (cause) {
      setError('ما اتحفظش: ' + (cause instanceof Error ? cause.message : String(cause)) + '. جرّب تاني — اللي اتحفظ مش هيتكرر.')
    } finally { setBusy(false) }
  }

  const setDebt = (i: number, patch: Partial<DebtRow>) => setDebts((rows) => rows.map((row, j) => (j === i ? { ...row, ...patch } : row)))

  if (!start) {
    return (
      <div className="onboarding" role="dialog" aria-modal="true" aria-label="أهلًا بيك في مصروفي">
        <div className="onboarding__card">
          {error
            ? <><p className="settings__error" role="alert">{error}</p>
                <div className="onboarding__actions"><button type="button" className="btn" onClick={load}>جرّب تاني</button></div></>
            : <p className="onboarding__hint">بنجهّز الأسئلة…</p>}
        </div>
      </div>
    )
  }

  return (
    <div className="onboarding" role="dialog" aria-modal="true" aria-label="أهلًا بيك في مصروفي">
      <div className="onboarding__card">
        <p className="onboarding__progress">سؤال {index + 1} من {STEPS.length}</p>

        {step === 'name' && <>
          <h2 className="onboarding__title">أهلًا بيك في مصروفي</h2>
          <p className="onboarding__hint">كام سؤال سريع علشان الأرقام تبقى مظبوطة من أول يوم. أي حاجة تقدر تغيّرها بعدين من الإعدادات.</p>
          <label className="onboarding__field">
            <span>اسمك (اختياري)</span>
            <input className="settings__input" maxLength={60} value={profile.displayName ?? ''} onChange={(e) => update({ displayName: e.target.value })} />
          </label>
        </>}

        {step === 'salary' && <>
          <h2 className="onboarding__title">المرتب</h2>
          <label className="onboarding__field">
            <span>المرتب بالريال (اختياري)</span>
            <input className="settings__input" inputMode="decimal" placeholder="مثلًا 11000" value={salaryText} onChange={(e) => setSalaryText(e.target.value)} />
          </label>
          <label className="onboarding__field">
            <span>يوم نزول الراتب</span>
            <select className="settings__input" value={profile.payday} onChange={(e) => update({ payday: Number(e.target.value) })}>
              {DAYS.map((day) => <option key={day} value={day}>{day}</option>)}
            </select>
            <small className="onboarding__hint">الشهر المالي بيبدأ من اليوم ده.</small>
          </label>
        </>}

        {step === 'about' && <>
          <h2 className="onboarding__title">عنك (اختياري)</h2>
          <label className="onboarding__field">
            <span>النوع</span>
            <select className="settings__input" value={profile.gender ?? ''} onChange={(e) => update({ gender: e.target.value === '' ? null : e.target.value as Gender })}>
              <option value="">مش عايز أحدد</option><option value="male">ذكر</option><option value="female">أنثى</option>
            </select>
          </label>
          <label className="onboarding__field">
            <span>بتعول حد؟</span>
            <select className="settings__input" value={profile.supportsDependents === null ? '' : profile.supportsDependents ? 'yes' : 'no'}
              onChange={(e) => update({ supportsDependents: e.target.value === '' ? null : e.target.value === 'yes' })}>
              <option value="">مش عايز أحدد</option><option value="yes">أيوه</option><option value="no">لأ</option>
            </select>
          </label>
        </>}

        {step === 'cash' && <>
          <h2 className="onboarding__title">الكاش اللي معاك دلوقتي</h2>
          {start.cashOpening
            ? <p className="onboarding__hint">
                المكتوب هو رصيد البداية الحالي لمحفظة الكاش (من {start.cashOpening.openingAt}). لو سبته زي ما هو، مش هيتغير حاجة.
                لو غيّرته، بيبقى رصيد بداية جديد من النهارده، وعمليات الكاش اللي قبل النهارده ما تتحسبش في رصيد الكاش.
              </p>
            : <p className="onboarding__hint">ده بيبقى رصيد البداية لمحفظة الكاش من النهارده — مش بيتحسب دخل.</p>}
          <label className="onboarding__field">
            <span>المبلغ بالريال (اختياري)</span>
            <input className="settings__input" inputMode="decimal" placeholder="مثلًا 500" value={cashText} onChange={(e) => setCashText(e.target.value)} />
          </label>
        </>}

        {step === 'debts' && <>
          <h2 className="onboarding__title">عليك ديون أو ليك فلوس عند حد؟</h2>
          <p className="onboarding__hint">كل واحد بيتضاف في شاشة الأشخاص كدين قديم من قبل التطبيق. مش بيدخل في المصروف ولا الدخل.</p>
          {debts.map((row, i) => (
            <div className="onboarding__debt" key={i}>
              <input className="settings__input" placeholder="الاسم" maxLength={80} value={row.name} onChange={(e) => setDebt(i, { name: e.target.value })} />
              <input className="settings__input" inputMode="decimal" placeholder="المبلغ" value={row.amount} onChange={(e) => setDebt(i, { amount: e.target.value })} />
              <select className="settings__input" value={row.kind} onChange={(e) => setDebt(i, { kind: e.target.value as DebtRow['kind'] })}>
                <option value="receivable">ليا عنده</option><option value="loan_payable">عليا ليه</option>
              </select>
              <button type="button" className="link" onClick={() => setDebts((rows) => rows.filter((_, j) => j !== i))}>امسح السطر</button>
            </div>
          ))}
          <button type="button" className="btn btn--quiet" onClick={() => setDebts((rows) => [...rows, { name: '', amount: '', kind: 'receivable' }])}>
            ضيف شخص
          </button>
        </>}

        {error && <p className="settings__error" role="alert">{error}</p>}

        <div className="onboarding__actions">
          {step === 'debts'
            ? <button type="button" className="btn" onClick={() => void finish()} disabled={busy}>{busy ? 'بنحفظ…' : 'خلّصت'}</button>
            : <button type="button" className="btn" onClick={next}>التالي</button>}
          {index > 0 && <button type="button" className="btn btn--quiet" onClick={() => go(STEPS[index - 1])} disabled={busy}>رجوع</button>}
        </div>
      </div>
    </div>
  )
}
