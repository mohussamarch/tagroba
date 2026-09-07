import '../screens/ImportSheet.css'

/** عدّاد في ملخص الاستيراد — مستخرج من ImportSheet لحد الـ300 سطر. */
export function Count({ label, value }: { label: string; value: number }) {
  return (
    <div className="count">
      <span className="count__value">{value}</span>
      <span className="count__label">{label}</span>
    </div>
  )
}
