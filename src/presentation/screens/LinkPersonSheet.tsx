import { useEffect, useState, type FormEvent } from 'react'
import { formatAmount } from '../../domain/formatMoney'
import { tryParseMoney } from '../../domain/money'
import { ErrorNotice } from '../components/ErrorNotice'
import type { ObligationKind, Person, Transaction } from '../../domain/entities/types'
import type { UserContainer } from '../../app/container'
import './LinkPersonSheet.css'

interface Props {
  user: UserContainer
  transaction: Transaction
  people: Person[]
  loading?: boolean
  loadError?: unknown
  onClose: () => void
  onLinked: () => void
}

type Mode = 'receivable' | 'gift'

/**
 * ربط عملية بشخص — spec/04: «ربط الشخص | اختيار من الحسابات أو إضافة شخص؛
 * **تحديد هدية أو دين وجزء الفاتورة**».
 *
 * الفرق بين الاثنين هو كل شيء (spec/06):
 *   دين  ⇒ المصروف يقل بالمبلغ، ويظهر «لك عنده»
 *   هدية ⇒ المصروف **يبقى كاملًا**، ولا التزام عليه
 *
 * ولذلك الفرق مكتوب في الشاشة لا مستنتَجًا من اسم الخيار.
 */
export function LinkPersonSheet({ user, transaction, people, onClose, onLinked, loading, loadError }: Props) {
  const active = people.filter((p) => !p.archived)

  const [personId, setPersonId] = useState(active[0]?.id ?? '')
  useEffect(()=>{if(!personId&&people.length)setPersonId(people.find(p=>!p.archived)?.id??'')},[people,personId])
  const [mode, setMode] = useState<Mode>('receivable')
  const [amountText, setAmountText] = useState(
    formatAmount(transaction.amountMinor, transaction.currency, { grouping: false }),
  )
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)

  /*
   * نوع الالتزام يتبع اتجاه الحركة لا يختاره المستخدم:
   * صادر ⇒ دفعت عنه أو سلّفته ⇒ «لك عنده».
   * وارد ⇒ استلمت منه ⇒ قرض عليك (spec/02).
   */
  const kind: ObligationKind =
    transaction.observedDirection === 'out' ? 'receivable' : 'loan_payable'

  const isIncoming = transaction.observedDirection === 'in'

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    if (!personId) {
      setError(new Error('اختار شخص الأول. لو مفيش، ضيفه من شاشة الأشخاص.'))
      return
    }

    const amountMinor = tryParseMoney(amountText)
    if (amountMinor === null || amountMinor <= 0) {
      setError(new Error('اكتب مبلغ صحيح أكبر من صفر'))
      return
    }

    setBusy(true)
    try {
      await user.managePeople.linkToPerson({
        transactionId: transaction.id,
        personId,
        kind,
        amountMinor,
        asGift: mode === 'gift',
      })
      onLinked()
    } catch (cause) {
      setError(cause)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="sheet" role="dialog" aria-modal="true" aria-label="ربط بشخص">
      <div className="sheet__panel">
        <header className="sheet__head">
          <h2 className="sheet__title">ربط بشخص</h2>
          <button type="button" className="iconBtn" onClick={onClose} aria-label="إغلاق">
            <span aria-hidden="true">✕</span>
          </button>
        </header>

        <form className="sheet__body" onSubmit={submit} noValidate>
          <div className="linkPerson__txn">
            <span className="linkPerson__name">
              {transaction.rawMerchantName || transaction.rawDescription || 'بلا اسم'}
            </span>
            <span className="num">
              {formatAmount(transaction.amountMinor)} · {transaction.occurredAt}
            </span>
          </div>

          {loading ? <p role="status">بنحمّل الأشخاص…</p> : loadError ? <p role="alert">تعذر تحميل الأشخاص. اقفل النافذة وجرّب تاني.</p> : active.length === 0 ? (
            <p className="notice">
              مفيش أشخاص لسه. روح لشاشة «الأشخاص» وضيف شخص، وبعدين ارجع هنا.
            </p>
          ) : (
            <>
              <label className="sheet__field">
                <span className="sheet__label">مين؟</span>
                <select
                  className="sheet__input"
                  value={personId}
                  onChange={(e) => setPersonId(e.target.value)}
                >
                  {active.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </select>
              </label>

              {!isIncoming && (
                <fieldset className="linkPerson__modes">
                  <legend className="sheet__label">دين ولا هدية؟</legend>

                  <label className={`linkPerson__mode${mode === 'receivable' ? ' linkPerson__mode--on' : ''}`}>
                    <input
                      type="radio"
                      name="mode"
                      checked={mode === 'receivable'}
                      onChange={() => setMode('receivable')}
                    />
                    <span>
                      <b>دين — هيرجّعهالك</b>
                      <small>المصروف بتاعك هيقل بالمبلغ ده، وهيتسجل «لك عنده»</small>
                    </span>
                  </label>

                  <label className={`linkPerson__mode${mode === 'gift' ? ' linkPerson__mode--on' : ''}`}>
                    <input
                      type="radio"
                      name="mode"
                      checked={mode === 'gift'}
                      onChange={() => setMode('gift')}
                    />
                    <span>
                      <b>هدية — مش هترجع</b>
                      <small>المصروف بتاعك هيفضل كامل، ومفيش التزام عليه</small>
                    </span>
                  </label>
                </fieldset>
              )}

              {isIncoming && (
                <p className="notice">
                  دي حركة وارد، فهتتسجل <strong>قرض عليك</strong> للشخص ده. الدخل مش هيتغير.
                </p>
              )}

              <label className="sheet__field">
                <span className="sheet__label">المبلغ الخاص بيه</span>
                <input
                  className="sheet__input"
                  type="text"
                  inputMode="decimal"
                  value={amountText}
                  onChange={(e) => setAmountText(e.target.value)}
                />
                <span className="sheet__hint">
                  لو الفاتورة مشتركة، اكتب نصيبه هو بس. الباقي يفضل عليك.
                </span>
              </label>

              {error ? <ErrorNotice cause={error} /> : null}

              <div className="sheet__foot">
                <button type="button" className="btn btn--quiet" onClick={onClose}>
                  إلغاء
                </button>
                <button type="submit" className="btn" disabled={busy}>
                  {busy ? 'بنربط…' : 'ربط'}
                </button>
              </div>
            </>
          )}
        </form>
      </div>
    </div>
  )
}
