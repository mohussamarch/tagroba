import { useEffect, useState, type FormEvent } from 'react'
import { ErrorNotice } from './ErrorNotice'
import type { Tag } from '../../domain/entities/types'
import type { UserContainer } from '../../app/container'
import '../screens/TransactionSheet.css'

interface Props {
  user: UserContainer
  transactionId: string
  tags: Tag[]
  onChanged: (tags: Tag[]) => void
}

/**
 * وسوم عملية — إضافة وحذف واقتراح من الموجود.
 *
 * `spec/02`: «انضمام جدول الوسوم **لا يجوز أن يضاعف SUM**».
 * الضمانة دي في `domain/ledger.ts` (الجمع بيعدّ العمليات المميزة)،
 * والشاشة بتعرض الوسوم كعلامات مش كمبالغ — فمفيش مكان يوحي بالمضاعفة.
 */
export function TagEditor({ user, transactionId, tags, onChanged }: Props) {
  const [name, setName] = useState('')
  const [all, setAll] = useState<Tag[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    let alive = true
    user.editTransaction
      .listTags()
      .then((list) => alive && setAll(list))
      .catch(() => {})
    return () => {
      alive = false
    }
  }, [user, tags])

  async function add(value: string) {
    const trimmed = value.trim()
    if (!trimmed) return
    setError(null)
    setBusy(true)
    try {
      await user.editTransaction.addTag(transactionId, trimmed)
      const detail = await user.editTransaction.load(transactionId)
      setName('')
      onChanged(detail.tags)
    } catch (cause) {
      setError(cause)
    } finally {
      setBusy(false)
    }
  }

  async function remove(tagId: string) {
    setError(null)
    setBusy(true)
    try {
      await user.editTransaction.removeTag(transactionId, tagId)
      const detail = await user.editTransaction.load(transactionId)
      onChanged(detail.tags)
    } catch (cause) {
      setError(cause)
    } finally {
      setBusy(false)
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    void add(name)
  }

  const attached = new Set(tags.map((t) => t.id))
  const suggestions = all.filter((t) => !attached.has(t.id)).slice(0, 8)

  return (
    <div className="sheet__field tagEditor">
      <span className="sheet__label">وسوم</span>

      {tags.length > 0 ? (
        <div className="tagEditor__row">
          {tags.map((tag) => (
            <span key={tag.id} className="tagChip">
              {tag.displayName}
              <button
                type="button"
                className="tagChip__x"
                onClick={() => void remove(tag.id)}
                disabled={busy}
                aria-label={`شيل وسم ${tag.displayName}`}
              >
                ✕
              </button>
            </span>
          ))}
        </div>
      ) : (
        <span className="sheet__hint">مفيش وسوم على العملية دي.</span>
      )}

      <form className="tagEditor__add" onSubmit={submit} noValidate>
        <input
          className="sheet__input"
          type="text"
          value={name}
          maxLength={40}
          disabled={busy}
          onChange={(e) => setName(e.target.value)}
          placeholder="وسم جديد"
        />
        <button type="submit" className="btn" disabled={busy || !name.trim()}>
          ضيف
        </button>
      </form>

      {suggestions.length > 0 && (
        <div className="tagEditor__row">
          {/* اقتراح من الموجود — بيقلل الوسوم المكررة بأسماء متقاربة */}
          {suggestions.map((tag) => (
            <button
              key={tag.id}
              type="button"
              className="tagChip tagChip--suggest"
              onClick={() => void add(tag.displayName)}
              disabled={busy}
            >
              ＋ {tag.displayName}
            </button>
          ))}
        </div>
      )}

      <span className="sheet__hint">
        الوسم بيجمّع عمليات من تصنيفات مختلفة. عملية عليها كذا وسم بتتحسب
        <strong> مرة واحدة</strong> في أي مجموع.
      </span>

      {error ? <ErrorNotice cause={error} /> : null}
    </div>
  )
}
