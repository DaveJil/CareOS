enum ApiErrorKind {
  validation,
  unauthorized,
  forbidden,
  notFound,
  conflict,
  offline,
  timeout,
  server,
  unknown,
}

class ApiException implements Exception {
  const ApiException({
    required this.kind,
    required this.message,
    this.statusCode,
    this.code,
    this.requestId,
    this.fieldErrors = const {},
  });

  final ApiErrorKind kind;
  final String message;
  final int? statusCode;
  final String? code;
  final String? requestId;
  final Map<String, String> fieldErrors;

  String get userMessage {
    if (message.trim().isNotEmpty && kind != ApiErrorKind.unknown) {
      return message;
    }
    return switch (kind) {
      ApiErrorKind.validation => 'Please check the highlighted fields.',
      ApiErrorKind.unauthorized =>
        'Your session has expired. Please sign in again.',
      ApiErrorKind.forbidden => 'You do not have access to that action.',
      ApiErrorKind.notFound => 'We could not find that record.',
      ApiErrorKind.conflict => 'That action conflicts with an existing record.',
      ApiErrorKind.offline =>
        'You appear to be offline. Check your connection and retry.',
      ApiErrorKind.timeout => 'The request took too long. Please try again.',
      ApiErrorKind.server =>
        'CareOS is having trouble right now. Please try again shortly.',
      ApiErrorKind.unknown => 'Something went wrong. Please try again.',
    };
  }

  @override
  String toString() => 'ApiException($kind, statusCode: $statusCode)';
}

ApiException mapHttpError({
  required int statusCode,
  required Map<String, dynamic>? payload,
}) {
  final message = _messageFromPayload(payload);
  final code = payload?['code']?.toString();
  final requestId = payload?['requestId']?.toString();
  return ApiException(
    kind: switch (statusCode) {
      400 || 422 => ApiErrorKind.validation,
      401 => ApiErrorKind.unauthorized,
      403 => ApiErrorKind.forbidden,
      404 => ApiErrorKind.notFound,
      409 => ApiErrorKind.conflict,
      >= 500 => ApiErrorKind.server,
      _ => ApiErrorKind.unknown,
    },
    message: message,
    statusCode: statusCode,
    code: code,
    requestId: requestId,
    fieldErrors: _fieldErrorsFromPayload(payload),
  );
}

String _messageFromPayload(Map<String, dynamic>? payload) {
  final value = payload?['message'];
  if (value is List) {
    return value.map((item) => item.toString()).join('\n');
  }
  return value?.toString() ?? '';
}

Map<String, String> _fieldErrorsFromPayload(Map<String, dynamic>? payload) {
  final errors = payload?['errors'];
  if (errors is! Map) {
    return const {};
  }
  return errors.map((key, value) => MapEntry(key.toString(), value.toString()));
}
