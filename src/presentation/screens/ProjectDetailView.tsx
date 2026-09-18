import { useEffect, useState, type FormEvent } from 'react'
import { Archive, ArchiveRestore, ChevronRight, Pencil, Plus, WandSparkles } from 'lucide-react'
import type { UserContainer } from '../../app/container'
import type { ProjectDetail } from '../../application/useCases/manageProjects'
import type { Category, Transaction } from '../../domain/entities/types'
import { formatAmount } from '../../domain/formatMoney'
import { ErrorNotice } from '../components/ErrorNotice'
import { ProjectRuleForm } from '../components/ProjectRuleForm'
import { PROJECT_RULE_DIRECTIONS, PROJECT_RULE_MODES } from '../components/projectRuleLabels'
import { TransactionRow } from '../components/TransactionRow'

/**
 * مشروع واحد — OVERRIDES §34: صرفت (نصيبك) وجالك، والقواعد التلقائية، والعمليات.
 * الشاشة ما بتحسبش مبلغ (القاعدة 4) — الأرقام جاية جاهزة من `manageProjects.detail`.
 */
export function ProjectDetailView({
  user,
  projectId,
  categories,
  amountsHidden,
  onBack,
  onChanged,
}: {
  user: UserContainer
  projectId: string
  categories: readonly Category[]
  amountsHidden: boolean
  onBack: () => void
  onChanged: () => void
}) {
  const [detail, setDetail] = useState<ProjectDetail | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [addingRule, setAddingRule] = useState(false)
  const [name, setName] = useState<string | null>(null)
  const [removing, setRemoving] = useState<Transaction | null>(null)

  async function load() {
    setDetail(await user.manageProjects.detail(projectId))
  }
  useEffect(() => {
    void load().catch(setError)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, projectId])

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

  function rename(event: FormEvent) {
    event.preventDefault()
    if (name === null) return
    void run(() => user.manageProjects.rename(projectId, name), 'الاسم اتغير.').then(() => setName(null))
  }

  const money = (minor: number) => (amountsHidden ? '••••' : formatAmount(minor, 'SAR'))
  const byId = new Map(categories.map((c) => [c.id, c]))

  const back = (
    <button type="button" className="link projectDetail__back" onClick={onBack}>
      <ChevronRight size={16} aria-hidden="true" /> كل المشاريع
    </button>
  )
  if (!detail) {
    return (
      <div className="projectDetail">
        {back}
        {error != null ? <ErrorNotice cause={error} /> : <p role="status">بنحمّل المشروع…</p>}
      </div>
    )
  }

  const { project, summary, rules, transactions } = detail
  const byRule = transactions.filter((t) => t.source === 'rule').length
  return (
    <div className="projectDetail">
      {back}
      {name === null ? (
        <div className="projectDetail__head">
          <h3 className="projectDetail__name">{project.name}</h3>
          {project.archived && <span className="projectBadge">مؤرشف</span>}
          <button type="button" className="iconBtn" aria-label="غيّر الاسم" disabled={busy} onClick={() => setName(project.name)}>
            <Pencil size={16} aria-hidden="true" />
          </button>
        </div>
      ) : (
        <form className="projectDetail__rename" onSubmit={rename} noValidate>
          <input className="sheet__input" value={name} maxLength={60} disabled={busy} onChange={(e) => setName(e.target.value)} aria-label="اسم المشروع" />
          <button type="submit" className="btn" disabled={busy || !name.trim()}>احفظ</button>
          <button type="button" className="btn btn--quiet" onClick={() => setName(null)}>إلغاء</button>
        </form>
      )}

      <div className="projectMetrics">
        <div className="projectMetric">
          <span className="projectMetric__label">صرفت</span>
          <span className="projectMetric__value projectMetric__value--out num">{money(summary.spentMinor)}</span>
        </div>
        <div className="projectMetric">
          <span className="projectMetric__label">جالك</span>
          <span className="projectMetric__value projectMetric__value--in num">{money(summary.receivedMinor)}</span>
        </div>
        <div className="projectMetric">
          <span className="projectMetric__label">العمليات</span>
          <span className="projectMetric__value num">{summary.count}</span>
        </div>
      </div>
      <p className="sheet__hint">
        «صرفت» نصيبك إنت بس — اللي على حد تاني بيفضل دين في الأشخاص. المشروع مش بيغيّر أي مجموع تاني.
        {summary.estimatedCount > 0 && ` الرقم تقريبي: ${summary.needsReviewCount} عملية نوعها محتاج تأكيد.`}
      </p>
      {message && <p className="notice" role="status">{message}</p>}
      {error != null && <ErrorNotice cause={error} />}

      <section className="projectSection" aria-label="القواعد التلقائية">
        <h4 className="projectSection__title"><WandSparkles size={16} aria-hidden="true" /> القواعد التلقائية ({rules.length})</h4>
        {rules.length === 0 && !addingRule && <p className="sheet__hint">مفيش قواعد. القاعدة بتضيف أي عملية جديدة فيها نص معين للمشروع لوحدها.</p>}
        <ul className="projectRules">
          {rules.map((rule) => (
            <li key={rule.id} className={`projectRules__row${rule.enabled ? '' : ' projectRules__row--off'}`}>
              <span>
                {PROJECT_RULE_MODES[rule.matchMode]} «{rule.matchText}» · {PROJECT_RULE_DIRECTIONS[rule.direction]}
              </span>
              <button
                type="button"
                className="link"
                disabled={busy}
                onClick={() => void run(() => user.manageProjects.setRuleEnabled(rule.id, !rule.enabled), rule.enabled ? 'القاعدة اتقفلت — اللي ضافته بيفضل.' : 'القاعدة اتفتحت.')}
              >
                {rule.enabled ? 'اقفلها' : 'افتحها'}
              </button>
            </li>
          ))}
        </ul>
        {addingRule ? (
          <ProjectRuleForm
            user={user}
            projectId={project.id}
            onDone={(done) => {
              setAddingRule(false)
              void load().then(onChanged)
              setMessage(done)
            }}
          />
        ) : (
          <button type="button" className="btn btn--quiet" disabled={busy} onClick={() => setAddingRule(true)}>
            <Plus size={16} aria-hidden="true" /> ضيف قاعدة
          </button>
        )}
      </section>

      <section className="projectSection" aria-label="عمليات المشروع">
        <h4 className="projectSection__title">
          العمليات ({transactions.length}){byRule > 0 && <span className="projectBadge">{byRule} بقاعدة</span>}
        </h4>
        {transactions.length === 0 && <p className="sheet__hint">ضيف عمليات من النقط التلاتة جنب أي عملية ← «ضيف لمشروع»، أو بقاعدة.</p>}
        {removing && (
          <div className="notice projectRule__ask" role="group" aria-label="شيل عملية">
            <p>تشيل «{removing.rawMerchantName || removing.rawDescription || 'العملية'}» من المشروع؟ العملية نفسها مش هتتمسح.</p>
            <div className="projectRule__actions">
              <button
                type="button"
                className="btn"
                disabled={busy}
                onClick={() => void run(() => user.manageProjects.setMember(removing.id, project.id, false), 'اتشالت من المشروع.').then(() => setRemoving(null))}
              >
                شيلها
              </button>
              <button type="button" className="btn btn--quiet" onClick={() => setRemoving(null)}>إلغاء</button>
            </div>
          </div>
        )}
        <ul className="list">
          {transactions.map(({ transaction }) => {
            const category = transaction.categoryId ? byId.get(transaction.categoryId) : undefined
            const props: Parameters<typeof TransactionRow>[0] = { transaction, amountsHidden, onMenu: () => setRemoving(transaction) }
            if (category?.name) props.categoryName = category.name
            if (category?.lightColor) props.categoryColor = category.lightColor
            if (category?.darkColor) props.categoryDarkColor = category.darkColor
            if (category?.iconKey) props.categoryIconKey = category.iconKey
            return <TransactionRow key={transaction.id} {...props} />
          })}
        </ul>
      </section>

      <button
        type="button"
        className="btn btn--quiet"
        disabled={busy}
        onClick={() => void run(() => user.manageProjects.setArchived(project.id, !project.archived), project.archived ? 'المشروع رجع.' : 'المشروع اتأرشف — عملياته ومجاميعه بتفضل.')}
      >
        {project.archived ? <ArchiveRestore size={16} aria-hidden="true" /> : <Archive size={16} aria-hidden="true" />}
        {project.archived ? ' رجّع المشروع' : ' أرشف المشروع'}
      </button>
    </div>
  )
}
