import type { Message, NotificationRequest } from "./types";

const BASE_URL = "/api/message";

async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new Error(`Request failed: ${res.status} ${res.statusText}`);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return res.json() as Promise<T>;
}

export const api = {
  list(): Promise<Message[]> {
    return fetch(BASE_URL).then(handle<Message[]>);
  },

  create(request: NotificationRequest): Promise<Message> {
    return fetch(BASE_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    }).then(handle<Message>);
  },

  update(id: string, request: NotificationRequest): Promise<Message> {
    return fetch(`${BASE_URL}/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    }).then(handle<Message>);
  },

  remove(id: string): Promise<void> {
    return fetch(`${BASE_URL}/${id}`, { method: "DELETE" }).then(handle<void>);
  },
};
