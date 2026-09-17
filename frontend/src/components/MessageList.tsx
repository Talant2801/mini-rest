import { Message } from "../types";

interface MessageListProps {
  messages: Message[];
  deletingId: string | null;
  onEdit: (message: Message) => void;
  onDelete: (id: string) => void;
}

export default function MessageList({ messages, deletingId, onEdit, onDelete }: MessageListProps) {
  if (messages.length === 0) {
    return <p className="empty-state">No notifications yet.</p>;
  }

  return (
    <table className="message-table">
      <thead>
        <tr>
          <th>Message</th>
          <th>Channel</th>
          <th>Sent via</th>
          <th>Correlation ID</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        {messages.map((m) => (
          <tr key={m.id}>
            <td>{m.message}</td>
            <td>
              <span className="badge">{m.channel}</span>
            </td>
            <td>{m.channelUsed.length > 0 ? m.channelUsed.join(", ") : "—"}</td>
            <td className="mono">{m.correlationId}</td>
            <td className="row-actions">
              <button onClick={() => onEdit(m)}>Edit</button>
              <button
                className="danger"
                onClick={() => onDelete(m.id)}
                disabled={deletingId === m.id}
              >
                {deletingId === m.id ? "Deleting…" : "Delete"}
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
