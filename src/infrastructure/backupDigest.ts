export async function backupDigest(text:string):Promise<string>{
  const buffer=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(text))
  return [...new Uint8Array(buffer)].map(byte=>byte.toString(16).padStart(2,'0')).join('')
}
