import { useEffect, useState } from 'react'
import { formatAmount } from '../../domain/formatMoney'
import { ruleFor, type EconomicKind } from '../../domain/entities/economicKind'
import type { SuggestionLine, SuggestionSummary } from '../../application/useCases/setEconomicKind'
import type { UserContainer } from '../../app/container'
import type { Transaction } from '../../domain/entities/types'
import './KindsSheet.css'

interface Props {
  user: UserContainer
  transactions: Transaction[]
  onClose: () => void
  onDone: () => void
}

/**
 * ورقة تحديد الأنواع الاقتصادية.
 *
 * spec/02: «التصنيف الآلي **يقترح**، وتأكيد المستخدم يحمي اختياره.»
 * فالورقة تعرض الاقتراحات مجمّعة، والمستخدم يوافق. لا شيء يُطبَّق تلقائيًا.
 *
 * التقسيم الثلاثي مقصود: القاطع يُؤكَّد جماعيًا بضغطة، والغامض يُعرض
 * ببدائله ليُحسم فرديًا — لأن الغامض هو «عالم انتقال الفلوس» الذي
 * يقرر معنى كل أرقام التطبيق.
 */
export function KindsSheet({ user, transactions, onClose, onDone }: Props) {
  const [summary, setSummary] = useState<SuggestionSummary | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    void user.setEconomicKind.summarize(transactions).then((s) => {
      if (cancelled) return
      setSummary(s)
      setDone(null)
      // المحدد افتراضيًا هو القاطع وحده — الغامض لا يُؤكَّد جماعيًا
      setSelected(new Set(s.confirmable.map((line) => line.transaction.id)))
    })
    return () => {
      cancelled = true
    }
  }, [user, transactions])

  /*
   * `busy` يُرفع دائمًا في finally، نجاحًا أو فشلًا.
   * بدونه تبقى الورقة عالقة على «بنحفظ…» وزر الإغلاق معطّل،
   * فيُحبس المستخدم داخلها. اكتُشف بالتشغيل لا بالمراجعة.
   */
  async function applyBulk() {
    if (!summary) return
    setBusy(true)
    setError(null)
    try {
      const result = await user.setEconomicKind.confirmBulk(transactions, [...selected])
      setDone(`اتأكدت ${result.applied} عملية.`)
      onDone() // يعيد تحميل الشاشة، فتصل عمليات محدَّثة وتُعاد الاقتراحات
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause))
    } finally {
      setBusy(false)
    }
  }

  async function setOne(id: string, kind: EconomicKind) {
    setError(null)
    setBusy(true)
    try {
      await user.setEconomicKind.setOne(id, kind)
      onDone()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause))
    } finally {
      setBusy(false)
    }
  }

  const toggle = (id: string) =>
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })

  return (
    <div className="sheet" role="dialog" aria-modal="true" aria-label="تحديد أنواع العمليات">
      <div className="sheet__panel">
        <header className="sheet__head">
          <h2 className="sheet__title">تحديد الأنواع</h2>
          <button type="button" className="iconBtn" onClick={onClose} aria-label="إغلاق">
            <span aria-hidden="true">✕</span>
          </button>
        </header>

        <div className="sheet__body">
          <p className="sheet__hint">
            الكشف بيقول الفلوس دخلت ولا خرجت بس. النوع الاقتصادي — دخل؟ نقل فلوس؟ قرض؟ —
            قرارك انت، والتطبيق بيقترح بس.
          </p>

          {error && (
            <p className="sheet__error" role="alert">
              <span aria-hidden="true">⚠</span> {error}
            </p>
          )}

          {done && (
            <p className="notice" role="status">
              {done}
            </p>
          )}

          {!summary && <p className="notice">بنحسب الاقتراحات…</p>}

          {summary && (
            <>
              {summary.confirmable.length > 0 && (
                <section className="kinds__group">
                  <h3 className="kinds__title">
                    واضحة ({summary.confirmable.length})
                    <span className="kinds__sub">اقتراح قاطع — راجعها ووافق</span>
                  </h3>
                  <ul className="kinds__list">
                    {summary.confirmable.slice(0, 60).map((line) => (
                      <KindRow
                        key={line.transaction.id}
                        line={line}
                        checked={selected.has(line.transaction.id)}
                        onToggle={() => toggle(line.transaction.id)}
                      />
                    ))}
                  </ul>
                  {summary.confirmable.length > 60 && (
                    <p className="sheet__hint">
                      بنعرض أول 60. الموافقة هتتطبق على كل المحدد ({selected.size}).
                    </p>
                  )}
                </section>
              )}

              {summary.needsLook.length > 0 && (
                <section className="kinds__group">
                  <h3 className="kinds__title">
                    محتاجة نظرة ({summary.needsLook.length})
                    <span className="kinds__sub">مرجّحة مش مؤكدة — حدّدها واحدة واحدة</span>
                  </h3>
                  <ul className="kinds__list">
                    {summary.needsLook.slice(0, 30).map((line) => (
                      <AmbiguousRow key={line.transaction.id} line={line} onPick={setOne} />
                    ))}
                  </ul>
                </section>
              )}

              {summary.ambiguous.length > 0 && (
                <section className="kinds__group">
                  <h3 className="kinds__title">
                    محتاجة قرارك ({summary.ambiguous.length})
                    <span className="kinds__sub">
                      الكشف مش كفاية — دي الفلوس اللي مش واضح راحت فين
                    </span>
                  </h3>
                  <ul className="kinds__list">
                    {summary.ambiguous.slice(0, 30).map((line) => (
                      <AmbiguousRow key={line.transaction.id} line={line} onPick={setOne} />
                    ))}
                  </ul>
                  {summary.ambiguous.length > 30 && (
                    <p className="sheet__hint">بنعرض أول 30 من {summary.ambiguous.length}.</p>
                  )}
                </section>
              )}

              {summary.confirmable.length === 0 &&
                summary.needsLook.length === 0 &&
                summary.ambiguous.length === 0 && (
                  <p className="notice">كل عمليات الفترة دي محددة النوع. مفيش حاجة مطلوبة.</p>
                )}

              <div className="sheet__foot">
                {/* الإغلاق متاح دائمًا — لا يُحبس المستخدم داخل الورقة */}
                <button type="button" className="btn btn--quiet" onClick={onClose}>
                  إغلاق
                </button>
                <button
                  type="button"
                  className="btn"
                  onClick={applyBulk}
                  disabled={busy || selected.size === 0}
                >
                  {busy ? 'بنحفظ…' : `وافق على ${selected.size}`}
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}

function KindRow({
  line,
  checked,
  onToggle,
}: {
  line: SuggestionLine
  checked: boolean
  onToggle: () => void
}) {
  const t = line.transaction
  const kind = line.suggestion.kind!
  return (
    <li className="kinds__row">
      <input
        type="checkbox"
        checked={checked}
        onChange={onToggle}
        aria-label={`تأكيد ${t.rawMerchantName ?? 'العملية'} كـ${ruleFor(kind).label}`}
      />
      <div className="kinds__main">
        <div className="kinds__name">{t.rawMerchantName || t.rawDescription || 'بلا اسم'}</div>
        <div className="kinds__meta">
          <span>{t.occurredAt}</span>
          <span className="badge">{ruleFor(kind).label}</span>
        </div>
        <div className="kinds__reason">{line.suggestion.reason}</div>
      </div>
      <span
        className="kinds__amount num"
        style={{ color: t.observedDirection === 'in' ? 'var(--c-incoming)' : 'var(--c-outgoing)' }}
      >
        {/* الإشارة جوه الخانة المعزولة زي صف العملية — مش لون وحده (spec/04) */}
        {t.observedDirection === 'in' ? '+' : '−'}
        {formatAmount(t.amountMinor)}
      </span>
    </li>
  )
}

function AmbiguousRow({
  line,
  onPick,
}: {
  line: SuggestionLine
  onPick: (id: string, kind: EconomicKind) => void
}) {
  const t = line.transaction
  const options = line.suggestion.kind
    ? [line.suggestion.kind, ...line.suggestion.alternatives]
    : line.suggestion.alternatives

  return (
    <li className="kinds__row kinds__row--pick">
      <div className="kinds__main">
        <div className="kinds__name">{t.rawMerchantName || t.rawDescription || 'بلا اسم'}</div>
        <div className="kinds__meta">
          <span>{t.occurredAt}</span>
          <span
            className="num"
            style={{
              color: t.observedDirection === 'in' ? 'var(--c-incoming)' : 'var(--c-outgoing)',
              fontWeight: 700,
            }}
          >
            {t.observedDirection === 'in' ? '+' : '−'}
            {formatAmount(t.amountMinor)}
          </span>
        </div>
        <div className="kinds__reason">{line.suggestion.reason}</div>
        <div className="kinds__options">
          {options.map((kind, index) => {
            // أول اختيار هو اقتراح التطبيق — لما يكون فيه اقتراح أصلًا
            const isSuggested = index === 0 && Boolean(line.suggestion.kind)
            return (
              <button
                key={kind}
                type="button"
                className={`kinds__option${isSuggested ? ' kinds__option--suggested' : ''}`}
                onClick={() => onPick(t.id, kind)}
              >
                {ruleFor(kind).label}
                {isSuggested && <span className="kinds__optionTag">الاقتراح</span>}
              </button>
            )
          })}
        </div>
      </div>
    </li>
  )
}
