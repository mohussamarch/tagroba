import { useEffect, useState, type FormEvent } from 'react'
import { FolderKanban, FolderPlus, X } from 'lucide-react'
import type { UserContainer } from '../../app/container'
import type { ProjectRow } from '../../application/useCases/manageProjects'
import type { Category } from '../../domain/entities/types'
import { formatAmount } from '../../domain/formatMoney'
import { ErrorNotice } from '../components/ErrorNotice'
import { ProjectDetailView } from './ProjectDetailView'
import './ImportSheet.css'
import './ProjectsScreen.css'

/**
 * «المشاريع» من «المزيد» — OVERRIDES §34: مجموعة عمليات من تصنيفات مختلفة بتخدم هدف واحد،
 * وكل مشروع صرفت فيه قد إيه وجالك منه قد إيه. المشروع علامة إضافية — مفيش أي مجموع تاني بيتغير.
 */
export function ProjectsScreen({
  user,
  categories,
  amountsHidden,
  onClose,
}: {
  user: UserContainer
  categories: readonly Category[]
  amountsHidden: boolean
  onClose: () => void
}) {
  const [rows, setRows] = useState<{ active: ProjectRow[]; archived: ProjectRow[] } | null>(null)
  const [openId, setOpenId] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)

  async function load() {
    setRows(await user.manageProjects.list())
  }
  useEffect(() => {
    void load().catch(setError)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user])

  async function create(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const project = await user.manageProjects.create(name)
      setName('')
      await load()
      setOpenId(project.id)
    } catch (cause) {
      setError(cause)
    } finally {
      setBusy(false)
    }
  }

  const money = (minor: number) => (amountsHidden ? '••••' : formatAmount(minor, 'SAR'))
  const card = ({ project, summary }: ProjectRow) => (
    <li key={project.id}>
      <button type="button" className="projectCard" onClick={() => setOpenId(project.id)}>
        <span className="projectCard__icon" aria-hidden="true"><FolderKanban size={20} /></span>
        <span className="projectCard__main">
          <span className="projectCard__name">{project.name}</span>
          <span className="projectCard__meta">
            {summary.count} عملية{summary.estimatedCount > 0 ? ' · تقريبي' : ''}
          </span>
        </span>
        <span className="projectCard__nums">
          <span className="num projectCard__out">{amountsHidden ? money(summary.spentMinor) : '−' + money(summary.spentMinor)}</span>
          {summary.receivedMinor > 0 && (
            <span className="num projectCard__in">{amountsHidden ? money(summary.receivedMinor) : '+' + money(summary.receivedMinor)}</span>
          )}
        </span>
      </button>
    </li>
  )

  return (
    <div className="sheet" role="dialog" aria-modal="true" aria-label="المشاريع">
      <section className="sheet__panel">
        <header className="sheet__head">
          <h2 className="sheet__title">المشاريع</h2>
          <button type="button" className="iconBtn" onClick={onClose} aria-label="إغلاق">
            <X size={18} aria-hidden="true" />
          </button>
        </header>
        <div className="sheet__body">
          {openId ? (
            <ProjectDetailView
              user={user}
              projectId={openId}
              categories={categories}
              amountsHidden={amountsHidden}
              onBack={() => {
                setOpenId(null)
                void load().catch(setError)
              }}
              onChanged={() => void load().catch(setError)}
            />
          ) : (
            <>
              <p className="sheet__hint">
                المشروع بيجمّع عمليات من تصنيفات مختلفة بتخدم نفس الهدف — ماكت، رحلة، شغلانة جانبية. «صرفت» نصيبك بس،
                وكل عملية بتتحسب مرة واحدة، ومفيش أي مجموع تاني بيتغير.
              </p>
              <form className="projectCreate" onSubmit={create} noValidate>
                <input
                  className="sheet__input"
                  value={name}
                  maxLength={60}
                  disabled={busy}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="اسم مشروع جديد"
                  aria-label="اسم مشروع جديد"
                />
                <button type="submit" className="btn" disabled={busy || !name.trim()}>
                  <FolderPlus size={18} aria-hidden="true" /> ضيف
                </button>
              </form>
              {error != null && <ErrorNotice cause={error} />}
              {!rows && error == null && <p role="status">بنحمّل المشاريع…</p>}
              {rows && rows.active.length === 0 && (
                <p className="sheet__hint">مفيش مشاريع لسه. اعمل واحد، وضيفله عمليات من النقط التلاتة جنب أي عملية.</p>
              )}
              {rows && rows.active.length > 0 && <ul className="projectList">{rows.active.map(card)}</ul>}
              {rows && rows.archived.length > 0 && (
                <details className="projectArchived">
                  <summary>المؤرشفة ({rows.archived.length})</summary>
                  <ul className="projectList">{rows.archived.map(card)}</ul>
                </details>
              )}
            </>
          )}
        </div>
      </section>
    </div>
  )
}
