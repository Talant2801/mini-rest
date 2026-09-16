export type ChannelName = "console" | "email" | "slack";

export const CHANNELS: ChannelName[] = ["console", "email", "slack"];

export interface Message {
  id: string;
  message: string;
  channel: string;
  correlationId: string;
  channelUsed: string[];
}

export interface NotificationRequest {
  message: string;
  channel: string;
}
