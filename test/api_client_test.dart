import 'dart:convert';

import 'package:careos/src/api/api_error.dart';
import 'package:careos/src/api/careos_api_client.dart';
import 'package:careos/src/api/careos_models.dart';
import 'package:careos/src/api/token_store.dart';
import 'package:careos/src/config/app_environment.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  final config = AppConfig(
    environment: CareOsEnvironment.local,
    apiBaseUrl: Uri.parse('http://localhost:3000/v1'),
    enableTelemetry: false,
  );

  test('login stores token pair in the configured token store', () async {
    final tokenStore = MemoryTokenStore();
    final client = CareOsApiClient(
      config: config,
      tokenStore: tokenStore,
      httpClient: MockClient((request) async {
        expect(request.url.path, '/v1/auth/login');
        expect(jsonDecode(request.body)['password'], 'secret-password');
        return jsonResponse({
          'accessToken': 'access-1',
          'refreshToken': 'refresh-1',
          'expiresInSeconds': 900,
          'user': {
            'id': 'user-1',
            'email': 'patient@example.com',
            'phone': null,
            'role': 'patient',
          },
        });
      }),
    );

    final session = await client.login(
      email: 'patient@example.com',
      password: 'secret-password',
    );
    final stored = await tokenStore.read();

    expect(session.user.role, CareOsRole.patient);
    expect(stored?.accessToken, 'access-1');
    expect(stored?.refreshToken, 'refresh-1');
  });

  test('authenticated calls refresh and retry once after a 401', () async {
    final tokenStore = MemoryTokenStore();
    await tokenStore.write(
      const TokenPair(
        accessToken: 'expired-access',
        refreshToken: 'valid-refresh',
        expiresInSeconds: 900,
      ),
    );
    final seen = <String>[];
    final client = CareOsApiClient(
      config: config,
      tokenStore: tokenStore,
      httpClient: MockClient((request) async {
        seen.add('${request.method} ${request.url.path}');
        if (seen.length == 1) {
          expect(request.headers['Authorization'], 'Bearer expired-access');
          return jsonResponse({'message': 'Unauthorized'}, statusCode: 401);
        }
        if (request.url.path == '/v1/auth/refresh') {
          return jsonResponse({
            'accessToken': 'fresh-access',
            'refreshToken': 'fresh-refresh',
            'expiresInSeconds': 900,
            'user': {
              'id': 'user-1',
              'email': 'patient@example.com',
              'phone': null,
              'role': 'patient',
            },
          });
        }
        expect(request.headers['Authorization'], 'Bearer fresh-access');
        return jsonResponse({
          'id': 'profile-1',
          'userId': 'user-1',
          'firstName': 'Demo',
          'lastName': 'Patient',
        });
      }),
    );

    final profile = await client.getPatientProfile();
    final stored = await tokenStore.read();

    expect(profile['firstName'], 'Demo');
    expect(stored?.accessToken, 'fresh-access');
    expect(seen, [
      'GET /v1/patient/profile',
      'POST /v1/auth/refresh',
      'GET /v1/patient/profile',
    ]);
  });

  test('maps backend validation errors to user-safe messages', () async {
    final client = CareOsApiClient(
      config: config,
      tokenStore: MemoryTokenStore(),
      httpClient: MockClient((_) async {
        return jsonResponse({
          'statusCode': 400,
          'code': 'BadRequestException',
          'message': ['email must be an email'],
          'requestId': 'req-1',
        }, statusCode: 400);
      }),
    );

    await expectLater(
      client.login(email: 'not-an-email', password: 'secret-password'),
      throwsA(
        isA<ApiException>()
            .having((error) => error.kind, 'kind', ApiErrorKind.validation)
            .having(
              (error) => error.userMessage,
              'message',
              'email must be an email',
            ),
      ),
    );
  });
}

http.Response jsonResponse(Map<String, Object?> body, {int statusCode = 200}) {
  return http.Response(
    jsonEncode(body),
    statusCode,
    headers: {'content-type': 'application/json'},
  );
}
