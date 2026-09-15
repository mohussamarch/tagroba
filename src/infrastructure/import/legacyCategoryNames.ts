import tokens from '../../../design-source/masroofi-claude-code/design/tokens.json'

/**
 * أسماء التصنيفات القديمة بترتيبها في `tokens.json` — الحسابات اللي اتزرعت قبل شجرة التصنيفات
 * (OVERRIDES §28.1) معرّفاتها اتبنت منها، فنقل الحساب القديم محتاجها بنفس الترتيب.
 */
export const LEGACY_CATEGORY_NAMES: readonly string[] = tokens.categories.map((category) => category.name)
