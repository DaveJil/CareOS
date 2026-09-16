import { Controller, Get } from '@nestjs/common';
import { ApiOkResponse, ApiTags } from '@nestjs/swagger';

@ApiTags('health')
@Controller('health')
export class HealthController {
  @Get()
  @ApiOkResponse({
    description: 'Service health status',
    schema: {
      example: {
        status: 'ok',
        service: 'careos-api',
        timestamp: '2026-09-12T10:30:00.000Z',
      },
    },
  })
  check() {
    return {
      status: 'ok',
      service: 'careos-api',
      timestamp: new Date().toISOString(),
    };
  }
}
