import { FormEvent, useEffect, useState } from "react";
import { CHANNELS, ChannelName, Message, NotificationRequest } from "../types";

interface MessageFormProps {
  editing: Message | null;
  submitting: boolean;
  onSubmit: (request: NotificationRequest) => void;
  onCancel: () => void;
}

export default function MessageForm({ editing, submitting, onSubmit, onCancel }: MessageFormProps) {
  const [message, setMessage] = useState("");
  const [channel, setChannel] = useState<ChannelName>("console");

  useEffect(() => {
    if (editing) {
      setMessage(editing.message);
      setChannel(editing.channel as ChannelName);
    } else {
      setMessage("");
      setChannel("console");
    }
  }, [editing]);

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!message.trim()) return;
    onSubmit({ message: message.trim(), channel });
    if (!editing) {
      setMessage("");
    }
  }

  return (
    <form className="message-form" onSubmit={handleSubmit}>
      <h2>{editing ? "Edit notification" : "New notification"}</h2>

      <label htmlFor="message">Message</label>
      <textarea
        id="message"
        value={message}
        onChange={(e) => setMessage(e.target.value)}
        placeholder="Type a message to send…"
        rows={3}
        required
      />

      <label htmlFor="channel">Channel</label>
      <select id="channel" value={channel} onChange={(e) => setChannel(e.target.value as ChannelName)}>
        {CHANNELS.map((c) => (
          <option key={c} value={c}>
            {c}
          </option>
        ))}
      </select>

      <div className="form-actions">
        <button type="submit" disabled={submitting}>
          {editing ? "Save changes" : "Send"}
        </button>
        {editing && (
          <button type="button" className="secondary" onClick={onCancel} disabled={submitting}>
            Cancel
          </button>
        )}
      </div>
    </form>
  );
}
