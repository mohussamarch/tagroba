import './AddMenu.css'

interface Props {
  onClose: () => void
  onAddTransaction: () => void
  onImport: () => void
  onSms: () => void
}

/**
 * قائمة زر الإضافة — spec/01:
 * «+ | قائمة فاتورة/حركة/كشف/شخص؛ كل خيار يفتح نموذجًا مناسبًا».
 *
 * ⚠️ الزر كان يفتح الاستيراد مباشرة، فلم يكن هناك أي طريق لتسجيل
 * عملية يدويًا — والكاش تحديدًا لا يصل من أي كشف.
 * اكتشفه المالك بالاستعمال: اشترى قهوة بالكاش ولم يجد أين يسجّلها.
 */
export function AddMenu({ onClose, onAddTransaction, onImport, onSms }: Props) {
  return (
    <div className="sheet" role="dialog" aria-modal="true" aria-label="إضافة">
      <div className="sheet__panel addMenu">
        <header className="sheet__head">
          <h2 className="sheet__title">تضيف إيه؟</h2>
          <button type="button" className="iconBtn" onClick={onClose} aria-label="إغلاق">
            <span aria-hidden="true">✕</span>
          </button>
        </header>

        <div className="sheet__body">
          <button className="addMenu__item" onClick={onSms}><span className="addMenu__icon" aria-hidden="true">✉</span><span className="addMenu__text"><b>رسائل البنك</b><small>قراءة الرسائل أو لصق رسالة، ثم مراجعتها قبل الحفظ</small></span></button>
          <button type="button" className="addMenu__item" onClick={onAddTransaction}>
            <span className="addMenu__icon" aria-hidden="true">🧾</span>
            <span className="addMenu__text">
              <b>عملية واحدة</b>
              <small>قهوة، بنزين، تحويل — أي حاجة عملتها دلوقتي</small>
            </span>
          </button>

          <button type="button" className="addMenu__item" onClick={onImport}>
            <span className="addMenu__icon" aria-hidden="true">📄</span>
            <span className="addMenu__text">
              <b>كشف حساب</b>
              <small>ملف CSV فيه عمليات كتير مرة واحدة</small>
            </span>
          </button>
        </div>
      </div>
    </div>
  )
}
