import type {CategoryRepository,IdGenerator} from '../ports/repositories'
import {planCategorySave,type CategorySaveInput} from '../../domain/categoryEdit'
/** إدارة التصنيفات — قواعد المكان واللون والرمز في `planCategorySave` (OVERRIDES §33.1). */
export function makeManageCategories(deps:{categories:CategoryRepository;ids:IdGenerator}) {
 async function save(input:CategorySaveInput) {
  const all=await deps.categories.listAll()
  const plan=planCategorySave(all,input,()=>deps.ids.next('category'))
  // الفرعيات قبل أبوها: لو الحفظ وقف في النص، لون الأساسي لسه القديم فالحفظ تاني بيعيد حسابهم
  for(const kid of plan.recolored) await deps.categories.save(kid)
  await deps.categories.save(plan.item)
  return plan.item
 }
 return {list:()=>deps.categories.listAll(),save}
}
