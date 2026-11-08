/**
 * Reuse successful requests (including in-flight requests), but allow retries
 * after a failure. Each cache instance is scoped to one browser page load.
 */
export function createAsyncRequestCache<T>() {
  const cache = new Map<string, Promise<T>>();

  return (key: string, load: () => Promise<T>): Promise<T> => {
    const cached = cache.get(key);
    if (cached) {
      return cached;
    }

    const pending = Promise.resolve()
      .then(load)
      .catch((error: unknown) => {
        cache.delete(key);
        throw error;
      });
    cache.set(key, pending);
    return pending;
  };
}
