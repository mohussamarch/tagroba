import type { AccountPort } from '../../application/ports/AccountPort'

/** حساب وهمي للاختبار ووضع المعاينة — بيعد إيميلات تغيير كلمة السر بدل ما يبعتها. */
export function memoryAccount(email: string | null = 'demo@example.com'): AccountPort & { resetsSent: number } {
  const account = {
    resetsSent: 0,
    email: () => email,
    async sendPasswordReset() {
      if (!email) throw new Error('الحساب ده مالوش إيميل نبعتله عليه')
      account.resetsSent++
    },
  }
  return account
}
