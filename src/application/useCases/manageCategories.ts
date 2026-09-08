import type {Category} from '../../domain/entities/types'
import type {CategoryRepository,IdGenerator} from '../ports/repositories'
import {normalizeText} from '../../domain/normalize'
export function makeManageCategories(deps:{categories:CategoryRepository;ids:IdGenerator}) {
 async function save(input:{id?:string;name:string;paletteId:string;active:boolean}) {
  const all=await deps.categories.listAll()
  const old=all.find(c=>c.id===input.id)
  if(input.id&&!old) throw new Error('التصنيف مش موجود.')
  const name=input.name.trim()
  if(!name||name.length>80) throw new Error('اكتب اسم التصنيف بحد أقصى ٨٠ حرف.')
  if(all.some(c=>c.id!==input.id&&normalizeText(c.name)===normalizeText(name))) throw new Error('فيه تصنيف بنفس الاسم.')
  const palette=all.find(c=>c.id===input.paletteId)
  if(!palette) throw new Error('اختار لونًا من القائمة.')
  const item:Category={id:old?.id??deps.ids.next('category'),parentId:old?.parentId??null,
   name,iconKey:old?.iconKey??'tag',lightColor:palette.lightColor,darkColor:palette.darkColor,
   active:input.active,order:old?.order??Math.max(0,...all.map(c=>c.order))+1}
  await deps.categories.save(item);return item
 }
 return {list:()=>deps.categories.listAll(),save}
}
