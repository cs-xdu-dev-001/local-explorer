export function createRefreshCoordinator() {
  const flights = new Map();
  return {
    run(scope, refresh) {
      if (flights.has(scope)) return flights.get(scope);
      const flight = Promise.resolve().then(refresh).finally(() => flights.delete(scope));
      flights.set(scope, flight);
      return flight;
    }
  };
}

export function createAuthenticatedRequester({ fetcher, refresh, coordinator = createRefreshCoordinator(), scope = "client" }) {
  return async function authenticatedRequest(path, options) {
    let response = await fetcher(path, options);
    if (response.status !== 401) return response;
    await coordinator.run(scope, refresh);
    response = await fetcher(path, options);
    return response;
  };
}

export function clearStoredAuthentication({ sessionStorage, localStorage, sessionKeys = [], legacyKeys = [] }) {
  for (const key of sessionKeys) sessionStorage?.removeItem(key);
  for (const key of legacyKeys) localStorage?.removeItem(key);
}
