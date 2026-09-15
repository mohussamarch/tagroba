import type { CategoryRequirement } from './categoryTree'
import type { Category } from './entities/types'
import type { UserProfile } from './userProfile'

/**
 * التصنيفات بتظهر حسب معلومات الشخص — OVERRIDES §28.1 (نص المالك: «مش كل التصنيفات تظهر…
 * لما نعرف معلومات معينة عن الشخص ساعتها هتظهر»).
 *
 * - **مجهول = مخفي**: الشرط لازم يتأكد بـ«أيوه» عشان التصنيف يظهر في الاختيارات.
 * - «السيارة» اسمها ورمزها «المواصلات» لحد ما الشخص يقول إن عنده سيارة.
 * - **مش بيمسح ولا بيغيّر حاجة متخزنة**: التصنيف المشروط بيبقى `active: false` في العرض بس،
 *   فاسمه بيفضل ظاهر على العمليات القديمة، والتصنيف الحالي للعملية بيفضل في قايمتها (`keepId`).
 */

export interface ProfileFacts {
  hasCar: boolean | null
  /** بيعول زوج أو زوجة أو أولاد. */
  familyDependents: boolean | null
  renter: boolean | null
  domesticWorker: boolean | null
  business: boolean | null
}

export const UNKNOWN_FACTS: ProfileFacts = {
  hasCar: null, familyDependents: null, renter: null, domesticWorker: null, business: null,
}

export function factsFromProfile(profile: UserProfile | null): ProfileFacts {
  if (!profile) return UNKNOWN_FACTS
  const kinds = profile.dependentKinds
  const familyDependents =
    profile.supportsDependents === false ? false
      : kinds === null ? null
        : kinds.includes('spouse') || kinds.includes('children')
  return {
    hasCar: profile.hasCar,
    familyDependents,
    renter: profile.renter,
    domesticWorker: profile.domesticWorker,
    business: profile.business,
  }
}

export function requirementMet(requirement: CategoryRequirement, facts: ProfileFacts): boolean {
  switch (requirement) {
    case 'hasCar': return facts.hasCar === true
    case 'dependents': return facts.familyDependents === true
    case 'renter': return facts.renter === true
    case 'domesticWorker': return facts.domesticWorker === true
    case 'business': return facts.business === true
  }
}

/** نسخة للعرض من التصنيفات: الاسم البديل لو مالوش سيارة، والمشروط غير المتأكد مش ظاهر في الاختيارات. */
export function presentCategories(categories: readonly Category[], facts: ProfileFacts): Category[] {
  return categories.map((category) => {
    let shown = category
    if (category.noCarName && facts.hasCar !== true) {
      shown = { ...shown, name: category.noCarName, iconKey: category.noCarIconKey ?? category.iconKey }
    }
    if (category.requires && !requirementMet(category.requires, facts)) shown = { ...shown, active: false }
    return shown
  })
}
