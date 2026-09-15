import { useEffect, useState } from 'react'
import type { UserContainer } from '../../app/container'
import { formatAmount } from '../../domain/formatMoney'
import { tryParseMoney } from '../../domain/money'
import type { Gender, ProfileField, UserProfile } from '../../domain/userProfile'
import { DependentKindsField, YesNoField } from './ProfileAnswerFields'

const text = (error: unknown) => (error instanceof Error ? error.message : String(error))
const DAYS = Array.from({ length: 31 }, (_, i) => i + 1)
const FIELD = { fieldClass: 'settings__field', labelClass: 'settings__label', inputClass: 'settings__input' }

/**
 * قسم «الحساب» — أول حاجة في الإعدادات (OVERRIDES §26، طلب المالك 2026-09-13).
 * يوم الراتب لكل مستخدم والفترات بتتحسب منه؛ الاسم والمرتب والنوع و«بيعول حد» اختياريين
 * (فاضي = «مش عايز أحدد»، مش صفر). تغيير كلمة السر بإيميل — التطبيق ما بيشوفش كلمة السر.
 * المعلومات اللي بتظهر تصنيفات (سيارة، بتعول مين، إيجار، عمالة، شغل خاص) — OVERRIDES §28.1.
 * مكوّن مستقل عشان حد الـ300 سطر في SettingsScreen.
 */
