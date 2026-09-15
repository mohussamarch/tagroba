/** رابط شعار المحل للعرض — OVERRIDES §25.1. `undefined` = مفيش شعار، والصف بيعرض رمز التصنيف. */
export interface MerchantLogoPort {
  urlFor(merchantName: string | undefined): string | undefined
}
