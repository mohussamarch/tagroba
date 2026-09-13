import { useState } from 'react'
import type { UserContainer } from '../../app/container'
import type { RepairProgress } from '../../application/useCases/repairStoredIds'

type Plan = Awaited<ReturnType<UserContainer['cleanupOrphans']['preview']>>

const kb = (bytes: number) => `${Math.max(1, Math.round(bytes / 1024))} ك.ب`
const ORIGIN = { statement: 'من كشف مسجّل', revertedLeftover: 'من بقايا الإكسل اللي فضلت', noSource: 'من غير مصدر (يدوي أو رسالة)' } as const
const BATCH_TYPE: Record<string, string> = { pdf_alrajhi: 'PDF', csv_preview: 'CSV', csv_legacy: 'CSV قديم', sms: 'رسائل' }
const batchText = (batch: string) => { const [type, day] = batch.split(' '); return `${BATCH_TYPE[type] ?? type} ${day ?? ''}`.trim() }
const text = (error: unknown) => (error instanceof Error ? error.message : String(error))

/**
 * تنظيف بقايا استيراد متراجَع عنه — قرار المالك 2026-09-13 (HANDOVER §36).
 * المعاينة قراءة بس، وبتعرض **أعداد** (مفيش مبالغ ولا أوصاف). الحذف بعد زر تأكيد فيه
 * العدد، وبعد نسخة مؤكدة الحجم في مجلد التطبيق. الإلغاء لا يُعطَّل أبدًا (HANDOVER §9.3).
 */
