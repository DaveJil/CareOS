// ignore_for_file: prefer_initializing_formals, use_null_aware_elements

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

import '../config/app_environment.dart';
import '../telemetry/safe_telemetry.dart';
import 'api_error.dart';
import 'careos_models.dart';
import 'token_store.dart';

typedef ForceLogoutCallback = Future<void> Function();

class CareOsApiClient {
  CareOsApiClient({
    required AppConfig config,
    required TokenStore tokenStore,
    SafeTelemetry telemetry = const SafeTelemetry(enabled: false),
    http.Client? httpClient,
    ForceLogoutCallback? onForceLogout,
  }) : _config = config,
       _tokenStore = tokenStore,
       _telemetry = telemetry,
       _http = httpClient ?? http.Client(),
       _onForceLogout = onForceLogout;

  final AppConfig _config;
  final TokenStore _tokenStore;
  final SafeTelemetry _telemetry;
  final http.Client _http;
  final ForceLogoutCallback? _onForceLogout;

  Future<AuthSession> register({
    required String email,
    required String password,
    required CareOsRole role,
    String? phone,
    String? deviceId,
    String? deviceName,
  }) async {
    final json = await _sendJson(
      'POST',
      '/auth/register',
      authenticated: false,
      body: {
        'email': email,
        'password': password,
        'role': role.wireName,
        if (phone != null) 'phone': phone,
        if (deviceId != null) 'deviceId': deviceId,
        if (deviceName != null) 'deviceName': deviceName,
      },
    );
    return _storeSession(AuthSession.fromJson(json));
  }

  Future<AuthSession> login({
    required String email,
    required String password,
    String? deviceId,
    String? deviceName,
  }) async {
    final json = await _sendJson(
      'POST',
      '/auth/login',
      authenticated: false,
      body: {
        'email': email,
        'password': password,
        if (deviceId != null) 'deviceId': deviceId,
        if (deviceName != null) 'deviceName': deviceName,
      },
    );
    return _storeSession(AuthSession.fromJson(json));
  }

  Future<void> verifyPhoneOtp({required String phone, required String code}) {
    return _sendJson(
      'POST',
      '/auth/otp/verify-phone',
      authenticated: false,
      body: {'phone': phone, 'code': code},
    ).then((_) {});
  }

  Future<AuthSession> refreshSession() async {
    final stored = await _tokenStore.read();
    if (stored == null) {
      throw const ApiException(
        kind: ApiErrorKind.unauthorized,
        message: 'Your session has expired. Please sign in again.',
      );
    }
    final json = await _sendJson(
      'POST',
      '/auth/refresh',
      authenticated: false,
      body: {'refreshToken': stored.refreshToken},
    );
    return _storeSession(AuthSession.fromJson(json));
  }

  Future<void> logout() async {
    final stored = await _tokenStore.read();
    if (stored != null) {
      await _sendJson(
        'POST',
        '/auth/logout',
        authenticated: false,
        body: {'refreshToken': stored.refreshToken},
      ).catchError((Object error, StackTrace stackTrace) {
        _telemetry.recordError(error, stackTrace, context: 'logout');
        return <String, dynamic>{};
      });
    }
    await _tokenStore.clear();
  }

  Future<void> requestPasswordReset(String email) {
    return _sendJson(
      'POST',
      '/auth/password-reset/request',
      authenticated: false,
      body: {'email': email},
    ).then((_) {});
  }

  Future<void> confirmPasswordReset({
    required String email,
    required String code,
    required String newPassword,
  }) {
    return _sendJson(
      'POST',
      '/auth/password-reset/confirm',
      authenticated: false,
      body: {'email': email, 'code': code, 'newPassword': newPassword},
    ).then((_) {});
  }

  Future<Map<String, dynamic>> grantConsent({
    required String type,
    required String version,
    Map<String, Object?> metadata = const {},
  }) {
    return _sendJson(
      'POST',
      '/consents',
      body: {'type': type, 'version': version, 'metadata': metadata},
    );
  }

