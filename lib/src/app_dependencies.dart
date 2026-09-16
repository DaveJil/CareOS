import 'package:flutter/widgets.dart';

import 'api/careos_api_client.dart';
import 'api/token_store.dart';
import 'config/app_environment.dart';
import 'telemetry/safe_telemetry.dart';

class AppDependencies {
  AppDependencies({
    required this.config,
    required this.api,
    required this.tokenStore,
    required this.telemetry,
  });

  factory AppDependencies.fromEnvironment() {
    final config = AppConfig.fromEnvironment();
    final tokenStore = SecureTokenStore();
    final telemetry = SafeTelemetry(
      enabled: config.enableTelemetry && !config.isProduction,
    );
    return AppDependencies(
      config: config,
      tokenStore: tokenStore,
      telemetry: telemetry,
      api: CareOsApiClient(
        config: config,
        tokenStore: tokenStore,
        telemetry: telemetry,
      ),
    );
  }

  final AppConfig config;
  final CareOsApiClient api;
  final TokenStore tokenStore;
  final SafeTelemetry telemetry;
}

class AppDependenciesScope extends InheritedWidget {
  const AppDependenciesScope({
    required this.dependencies,
    required super.child,
    super.key,
  });

  final AppDependencies dependencies;

  static AppDependencies of(BuildContext context) {
    final scope = context
        .dependOnInheritedWidgetOfExactType<AppDependenciesScope>();
    assert(scope != null, 'AppDependenciesScope not found in widget tree.');
    return scope!.dependencies;
  }

  @override
  bool updateShouldNotify(AppDependenciesScope oldWidget) {
    return dependencies != oldWidget.dependencies;
  }
}