export function OrphanCleanupPanel({ user, onDone }: { user: UserContainer; onDone: () => void }) {
  const [plan, setPlan] = useState<Plan | null>(null)
  const [busy, setBusy] = useState(false)
  const [progress, setProgress] = useState<RepairProgress | null>(null)
  const [message, setMessage] = useState('')

  async function check() {
    setBusy(true); setMessage(''); setPlan(null)
    try {
      const result = await user.cleanupOrphans.preview()
      const nothing = result.transactions.length === 0 && result.records.length === 0
      if (nothing && result.keptLinked === 0 && result.keptNoEvidence === 0) {
        setMessage('مفيش بقايا من استيرادات متراجَع عنها.')
      } else setPlan(result)
    } catch (error) {
      setMessage('الفحص ما كملش: ' + text(error))
    } finally { setBusy(false) }
  }

  async function clean() {
    if (!plan) return
    setBusy(true); setMessage(''); setProgress(null)
    try {
      const outcome = await user.cleanupOrphans.apply(plan, setProgress)
      const saved = outcome.backup
      setMessage((saved
        ? saved.bytes === null ? 'النسخة اتبعتت لتنزيلات المتصفح (المتصفح مش بيأكد حجمها). ' : `النسخة اتحفظت (${kb(saved.bytes)}) في مجلد التطبيق. `
        : '')
        + `اتمسح ${outcome.removed} مستند.`
        + (outcome.skipped ? ` و${outcome.skipped} ظهروا بعد المعاينة فما اتلمسوش — افحص تاني.` : ''))
      setPlan(null)
      onDone()
    } catch (error) {
      setMessage('التنظيف ما كملش: ' + text(error) + '. دوس «افحص» تاني وكمّل — اللي اتمسح مش هيظهر تاني.')
    } finally { setBusy(false); setProgress(null) }
  }

  const total = plan ? plan.transactions.length + plan.records.length : 0
  const months = plan ? Object.entries(plan.byMonth).sort(([a], [b]) => b.localeCompare(a)) : []
  return (
    <section className="sheet__field" aria-label="بقايا استيراد متراجع عنه">
      <p className="settings__hint">
        التراجع عن استيراد في نسخة قديمة كان ممكن يسيب عمليات مكررة وسجلات من غير عملية. الفحص بيقرا بس.
      </p>
      <button type="button" className="btn btn--quiet" onClick={() => void check()} disabled={busy}>
        {busy && !plan ? 'بنفحص…' : 'افحص بقايا الاستيرادات المتراجَع عنها'}
      </button>
      {message && <p className="notice" role="status">{message}</p>}
      {plan && <>
        <p>المعاينة بس — لسه ما اتمسحش حاجة.</p>
        <ul>
          <li>عمليات مكررة هتتمسح (التوأم ورصيد الكشف متفقين): {plan.transactions.length}</li>
          {plan.chainUnconfirmed.length > 0 && (
            <li>ليها توأم بس رصيد الكشف ما أكدش إنها زيادة — هتفضل: {plan.chainUnconfirmed.length}</li>
          )}
          <li>سجلات مصدر من غير عملية هتتمسح: {plan.records.length}</li>
        </ul>
        {months.length > 0 && <details>
          <summary>العمليات المكررة حسب الشهر</summary>
          <ul>{months.map(([month, count]) => <li key={month}>{month}: {count}</li>)}</ul>
        </details>}
        <div className="notice">
          <p>
            دليل من رصيد الكشف ({plan.chain.now.checked} سطر فيه رصيد): كل «كسر» سطر رصيده مش مساوي لرصيد اللي قبله
            بزيادة أو نقص مبلغه. صفر كسر يعني مفيش مكرر فاضل ومفيش عملية حقيقية اتشالت.
          </p>
          <ul>
            <li>دلوقتي: {plan.chain.now.breaks} كسر</li>
            <li>بعد التنظيف: {plan.chain.afterCleanup.breaks} كسر</li>
            {plan.keptNoEvidence > 0 && (
              <li>لو اللي من غير توأم ({plan.keptNoEvidence}) اتشالوا كمان — افتراض بس، مش هيتمسحوا: {plan.chain.ifUnprovenRemovedToo.breaks} كسر</li>
            )}
          </ul>
          {plan.remainingBreaks.length > 0 && <details>
            <summary>الكسور اللي هتفضل — جاية منين</summary>
            <ul>{plan.remainingBreaks.map((g, i) => (
              <li key={i}>
                {g.count} · السطر {ORIGIN[g.origin]}{g.batch && ` (${batchText(g.batch)})`}
                {g.previousOrigin && ` · اللي قبله ${ORIGIN[g.previousOrigin]}`}
                {' · '}{g.sameDayAsPrevious ? 'نفس اليوم' : 'يوم تاني'}
                {g.hasExactTwin && ' · ليه نسخة مطابقة حتى في الرصيد'}
              </li>
            ))}</ul>
          </details>}
          {plan.chain.afterCleanup.breaks > 0 && <details>
            <summary>الكسور اللي هتفضل بعد التنظيف حسب الشهر</summary>
            <ul>{Object.entries(plan.chain.afterCleanup.breakMonths).sort(([a], [b]) => b.localeCompare(a))
              .map(([month, count]) => <li key={month}>{month}: {count}</li>)}</ul>
          </details>}
        </div>
        {plan.keptLinked > 0 && (
          <p className="notice">{plan.keptLinked} عملية من نفس الاستيراد متربطة بشخص أو تسوية أو وسم — مش هتتمسح.</p>
        )}
        {plan.keptNoEvidence > 0 && (
          <p className="notice">
            {plan.keptNoEvidence} عملية من نفس الاستيراد مالهاش توأم مسجّل يثبت إنها مكررة — مش هتتمسح.
            رصيد الكشف بيقول: {plan.unprovenVerdict.looksReal} شكلها حقيقية ·
            {' '}{plan.unprovenVerdict.looksDuplicate} شكلها مكررة بتاريخ مختلف · {plan.unprovenVerdict.unclear} مش واضحة.
          </p>
        )}
        {total > 0 && <>
          <p className="settings__hint">
            العملية بتتمسح بس لو ليها توأم بنفس اليوم والمبلغ والاتجاه من كشف مسجّل، ومفيش عليها أي ارتباط،
            ورصيد الكشف بيأكد إن وجودها بيكسر السلسلة.
            قبل الحذف هيتحفظ ملف فيه المستندات دي كاملة. خليك جوه التطبيق لحد ما يخلص.
          </p>
          <button type="button" className="btn" onClick={() => void clean()} disabled={busy}>
            {busy ? (progress ? `بنمسح… ${progress.written} من ${progress.total}` : 'بنحفظ النسخة…') : `احفظ نسخة وامسح ${total}`}
          </button>
        </>}
        <button type="button" className="btn btn--quiet" onClick={() => setPlan(null)}>إلغاء</button>
      </>}
    </section>
  )
}
