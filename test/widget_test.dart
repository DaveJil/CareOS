import 'package:careos/main.dart';
import 'package:careos/src/api/careos_api_client.dart';
import 'package:careos/src/api/token_store.dart';
import 'package:careos/src/app_dependencies.dart';
import 'package:careos/src/config/app_environment.dart';
import 'package:careos/src/telemetry/safe_telemetry.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  testWidgets('CareOS onboarding renders', (tester) async {
    final config = AppConfig(
      environment: CareOsEnvironment.local,
      apiBaseUrl: Uri.parse('http://localhost:3000/v1'),
      enableTelemetry: false,
    );
    final tokenStore = MemoryTokenStore();
    await tester.pumpWidget(
      AppDependenciesScope(
        dependencies: AppDependencies(
          config: config,
          tokenStore: tokenStore,
          telemetry: const SafeTelemetry(enabled: false),
          api: CareOsApiClient(
            config: config,
            tokenStore: tokenStore,
            httpClient: MockClient((_) async => http.Response('{}', 200)),
          ),
        ),
        child: const CareOsApp(),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('CareOS'), findsOneWidget);
    expect(
      find.text(
        'Emergency, insurance, clinical care, and health financing in one connected app.',
      ),
      findsOneWidget,
    );
  });
}
