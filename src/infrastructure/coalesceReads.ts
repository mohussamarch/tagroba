/**
 * Share only requests currently in flight, per repository instance/user.
 * Results are not retained: edits, reloads and other devices aren't hidden by a TTL.
 */
export function coalesceReads<T extends object>(repository: T): T {
  const pending = new Map<string, Promise<unknown>>()
  return new Proxy(repository, {
    get(target, property, receiver) {
      const value = Reflect.get(target, property, receiver)
      if (typeof value !== 'function' || typeof property !== 'string') return value
      if (!/^(list|find)/.test(property)) {
        return (...args: unknown[]) => {
          pending.clear()
          return value.apply(target, args)
        }
      }
      return (...args: unknown[]) => {
        const key = property + JSON.stringify(args)
        let request = pending.get(key)
        if (!request) {
          request = Promise.resolve().then(() => value.apply(target, args))
          pending.set(key, request)
          const remove = () => { if (pending.get(key) === request) pending.delete(key) }
          void request.then(remove, remove)
        }
        // Consumers may sort/edit their own arrays; never share mutable values.
        return request.then(result => structuredClone(result))
      }
    },
  })
}
