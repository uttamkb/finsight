import { API_BASE_URL } from "./constants";

const API_KEY = "dev_secret_only_for_local";

export async function apiFetch(endpoint: string, options: RequestInit = {}) {
  const url = endpoint.startsWith("http") ? endpoint : `${API_BASE_URL}${endpoint.startsWith("/") ? endpoint : `/${endpoint}`}`;
  
  const headers: Record<string, string> = {
    "X-API-Key": API_KEY,
    ...Object.fromEntries(Object.entries(options.headers || {}) as [string, string][]),
  };

  if (!(options.body instanceof FormData) && !headers["Content-Type"]) {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(url, {
    ...options,
    headers,
  });

  return response;
}
