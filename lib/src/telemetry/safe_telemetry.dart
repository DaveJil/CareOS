import 'package:flutter/foundation.dart';

class SafeTelemetry {
  const SafeTelemetry({required this.enabled});

  final bool enabled;

  void recordEvent(String name, [Map<String, Object?> metadata = const {}]) {
    if (!enabled) {
      return;
    }
    debugPrint('telemetry:$name ${_scrub(metadata)}');
  }

  void recordError(Object error, StackTrace stackTrace, {String? context}) {
    if (!enabled) {
      return;
    }
    debugPrint(
      'telemetry:error ${_scrub({'context': context, 'error': '$error'})}',
    );
  }
}

Map<String, Object?> _scrub(Map<String, Object?> input) {
  const sensitiveTerms = [
    'password',
    'token',
    'refresh',
    'email',
    'phone',
    'symptom',
    'medical',
    'diagnosis',
    'document',
    'content',
  ];
  return input.map((key, value) {
    final lowerKey = key.toLowerCase();
    final sensitive = sensitiveTerms.any(lowerKey.contains);
    return MapEntry(key, sensitive ? '[redacted]' : value);
  });
}
