// Where the app starts when a first-time visitor has no saved location and
// geolocation is denied, unavailable, or never answers (Seattle).
export const DEFAULT_LOCATION = { lat: 47.6062, lon: -122.3321 };

// How long to wait for the location prompt before falling back to
// DEFAULT_LOCATION. Some browsers never call back if the prompt is dismissed.
export const GEOLOCATION_FALLBACK_MS = 10000;
