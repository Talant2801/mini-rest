import { useCallback, useEffect, useState } from "react";
import { api } from "./api";
import MessageForm from "./components/MessageForm";
import MessageList from "./components/MessageList";
import { Message, NotificationRequest } from "./types";

export default function App() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<Message | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const loadMessages = useCallback(() => {
    setLoading(true);
    setError(null);
    api
      .list()
      .then(setMessages)
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    loadMessages();
  }, [loadMessages]);

  async function handleSubmit(request: NotificationRequest) {
    setSubmitting(true);
    setError(null);
    try {
      if (editing) {
        const updated = await api.update(editing.id, request);
        setMessages((prev) => prev.map((m) => (m.id === updated.id ? updated : m)));
        setEditing(null);
      } else {
        const created = await api.create(request);
        setMessages((prev) => [created, ...prev]);
      }
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(id: string) {
    setDeletingId(id);
    setError(null);
    try {
      await api.remove(id);
      setMessages((prev) => prev.filter((m) => m.id !== id));
      if (editing?.id === id) setEditing(null);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <div className="app">
      <header>
        <h1>Mini Rest · Notifications</h1>
        <p className="subtitle">Send and manage notifications across console, email, and slack.</p>
      </header>

      {error && <div className="error-banner">{error}</div>}

      <MessageForm
        editing={editing}
        submitting={submitting}
        onSubmit={handleSubmit}
        onCancel={() => setEditing(null)}
      />

      <section>
        <div className="section-header">
          <h2>Notifications</h2>
          <button className="secondary" onClick={loadMessages} disabled={loading}>
            {loading ? "Loading…" : "Refresh"}
          </button>
        </div>
        {loading ? (
          <p>Loading notifications…</p>
        ) : (
          <MessageList
            messages={messages}
            deletingId={deletingId}
            onEdit={setEditing}
            onDelete={handleDelete}
          />
        )}
      </section>
    </div>
  );
}
