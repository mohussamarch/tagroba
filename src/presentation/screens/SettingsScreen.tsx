import { useState } from 'react'
import { formatAmount } from '../../domain/formatMoney'
import { WalletEditor } from '../components/WalletEditor'
import { RestorePanel } from '../components/RestorePanel'
import { RulesCard } from '../components/RulesCard'
import type { ReconcileOutcome } from '../../application/useCases/reconcileBalance'
import type { UserContainer } from '../../app/container'
import type { Wallet } from '../../domain/entities/types'
import './SettingsScreen.css'

interface Props {
  user: UserContainer
  wallets: Wallet[]
  today: string
  payday: number
  onWalletsChanged: () => void
  /** بعد الاستعادة لازم كل الشاشات تعيد التحميل. */
  onRestored: () => void
  /** فتح شاشة القواعد والتجار. */
  onOpenRules: () => void
}

/**
 * الإعدادات — spec/01: «المطابقة، التصدير الكامل، النسخ الاحتياطي والاستعادة».
 *
 * OVERRIDES §2: التصدير والنسخ الاحتياطي **مطلبان ولا يلغيهما وجود السحابة**.
 *
 * ⚠️ التنزيل يتم بـ Blob محلي — الملف **لا يمر بأي خادم**، وهو شرط
 * «لا تُرفع البيانات لأي خدمة أخرى غير فايربيز» (OVERRIDES §2).
 */
export function SettingsScreen({
  user,
  wallets,
  today,
  payday,
  onWalletsChanged,
  onRestored,
  onOpenRules,
}: Props) {
  const [walletId, setWalletId] = useState(wallets[0]?.id ?? '')
  const [outcome, setOutcome] = useState<ReconcileOutcome | null>(null)
  const [busy, setBusy] = useState<'none' | 'reconcile' | 'backup' | 'csv'>('none')
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const [editingWallet, setEditingWallet] = useState<string | null>(null)

  const working = busy !== 'none'

  function download(content: string, filename: string, type: string) {
    const blob = new Blob([content], { type })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename
    anchor.click()
    URL.revokeObjectURL(url)
  }

  async function runReconcile() {
    if (!walletId) return
    setBusy('reconcile')
    setError(null)
    setOutcome(null)
    try {
      setOutcome(await user.reconcileBalance({ walletId, until: today, payday }))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause))
    } finally {
      setBusy('none')
    }
  }

  async function runBackup() {
    setBusy('backup')
    setError(null)
    setDone(null)
    try {
      const wallet = wallets.find((w) => w.id === walletId) ?? wallets[0]
      const file = await user.exportBackup.backup({
        from: wallet?.openingAt ?? today,
        to: today,
        payday,
        exportedAt: new Date().toISOString(),
      })
      download(
        JSON.stringify(file, null, 2),
        `masroufy-backup-${today}.json`,
        'application/json',
      )
      setDone(`اتنزّلت نسخة فيها ${file.counts.transactions} عملية.`)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause))
    } finally {
      setBusy('none')
    }
  }

  async function runCsv() {
    setBusy('csv')
    setError(null)
    setDone(null)
    try {
      const wallet = wallets.find((w) => w.id === walletId) ?? wallets[0]
      const csv = await user.exportBackup.exportCsv({
        from: wallet?.openingAt ?? today,
        to: today,
        payday,
      })
      download(csv, `masroufy-${today}.csv`, 'text/csv;charset=utf-8')
      setDone('اتنزّل ملف CSV. تقدر تفتحه في Excel أو تعيد استيراده.')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause))
    } finally {
      setBusy('none')
    }
  }

  return (
    <div className="settings">
      <section className="card" aria-label="المحافظ">
        <h2 className="card__title">المحافظ والرصيد الافتتاحي</h2>
        <p className="settings__hint">
          الرصيد الافتتاحي هو نقطة بداية سلسلة المطابقة. للراجحي هو الرصيد قبل أول
          عملية في الكشف. من غيره المطابقة هتطلع غلط بفرق ثابت.
        </p>
        <ul className="settings__wallets">
          {wallets.map((w) => (
            <li key={w.id} className="settings__wallet">
              <div className="settings__walletHead">
                <span className="settings__walletName">{w.name}</span>
                <span className="num">
                  {formatAmount(w.openingBalanceMinor)} في {w.openingAt}
                </span>
              </div>
              {editingWallet === w.id ? (
                <WalletEditor
                  wallet={w}
                  onSave={async (updated) => {
                    await user.wallets.save(updated)
                    setEditingWallet(null)
                    onWalletsChanged()
                  }}
                  onCancel={() => setEditingWallet(null)}
                />
              ) : (
                <button type="button" className="link" onClick={() => setEditingWallet(w.id)}>
                  عدّل الرصيد الافتتاحي وتاريخه
                </button>
              )}
            </li>
          ))}
        </ul>
      </section>

      <section className="card" aria-label="مطابقة الرصيد">
        <h2 className="card__title">مطابقة الرصيد</h2>
        <p className="settings__hint">
          بنبدأ من الرصيد الافتتاحي للمحفظة ونطبّق كل عملية بترتيبها، وبنقارن
          الناتج بعمود الرصيد اللي في الكشف — سطر بسطر بدقة الهللة.
        </p>

        <label className="settings__field">
          <span className="settings__label">المحفظة</span>
          <select
            className="settings__input"
            value={walletId}
            onChange={(e) => setWalletId(e.target.value)}
            disabled={working}
          >
            {wallets.map((w) => (
              <option key={w.id} value={w.id}>
                {w.name} — يبدأ من {formatAmount(w.openingBalanceMinor)} في {w.openingAt}
              </option>
            ))}
          </select>
        </label>

        <button type="button" className="btn" onClick={runReconcile} disabled={working || !walletId}>
          {busy === 'reconcile' ? 'بنطابق…' : 'طابق الرصيد'}
        </button>

        {outcome && <ReconcileReport outcome={outcome} />}
      </section>

      <section className="card" aria-label="التصدير والنسخ الاحتياطي">
        <h2 className="card__title">التصدير والنسخة الاحتياطية</h2>
        <p className="settings__hint">
          الملف بيتعمل على جهازك ومبيعدّيش على أي سيرفر. النسخة فيها كل حاجة
          بمعرفاتها وعلاقاتها، فتقدر ترجّعها زي ما هي.
        </p>
        <div className="settings__row">
          <button type="button" className="btn" onClick={runBackup} disabled={working}>
            {busy === 'backup' ? 'بنجهّز…' : 'نسخة احتياطية (JSON)'}
          </button>
          <button type="button" className="btn btn--quiet" onClick={runCsv} disabled={working}>
            {busy === 'csv' ? 'بنجهّز…' : 'تصدير CSV'}
          </button>
        </div>
        {done && (
          <p className="notice" role="status">
            {done}
          </p>
        )}

        <RestorePanel user={user} onDone={onRestored} />
      </section>

      <RulesCard onOpen={onOpenRules} />

      {error && (
        <p className="settings__error" role="alert">
          <span aria-hidden="true">⚠</span> {error}
        </p>
      )}
    </div>
  )
}

