import { useState, type FormEvent } from 'react'
import type { UserContainer } from '../../app/container'
import type { RuleMatchMode } from '../../domain/entities/types'
import type { ProjectRuleDirection } from '../../domain/entities/projectEntities'
import { ErrorNotice } from './ErrorNotice'
import { PROJECT_RULE_DIRECTIONS as DIRECTIONS, PROJECT_RULE_MODES as MODES } from './projectRuleLabels'

/**
 * قاعدة تلقائية لمشروع — OVERRIDES §34 (رد المالك: «يسأله عايز يتعمل على القديم ولا لا»):
 * بعد الحفظ، لو فيه عمليات قديمة بتنطبق بيسأل بعددها؛ والجديدة بتتضاف لوحدها في كل الأحوال.
 */
export function ProjectRuleForm({
  user,
  projectId,
  initialText = '',
  initialDirection = 'out',
  onDone,
}: {
  user: UserContainer
  projectId: string
  initialText?: string
  initialDirection?: ProjectRuleDirection
  onDone: (message: string) => void
}) {
  const [text, setText] = useState(initialText)
  const [mode, setMode] = useState<RuleMatchMode>('contains')
  const [direction, setDirection] = useState<ProjectRuleDirection>(initialDirection)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [pending, setPending] = useState<{ ruleId: string; count: number } | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const { rule, oldMatches } = await user.manageProjects.addRule(projectId, { matchText: text, matchMode: mode, direction })
      if (oldMatches > 0) setPending({ ruleId: rule.id, count: oldMatches })
      else onDone('القاعدة اتحفظت. مفيش عمليات قديمة بتنطبق عليها، والجديدة هتتضاف لوحدها.')
    } catch (cause) {
      setError(cause)
    } finally {
      setBusy(false)
    }
  }

  async function answer(addOld: boolean) {
    if (!pending) return
    setBusy(true)
    setError(null)
    try {
      const added = addOld ? await user.manageProjects.applyRuleToOld(pending.ruleId) : 0
      onDone(addOld ? `اتضاف ${added} عملية قديمة، والجديدة هتتضاف لوحدها.` : 'القاعدة اتحفظت للعمليات الجديدة بس.')
    } catch (cause) {
      setError(cause)
    } finally {
      setBusy(false)
    }
  }

  if (pending) {
    return (
      <div className="notice projectRule__ask" role="group" aria-label="العمليات القديمة">
        <p>
          فيه <strong className="num">{pending.count}</strong> عملية قديمة بتنطبق عليها القاعدة دي. تضيفها للمشروع؟
        </p>
        <div className="projectRule__actions">
          <button type="button" className="btn" disabled={busy} onClick={() => void answer(true)}>
            ضيفهم
          </button>
          <button type="button" className="btn btn--quiet" disabled={busy} onClick={() => void answer(false)}>
            لأ، الجديدة بس
          </button>
        </div>
        {error != null && <ErrorNotice cause={error} />}
      </div>
    )
  }

  return (
    <form className="projectRule" onSubmit={submit} noValidate>
      <label className="sheet__field">
        النص في اسم العملية أو وصفها
        <input
          className="sheet__input"
          value={text}
          maxLength={60}
          disabled={busy}
          onChange={(e) => setText(e.target.value)}
          placeholder="مثلًا: اسم المكتبة"
        />
      </label>
      <div className="projectRule__row">
        <label className="sheet__field">
          المطابقة
          <select className="sheet__input" value={mode} disabled={busy} onChange={(e) => setMode(e.target.value as RuleMatchMode)}>
            {(Object.keys(MODES) as RuleMatchMode[]).map((m) => (
              <option key={m} value={m}>{MODES[m]}</option>
            ))}
          </select>
        </label>
        <label className="sheet__field">
          على
          <select
            className="sheet__input"
            value={direction}
            disabled={busy}
            onChange={(e) => setDirection(e.target.value as ProjectRuleDirection)}
          >
            {(Object.keys(DIRECTIONS) as ProjectRuleDirection[]).map((d) => (
              <option key={d} value={d}>{DIRECTIONS[d]}</option>
            ))}
          </select>
        </label>
      </div>
      <button type="submit" className="btn" disabled={busy || text.trim().length < 2}>
        {busy ? 'بنحفظ…' : 'احفظ القاعدة'}
      </button>
      {error != null && <ErrorNotice cause={error} />}
    </form>
  )
}