export function AccountCard({ user, onSaved }: { user: UserContainer; onSaved: () => void }) {
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [salaryText, setSalaryText] = useState('')
  const [loadError, setLoadError] = useState<string | null>(null)
  const [fieldError, setFieldError] = useState<{ field: ProfileField; message: string } | null>(null)
  const [busy, setBusy] = useState<'none' | 'save' | 'reset'>('none')
  const [message, setMessage] = useState<string | null>(null)
  const email = user.manageProfile.email()

  useEffect(() => {
    let alive = true
    user.manageProfile.load()
      .then((loaded) => {
        if (!alive) return
        setProfile(loaded)
        setSalaryText(loaded.salaryMinor === null ? '' : formatAmount(loaded.salaryMinor, 'SAR', { grouping: false }))
      })
      .catch((error) => { if (alive) setLoadError(text(error)) })
    return () => { alive = false }
  }, [user])

  function update(patch: Partial<UserProfile>) {
    setProfile((old) => (old ? { ...old, ...patch } : old))
    setMessage(null)
  }

  async function save() {
    if (!profile) return
    const typed = salaryText.trim()
    const salaryMinor = typed === '' ? null : tryParseMoney(typed)
    if (typed !== '' && salaryMinor === null) {
      setFieldError({ field: 'salaryMinor', message: 'اكتب المرتب رقم، زي 11000 أو 11000.50' })
      return
    }
    setBusy('save'); setFieldError(null); setMessage(null)
    try {
      const check = await user.manageProfile.save({ ...profile, salaryMinor })
      if (!check.ok) { setFieldError(check); return }
      setProfile(check.profile)
      setMessage('بيانات الحساب اتحفظت.')
      onSaved()
    } catch (error) {
      setMessage('ما اتحفظش: ' + text(error))
    } finally { setBusy('none') }
  }

  async function resetPassword() {
    setBusy('reset'); setMessage(null)
    try {
      await user.manageProfile.sendPasswordReset()
      setMessage(`اتبعت إيميل لتغيير كلمة السر على ${email}. افتح الإيميل وكمّل من اللينك.`)
    } catch (error) {
      setMessage('ما اتبعتش: ' + text(error))
    } finally { setBusy('none') }
  }

  const errorFor = (field: ProfileField) =>
    fieldError?.field === field && <p className="settings__error" role="alert">{fieldError.message}</p>

  return (
    <section className="card" aria-label="الحساب">
      <h2 className="card__title">الحساب</h2>

      <div className="settings__field">
        <span className="settings__label">الإيميل</span>
        <span dir="ltr" style={{ textAlign: 'end' }}>{email ?? 'مش متاح'}</span>
        {email && (
          <button type="button" className="btn btn--quiet" onClick={() => void resetPassword()} disabled={busy !== 'none'}>
            {busy === 'reset' ? 'بنبعت…' : 'ابعتلي إيميل لتغيير كلمة السر'}
          </button>
        )}
      </div>

      {loadError && <p className="settings__error" role="alert">مقدرناش نجيب بيانات الحساب: {loadError}</p>}
      {!profile && !loadError && <p className="settings__hint" role="status">بنحمّل بيانات الحساب…</p>}

      {profile && <>
        <label className="settings__field">
          <span className="settings__label">الاسم (اختياري)</span>
          <input className="settings__input" type="text" maxLength={60} value={profile.displayName ?? ''}
            onChange={(e) => update({ displayName: e.target.value })} />
          {errorFor('displayName')}
        </label>

        <label className="settings__field">
          <span className="settings__label">المرتب بالريال (اختياري)</span>
          <input className="settings__input" type="text" inputMode="decimal" value={salaryText} placeholder="مثلًا 11000"
            onChange={(e) => { setSalaryText(e.target.value); setMessage(null) }} />
          {errorFor('salaryMinor')}
        </label>

        <label className="settings__field">
          <span className="settings__label">يوم نزول الراتب</span>
          <select className="settings__input" value={profile.payday} onChange={(e) => update({ payday: Number(e.target.value) })}>
            {DAYS.map((day) => <option key={day} value={day}>{day}</option>)}
          </select>
          <span className="settings__hint">الشهر المالي بيبدأ من اليوم ده. لو الشهر أقصر، بيبدأ آخر يوم فيه.</span>
          {errorFor('payday')}
        </label>

        <label className="settings__field">
          <span className="settings__label">النوع (اختياري)</span>
          <select className="settings__input" value={profile.gender ?? ''}
            onChange={(e) => update({ gender: e.target.value === '' ? null : e.target.value as Gender })}>
            <option value="">مش عايز أحدد</option>
            <option value="male">ذكر</option>
            <option value="female">أنثى</option>
          </select>
          {errorFor('gender')}
        </label>

        <YesNoField {...FIELD} label="بتعول حد؟ (اختياري)" value={profile.supportsDependents}
          onChange={(supportsDependents) => update({ supportsDependents })} />
        {errorFor('supportsDependents')}
        {profile.supportsDependents === true && (
          <DependentKindsField fieldClass={FIELD.fieldClass} labelClass={FIELD.labelClass} value={profile.dependentKinds}
            gender={profile.gender} onChange={(dependentKinds) => update({ dependentKinds })} />
        )}
        {errorFor('dependentKinds')}

        <p className="settings__hint">الإجابات دي بتظهر التصنيفات اللي تخصك بس. أي سؤال سايبه فاضي، تصنيفاته بتفضل مخفية.</p>
        <YesNoField {...FIELD} label="عندك سيارة؟" value={profile.hasCar} onChange={(hasCar) => update({ hasCar })} />
        {errorFor('hasCar')}
        <YesNoField {...FIELD} label="ساكن بإيجار؟" value={profile.renter} onChange={(renter) => update({ renter })} />
        {errorFor('renter')}
        <YesNoField {...FIELD} label="عندك عمالة منزلية؟" value={profile.domesticWorker}
          onChange={(domesticWorker) => update({ domesticWorker })} />
        {errorFor('domesticWorker')}
        <YesNoField {...FIELD} label="عندك شغل خاص أو بيزنس؟" value={profile.business} onChange={(business) => update({ business })} />
        {errorFor('business')}

        <button type="button" className="btn" onClick={() => void save()} disabled={busy !== 'none'}>
          {busy === 'save' ? 'بنحفظ…' : 'احفظ بيانات الحساب'}
        </button>
      </>}

      {message && <p className="notice" role="status">{message}</p>}
    </section>
  )
}
