import { createHmac } from 'node:crypto';
import type { ConfigService } from '@nestjs/config';

export type PaymentInitialization = {
  gateway: string;
  providerReference: string;
  authorizationUrl: string;
};

export abstract class PaymentGatewayProvider {
  abstract initializeCharge(input: {
    bookingId: string;
    amountKobo: number;
    email?: string;
  }): Promise<PaymentInitialization>;

  verifyWebhookSignature(_payload: unknown, _signature?: string): boolean {
    return true;
  }
}

export class PaystackGatewayProvider extends PaymentGatewayProvider {
  constructor(private readonly config: ConfigService) {
    super();
  }

  async initializeCharge(input: {
    bookingId: string;
    amountKobo: number;
    email?: string;
  }): Promise<PaymentInitialization> {
    const secretKey = this.config.getOrThrow<string>('PAYSTACK_SECRET_KEY');
    const response = await globalThis.fetch('https://api.paystack.co/transaction/initialize', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${secretKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        email: input.email,
        amount: input.amountKobo,
        reference: `careos_${input.bookingId}_${Date.now()}`,
        callback_url: this.config.get<string>('PAYSTACK_CALLBACK_URL'),
        metadata: { bookingId: input.bookingId },
      }),
    });
    if (!response.ok) {
      throw new Error('Payment gateway failed to initialize charge.');
    }
    const payload = (await response.json()) as {
      data?: { reference?: string; authorization_url?: string };
    };
    if (!payload.data?.reference || !payload.data.authorization_url) {
      throw new Error('Payment gateway returned an invalid charge response.');
    }
    return {
      gateway: 'paystack',
      providerReference: payload.data.reference,
      authorizationUrl: payload.data.authorization_url,
    };
  }

  verifyWebhookSignature(payload: unknown, signature?: string): boolean {
    if (!signature) {
      return false;
    }
    const secret = this.config.getOrThrow<string>('PAYSTACK_WEBHOOK_SECRET');
    const expected = createHmac('sha512', secret).update(JSON.stringify(payload)).digest('hex');
    return expected === signature;
  }
}

export class MockPaystackGatewayProvider extends PaymentGatewayProvider {
  async initializeCharge(input: {
    bookingId: string;
    amountKobo: number;
    email?: string;
  }): Promise<PaymentInitialization> {
    const providerReference = `careos_${input.bookingId}_${input.amountKobo}`;
    return {
      gateway: 'paystack_mock',
      providerReference,
      authorizationUrl: `https://paystack.example.test/pay/${providerReference}`,
    };
  }
}
