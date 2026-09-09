/** A slow or failed section must not hold back other screens. */
export async function loadIndependently(
  jobs: Record<string, () => Promise<void>>,
  onError: (section: string, error: unknown) => void,
  onSettled: (section: string) => void,
) {
  await Promise.all(Object.entries(jobs).map(async ([section, work]) => {
    try { await work() } catch (error) { onError(section, error) }
    finally { onSettled(section) }
  }))
}
