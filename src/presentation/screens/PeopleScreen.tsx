import { useState, type FormEvent } from 'react'
import { formatAmount } from '../../domain/formatMoney'
import { tryParseMoney } from '../../domain/money'
import { ErrorNotice } from '../components/ErrorNotice'
import type { PersonRow } from '../../application/useCases/managePeople'
import type { Id, ObligationKind } from '../../domain/entities/types'
import type { UserContainer } from '../../app/container'
import './PeopleScreen.css'

interface Props {
  user: UserContainer
  rows: PersonRow[]
  loading: boolean
  error: unknown
  amountsHidden: boolean
  onChanged: () => void
  onRetry: () => void
}

const OBLIGATION_LABEL: Record<ObligationKind, string> = {
  receivable: 'لك عنده',
  loan_payable: 'قرض عليك',
  custody_payable: 'أمانة عندك',
}

/**
 * شاشة الأشخاص — spec/02:
 * «اعرض منفصلين: **لك عنده** و**له عندك**… سجّل نوع الالتزام داخليًا
 *  منفصلًا للديون والأمانات، حتى لو عرضت الإجمالي في خانة واحدة.»
 *
 * الفصل هنا **ثلاثي لا ثنائي**: القرض والأمانة يُعرضان منفصلين لأن
 * `spec/06` يمنع سداد قرض من رصيد أمانة، والدمج البصري يخفي هذا المنع.
 */
export function PeopleScreen({
  user,
  rows,
  loading,
  error,
  amountsHidden,
  onChanged,
  onRetry,
}: Props) {
  const [newName, setNewName] = useState('')
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState<unknown>(null)
  const [settling, setSettling] = useState<Id | null>(null)

  const money = (value: number) => (amountsHidden ? '••••' : formatAmount(value))

  async function addPerson(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    if (!newName.trim()) return
    setBusy(true)
    try {
      await user.managePeople.addPerson(newName)
      setNewName('')
      onChanged()
    } catch (cause) {
      setFormError(cause)
    } finally {
      setBusy(false)
    }
  }

  if (loading && rows.length === 0) {
    return (
      <p className="notice" role="status">
        بنحمّل الأشخاص…
      </p>
    )
  }

  if (error) return <ErrorNotice cause={error} onRetry={onRetry} />

  return (
    <div className="people">
      <section className="card">
        <h2 className="card__title">شخص جديد</h2>
        <form className="people__add" onSubmit={addPerson} noValidate>
          <input
            className="sheet__input"
            type="text"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            placeholder="الاسم"
            maxLength={80}
          />
          <button type="submit" className="btn" disabled={busy || !newName.trim()}>
            {busy ? 'بنضيف…' : 'إضافة'}
          </button>
        </form>
        {formError ? <ErrorNotice cause={formError} /> : null}
      </section>

      {rows.length === 0 ? (
        <div className="empty">
          <span className="empty__title">مفيش أشخاص لسه</span>
          <span>
            ضيف شخص هنا، وبعدين تقدر تربط أي عملية بيه من شاشة العمليات — لو دفعت عن حد
            أو سلّفته.
          </span>
        </div>
      ) : (
        <ul className="people__list">
          {rows.map((row) => (
            <li key={row.person.id} className={`card people__row${row.person.archived ? ' people__row--archived' : ''}`}>
              <div className="people__head">
                <span className="people__name">
                  {row.person.name}
                  {row.person.archived && <span className="badge">مؤرشف</span>}
                </span>
                <button
                  type="button"
                  className="link"
                  onClick={async () => {
                    await user.managePeople.archivePerson(row.person.id, !row.person.archived)
                    onChanged()
                  }}
                >
                  {row.person.archived ? 'إلغاء الأرشفة' : 'أرشفة'}
                </button>
              </div>

              {/* ثلاثة أرصدة منفصلة — لا تقاص تلقائي (spec/02) */}
              <div className="people__balances">
                <Balance
                  label="لك عنده"
                  value={money(row.balance.receivableMinor)}
                  tone={row.balance.receivableMinor > 0 ? 'in' : 'zero'}
                />
                <Balance
                  label="قرض عليك"
                  value={money(row.balance.payableLoanMinor)}
                  tone={row.balance.payableLoanMinor > 0 ? 'out' : 'zero'}
                />
                <Balance
                  label="أمانة عندك"
                  value={money(row.balance.payableCustodyMinor)}
                  tone={row.balance.payableCustodyMinor > 0 ? 'out' : 'zero'}
                />
              </div>

              {row.obligations.length === 0 ? (
                <p className="people__none">مفيش التزامات مفتوحة.</p>
              ) : (
                <ul className="people__obligations">
                  {row.obligations.map(({ obligation, remainingMinor }) => (
                    <li key={obligation.id} className="people__obligation">
                      <div className="people__obHead">
                        <span className="badge">{OBLIGATION_LABEL[obligation.kind]}</span>
                        <span className="num">
                          باقي {money(remainingMinor)} من {money(obligation.originalMinor)}
                        </span>
                      </div>

                      {settling === obligation.id ? (
                        <SettleForm
                          maxMinor={remainingMinor}
                          onCancel={() => setSettling(null)}
                          onSettle={async (amountMinor) => {
                            await user.managePeople.settle({
                              obligationId: obligation.id,
                              personId: row.person.id,
                              amountMinor,
                            })
                            setSettling(null)
                            onChanged()
                          }}
                        />
                      ) : (
                        <button
                          type="button"
                          className="link"
                          onClick={() => setSettling(obligation.id)}
                        >
                          {obligation.kind === 'receivable' ? 'سجّل تحصيل' : 'سجّل سداد'}
                        </button>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function Balance({
  label,
  value,
  tone,
}: {
  label: string
  value: string
  tone: 'in' | 'out' | 'zero'
}) {
  return (
    <div className="peopleBal">
      <span className="peopleBal__label">{label}</span>
      <span className={`peopleBal__value num peopleBal__value--${tone}`}>{value}</span>
    </div>
  )
}

function SettleForm({
  maxMinor,
  onSettle,
  onCancel,
}: {
  maxMinor: number
  onSettle: (amountMinor: number) => Promise<void>
  onCancel: () => void
}) {
  const [text, setText] = useState(formatAmount(maxMinor, 'SAR', { grouping: false }))
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  return (
    <form
      className="people__settle"
      onSubmit={async (event) => {
        event.preventDefault()
        setError(null)
        const amountMinor = tryParseMoney(text)
        if (amountMinor === null || amountMinor <= 0) {
          setError(new Error('اكتب مبلغ صحيح أكبر من صفر'))
          return
        }
        setBusy(true)
        try {
          await onSettle(amountMinor)
        } catch (cause) {
          setError(cause)
        } finally {
          setBusy(false)
        }
      }}
      noValidate
    >
      <div className="people__settleRow">
        <input
          className="sheet__input"
          type="text"
          inputMode="decimal"
          value={text}
          onChange={(e) => setText(e.target.value)}
          autoFocus
        />
        <button type="submit" className="btn" disabled={busy}>
          {busy ? '…' : 'تأكيد'}
        </button>
        <button type="button" className="btn btn--quiet" onClick={onCancel}>
          إلغاء
        </button>
      </div>
      {error ? <ErrorNotice cause={error} /> : null}
    </form>
  )
}
