import { NotificationChannel } from '@prisma/client';
import type { ConfigService } from '@nestjs/config';

export type DispatchResult = {
  delivered: boolean;
  providerMessageId?: string;
  error?: string;
};

export abstract class NotificationDispatcher {
  abstract send(input: {
    channel: NotificationChannel;
    target: string;
    template: string;
    payload?: Record<string, unknown>;
  }): Promise<DispatchResult>;
}

export class ExternalNotificationDispatcher extends NotificationDispatcher {
  constructor(private readonly config: ConfigService) {
    super();
  }

  async send(input: {
    channel: NotificationChannel;
    target: string;
    template: string;
    payload?: Record<string, unknown>;
  }): Promise<DispatchResult> {
    if (input.channel === NotificationChannel.sms) {
      return this.sendSms(input);
    }
    if (input.channel === NotificationChannel.email) {
      return this.sendEmail(input);
    }
    return { delivered: false, error: 'Push notifications require Firebase server credentials.' };
  }

  private async sendSms(input: {
    target: string;
    template: string;
    payload?: Record<string, unknown>;
  }): Promise<DispatchResult> {
    const apiKey = this.config.getOrThrow<string>('SENDCHAMP_API_KEY');
    const senderName = this.config.getOrThrow<string>('SENDCHAMP_SENDER_ID');
    const response = await globalThis.fetch('https://api.sendchamp.com/api/v1/sms/send', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        to: [input.target.replace(/^\+/, '')],
        message: this.renderTemplate(input.template, input.payload),
        sender_name: senderName,
        route: 'non_dnd',
      }),
    });
    if (!response.ok) {
      return { delivered: false, error: 'Sendchamp failed delivery.' };
    }
    const payload = (await response.json()) as { data?: { id?: string; reference?: string } };
    return { delivered: true, providerMessageId: payload.data?.reference ?? payload.data?.id };
  }

  private async sendEmail(input: {
    target: string;
    template: string;
    payload?: Record<string, unknown>;
  }): Promise<DispatchResult> {
    const apiKey = this.config.getOrThrow<string>('RESEND_API_KEY');
    const from = this.config.getOrThrow<string>('RESEND_FROM_EMAIL');
    const response = await globalThis.fetch('https://api.resend.com/emails', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        from,
        to: input.target,
        subject: 'CareOS notification',
        html: `<p>${this.renderTemplate(input.template, input.payload)}</p>`,
      }),
    });
    if (!response.ok) {
      return { delivered: false, error: 'Resend failed delivery.' };
    }
    const payload = (await response.json()) as { id?: string };
    return { delivered: true, providerMessageId: payload.id };
  }

  private renderTemplate(template: string, payload?: Record<string, unknown>): string {
    if (template === 'booking_confirmation') {
      return `Your CareOS booking is confirmed for ${payload?.startsAt ?? 'your scheduled time'}.`;
    }
    return `CareOS: ${template}`;
  }
}

export class MockNotificationDispatcher extends NotificationDispatcher {
  async send(input: {
    channel: NotificationChannel;
    target: string;
    template: string;
    payload?: Record<string, unknown>;
  }): Promise<DispatchResult> {
    if (input.target.includes('fail')) {
      return { delivered: false, error: 'Mock provider failed delivery.' };
    }
    return {
      delivered: true,
      providerMessageId: `mock-${input.channel}-${Date.now()}`,
    };
  }
}
