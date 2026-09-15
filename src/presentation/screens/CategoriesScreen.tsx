import { useEffect, useMemo, useRef, useState } from 'react'
import { EyeOff, Plus, X } from 'lucide-react'
import type { UserContainer } from '../../app/container'
import type { Category } from '../../domain/entities/types'
import type { CategorySaveInput } from '../../domain/categoryEdit'
import { manageCategoryView, type ManagedMain } from '../../domain/categoryManageView'
import { REQUIREMENT_LABELS } from '../../domain/categoryTree'
import { CategoryIcon } from '../components/CategoryIcon'
import { ErrorNotice } from '../components/ErrorNotice'
import { catColorStyle } from '../components/categoryStyle'
import { CategoryForm } from './CategoryForm'
import './ImportSheet.css'
import './CategoriesScreen.css'

/**
 * إدارة التصنيفات — OVERRIDES §33.1: بالمجموعات، والفرعي تحت أبوه، وكل تصنيف بلونه ورمزه.
 * المخفي بيظهر هنا بس (عشان يترجع) في قسم مقفول تحت، والإخفاء ما بيمسحش سجل ولا مبالغ ولا قواعد.
 */
export function CategoriesScreen({ user, onClose, onChanged }: { user: UserContainer; onClose: () => void; onChanged: () => void }) {
  const [items, setItems] = useState<Category[]>([])
  const [form, setForm] = useState<{ editing: Category | null; key: number } | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [message, setMessage] = useState('')
  const formRef = useRef<HTMLDivElement>(null)
  const nextKey = useRef(0)

  async function load() {
    setItems(await user.manageCategories.list())
  }
  useEffect(() => {
    void load().catch(setError)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user])

  const formKey = form?.key
  useEffect(() => {
    if (formKey !== undefined) formRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, [formKey])

  const view = useMemo(() => manageCategoryView(items), [items])

  function open(editing: Category | null) {
    nextKey.current += 1
    setForm({ editing, key: nextKey.current })
    setMessage('')
    setError(null)
  }

  async function save(input: CategorySaveInput) {
    setBusy(true)
    setError(null)
    setMessage('')
    try {
      const saved = await user.manageCategories.save(input)
      await load()
      onChanged()
      setForm(null)
      setMessage(`اتحفظ «${saved.name}».`)
    } catch (cause) {
      setError(cause)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="sheet" role="dialog" aria-modal="true" aria-label="التصنيفات وألوانها">
      <section className="sheet__panel">
        <header className="sheet__head">
          <h2 className="sheet__title">التصنيفات وألوانها</h2>
          <button type="button" className="iconBtn" onClick={onClose} aria-label="إغلاق">
            <X size={18} aria-hidden="true" />
          </button>
        </header>
        <div className="sheet__body">
          <p className="sheet__hint">
            دوس على أي تصنيف تغيّر اسمه أو مكانه أو لونه أو رمزه. الإخفاء بيشيله من الاختيارات بس، وسجله
            ومبالغه وقواعده بتفضل زي ما هي.
          </p>
          <div ref={formRef} className="catScreen__form">
            {form ? (
              <CategoryForm
                key={form.key}
                categories={items}
                editing={form.editing}
                busy={busy}
                onSave={(input) => void save(input)}
                onCancel={() => setForm(null)}
              />
            ) : (
              <button type="button" className="btn" disabled={busy} onClick={() => open(null)}>
                <Plus size={18} aria-hidden="true" /> ضيف تصنيف جديد
              </button>
            )}
          </div>
          {error != null && <ErrorNotice cause={error} />}
          {message && (
            <p className="notice" role="status">
              {message}
            </p>
          )}
          {view.groups.map((group) => (
            <section key={group.key} className="catGroup" aria-label={group.label}>
              <h3 className="catGroup__title">
                <CategoryIcon iconKey={group.iconKey} size={18} />
                {group.label}
              </h3>
              <MainList mains={group.mains} busy={busy} onEdit={open} />
            </section>
          ))}
          {/* المخفي (زي التصنيفات القديمة اللي اتدمجت في الشجرة) تحت ومقفول، عشان ما يزاحمش اللي شغال */}
          {view.hidden.length > 0 && (
            <details className="catGroup catHidden">
              <summary className="catGroup__title catHidden__summary">
                <EyeOff size={18} aria-hidden="true" />
                المخفية ({view.hidden.length})
              </summary>
              <MainList mains={view.hidden} busy={busy} onEdit={open} />
            </details>
          )}
        </div>
      </section>
    </div>
  )
}

function MainList({ mains, busy, onEdit }: { mains: readonly ManagedMain[]; busy: boolean; onEdit: (c: Category) => void }) {
  return (
    <ul className="catGroup__list">
      {mains.map(({ category, subs }) => (
        <li key={category.id}>
          <CategoryRow category={category} busy={busy} onEdit={onEdit} />
          {subs.length > 0 && (
            <ul className="catGroup__subs">
              {subs.map((sub) => (
                <li key={sub.id}>
                  <CategoryRow category={sub} busy={busy} onEdit={onEdit} />
                </li>
              ))}
            </ul>
          )}
        </li>
      ))}
    </ul>
  )
}

function CategoryRow({ category, busy, onEdit }: { category: Category; busy: boolean; onEdit: (c: Category) => void }) {
  return (
    <button
      type="button"
      className={`catRow${category.active ? '' : ' catRow--off'}`}
      style={catColorStyle(category)}
      disabled={busy}
      onClick={() => onEdit(category)}
    >
      <span className="catChip" aria-hidden="true">
        <CategoryIcon iconKey={category.iconKey} size={18} />
      </span>
      <span className="catRow__name">{category.name}</span>
      {category.requires && <span className="catRow__badge">{REQUIREMENT_LABELS[category.requires]}</span>}
      {!category.active && (
        <span className="catRow__badge">
          <EyeOff size={12} aria-hidden="true" /> مخفي
        </span>
      )}
    </button>
  )
}
