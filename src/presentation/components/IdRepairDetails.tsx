import { BACKUP_LABELS } from '../../domain/fullBackup'
import type { BatchInfo, LinkDiagnosis } from '../../domain/idRepairDiagnosis'
import type { ImportBatch } from '../../domain/entities/types'
import { SOURCE_LABEL, STATE_LABEL } from './ImportHistoryPanel'

const FIELD_LABEL: Record<string, string> = {
  transactionId: 'العملية', originTransactionId: 'العملية الأصلية', batchId: 'الدفعة', merchantId: 'التاجر',
  categoryId: 'التصنيف', verifiedCategoryId: 'التصنيف', parentId: 'التصنيف الأب', walletId: 'المحفظة',
  transferToWalletId: 'المحفظة المستقبِلة', personId: 'الشخص', obligationId: 'الالتزام', tagId: 'الوسم',
  budgetId: 'الميزانية', assetId: 'الأصل',
}
const MATCH_LABEL: Record<string, string> = { new: 'جديد', duplicate: 'مكرر', similar: 'مشابه' }

const batchText = (batch: BatchInfo) =>
  `دفعة ${SOURCE_LABEL[batch.sourceType as ImportBatch['sourceType']] ?? batch.sourceType}`
  + ` ${batch.day} (${STATE_LABEL[batch.state as ImportBatch['state']] ?? batch.state})`

const SHOWN = 12

/** تفاصيل ما لا يرده الإصلاح بيقين — أعداد وحالات بس، لا مبالغ ولا أوصاف. قراءة فقط. */
export function IdRepairDetails({ diagnosis }: { diagnosis: LinkDiagnosis }) {
  const { unresolved, orphans, orphanTotal } = diagnosis
  if (unresolved.length === 0 && orphanTotal === 0) return null
  const hidden = orphans.slice(SHOWN)
  return (
    <details>
      <summary>تفاصيل الروابط التايهة والعمليات اللي من غير مصدر</summary>
      {unresolved.length > 0 && <>
        <p className="settings__hint">روابط بتشاور على مستند مش موجود — متقسّمة بالمكان والحقل:</p>
        <ul>
          {unresolved.map((u, i) => (
            <li key={i}>
              {u.count} · {BACKUP_LABELS[u.group]} ← {FIELD_LABEL[u.field] ?? u.field}
              {' · '}{u.shape === 'damaged' ? 'معرّف مقصوص' : 'معرّف سليم الشكل'}
              {u.ambiguous && ' · بيطابق أكتر من مستند'}
              {u.matchingState && ` · السجل كان «${MATCH_LABEL[u.matchingState] ?? u.matchingState}»`}
              {u.batch && ` · ${batchText(u.batch)}`}
            </li>
          ))}
        </ul>
      </>}
      {orphanTotal > 0 && <>
        <p className="settings__hint">
          {orphanTotal} عملية مفيش ولا سجل مصدر بيشاور عليها. العمليات اللي ضفتها بإيدك طبيعي تبقى كده.
          متجمّعة بوقت إنشائها (بتوقيت جرينتش):
        </p>
        <ul>
          {orphans.slice(0, SHOWN).map((o) => (
            <li key={o.createdMinute}>
              {o.count} · اتعملت {o.createdMinute.replace('T', ' ') || 'وقت غير معروف'}
              {o.withTwin > 0 && ` · منها ${o.withTwin} ليها توأم بنفس اليوم والمبلغ والاتجاه`}
              {o.nearBatch && ` · وقت ${batchText(o.nearBatch)}`}
            </li>
          ))}
        </ul>
        {hidden.length > 0 && (
          <p className="settings__hint">و{hidden.reduce((sum, o) => sum + o.count, 0)} عملية في {hidden.length} وقت تاني.</p>
        )}
      </>}
    </details>
  )
}
