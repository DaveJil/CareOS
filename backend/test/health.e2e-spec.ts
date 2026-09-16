import type { INestApplication } from '@nestjs/common';
import { FastifyAdapter } from '@nestjs/platform-fastify';
import type { FastifyInstance } from 'fastify';
import { Test } from '@nestjs/testing';
import { AppModule } from '../src/app.module';

describe('Health endpoint', () => {
  let app: INestApplication;
  let fastify: FastifyInstance;

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();

    app = moduleRef.createNestApplication(new FastifyAdapter());
    app.setGlobalPrefix('v1');
    await app.init();
    fastify = app.getHttpAdapter().getInstance() as FastifyInstance;
    await fastify.ready();
  });

  afterAll(async () => {
    await app?.close();
  });

  it('/v1/health returns ok', async () => {
    const response = await fastify.inject({
      method: 'GET',
      url: '/v1/health',
    });
    const body = JSON.parse(response.body) as {
      status: string;
      service: string;
      timestamp: string;
    };

    expect(response.statusCode).toBe(200);
    expect(body.status).toBe('ok');
    expect(body.service).toBe('careos-api');
    expect(body.timestamp).toEqual(expect.any(String));
  });
});
