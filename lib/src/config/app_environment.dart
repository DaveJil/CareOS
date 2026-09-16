import 'dart:io' show Platform;

import 'package:flutter/foundation.dart';

enum CareOsEnvironment {
  local,
  staging,
  production;

  static CareOsEnvironment fromName(String value) {
    return CareOsEnvironment.values.firstWhere(
      (environment) => environment.name == value,
      orElse: () => CareOsEnvironment.local,
    );
  }
}

class AppConfig {
  const AppConfig({
    required this.environment,
    required this.apiBaseUrl,
    this.enableTelemetry = true,
  });

  factory AppConfig.fromEnvironment() {
    const environmentName = String.fromEnvironment(
      'CAREOS_ENV',
      defaultValue: 'local',
    );
    const configuredBaseUrl = String.fromEnvironment('CAREOS_API_BASE_URL');
    final environment = CareOsEnvironment.fromName(environmentName);
    return AppConfig(
      environment: environment,
      apiBaseUrl: configuredBaseUrl.isNotEmpty
          ? Uri.parse(configuredBaseUrl)
          : _defaultBaseUrl(environment),
      enableTelemetry: const bool.fromEnvironment(
        'CAREOS_ENABLE_TELEMETRY',
        defaultValue: true,
      ),
    );
  }

  final CareOsEnvironment environment;
  final Uri apiBaseUrl;
  final bool enableTelemetry;

  bool get isProduction => environment == CareOsEnvironment.production;
}

Uri _defaultBaseUrl(CareOsEnvironment environment) {
  switch (environment) {
    case CareOsEnvironment.local:
      if (!kIsWeb && Platform.isAndroid) {
        return Uri.parse('http://10.0.2.2:3000/v1');
      }
      return Uri.parse('http://localhost:3000/v1');
    case CareOsEnvironment.staging:
      return Uri.parse('https://staging-api.careos.app/v1');
    case CareOsEnvironment.production:
      return Uri.parse('https://api.careos.app/v1');
  }
}
