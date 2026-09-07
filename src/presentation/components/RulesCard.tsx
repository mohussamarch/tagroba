import '../screens/SettingsScreen.css'

/** مدخل شاشة القواعد والتجار — مفصول لحد الملف 300 سطر. */
export function RulesCard({ onOpen }: { onOpen: () => void }) {
  return (
    <section className="card" aria-label="القواعد والتجار">
      <h2 className="card__title">التصنيف التلقائي</h2>
      <p className="settings__hint">
        القواعد اللي بتصنّف عملياتك، والتجار اللي ثبّتّ تصنيفهم. تقدر تعدّلهم وتقفل
        اللي مش عاجبك — والقاعدة عمرها ما بتكتب فوق حاجة أكّدتها بنفسك.
      </p>
      <button type="button" className="btn btn--quiet" onClick={onOpen}>
        القواعد والتجار
      </button>
    </section>
  )
}
