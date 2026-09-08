import { MerchantAliasEditor } from '../components/MerchantAliasEditor'
import { useEffect, useState, type FormEvent } from 'react'
import { ErrorNotice } from '../components/ErrorNotice'
import type { RuleMatchMode, Category } from '../../domain/entities/types'
import type { MerchantRow, RuleRow } from '../../application/useCases/manageRules'
import type { UserContainer } from '../../app/container'
import './RulesScreen.css'

interface Props {
  user: UserContainer
  categories: Category[]
  onClose: () => void
}

const MODE_LABEL: Record<RuleMatchMode, string> = {
  contains: 'يحتوي',
  startsWith: 'يبدأ بـ',
  exact: 'مطابق تمامًا',
}

/**
 * إدارة القواعد والتجار — `spec/05`: المراجع **قابلة للتحرير**.
 *
 * ترتيب الأولويات في التصنيف ثابت ومكتوب في الشاشة:
 * تأكيدك ← تاجر موثّق ← القواعد ← اقتراح. تعديل القواعد بيغيّر
 * الخطوة التالتة بس — عشان المستخدم يفهم ليه قاعدته ممكن ما تتطبقش.
 */
export function RulesScreen({ user, categories, onClose }: Props) {
  const [tab, setTab] = useState<'rules' | 'merchants'>('rules')
  const [rules, setRules] = useState<RuleRow[]>([])
  const [merchants, setMerchants] = useState<MerchantRow[]>([])
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  const [text, setText] = useState('')
  const [mode, setMode] = useState<RuleMatchMode>('contains')
  const [categoryId, setCategoryId] = useState(categories[0]?.id ?? '')

  async function reload() {
    try {
      const [r, m] = await Promise.all([
        user.manageRules.listRules(),
        user.manageRules.listMerchants(),
      ])
      setRules(r)
      setMerchants(m)
    } catch (cause) {
      setError(cause)
    }
  }

  useEffect(() => {
    void reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user])

  async function run(action: () => Promise<unknown>) {
    setError(null)
    setBusy(true)
    try {
      await action()
      await reload()
    } catch (cause) {
      setError(cause)
    } finally {
      setBusy(false)
    }
  }

  function addRule(event: FormEvent) {
    event.preventDefault()
    void run(async () => {
      await user.manageRules.addRule({ matchText: text, matchMode: mode, categoryId })
      setText('')
    })
  }

  return (
    <div className="sheet" role="dialog" aria-modal="true" aria-label="القواعد والتجار">
      <div className="sheet__panel">
        <header className="sheet__head">
          <h2 className="sheet__title">القواعد والتجار</h2>
          <button type="button" className="iconBtn" onClick={onClose} aria-label="إغلاق">
            <span aria-hidden="true">✕</span>
          </button>
        </header>

        <div className="sheet__body">
          {/* الترتيب مكتوب: المستخدم يفهم ليه قاعدته ممكن ما تتطبقش */}
          <p className="rules__order">
            ترتيب التصنيف: <strong>تأكيدك</strong> ← <strong>تاجر موثّق</strong> ←
            <strong> القواعد</strong> ← اقتراح. القاعدة ما بتكتبش فوق حاجة أكّدتها بنفسك.
          </p>

          <div className="rules__tabs" role="tablist">
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'rules'}
              className={`chip${tab === 'rules' ? ' chip--on' : ''}`}
              onClick={() => setTab('rules')}
            >
              القواعد ({rules.length})
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'merchants'}
              className={`chip${tab === 'merchants' ? ' chip--on' : ''}`}
              onClick={() => setTab('merchants')}
            >
              التجار ({merchants.length})
            </button>
          </div>

          {error ? <ErrorNotice cause={error} /> : null}

          {tab === 'rules' ? (
            <>
              <form className="rules__add" onSubmit={addRule} noValidate>
                <input
                  className="sheet__input"
                  type="text"
                  value={text}
                  maxLength={60}
                  disabled={busy}
                  onChange={(e) => setText(e.target.value)}
                  placeholder="النص في اسم العملية"
                />
                <select
                  className="sheet__input"
                  value={mode}
                  disabled={busy}
                  onChange={(e) => setMode(e.target.value as RuleMatchMode)}
                >
                  {(Object.keys(MODE_LABEL) as RuleMatchMode[]).map((m) => (
                    <option key={m} value={m}>
                      {MODE_LABEL[m]}
                    </option>
                  ))}
                </select>
                <select
                  className="sheet__input"
                  value={categoryId}
                  disabled={busy}
                  onChange={(e) => setCategoryId(e.target.value)}
                >
                  {categories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
                <button type="submit" className="btn" disabled={busy || !text.trim()}>
                  ضيف قاعدة
                </button>
              </form>

              <ul className="rules__list">
                {rules.map((row) => (
                  <li
                    key={row.rule.id}
                    className={`rules__row${row.rule.enabled ? '' : ' rules__row--off'}`}
                  >
                    <div className="rules__main">
                      <span className="rules__text">
                        {MODE_LABEL[row.rule.matchMode]} «{row.rule.matchText}»
                      </span>
                      <span
                        className={`rules__cat${row.categoryMissing ? ' rules__cat--missing' : ''}`}
                      >
                        ← {row.categoryName}
                      </span>
                    </div>
                    {/* القفل مش حذف — القاعدة بتفضل ظاهرة عشان المستخدم يفتكرها */}
                    <button
                      type="button"
                      className="link"
                      disabled={busy}
                      onClick={() =>
                        void run(() =>
                          user.manageRules.setRuleEnabled(row.rule.id, !row.rule.enabled),
                        )
                      }
                    >
                      {row.rule.enabled ? 'اقفلها' : 'افتحها'}
                    </button>
                  </li>
                ))}
              </ul>
              {rules.length === 0 && <p className="sheet__hint">مفيش قواعد لسه.</p>}
            </>
          ) : (
            <>
              <MerchantAliasEditor merchants={merchants.map(r=>r.merchant)} busy={busy} onSave={(id,alias)=>run(()=>user.manageRules.addAlias(id,alias))}/>
              <p className="sheet__hint">
                تصنيف التاجر هنا <strong>أقوى من كل القواعد</strong>. الاسم بيتغيّر للعرض
                بس — الربط بعملياته القديمة بيفضل زي ما هو.
              </p>
              <ul className="rules__list">
                {merchants.map((row) => (
                  <li key={row.merchant.id} className="rules__row">
                    <div className="rules__main">
                      <span className="rules__text">{row.merchant.displayName}</span>
                      <span className="rules__cat">
                        {row.verifiedCategoryName ?? 'بلا تصنيف موثّق'}
                      </span>
                    </div>
                    <select
                      className="sheet__input rules__pick"
                      value={row.merchant.verifiedCategoryId ?? ''}
                      disabled={busy}
                      onChange={(e) =>
                        void run(() =>
                          user.manageRules.setMerchantCategory(
                            row.merchant.id,
                            e.target.value || null,
                          ),
                        )
                      }
                    >
                      <option value="">بلا تثبيت</option>
                      {categories.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.name}
                        </option>
                      ))}
                    </select>
                  </li>
                ))}
              </ul>
              {merchants.length === 0 && <p className="sheet__hint">مفيش تجار لسه.</p>}
            </>
          )}
        </div>
      </div>
    </div>
  )
}
