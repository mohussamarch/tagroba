import type { ChangeEvent } from 'react'
import type { Wallet } from '../../domain/entities/types'
import '../screens/ImportSheet.css'

interface Props {
  wallets: Wallet[]
  walletId: string
  onWalletChange: (id: string) => void
  onFile: (event: ChangeEvent<HTMLInputElement>) => void
  fileName: string
  /** نتيجة قراءة الـPDF بأرقامها. */
  note: string | null
  /** تقدّم القراءة أثناء الشغل. */
  progress: string | null
  disabled: boolean
}

/**
 * اختيار المحفظة وملف الكشف — مفصول عن `ImportSheet` لحد الـ300 سطر.
 *
 * بيقبل **PDF وCSV**: كشف الراجحي بيتقرا مباشرة من الـPDF
 * (`ARCHITECTURE §19`) بلا ما المستخدم يحوّله لحاجة تانية.
 */
export function StatementPicker({
  wallets,
  walletId,
  onWalletChange,
  onFile,
  fileName,
  note,
  progress,
  disabled,
}: Props) {
  return (
    <>
      <label className="sheet__field">
        <span className="sheet__label">المحفظة</span>
        <select
          className="sheet__input"
          value={walletId}
          onChange={(e) => onWalletChange(e.target.value)}
          disabled={disabled}
        >
          {wallets.map((w) => (
            <option key={w.id} value={w.id}>
              {w.name}
            </option>
          ))}
        </select>
        <span className="sheet__hint">
          الكشف ده بتاع أنهي محفظة؟ ده اللي بيربط العمليات بيها عشان مطابقة الرصيد
          تشتغل، وبيمنع اعتبار نفس المرجع من حسابين تكرارًا.
        </span>
      </label>

      <label className="sheet__field">
        <span className="sheet__label">ملف الكشف</span>
        <input
          className="sheet__input"
          type="file"
          accept=".csv,text/csv,.pdf,application/pdf"
          onChange={onFile}
          disabled={disabled}
        />
        {fileName ? (
          <span className="sheet__hint">اخترت: {fileName}</span>
        ) : (
          <span className="sheet__hint">كشف الراجحي بصيغة PDF، أو ملف CSV.</span>
        )}

        {/* النتيجة بأرقامها لا بـ«تم» — «تم» بتخفي إن العدد ممكن يكون صفر */}
        {note ? <span className="sheet__hint">{note}</span> : null}
        {progress ? (
          <span className="sheet__hint" role="status">
            {progress}
          </span>
        ) : null}
      </label>
    </>
  )
}