function ReconcileReport({ outcome }: { outcome: ReconcileOutcome }) {
  const { result } = outcome
  const clean = result.mismatches.length === 0 && result.checkedCount > 0

  return (
    <div className="recon">
      <div className={`recon__verdict recon__verdict--${clean ? 'ok' : 'bad'}`}>
        {result.checkedCount === 0
          ? 'مفيش سطر فيه رصيد معلن يتقارن بيه.'
          : clean
            ? `مطابقة كاملة: ${result.checkedCount} سطر، صفر فروق.`
            : `${result.mismatches.length} سطر رصيده ما طابقش من ${result.checkedCount}.`}
      </div>

      <dl className="recon__facts">
        <Row label="الرصيد الافتتاحي" value={`${formatAmount(result.openingMinor)} في ${result.openingAt}`} />
        <Row
          label="الرصيد الأخير"
          value={
            result.closingAt
              ? `${formatAmount(result.closingMinor)} في ${result.closingAt}`
              : formatAmount(result.closingMinor)
          }
        />
        <Row label="إجمالي المدين" value={formatAmount(result.totalDebitMinor)} />
        <Row label="إجمالي الدائن" value={formatAmount(result.totalCreditMinor)} />
        <Row label="عدد الحركات" value={String(result.movementCount)} />
        {outcome.withoutStatedBalance > 0 && (
          <Row
            label="بلا رصيد معلن"
            value={`${outcome.withoutStatedBalance} — مش داخلة المقارنة`}
          />
        )}
        {outcome.unassignedCount > 0 && (
          <Row
            label="بلا محفظة"
            value={`${outcome.unassignedCount} — مش منسوبة لمحفظة فمش داخلة الحساب`}
          />
        )}
      </dl>

      {result.ambiguityNote && <p className="recon__note">{result.ambiguityNote}</p>}

      {result.mismatches.length > 0 && (
        <ul className="recon__list">
          {result.mismatches.slice(0, 20).map((m) => (
            <li key={`${m.index}`} className="recon__item">
              <div className="recon__itemHead">
                <span>{m.date}</span>
                <span>{m.label ?? ''}</span>
              </div>
              <div className="recon__itemBody num">
                محسوب {formatAmount(m.computedMinor)} · معلن {formatAmount(m.statedMinor)} · فرق{' '}
                {formatAmount(m.differenceMinor)}
              </div>
              {m.sameDayCount > 1 && (
                <div className="recon__itemNote">
                  اليوم ده فيه {m.sameDayCount} حركة، وترتيبها مش مثبت في الكشف.
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <>
      <dt className="recon__label">{label}</dt>
      <dd className="recon__value num">{value}</dd>
    </>
  )
}
