import { Capacitor, registerPlugin } from '@capacitor/core'
import type { BankSmsPort } from '../application/ports/BankSmsPort'
const native = registerPlugin<{read: BankSmsPort['read']}>('BankSms')
export const androidBankSms: BankSmsPort = {
  available: Capacitor.isNativePlatform(),
  read: input => native.read(input),
}
