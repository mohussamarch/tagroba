import { useEffect, useState, type FormEvent } from 'react'
import { FolderPlus, X } from 'lucide-react'
import type { UserContainer } from '../../app/container'
import type { Transaction } from '../../domain/entities/types'
import type { Project } from '../../domain/entities/projectEntities'
import { formatAmount } from '../../domain/formatMoney'
import { ErrorNotice } from '../components/ErrorNotice'
import { ProjectRuleForm } from '../components/ProjectRuleForm'
import './ImportSheet.css'
import './ProjectsScreen.css'

/**
 * «ضيف لمشروع» من النقط التلاتة — OVERRIDES §34. العملية ممكن تبقى في أكتر من مشروع، وبتفضل بتصنيفها.
 * «ضيفها أوتوماتيك كل مرة» بتعمل قاعدة من اسم العملية للمشروع اللي تختاره (والقديم بسؤال).
 */
export function AddToProjectSheet({
  user,
  transaction,
  amountsHidden,
  onClose,
  onChanged,
}: {
  user: UserContainer
  transaction: Transaction
  amountsHidden: boolean
  onClose: () => void
  onChanged: () => void
}) {
  const [items, setItems] = useState<{ project: Project; member: boolean }[] | null>(null)
  const [name, setName] = useState('')
  const [autoFor, setAutoFor] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [message, setMessage] = useState('')

  const title = transaction.rawMerchantName?.trim() || transaction.rawDescription?.trim() || 'بلا اسم'
  const ruleText = transaction.rawMerchantName?.trim() || transaction.rawDescription?.trim() || ''

  async function load() {
    const next = await user.manageProjects.membership(transaction.id)
    setItems(next)
    setAutoFor((current) => current || next.find((i) => i.member)?.project.id || '')
  }
  useEffect(() => {
    void load().catch(setError)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, transaction.id])

  async function run(action: () => Promise<unknown>, done: string) {
    setBusy(true)
    setError(null)
    setMessage('')
    try {
      await action()
      await load()
      onChanged()
      setMessage(done)
    } catch (cause) {
      setError(cause)
    } finally {
      setBusy(false)
    }
  }

  function create(event: FormEvent) {
    event.preventDefault()
    void run(async () => {
      const project = await user.manageProjects.create(name)
      await user.manageProjects.setMember(transaction.id, project.id, true)
      setName('')
    }, 'اتعمل المشروع والعملية اتضافت له.')
  }

  const members = items?.filter((i) => i.member) ?? []
  return (
    <div className="sheet" role="dialog" aria-modal="true" aria-label="ضيف لمشروع">
      <section className="sheet__panel">
        <header className="sheet__head">
          <h2 className="sheet__title">ضيف لمشروع</h2>
          <button type="button" className="iconBtn" onClick={onClose} aria-label="إغلاق">
            <X size={18} aria-hidden="true" />
          </button>
        </header>
        <div className="sheet__body">
          <p className="projectTxn">
            <strong>{title}</strong> · {transaction.occurredAt} ·{' '}
            <span className="num">{amountsHidden ? '••••' : formatAmount(transaction.amountMinor, transaction.currency)}</span>
          </p>
          <p className="sheet__hint">العملية بتفضل بتصنيفها، وممكن تبقى في أكتر من مشروع — ومبلغها ما يتحسبش مرتين في أي مجموع.</p>
          {message && <p className="notice" role="status">{message}</p>}
          {error != null && <ErrorNotice cause={error} />}
          {!items && error == null && <p role="status">بنحمّل المشاريع…</p>}
          {items && items.length > 0 && (
            <ul className="projectChecks">
              {items.map(({ project, member }) => (
                <li key={project.id}>
                  <label className="projectCheck">
                    <input
                      type="checkbox"
                      checked={member}
                      disabled={busy}
                      onChange={(e) => void run(
                        () => user.manageProjects.setMember(transaction.id, project.id, e.target.checked),
                        e.target.checked ? `اتضافت لـ«${project.name}».` : `اتشالت من «${project.name}».`,
                      )}
                    />
                    {project.name}
                    {project.archived && <span className="projectBadge">مؤرشف</span>}
                  </label>
                </li>
              ))}
            </ul>
          )}
          <form className="projectCreate" onSubmit={create} noValidate>
            <input
              className="sheet__input"
              value={name}
              maxLength={60}
              disabled={busy}
              onChange={(e) => setName(e.target.value)}
              placeholder="أو مشروع جديد"
              aria-label="اسم مشروع جديد"
            />
            <button type="submit" className="btn" disabled={busy || !name.trim()}>
              <FolderPlus size={18} aria-hidden="true" /> اعمل وضيف
            </button>
          </form>
          {ruleText && members.length > 0 && (
            <details className="projectAuto">
              <summary>ضيفها أوتوماتيك كل مرة</summary>
              <p className="sheet__hint">أي عملية جديدة فيها «{ruleText}» هتتضاف للمشروع لوحدها. تقدر تعدّل النص قبل الحفظ.</p>
              {members.length > 1 && (
                <label className="sheet__field">
                  للمشروع
                  <select className="sheet__input" value={autoFor} disabled={busy} onChange={(e) => setAutoFor(e.target.value)}>
                    {members.map(({ project }) => (
                      <option key={project.id} value={project.id}>{project.name}</option>
                    ))}
                  </select>
                </label>
              )}
              {autoFor && (
                <ProjectRuleForm
                  key={autoFor}
                  user={user}
                  projectId={autoFor}
                  initialText={ruleText}
                  initialDirection={transaction.observedDirection}
                  onDone={(done) => {
                    setMessage(done)
                    onChanged()
                  }}
                />
              )}
            </details>
          )}
        </div>
      </section>
    </div>
  )
}
