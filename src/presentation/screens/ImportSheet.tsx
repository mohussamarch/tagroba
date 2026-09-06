import { useState, type ChangeEvent } from 'react'
import { formatAmount } from '../../domain/formatMoney'
import { stripBom } from '../../infrastructure/import/csvReader'
import type { ImportPreview } from '../../application/useCases/importStatement'
import type { ImportSourceType, MatchingState } from '../../domain/entities/types'
import type { UserContainer } from '../../app/container'
import './ImportSheet.css'

interface Props {
  user: UserContainer
  onClose: () => void
  onImported: () => void
}

/** علامات مميِّزة لكل مخطط، من spec/05. */
const PREVIEW_HEADER_START = 'date,name,amount'
const LEGACY_DATE = 'التاريخ'
const LEGACY_DEBIT = 'مدين'

/**
 * نوع المصدر للتسجيل في الدفعة. المخطط نفسه يُكتشف من الترويسة داخل
 * parseRows؛ ده وسم للدفعة عشان نعرف بعدين الملف جه منين.
 */
function guessSourceType(content: string): ImportSourceType {
  const header = stripBom(content).slice(0, 200)
  if (header.startsWith(PREVIEW_HEADER_START)) return 'csv_preview'
  if (header.includes(LEGACY_DATE) && header.includes(LEGACY_DEBIT)) return 'csv_legacy'
  return 'csv_preview'
}

const STATE_LABEL: Record<MatchingState, string> = {
  new: 'جديد',
  duplicate: 'مكرر',
  similar: 'متشابه',
  conflict: 'تعارض',
  invalid: 'غير صالح',
}

/**
 * ورقة الاستيراد — spec/04:
 * «اختيار الملف → استخراج → مراجعة الأنواع والتصنيفات والتكرار
 *  → ملخص الأثر → تأكيد ذري»
 *
 * الشاشة لا تحلّل ولا تحسب: تنادي user.importStatement.preview ثم commit.
 */