  Future<List<Map<String, dynamic>>> listConsents() => _sendList('/consents');

  Future<Map<String, dynamic>> revokeConsent(String type) {
    return _sendJson('POST', '/consents/revoke', body: {'type': type});
  }

  Future<Map<String, dynamic>> getPatientProfile() {
    return _sendJson('GET', '/patient/profile');
  }

  Future<Map<String, dynamic>> upsertPatientProfile(Map<String, Object?> body) {
    return _sendJson('PATCH', '/patient/profile', body: body);
  }

  Future<List<Map<String, dynamic>>> listEmergencyContacts() {
    return _sendList('/patient/emergency-contacts');
  }

  Future<Map<String, dynamic>> addEmergencyContact(Map<String, Object?> body) {
    return _sendJson('POST', '/patient/emergency-contacts', body: body);
  }

  Future<void> deleteEmergencyContact(String id) {
    return _sendJson('DELETE', '/patient/emergency-contacts/$id').then((_) {});
  }

  Future<List<Map<String, dynamic>>> listAllergies() =>
      _sendList('/patient/allergies');

  Future<Map<String, dynamic>> addAllergy(Map<String, Object?> body) {
    return _sendJson('POST', '/patient/allergies', body: body);
  }

  Future<void> deleteAllergy(String id) {
    return _sendJson('DELETE', '/patient/allergies/$id').then((_) {});
  }

  Future<List<Map<String, dynamic>>> listChronicConditions() {
    return _sendList('/patient/chronic-conditions');
  }

  Future<Map<String, dynamic>> addChronicCondition(Map<String, Object?> body) {
    return _sendJson('POST', '/patient/chronic-conditions', body: body);
  }

  Future<void> deleteChronicCondition(String id) {
    return _sendJson('DELETE', '/patient/chronic-conditions/$id').then((_) {});
  }

  Future<Map<String, dynamic>> uploadDocument(Map<String, Object?> body) {
    return _sendJson('POST', '/vault/documents', body: body);
  }

  Future<List<Map<String, dynamic>>> listDocuments({
    String? type,
    String? query,
  }) {
    return _sendList(
      '/vault/documents',
      query: {
        if (type != null) 'type': type,
        if (query != null) 'query': query,
      },
    );
  }

  Future<Map<String, dynamic>> getDocument(String id) {
    return _sendJson('GET', '/vault/documents/$id');
  }

  Future<TriageResult> submitTriage({
    required String symptomsText,
    Map<String, Object?>? questionnaire,
  }) async {
    final json = await _sendJson(
      'POST',
      '/triage',
      body: {
        'symptomsText': symptomsText,
        if (questionnaire != null) 'questionnaire': questionnaire,
      },
    );
    return TriageResult.fromJson(json);
  }

  Future<List<TriageResult>> triageHistory({
    String? query,
    List<String> urgency = const [],
  }) async {
    final list = await _sendList(
      '/triage/history',
      query: {
        if (query != null) 'query': query,
        if (urgency.isNotEmpty) 'urgency': urgency,
      },
    );
    return list.map(TriageResult.fromJson).toList();
  }

  Future<List<TriageResult>> clinicianTriageQueue() async {
    final list = await _sendList('/triage/review-queue');
    return list.map(TriageResult.fromJson).toList();
  }

  Future<TriageResult> markTriageReviewed(String id) async {
    final json = await _sendJson('PATCH', '/triage/$id/reviewed');
    return TriageResult.fromJson(json);
  }

  Future<Map<String, dynamic>> upsertClinicianProfile(
    Map<String, Object?> body,
  ) {
    return _sendJson('PATCH', '/telehealth/clinician-profile', body: body);
  }

