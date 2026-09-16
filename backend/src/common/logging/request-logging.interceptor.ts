import { CallHandler, ExecutionContext, Injectable, Logger, NestInterceptor } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { Observable, tap } from 'rxjs';

type HttpRequest = {
  headers?: Record<string, string | string[] | undefined>;
  method?: string;
  url?: string;
  originalUrl?: string;
  requestId?: string;
};

type HttpResponse = {
  setHeader?: (name: string, value: string) => void;
  header?: (name: string, value: string) => void;
  statusCode?: number;
};

@Injectable()
export class RequestLoggingInterceptor implements NestInterceptor {
  private readonly logger = new Logger(RequestLoggingInterceptor.name);

  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    const request = context.switchToHttp().getRequest<HttpRequest>();
    const response = context.switchToHttp().getResponse<HttpResponse>();
    const startedAt = Date.now();
    const incomingRequestId = request.headers?.['x-request-id'];
    request.requestId = Array.isArray(incomingRequestId)
      ? incomingRequestId[0]
      : (incomingRequestId ?? randomUUID());

    response.setHeader?.('x-request-id', request.requestId);
    response.header?.('x-request-id', request.requestId);

    return next.handle().pipe(
      tap(() => {
        this.logger.log({
          requestId: request.requestId,
          method: request.method,
          path: request.originalUrl ?? request.url,
          statusCode: response.statusCode,
          durationMs: Date.now() - startedAt,
        });
      }),
    );
  }
}
