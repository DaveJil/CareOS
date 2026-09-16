import { Buffer } from 'node:buffer';
import type { ConfigService } from '@nestjs/config';

export type VideoSession = {
  provider: string;
  roomId: string;
  token: string;
};

export abstract class VideoSessionProvider {
  abstract createSession(input: {
    bookingId: string;
    patientId: string;
    clinicianId: string;
    startsAt: Date;
  }): Promise<VideoSession>;
}

export class ExternalVideoSessionProvider extends VideoSessionProvider {
  constructor(private readonly config: ConfigService) {
    super();
  }

  async createSession(input: {
    bookingId: string;
    patientId: string;
    clinicianId: string;
    startsAt: Date;
  }): Promise<VideoSession> {
    const apiKey = this.config.getOrThrow<string>('DAILY_API_KEY');
    const domain = this.config.getOrThrow<string>('DAILY_DOMAIN');
    const exp = Math.floor(
      input.startsAt.getTime() / 1000 + this.config.get<number>('DAILY_ROOM_EXPIRY_SECONDS', 7200),
    );
    const roomResponse = await globalThis.fetch('https://api.daily.co/v1/rooms', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        name: `careos-${input.bookingId}`,
        privacy: 'private',
        properties: {
          exp,
          enable_recording: false,
          eject_at_room_exp: true,
        },
      }),
    });
    if (!roomResponse.ok) {
      throw new Error('Video provider failed to create session.');
    }
    const room = (await roomResponse.json()) as { name?: string; url?: string };
    if (!room.name) {
      throw new Error('Video provider returned an invalid room.');
    }
    const tokenResponse = await globalThis.fetch('https://api.daily.co/v1/meeting-tokens', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        properties: {
          room_name: room.name,
          exp,
          user_name: input.patientId,
        },
      }),
    });
    if (!tokenResponse.ok) {
      throw new Error('Video provider failed to create meeting token.');
    }
    const token = (await tokenResponse.json()) as { token?: string };
    if (!token.token) {
      throw new Error('Video provider returned an invalid meeting token.');
    }
    return {
      provider: 'daily',
      roomId: room.url ?? `https://${domain}.daily.co/${room.name}`,
      token: token.token,
    };
  }
}

export class MockVideoSessionProvider extends VideoSessionProvider {
  async createSession(input: {
    bookingId: string;
    patientId: string;
    clinicianId: string;
    startsAt: Date;
  }): Promise<VideoSession> {
    return {
      provider: 'mock-video',
      roomId: `careos-${input.bookingId}`,
      token: Buffer.from(
        JSON.stringify({
          bookingId: input.bookingId,
          patientId: input.patientId,
          clinicianId: input.clinicianId,
          startsAt: input.startsAt.toISOString(),
        }),
      ).toString('base64url'),
    };
  }
}