  Future<List<SpecialistProfile>> searchSpecialists({
    String? specialty,
    DateTime? startsAt,
  }) async {
    final list = await _sendList(
      '/telehealth/specialists',
      query: {
        if (specialty != null) 'specialty': specialty,
        if (startsAt != null) 'startsAt': startsAt.toUtc().toIso8601String(),
      },
    );
    return list.map(SpecialistProfile.fromJson).toList();
  }

  Future<Booking> createBooking({
    required String clinicianId,
    required DateTime startsAt,
  }) async {
    final json = await _sendJson(
      'POST',
      '/telehealth/bookings',
      body: {
        'clinicianId': clinicianId,
        'startsAt': startsAt.toUtc().toIso8601String(),
      },
    );
    return Booking.fromJson(json);
  }

  Future<Booking> getBooking(String id) async {
    return Booking.fromJson(await _sendJson('GET', '/telehealth/bookings/$id'));
  }

  Future<Booking> rescheduleBooking({
    required String id,
    required DateTime startsAt,
  }) async {
    final json = await _sendJson(
      'PATCH',
      '/telehealth/bookings/$id/reschedule',
      body: {'startsAt': startsAt.toUtc().toIso8601String()},
    );
    return Booking.fromJson(json);
  }

  Future<Booking> cancelBooking({required String id, String? reason}) async {
    final json = await _sendJson(
      'PATCH',
      '/telehealth/bookings/$id/cancel',
      body: {if (reason != null) 'reason': reason},
    );
    return Booking.fromJson(json);
  }

  Future<Map<String, dynamic>> sendChatMessage({
    required String bookingId,
    required String message,
  }) {
    return _sendJson(
      'POST',
      '/telehealth/bookings/$bookingId/chat',
      body: {'message': message},
    );
  }

  Future<List<Map<String, dynamic>>> listChatMessages(String bookingId) {
    return _sendList('/telehealth/bookings/$bookingId/chat');
  }

  Future<PaymentCharge> createCharge({
    required String bookingId,
    required String idempotencyKey,
  }) async {
    final json = await _sendJson(
      'POST',
      '/payments/charges',
      body: {'bookingId': bookingId, 'idempotencyKey': idempotencyKey},
    );
    return PaymentCharge.fromJson(json);
  }

  Future<PaymentReceipt> getReceipt(String paymentId) async {
    final json = await _sendJson('GET', '/payments/$paymentId/receipt');
    return PaymentReceipt.fromJson(json);
  }

  Future<Map<String, dynamic>> dispatchNotification(Map<String, Object?> body) {
    return _sendJson('POST', '/notifications/dispatch', body: body);
  }

  Future<List<Map<String, dynamic>>> retryFailedNotifications() {
    return _sendList('/notifications/retry-failed', method: 'POST');
  }

  Future<Map<String, dynamic>> adminPaymentSummary() {
    return _sendJson('GET', '/admin/payments');
  }

  Future<List<Map<String, dynamic>>> adminPatients() =>
      _sendList('/admin/patients');

  Future<List<Map<String, dynamic>>> adminClinicians() =>
      _sendList('/admin/clinicians');

  Future<Map<String, dynamic>> approveClinician(String id) {
    return _sendJson('PATCH', '/admin/clinicians/$id/approve');
  }

  Future<List<Map<String, dynamic>>> adminHighUrgencyQueue() {
    return _sendList('/admin/triage/high-urgency');
  }

  Future<String> adminAuditCsv() async {
    final response = await _send('GET', '/admin/audit.csv');
    return response.body;
  }

  void close() => _http.close();

  Future<AuthSession> _storeSession(AuthSession session) async {
    await _tokenStore.write(session.tokens);
    return session;
  }

  Future<List<Map<String, dynamic>>> _sendList(
    String path, {
    String method = 'GET',
    Map<String, Object?> query = const {},
  }) async {
    final response = await _send(method, path, query: query);
    final decoded = _decode(response);
    if (decoded is List) {
      return decoded.cast<Map<String, dynamic>>();
    }
    throw const ApiException(
      kind: ApiErrorKind.unknown,
      message: 'CareOS returned an unexpected response.',
    );
  }

