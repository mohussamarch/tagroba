import { RefreshCw, Settings, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import type { UserContainer } from '../../app/container'
import type { SmsReview, SmsReviewLine, SmsReviewTarget } from '../../application/useCases/reviewSmsInbox'
import { groupCategoryOptions } from '../../domain/categoryOptions'
import type { Id, Wallet } from '../../domain/entities/types'
import { SmsInboxSettings } from '../components/SmsInboxSettings'
import { SmsReviewRow } from '../components/SmsReviewRow'
import './ImportSheet.css'
import './SmsInboxSheet.css'

/**
 * رسايل البنك — OVERRIDES §36 (قرار المالك 2026-09-19): قايمة واحدة بكل عملية وتصنيفها، وزرار «سجّل الكل».
 * تختار تصنيف لمحل جديد ⇒ سؤال واحد «نفتكره؟». الإعداد ورا الترس. مفيش حاجة بتتسجل من غير الضغطة.
 */
type Ask = { merchant: string; categoryId: Id; categoryName: string; direction: 'in' | 'out' }
const sameStore = (a: string, b: string) => a.trim().toLowerCase().replace(/\s+/g, ' ') === b.trim().toLowerCase().replace(/\s+/g, ' ')
const dayLabel = (iso: string) =>
  new Date(iso + 'T12:00:00').toLocaleDateString('ar-u-nu-latn', { weekday: 'long', day: 'numeric', month: 'long' })

export function SmsInboxSheet({ user, wallets, onClose, onImported }: {
  user: UserContainer; wallets: Wallet[]; onClose: () => void; onImported: () => void
}) {
  const [walletId, setWalletId] = useState(() => (wallets.find((w) => w.kind === 'bank') ?? wallets[0])?.id ?? '')
  const [view, setView] = useState<SmsReview | null>(null)
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [done, setDone] = useState('')
  const [settings, setSettings] = useState(false), [openRow, setOpenRow] = useState<string | null>(null)
  /** اختيارات المستخدم بمعرّف الرسالة (رقم السطر ممكن يتغير لو وصلت رسالة جديدة). */
  const [chosen, setChosen] = useState<Map<string, Id>>(new Map())
  const [include, setInclude] = useState<Set<string>>(new Set())
  const [ask, setAsk] = useState<Ask | null>(null)

  const target: SmsReviewTarget = { walletId, accountIdentity: wallets.find((w) => w.id === walletId)?.name ?? 'غير محدد' }
  async function run(action: () => Promise<SmsReview>) {
    setBusy(true); setError('')
    try { setView(await action()) } catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)) } finally { setBusy(false) }
  }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void run(() => user.smsReview.load(target)) }, [user, walletId])

  const byId = useMemo(() => new Map((view?.categories ?? []).map((c) => [c.id, c])), [view])
  const groups = useMemo(() => groupCategoryOptions(view?.categories ?? []), [view])
  const categoryOf = (line: SmsReviewLine) => byId.get(chosen.get(line.messageId) ?? line.categoryId ?? '')
  const all = [...(view?.ready ?? []), ...(view?.similar ?? [])]
  const toRecord = (view?.ready.length ?? 0) + (view?.similar.filter((l) => include.has(l.messageId)).length ?? 0)
  const uncategorized = all.filter((l) => (l.state === 'new' || include.has(l.messageId)) && !categoryOf(l)).length

  function pick(line: SmsReviewLine, categoryId: Id) {
    const next = new Map(chosen)
    for (const other of all) if (other.messageId === line.messageId || (line.merchant && sameStore(other.merchant, line.merchant))) next.set(other.messageId, categoryId)
    setChosen(next); setOpenRow(null)
    if (line.merchant && !line.remembered) setAsk({ merchant: line.merchant, categoryId, categoryName: byId.get(categoryId)?.name ?? '', direction: line.direction })
  }
  async function remember(a: Ask) {
    setAsk(null)
    await run(async () => { await user.smsReview.remember(a.merchant, a.categoryId, a.direction); return user.smsReview.load(target) })
  }
  async function recordAll() {
    if (!view) return
    const categories = new Map<number, Id>()
    for (const line of all) { const id = chosen.get(line.messageId); if (id) categories.set(line.lineNumber, id) }
    const includeSimilar = view.similar.filter((l) => include.has(l.messageId)).map((l) => l.lineNumber)
    setBusy(true); setError(''); setDone('')
    try {
      const { recorded } = await user.smsReview.recordAll({ categories, includeSimilar })
      setChosen(new Map()); setInclude(new Set()); setAsk(null)
      setDone(recorded ? `اتسجلت ${recorded} عملية.` : 'مفيش عمليات جديدة — اللي كان موجود قبل كده اتشال من القايمة.')
      onImported()
      setView(await user.smsReview.load(target))
    } catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)) } finally { setBusy(false) }
  }

  const days = [...new Set(view?.ready.map((l) => l.date))]
  const row = (line: SmsReviewLine, similar = false) => (
    <SmsReviewRow key={line.messageId} line={line} category={categoryOf(line)} groups={groups} busy={busy}
      open={openRow === line.messageId} onOpen={() => setOpenRow(openRow === line.messageId ? null : line.messageId)}
      onPick={(id) => pick(line, id)} onDismiss={() => void run(() => user.smsReview.dismiss([line.messageId], target))}
      similar={similar} included={include.has(line.messageId)}
      onInclude={(on) => setInclude((old) => { const next = new Set(old); if (on) next.add(line.messageId); else next.delete(line.messageId); return next })} />
  )

  return (
    <div className="sheet smsSheet" role="dialog" aria-modal="true" aria-label="رسايل البنك">
      <div className="sheet__panel">
        <header className="sheet__head">
          <h2 className="sheet__title">رسايل البنك</h2>
          <div className="smsSheet__tools">
            <button type="button" className="iconBtn" aria-label="اقرا الرسايل تاني" disabled={busy} onClick={() => void run(() => user.smsReview.load(target))}><RefreshCw size={18} aria-hidden="true" /></button>
            <button type="button" className="iconBtn" aria-label="الإعداد" aria-pressed={settings} onClick={() => setSettings((v) => !v)}><Settings size={18} aria-hidden="true" /></button>
            <button type="button" className="iconBtn" aria-label="إغلاق" disabled={busy} onClick={onClose}><X size={18} aria-hidden="true" /></button>
          </div>
        </header>
        <div className="sheet__body smsSheet__body">
          {view && (settings || !view.enabled || !view.permission) && (
            <SmsInboxSettings enabled={view.enabled} permission={view.permission} senders={view.senders} wallets={wallets} walletId={walletId} busy={busy}
              onWallet={setWalletId} onEnable={(s) => void run(() => user.smsReview.enable(s, target))} onDisable={() => void run(() => user.smsReview.disable(target))} />
          )}
          {error && <p className="sheet__error" role="alert">{error}</p>}
          {done && <p className="notice" role="status">{done}</p>}
          {!view && !error && <p role="status">بنقرا الرسايل…</p>}
          {view && (
            <>
              <p className="smsSheet__summary">
                {view.ready.length ? `${view.ready.length} عملية جديدة · اضغط على أي واحدة تغيّر تصنيفها` : 'مفيش عمليات جديدة'}
                {uncategorized > 0 && ` · ${uncategorized} من غير تصنيف هتتسجل «غير مصنف»`}
              </p>
              {days.map((day) => (
                <section key={day} className="smsDay" aria-label={dayLabel(day)}>
                  <h3 className="smsDay__title">{dayLabel(day)}</h3>
                  <ul className="smsList">{view.ready.filter((l) => l.date === day).map((l) => row(l))}</ul>
                </section>
              ))}
              {view.similar.length > 0 && (
                <details className="smsFold">
                  <summary>{view.similar.length} شبه عمليات متسجلة قبل كده — مش هتتسجل إلا لو اخترتها</summary>
                  <ul className="smsList">{view.similar.map((l) => row(l, true))}</ul>
                </details>
              )}
              {view.duplicates.length > 0 && <p className="sheet__hint">{view.duplicates.length} رسالة متسجلة قبل كده — هتتشال من القايمة مع التسجيل.</p>}
              {view.failed.length > 0 && (
                <details className="smsFold">
                  <summary>{view.failed.length} رسالة ما اتفهمتش</summary>
                  <ul className="smsFailed">
                    {view.failed.map((f) => (
                      <li key={f.messageId}>
                        <span>{f.date} · {f.reason}</span>
                        <button type="button" className="link" disabled={busy} onClick={() => void run(() => user.smsReview.dismiss([f.messageId], target))}>شيلها</button>
                      </li>
                    ))}
                  </ul>
                  <p className="sheet__hint">ابعت لنا صورة الرسالة دي عشان نعلّم التطبيق يقراها. تقدر تضيف العملية بإيدك من «إضافة».</p>
                </details>
              )}
              {view.more && <p className="sheet__hint">فيه رسايل أكتر — سجّل دول الأول وهتظهر الباقية.</p>}
            </>
          )}
        </div>
        {ask && (
          <div className="smsAsk" role="group" aria-label="نفتكر المحل؟">
            <p>نفتكر «{ask.merchant}» على طول إنه <strong>{ask.categoryName}</strong>؟</p>
            <div className="smsAsk__actions">
              <button type="button" className="btn" disabled={busy} onClick={() => void remember(ask)}>أيوه افتكره</button>
              <button type="button" className="btn btn--quiet" disabled={busy} onClick={() => setAsk(null)}>المرة دي بس</button>
            </div>
          </div>
        )}
        {view && (toRecord > 0 || view.duplicates.length > 0) && (
          <footer className="sheet__foot">
            <button type="button" className="btn" disabled={busy || !!ask} onClick={() => void recordAll()}>
              {busy ? 'بنسجّل…' : toRecord ? `سجّل الـ${toRecord} عملية` : 'شيل المتسجل قبل كده'}
            </button>
          </footer>
        )}
      </div>
    </div>
  )
}
