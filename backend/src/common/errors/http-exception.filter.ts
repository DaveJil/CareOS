import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import { HttpAdapterHost } from '@nestjs/core';

type ErrorPayload = {
  statusCode: number;
  code: string;
  message: string;
  requestId?: string;
  timestamp: string;
  path: string;
};

@Catch()
export class HttpExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger(HttpExceptionFilter.name);

  constructor(private readonly httpAdapterHost: HttpAdapterHost) {}

  catch(exception: unknown, host: ArgumentsHost): void {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse();
    const request = ctx.getRequest<{
      requestId?: string;
      url: string;
      originalUrl?: string;
      method: string;
    }>();
    const status =
      exception instanceof HttpException ? exception.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR;

    const message =
      exception instanceof HttpException
        ? this.getPublicMessage(exception)
        : 'An unexpected error occurred.';

    if (status >= 500) {
      this.logger.error({
        requestId: request.requestId,
        path: request.originalUrl ?? request.url,
        method: request.method,
        status,
      });
    }

    const payload: ErrorPayload = {
      statusCode: status,
      code: exception instanceof HttpException ? exception.name : 'InternalServerError',
      message,
      requestId: request.requestId,
      timestamp: new Date().toISOString(),
      path: request.originalUrl ?? request.url,
    };

    this.httpAdapterHost.httpAdapter.reply(response, payload, status);
  }

  private getPublicMessage(exception: HttpException): string {
    const response = exception.getResponse();

    if (typeof response === 'string') {
      return response;
    }

    if (typeof response === 'object' && response !== null && 'message' in response) {
      const message = (response as { message: string | string[] }).message;
      return Array.isArray(message) ? message.join('; ') : message;
    }

    return exception.message;
  }
}