  Future<Map<String, dynamic>> _sendJson(
    String method,
    String path, {
    bool authenticated = true,
    Map<String, Object?> body = const {},
    Map<String, Object?> query = const {},
  }) async {
    final response = await _send(
      method,
      path,
      authenticated: authenticated,
      body: body,
      query: query,
    );
    final decoded = _decode(response);
    if (decoded is Map<String, dynamic>) {
      return decoded;
    }
    if (decoded == null) {
      return <String, dynamic>{};
    }
    throw const ApiException(
      kind: ApiErrorKind.unknown,
      message: 'CareOS returned an unexpected response.',
    );
  }

  Future<http.Response> _send(
    String method,
    String path, {
    bool authenticated = true,
    Map<String, Object?> body = const {},
    Map<String, Object?> query = const {},
    bool hasRetried = false,
  }) async {
    final request = http.Request(method, _uri(path, query));
    request.headers.addAll({
      'Accept': 'application/json',
      'Content-Type': 'application/json',
    });
    if (body.isNotEmpty) {
      request.body = jsonEncode(body);
    }
    if (authenticated) {
      final stored = await _tokenStore.read();
      if (stored != null) {
        request.headers['Authorization'] = 'Bearer ${stored.accessToken}';
      }
    }

    http.Response response;
    try {
      response = await http.Response.fromStream(
        await _http.send(request).timeout(const Duration(seconds: 25)),
      );
    } on SocketException catch (error, stackTrace) {
      _telemetry.recordError(error, stackTrace, context: 'offline');
      throw const ApiException(
        kind: ApiErrorKind.offline,
        message: 'You appear to be offline. Check your connection and retry.',
      );
    } on TimeoutException catch (error, stackTrace) {
      _telemetry.recordError(error, stackTrace, context: 'timeout');
      throw const ApiException(
        kind: ApiErrorKind.timeout,
        message: 'The request took too long. Please try again.',
      );
    }

    if (response.statusCode == 401 && authenticated && !hasRetried) {
      final refreshed = await _tryRefresh();
      if (refreshed) {
        return _send(
          method,
          path,
          authenticated: authenticated,
          body: body,
          query: query,
          hasRetried: true,
        );
      }
    }

    if (response.statusCode < 200 || response.statusCode >= 300) {
      final decoded = _decode(response);
      throw mapHttpError(
        statusCode: response.statusCode,
        payload: decoded is Map<String, dynamic> ? decoded : null,
      );
    }

    return response;
  }

  Future<bool> _tryRefresh() async {
    try {
      await refreshSession();
      return true;
    } catch (error, stackTrace) {
      await _tokenStore.clear();
      _telemetry.recordError(error, stackTrace, context: 'refresh_session');
      await _onForceLogout?.call();
      return false;
    }
  }

  Uri _uri(String path, Map<String, Object?> query) {
    final cleanPath = path.startsWith('/') ? path.substring(1) : path;
    final basePath = _config.apiBaseUrl.path.endsWith('/')
        ? _config.apiBaseUrl.path
        : '${_config.apiBaseUrl.path}/';
    final queryParameters = <String, dynamic>{};
    for (final entry in query.entries) {
      final value = entry.value;
      if (value == null) {
        continue;
      }
      queryParameters[entry.key] = value is Iterable
          ? value.map((item) => item.toString()).toList()
          : value.toString();
    }
    return _config.apiBaseUrl.replace(
      path: '$basePath$cleanPath',
      queryParameters: queryParameters.isEmpty ? null : queryParameters,
    );
  }

  Object? _decode(http.Response response) {
    if (response.body.trim().isEmpty) {
      return null;
    }
    return jsonDecode(response.body);
  }
}