export function ImportSheet({ user, onClose, onImported }: Props) {
  const [fileName, setFileName] = useState('')
  const [content, setContent] = useState('')
  const [accountIdentity, setAccountIdentity] = useState('الراجحي')
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [busy, setBusy] = useState<'none' | 'reading' | 'previewing' | 'committing'>('none')
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<Set<number>>(new Set())

  const working = busy !== 'none'

  async function onFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) return
    setError(null)
    setPreview(null)
    setBusy('reading')
    try {
      const text = await file.text()
      setFileName(file.name)
      setContent(text)
    } catch {
      setError('مقدرناش نقرأ الملف. اتأكد إنه ملف CSV نصي.')
    } finally {
      setBusy('none')
    }
  }

  async function runPreview() {
    if (!content) {
      setError('اختار ملف الأول')
      return
    }
    setError(null)
    setBusy('previewing')
    try {
      const result = await user.importStatement.preview({
        fileName,
        content,
        accountIdentity: accountIdentity.trim() || 'غير محدد',
        sourceType: guessSourceType(content),
      })
      setPreview(result)
      setSelected(new Set(result.lines.filter((l) => l.selectedByDefault).map((l) => l.row.lineNumber)))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause))
    } finally {
      setBusy('none')
    }
  }

  async function runCommit() {
    if (!preview) return
    setError(null)
    setBusy('committing')
    try {
      await user.importStatement.commit(
        {
          fileName,
          content,
          accountIdentity: accountIdentity.trim() || 'غير محدد',
          sourceType: guessSourceType(content),
        },
        preview,
        [...selected],
      )
      onImported()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause))
      setBusy('none')
    }
  }

  function toggle(lineNumber: number) {
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(lineNumber)) next.delete(lineNumber)
      else next.add(lineNumber)
      return next
    })
  }

  const selectedImpact = preview
    ? preview.lines
        .filter((l) => selected.has(l.row.lineNumber))
        .reduce(
          (acc, l) => {
            if (l.row.direction === 'in') acc.in += l.row.amountMinor
            else acc.out += l.row.amountMinor
            return acc
          },
          { in: 0, out: 0 },
        )
    : { in: 0, out: 0 }

  return (
    <div className="sheet" role="dialog" aria-modal="true" aria-label="استيراد كشف حساب">
      <div className="sheet__panel">
        <header className="sheet__head">
          <h2 className="sheet__title">استيراد كشف</h2>
          <button type="button" className="iconBtn" onClick={onClose} aria-label="إغلاق" disabled={working}>
            <span aria-hidden="true">✕</span>
          </button>
        </header>

        <div className="sheet__body">
          {!preview && (
            <>
              <label className="sheet__field">
                <span className="sheet__label">اسم الحساب أو المصدر</span>
                <input
                  className="sheet__input"
                  value={accountIdentity}
                  onChange={(e) => setAccountIdentity(e.target.value)}
                  disabled={working}
                />
                <span className="sheet__hint">
                  ده اللي بيحدد إن نفس المرجع من حسابين مختلفين مش تكرار.
                </span>
              </label>

              <label className="sheet__field">
                <span className="sheet__label">ملف CSV</span>
                <input
                  className="sheet__input"
                  type="file"
                  accept=".csv,text/csv"
                  onChange={onFile}
                  disabled={working}
                />
                {fileName && <span className="sheet__hint">اخترت: {fileName}</span>}
              </label>

              <button type="button" className="btn" onClick={runPreview} disabled={working || !content}>
                {busy === 'previewing' ? 'بنستخرج…' : 'استخراج ومراجعة'}
              </button>
            </>
          )}

          {error && (
            <p className="sheet__error" role="alert">
              <span aria-hidden="true">⚠</span> {error}
            </p>
          )}

          {preview && (
            <>
              {preview.previousBatch && (
                <p className="notice" role="status">
                  <strong>الملف ده اتستورد قبل كده.</strong> دفعة بتاريخ{' '}
                  {preview.previousBatch.importedAt.slice(0, 10)} استوردت{' '}
                  {preview.previousBatch.counts.imported} عملية. اللي تحت ده هيتضاف من غير تكرار.
                </p>
              )}

              <div className="counts">
                <Count label="جديد" value={preview.counts.newCount} />
                <Count label="مكرر" value={preview.counts.duplicates} />
                <Count label="متشابه" value={preview.counts.similar} />
                <Count label="تعارض" value={preview.counts.conflicts} />
                <Count label="غير صالح" value={preview.counts.invalid} />
              </div>

              <div className="impact">
                <span>أثر المحدد ({selected.size} عملية):</span>
                <span className="num" style={{ color: 'var(--c-incoming)' }}>
                  + {formatAmount(selectedImpact.in)}
                </span>
                <span className="num" style={{ color: 'var(--c-outgoing)' }}>
                  − {formatAmount(selectedImpact.out)}
                </span>
              </div>

              {preview.errors.length > 0 && (
                <details className="errors">
                  <summary>{preview.errors.length} صف مرفوض — اضغط للتفاصيل</summary>
                  <ul>
                    {preview.errors.slice(0, 50).map((e, i) => (
                      <li key={i}>
                        <strong>سطر {e.lineNumber}</strong> ({e.field}): {e.message}
                      </li>
                    ))}
                  </ul>
                </details>
              )}

              <ul className="lines">
                {preview.lines.slice(0, 200).map((line) => (
                  <li key={line.row.lineNumber} className={`line line--${line.state}`}>
                    <label className="line__check">
                      <input
                        type="checkbox"
                        checked={selected.has(line.row.lineNumber)}
                        onChange={() => toggle(line.row.lineNumber)}
                        disabled={line.state === 'conflict' || working}
                        aria-label={`تضمين سطر ${line.row.lineNumber}`}
                      />
                    </label>
                    <div className="line__main">
                      <div className="line__name">
                        {line.row.merchantName || line.row.description || 'بلا اسم'}
                      </div>
                      <div className="line__meta">
                        <span>{line.row.date}</span>
                        <span className="badge">{STATE_LABEL[line.state]}</span>
                      </div>
                      <div className="line__reason">{line.reason}</div>
                    </div>
                    <div
                      className="line__amount num"
                      style={{
                        color: line.row.direction === 'in' ? 'var(--c-incoming)' : 'var(--c-outgoing)',
                      }}
                    >
                      {formatAmount(line.row.amountMinor)}
                    </div>
                  </li>
                ))}
              </ul>
              {preview.lines.length > 200 && (
                <p className="sheet__hint">
                  بنعرض أول 200 سطر بس. التأكيد بيطبّق على كل المحدد ({selected.size}).
                </p>
              )}

              <div className="sheet__foot">
                <button type="button" className="btn btn--quiet" onClick={() => setPreview(null)} disabled={working}>
                  رجوع
                </button>
                <button type="button" className="btn" onClick={runCommit} disabled={working || selected.size === 0}>
                  {busy === 'committing' ? 'بنحفظ…' : `تأكيد استيراد ${selected.size}`}
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}

function Count({ label, value }: { label: string; value: number }) {
  return (
    <div className="count">
      <span className="count__value">{value}</span>
      <span className="count__label">{label}</span>
    </div>
  )
}
