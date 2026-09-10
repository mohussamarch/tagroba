import { useState, type ChangeEvent } from 'react'
import { formatAmount } from '../../domain/formatMoney'
import { guessSourceType } from '../../infrastructure/import/detectSourceType'
import { inspectFile } from '../../infrastructure/import/inspectFile'
import type { ImportPreview } from '../../application/useCases/importStatement'
import type { ParsedRow } from '../../infrastructure/import/schemas'
import type { Wallet } from '../../domain/entities/types'
import type { UserContainer } from '../../app/container'
import { Count } from '../components/Count'
import { ImportLine } from '../components/ImportLine'
import { StatementPicker } from '../components/StatementPicker'
import './ImportSheet.css'

interface Props {
  initialSms?: { rows: ParsedRow[]; content: string }
  user: UserContainer
  wallets: Wallet[]
  onClose: () => void
  onImported: () => void
}

/**
 * ورقة الاستيراد — spec/04:
 * «اختيار الملف → استخراج → مراجعة الأنواع والتصنيفات والتكرار
 *  → ملخص الأثر → تأكيد ذري»
 *
 * الشاشة لا تحلّل ولا تحسب: تنادي user.importStatement.preview ثم commit.
 */
export function ImportSheet({ user, wallets, onClose, onImported, initialSms }: Props) {
  const [fileName, setFileName] = useState(initialSms ? 'bank-sms.json' : '')
  const [content, setContent] = useState('')
  /** صفوف الـPDF المحلَّلة. فاضية في مسار الـCSV. */
  const [pdfRows, setPdfRows] = useState<ParsedRow[] | null>(initialSms?.rows ?? null)
  const [pdfNote, setPdfNote] = useState<string | null>(null)
  const [walletId, setWalletId] = useState(wallets[0]?.id ?? '')
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [busy, setBusy] = useState<'none' | 'reading' | 'previewing' | 'committing'>('none')
  const [readProgress, setReadProgress] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<Set<number>>(new Set())

  const working = busy !== 'none'
  /*
   * هوية الحساب تُشتق من اسم المحفظة لا يكتبها المستخدم:
   * هي نطاق تفرّد المرجع البنكي (spec/03)، وكتابتها يدويًا كانت تسمح
   * بخطأ مطبعي يجعل نفس الكشف يُستورد مرتين كأنه حسابان.
   */
  const walletName = wallets.find((w) => w.id === walletId)?.name ?? 'غير محدد'

  async function onFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) return
    setError(null)
    setPreview(null)
    setPdfRows(null)
    setPdfNote(null)
    setBusy('reading')
    try {
      const text = await file.text()
      /*
       * الفحص **قبل** أي تحليل. File.text() ينجح على أي ملف، فبدونه
       * يصل PDF إلى قارئ CSV فيشتكي من «اقتباس مفتوح» — رسالة مضلِّلة
       * تخفي السبب الحقيقي عن المستخدم.
       */
      const check = inspectFile(file.name, text)

      if (check.kind === 'pdf') {
        // مسار مختلف تمامًا: بايتات لا نص، وقارئ PDF لا قارئ CSV
        const result = await user.readPdfStatement(await file.arrayBuffer(), (p) =>
          setReadProgress(`بنقرا صفحة ${p.page} من ${p.total}`),
        )
        setFileName(file.name)
        setContent(result.content)
        setPdfRows(result.rows)
        setPdfNote(
          `قرينا ${result.pagesRead} صفحة ولقينا ${result.rows.length} عملية` +
            (result.errors.length > 0 ? ` · ${result.errors.length} سطر مش واضح واتساب` : ''),
        )
        return
      }

      if (!check.ok) {
        setError(check.message)
        setContent('')
        setFileName('')
        return
      }
      setFileName(file.name)
      setContent(text)
    } catch (cause) {
      // سبب القارئ بيتقال زي ما هو — أوضح من رسالة عامة
      setError(
        cause instanceof Error
          ? cause.message
          : 'مقدرناش نقرأ الملف. اتأكد إنه ملف موجود ومش متقفول في تطبيق تاني.',
      )
    } finally {
      setReadProgress(null)
      setBusy('none')
    }
  }

  /**
   * الطلب يُبنى **مرة واحدة** ويُستعمل في المعاينة والتثبيت.
   * لما كان بيتكتب مرتين، كان سهل ينسى حقل في واحدة منهم —
   * وبصمة الملف بتتغير فيتحسب الكشف الواحد كشفين.
   */
  function buildRequest() {
    return {
      fileName,
      content,
      accountIdentity: walletName,
      sourceType: initialSms ? 'sms' as const : guessSourceType(content),
      walletId,
      ...(pdfRows ? { parsedRows: pdfRows, schema: initialSms ? 'sms' as const : 'alrajhi_pdf' as const } : {}),
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
      const result = await user.importStatement.preview(buildRequest())
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
      await user.importStatement.commit(buildRequest(), preview, [...selected])
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
              {initialSms ? <label>المحفظة المرتبطة برسائل البنك<select value={walletId} onChange={e=>setWalletId(e.target.value)} disabled={working}>{wallets.map(w=><option key={w.id} value={w.id}>{w.name}</option>)}</select></label> : <StatementPicker
                wallets={wallets}
                walletId={walletId}
                onWalletChange={setWalletId}
                onFile={onFile}
                fileName={fileName}
                note={pdfNote}
                progress={readProgress}
                disabled={working}
              />}

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
                  <ImportLine
                    key={line.row.lineNumber}
                    line={line}
                    checked={selected.has(line.row.lineNumber)}
                    disabled={line.state === 'conflict' || working}
                    onToggle={() => toggle(line.row.lineNumber)}
                  />
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
