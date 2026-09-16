import 'dart:convert';

import 'package:flutter/material.dart';

import 'src/api/api_error.dart';
import 'src/api/careos_models.dart';
import 'src/app_dependencies.dart';
import 'src/ui/async_state_widgets.dart';
import 'src/ui/coming_soon_screen.dart';

void main() {
  runApp(
    AppDependenciesScope(
      dependencies: AppDependencies.fromEnvironment(),
      child: const CareOsApp(),
    ),
  );
}

const _ink = Color(0xFF0B1720);
const _teal = Color(0xFF0E9F8D);
const _mint = Color(0xFFE8F8F5);
const _aqua = Color(0xFF33D6C5);
const _navy = Color(0xFF061427);
const _paper = Color(0xFFF6FAFA);
const _danger = Color(0xFFE84545);
const _gold = Color(0xFFF4B740);

String formatNaira(int kobo) {
  final naira = (kobo / 100).round();
  final value = naira.toString().replaceAllMapped(
    RegExp(r'\B(?=(\d{3})+(?!\d))'),
    (_) => ',',
  );
  return 'N$value';
}

class CareOsApp extends StatefulWidget {
  const CareOsApp({super.key});

  @override
  State<CareOsApp> createState() => _CareOsAppState();
}

class _CareOsAppState extends State<CareOsApp> {
  Future<bool>? _hasSession;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _hasSession ??= AppDependenciesScope.of(
      context,
    ).tokenStore.read().then((tokens) => tokens != null);
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'CareOS',
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: _teal,
          primary: _teal,
          secondary: _aqua,
          surface: Colors.white,
        ),
        scaffoldBackgroundColor: _paper,
        fontFamily: 'Roboto',
        appBarTheme: const AppBarTheme(
          centerTitle: false,
          elevation: 0,
          backgroundColor: Colors.transparent,
          foregroundColor: _ink,
        ),
      ),
      home: FutureBuilder<bool>(
        future: _hasSession,
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const Scaffold(
              body: CareOsLoadingState(message: 'Opening CareOS'),
            );
          }
          return snapshot.data == true
              ? const CareHome()
              : const OnboardingShell();
        },
      ),
    );
  }
}

class OnboardingShell extends StatefulWidget {
  const OnboardingShell({super.key});

  @override
  State<OnboardingShell> createState() => _OnboardingShellState();
}

class _OnboardingShellState extends State<OnboardingShell> {
  int page = 0;

  final slides = const [
    _OnboardingSlide(
      title: 'CareOS',
      subtitle:
          'Emergency, insurance, clinical care, and health financing in one connected app.',
      icon: Icons.health_and_safety,
    ),
    _OnboardingSlide(
      title: 'Expert Care, Anywhere',
      subtitle:
          'Connect with verified doctors, triage nurses, and emergency responders.',
      icon: Icons.video_call_rounded,
    ),
    _OnboardingSlide(
      title: 'Secure Medical Vault',
      subtitle:
          'Keep health records, referrals, insurance activity, and care fund data protected.',
      icon: Icons.verified_user_rounded,
    ),
    _OnboardingSlide(
      title: 'Instant Clinical Assessment',
      subtitle:
          'Use symptom scans and AI triage to route patients to the right next action.',
      icon: Icons.psychology_alt_rounded,
    ),
  ];

  @override
  Widget build(BuildContext context) {
    if (page == slides.length) {
      return const LoginScreen();
    }

    return Scaffold(
      backgroundColor: _navy,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            children: [
              Align(
                alignment: Alignment.centerRight,
                child: TextButton(
                  onPressed: () => setState(() => page = slides.length),
                  child: const Text(
                    'Skip',
                    style: TextStyle(color: Colors.white70),
                  ),
                ),
              ),
              Expanded(child: slides[page]),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: List.generate(
                  slides.length,
                  (index) => AnimatedContainer(
                    duration: const Duration(milliseconds: 220),
                    width: index == page ? 22 : 7,
                    height: 7,
                    margin: const EdgeInsets.symmetric(horizontal: 4),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(999),
                      color: index == page ? _aqua : Colors.white24,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  style: FilledButton.styleFrom(
                    backgroundColor: _teal,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                  ),
                  onPressed: () => setState(() => page += 1),
                  child: Text(
                    page == slides.length - 1 ? 'Sign in securely' : 'Next',
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _OnboardingSlide extends StatelessWidget {
  const _OnboardingSlide({
    required this.title,
    required this.subtitle,
    required this.icon,
  });

  final String title;
  final String subtitle;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Container(
          width: 168,
          height: 168,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            gradient: const LinearGradient(colors: [_teal, _aqua]),
            boxShadow: [
              BoxShadow(
                color: _aqua.withValues(alpha: 0.24),
                blurRadius: 42,
                spreadRadius: 12,
              ),
            ],
          ),
          child: Icon(icon, size: 74, color: Colors.white),
        ),
        const SizedBox(height: 40),
        Text(
          title,
          textAlign: TextAlign.center,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 31,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 14),
        Text(
          subtitle,
          textAlign: TextAlign.center,
          style: const TextStyle(
            color: Colors.white70,
            fontSize: 16,
            height: 1.45,
          ),
        ),
      ],
    );
  }
}

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _loading = false;
  Object? _error;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _login() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await AppDependenciesScope.of(context).api.login(
        email: _email.text.trim(),
        password: _password.text,
        deviceId: 'careos-mobile',
        deviceName: 'CareOS Mobile',
      );
      if (!mounted) {
        return;
      }
      Navigator.of(
        context,
      ).pushReplacement(MaterialPageRoute(builder: (_) => const CareHome()));
    } catch (error) {
      setState(() => _error = error);
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            const SizedBox(height: 28),
            Center(
              child: Image.asset(
                'play-console/careos-play-icon-512.png',
                width: 72,
                height: 72,
              ),
            ),
            const SizedBox(height: 12),
            const Center(
              child: Text(
                'CareOS',
                style: TextStyle(fontSize: 26, fontWeight: FontWeight.w800),
              ),
            ),
            const SizedBox(height: 4),
            const Center(
              child: Text(
                'Secure Clinical Login',
                style: TextStyle(color: Colors.black54),
              ),
            ),
            const SizedBox(height: 36),
            if (_error != null) ...[
              CareOsErrorBanner.fromError(_error!, onRetry: _login),
              const SizedBox(height: 14),
            ],
            _InputField(
              label: 'Email address',
              icon: Icons.mail_outline,
              controller: _email,
              keyboardType: TextInputType.emailAddress,
            ),
            const SizedBox(height: 14),
            _InputField(
              label: 'Password',
              icon: Icons.lock_outline,
              obscureText: true,
              controller: _password,
              onSubmitted: (_) => _login(),
            ),
            const SizedBox(height: 10),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton(
                onPressed: () => _open(context, const PasswordResetScreen()),
                child: const Text('Forgot password?'),
              ),
            ),
            const SizedBox(height: 12),
            SizedBox(
              height: 54,
              child: FilledButton(
                onPressed: _loading ? null : _login,
                child: _loading
                    ? const SizedBox.square(
                        dimension: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('Sign in'),
              ),
            ),
            const SizedBox(height: 18),
            OutlinedButton.icon(
              onPressed: () {},
              icon: const Icon(Icons.fingerprint),
              label: const Text('Sign in with FaceID'),
            ),
            const SizedBox(height: 24),
            const Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _RolePill(icon: Icons.person_outline, label: 'Patient'),
                _RolePill(
                  icon: Icons.local_hospital_outlined,
                  label: 'Provider',
                ),
                _RolePill(
                  icon: Icons.admin_panel_settings_outlined,
                  label: 'Admin',
                ),
              ],
            ),
            const SizedBox(height: 42),
            Center(
              child: TextButton(
                onPressed: () => _open(context, const RegisterScreen()),
                child: const Text('New to CareOS? Create account'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class RegisterScreen extends StatefulWidget {
  const RegisterScreen({super.key});

  @override
  State<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends State<RegisterScreen> {
  final _email = TextEditingController();
  final _phone = TextEditingController();
  final _password = TextEditingController();
  CareOsRole _role = CareOsRole.patient;
  bool _medicalConsent = true;
  bool _triageConsent = true;
  bool _loading = false;
  Object? _error;

  @override
  void dispose() {
    _email.dispose();
    _phone.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _register() async {
    if (!_medicalConsent || !_triageConsent) {
      setState(() {
        _error = const ApiException(
          kind: ApiErrorKind.validation,
          message: 'Please accept the required consents to continue.',
        );
      });
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = AppDependenciesScope.of(context).api;
      await api.register(
        email: _email.text.trim(),
        phone: _phone.text.trim().isEmpty ? null : _phone.text.trim(),
        password: _password.text,
        role: _role,
        deviceId: 'careos-mobile',
        deviceName: 'CareOS Mobile',
      );
      await api.grantConsent(
        type: 'medical_records',
        version: '2026-09-12',
        metadata: {'source': 'mobile_app'},
      );
      await api.grantConsent(
        type: 'ai_triage',
        version: '2026-09-12',
        metadata: {'source': 'mobile_app'},
      );
      if (!mounted) {
        return;
      }
      Navigator.of(
        context,
      ).pushReplacement(MaterialPageRoute(builder: (_) => const CareHome()));
    } catch (error) {
      setState(() => _error = error);
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Create account')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            if (_error != null) ...[
              CareOsErrorBanner.fromError(_error!, onRetry: _register),
              const SizedBox(height: 14),
            ],
            _InputField(
              label: 'Email address',
              icon: Icons.mail_outline,
              controller: _email,
              keyboardType: TextInputType.emailAddress,
            ),
            const SizedBox(height: 14),
            _InputField(
              label: 'Phone number',
              icon: Icons.phone_outlined,
              controller: _phone,
              keyboardType: TextInputType.phone,
            ),
            const SizedBox(height: 14),
            _InputField(
              label: 'Password',
              icon: Icons.lock_outline,
              obscureText: true,
              controller: _password,
            ),
            const SizedBox(height: 14),
            SegmentedButton<CareOsRole>(
              segments: const [
                ButtonSegment(
                  value: CareOsRole.patient,
                  label: Text('Patient'),
                  icon: Icon(Icons.person_outline),
                ),
                ButtonSegment(
                  value: CareOsRole.clinician,
                  label: Text('Clinician'),
                  icon: Icon(Icons.local_hospital_outlined),
                ),
              ],
              selected: {_role},
              onSelectionChanged: (selected) => setState(() {
                _role = selected.first;
              }),
            ),
            const SizedBox(height: 16),
            CheckboxListTile(
              value: _medicalConsent,
              onChanged: (value) =>
                  setState(() => _medicalConsent = value ?? false),
              title: const Text(
                'I consent to secure medical record processing.',
              ),
            ),
            CheckboxListTile(
              value: _triageConsent,
              onChanged: (value) =>
                  setState(() => _triageConsent = value ?? false),
              title: const Text('I consent to AI-assisted triage support.'),
            ),
            const SizedBox(height: 16),
            SizedBox(
              height: 54,
              child: FilledButton(
                onPressed: _loading ? null : _register,
                child: _loading
                    ? const SizedBox.square(
                        dimension: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('Create account'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class PasswordResetScreen extends StatefulWidget {
  const PasswordResetScreen({super.key});

  @override
  State<PasswordResetScreen> createState() => _PasswordResetScreenState();
}

class _PasswordResetScreenState extends State<PasswordResetScreen> {
  final _email = TextEditingController();
  final _code = TextEditingController();
  final _newPassword = TextEditingController();
  bool _codeRequested = false;
  bool _loading = false;
  Object? _error;
  String? _message;

  @override
  void dispose() {
    _email.dispose();
    _code.dispose();
    _newPassword.dispose();
    super.dispose();
  }

  Future<void> _request() async {
    setState(() {
      _loading = true;
      _error = null;
      _message = null;
    });
    try {
      await AppDependenciesScope.of(
        context,
      ).api.requestPasswordReset(_email.text.trim());
      setState(() {
        _codeRequested = true;
        _message = 'If the account exists, a reset code has been sent.';
      });
    } catch (error) {
      setState(() => _error = error);
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _confirm() async {
    setState(() {
      _loading = true;
      _error = null;
      _message = null;
    });
    try {
      await AppDependenciesScope.of(context).api.confirmPasswordReset(
        email: _email.text.trim(),
        code: _code.text.trim(),
        newPassword: _newPassword.text,
      );
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Password reset. Please sign in.')),
      );
      Navigator.pop(context);
    } catch (error) {
      setState(() => _error = error);
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Reset password')),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          if (_error != null) ...[
            CareOsErrorBanner.fromError(_error!),
            const SizedBox(height: 14),
          ],
          if (_message != null) ...[
            _InfoStrip(icon: Icons.mark_email_read_outlined, text: _message!),
            const SizedBox(height: 14),
          ],
          _InputField(
            label: 'Email address',
            icon: Icons.mail_outline,
            controller: _email,
            keyboardType: TextInputType.emailAddress,
          ),
          if (_codeRequested) ...[
            const SizedBox(height: 14),
            _InputField(
              label: 'Reset code',
              icon: Icons.pin_outlined,
              controller: _code,
              keyboardType: TextInputType.number,
            ),
            const SizedBox(height: 14),
            _InputField(
              label: 'New password',
              icon: Icons.lock_reset,
              controller: _newPassword,
              obscureText: true,
            ),
          ],
          const SizedBox(height: 18),
          FilledButton(
            onPressed: _loading ? null : (_codeRequested ? _confirm : _request),
            child: Text(_codeRequested ? 'Confirm reset' : 'Send reset code'),
          ),
        ],
      ),
    );
  }
}

class CareHome extends StatefulWidget {
  const CareHome({super.key});

  @override
  State<CareHome> createState() => _CareHomeState();
}

class _CareHomeState extends State<CareHome> {
  int index = 0;

  final screens = const [
    PatientHomeScreen(),
    SymptomScanScreen(),
    SpecialistNetworkScreen(),
    InsurancePortalScreen(),
    MutualCareFundScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(child: screens[index]),
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (value) => setState(() => index = value),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home),
            label: 'Home',
          ),
          NavigationDestination(
            icon: Icon(Icons.camera_alt_outlined),
            selectedIcon: Icon(Icons.camera_alt),
            label: 'Scan',
          ),
          NavigationDestination(
            icon: Icon(Icons.medical_services_outlined),
            selectedIcon: Icon(Icons.medical_services),
            label: 'Doctors',
          ),
          NavigationDestination(
            icon: Icon(Icons.shield_outlined),
            selectedIcon: Icon(Icons.shield),
            label: 'HMO',
          ),
          NavigationDestination(
            icon: Icon(Icons.savings_outlined),
            selectedIcon: Icon(Icons.savings),
            label: 'Fund',
          ),
        ],
      ),
    );
  }
}

class PatientHomeScreen extends StatelessWidget {
  const PatientHomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final isProduction = AppDependenciesScope.of(context).config.isProduction;
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
      children: [
        const _Header(title: 'CareOS', subtitle: 'Good morning, Samira'),
        const SizedBox(height: 16),
        const _VitalsCard(),
        const SizedBox(height: 18),
        _PrimaryCard(
          color: _teal,
          title: 'Symptom Triage',
          subtitle: 'AI powered pre-assessment ready',
          action: 'Start voice triage',
          icon: Icons.graphic_eq,
          onTap: () => _open(context, const ClinicalTriageScreen()),
        ),
        const SizedBox(height: 18),
        const _SectionTitle('Quick Services'),
        const SizedBox(height: 10),
        GridView.count(
          crossAxisCount: 2,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          childAspectRatio: 1.28,
          mainAxisSpacing: 12,
          crossAxisSpacing: 12,
          children: [
            _ServiceTile(
              'Book appointment',
              Icons.event_available,
              () => _open(context, const SpecialistNetworkScreen()),
            ),
            _ServiceTile(
              'Pharmacy locator',
              Icons.medication_outlined,
              () => _open(
                context,
                const ComingSoonScreen(
                  title: 'Pharmacy locator',
                  message:
                      'This feature is hidden until a production provider is integrated.',
                ),
              ),
            ),
            _ServiceTile(
              'Emergency assist',
              Icons.emergency_share_outlined,
              () => _open(context, const ReferralLedgerScreen()),
            ),
            _ServiceTile(
              'Medical vault',
              Icons.folder_copy_outlined,
              () => _open(context, const MedicalVaultScreen()),
            ),
            if (!isProduction)
              _ServiceTile(
                'QA checklist',
                Icons.fact_check_outlined,
                () => _open(context, const QaChecklistScreen()),
              ),
          ],
        ),
        const SizedBox(height: 18),
        const _SectionTitle('Top Specialists'),
        const SizedBox(height: 10),
        _DoctorCard(
          name: 'Dr. Grace Adebiran',
          specialty: 'General Physician',
          rating: '4.9',
          button: 'Book consult',
          onTap: () => _open(context, const TelehealthCallScreen()),
        ),
        _DoctorCard(
          name: 'Dr. Emeka Okafor',
          specialty: 'Cardiologist',
          rating: '4.8',
          button: 'View profile',
          onTap: () => _open(context, const SpecialistNetworkScreen()),
        ),
        const _SectionTitle('My Health Records'),
        const SizedBox(height: 10),
        _RecordTile(
          icon: Icons.person_outline,
          title: 'Patient profile',
          detail: 'Update demographics and next of kin',
          onTap: () => _open(context, const PatientProfileScreen()),
        ),
        _RecordTile(
          icon: Icons.folder_copy_outlined,
          title: 'Medical vault',
          detail: 'Upload and view encrypted documents',
          onTap: () => _open(context, const MedicalVaultScreen()),
        ),
      ],
    );
  }
}

class PatientProfileScreen extends StatefulWidget {
  const PatientProfileScreen({super.key});

  @override
  State<PatientProfileScreen> createState() => _PatientProfileScreenState();
}

class _PatientProfileScreenState extends State<PatientProfileScreen> {
  final _firstName = TextEditingController();
  final _lastName = TextEditingController();
  final _city = TextEditingController();
  final _state = TextEditingController();
  final _nextOfKinName = TextEditingController();
  final _nextOfKinPhone = TextEditingController();
  Object? _error;
  bool _loading = true;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _firstName.dispose();
    _lastName.dispose();
    _city.dispose();
    _state.dispose();
    _nextOfKinName.dispose();
    _nextOfKinPhone.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final profile = await AppDependenciesScope.of(
        context,
      ).api.getPatientProfile();
      _firstName.text = profile['firstName']?.toString() ?? '';
      _lastName.text = profile['lastName']?.toString() ?? '';
      _city.text = profile['city']?.toString() ?? '';
      _state.text = profile['state']?.toString() ?? '';
      _nextOfKinName.text = profile['nextOfKinName']?.toString() ?? '';
      _nextOfKinPhone.text = profile['nextOfKinPhone']?.toString() ?? '';
    } catch (error) {
      _error = error;
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _save() async {
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await AppDependenciesScope.of(context).api.upsertPatientProfile({
        'firstName': _firstName.text.trim(),
        'lastName': _lastName.text.trim(),
        'city': _city.text.trim(),
        'state': _state.text.trim(),
        'country': 'NG',
        'nextOfKinName': _nextOfKinName.text.trim(),
        'nextOfKinPhone': _nextOfKinPhone.text.trim(),
        'nextOfKinRelationship': 'Next of kin',
      });
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Profile saved')));
    } catch (error) {
      setState(() => _error = error);
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Patient profile')),
      body: _loading
          ? const CareOsLoadingState(message: 'Loading profile')
          : ListView(
              padding: const EdgeInsets.all(20),
              children: [
                if (_error != null) ...[
                  CareOsErrorBanner.fromError(_error!, onRetry: _load),
                  const SizedBox(height: 14),
                ],
                _InputField(
                  label: 'First name',
                  icon: Icons.person_outline,
                  controller: _firstName,
                ),
                const SizedBox(height: 12),
                _InputField(
                  label: 'Last name',
                  icon: Icons.person_outline,
                  controller: _lastName,
                ),
                const SizedBox(height: 12),
                _InputField(
                  label: 'City',
                  icon: Icons.location_city_outlined,
                  controller: _city,
                ),
                const SizedBox(height: 12),
                _InputField(
                  label: 'State',
                  icon: Icons.map_outlined,
                  controller: _state,
                ),
                const SizedBox(height: 12),
                _InputField(
                  label: 'Next of kin name',
                  icon: Icons.contact_emergency_outlined,
                  controller: _nextOfKinName,
                ),
                const SizedBox(height: 12),
                _InputField(
                  label: 'Next of kin phone',
                  icon: Icons.phone_outlined,
                  controller: _nextOfKinPhone,
                  keyboardType: TextInputType.phone,
                ),
                const SizedBox(height: 18),
                FilledButton(
                  onPressed: _saving ? null : _save,
                  child: Text(_saving ? 'Saving...' : 'Save profile'),
                ),
              ],
            ),
    );
  }
}

class MedicalVaultScreen extends StatefulWidget {
  const MedicalVaultScreen({super.key});

  @override
  State<MedicalVaultScreen> createState() => _MedicalVaultScreenState();
}

class _MedicalVaultScreenState extends State<MedicalVaultScreen> {
  final _title = TextEditingController();
  final _content = TextEditingController(text: 'Demo clinical note');
  List<Map<String, dynamic>> _documents = const [];
  Object? _error;
  bool _loading = true;
  bool _uploading = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _title.dispose();
    _content.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      _documents = await AppDependenciesScope.of(context).api.listDocuments();
    } catch (error) {
      _error = error;
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _uploadDemoDocument() async {
    setState(() {
      _uploading = true;
      _error = null;
    });
    try {
      final bytes = _content.text.codeUnits;
      await AppDependenciesScope.of(context).api.uploadDocument({
        'type': 'medical_record',
        'title': _title.text.trim().isEmpty
            ? 'Mobile upload'
            : _title.text.trim(),
        'originalName': 'mobile-note.txt',
        'mimeType': 'text/plain',
        'contentBase64': base64Encode(bytes),
        'tags': ['mobile'],
      });
      _title.clear();
      await _load();
    } catch (error) {
      setState(() => _error = error);
    } finally {
      if (mounted) {
        setState(() => _uploading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Medical vault')),
      body: _loading
          ? const CareOsLoadingState(message: 'Loading documents')
          : RefreshIndicator(
              onRefresh: _load,
              child: ListView(
                padding: const EdgeInsets.all(20),
                children: [
                  if (_error != null) ...[
                    CareOsErrorBanner.fromError(_error!, onRetry: _load),
                    const SizedBox(height: 14),
                  ],
                  _InputField(
                    label: 'Document title',
                    icon: Icons.title,
                    controller: _title,
                  ),
                  const SizedBox(height: 12),
                  _InputField(
                    label: 'Text content for pilot upload',
                    icon: Icons.notes_outlined,
                    controller: _content,
                  ),
                  const SizedBox(height: 12),
                  FilledButton.icon(
                    onPressed: _uploading ? null : _uploadDemoDocument,
                    icon: const Icon(Icons.upload_file),
                    label: Text(
                      _uploading ? 'Uploading...' : 'Upload document',
                    ),
                  ),
                  const SizedBox(height: 18),
                  if (_documents.isEmpty)
                    const CareOsEmptyState(
                      icon: Icons.folder_open,
                      title: 'No documents yet',
                      message: 'Uploaded records will appear here.',
                    )
                  else
                    ..._documents.map(
                      (document) => _RecordTile(
                        icon: Icons.description_outlined,
                        title: document['title']?.toString() ?? 'Document',
                        detail:
                            document['type']?.toString() ?? 'medical record',
                      ),
                    ),
                ],
              ),
            ),
    );
  }
}

class SymptomScanScreen extends StatelessWidget {
  const SymptomScanScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        const _Header(
          title: 'Symptom Visual Scan',
          subtitle: 'Capture, review, and send to clinical triage',
        ),
        const SizedBox(height: 20),
        Container(
          height: 360,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(28),
            gradient: const LinearGradient(
              colors: [Color(0xFF2E635F), Color(0xFF0A171E)],
            ),
          ),
          child: Stack(
            children: [
              Positioned.fill(
                child: Padding(
                  padding: const EdgeInsets.all(38),
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      border: Border.all(color: Colors.white70, width: 2),
                      borderRadius: BorderRadius.circular(22),
                    ),
                  ),
                ),
              ),
              const Center(
                child: Icon(
                  Icons.photo_camera_back_outlined,
                  size: 90,
                  color: Colors.white70,
                ),
              ),
              Positioned(
                left: 20,
                right: 20,
                bottom: 18,
                child: FilledButton.icon(
                  onPressed: null,
                  icon: Icon(Icons.camera_alt),
                  label: Text('Open clinical camera'),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 18),
        const _InfoStrip(
          icon: Icons.lock_outline,
          text:
              'Images stay encrypted until a licensed clinician reviews them.',
        ),
        const SizedBox(height: 16),
        _PrimaryCard(
          color: _danger,
          title: 'Escalate emergency',
          subtitle:
              'Severe bleeding, chest pain, seizures, or breathing distress',
          action: 'Call emergency desk',
          icon: Icons.warning_amber_rounded,
          onTap: () {},
        ),
      ],
    );
  }
}

class ClinicalTriageScreen extends StatefulWidget {
  const ClinicalTriageScreen({super.key});

  @override
  State<ClinicalTriageScreen> createState() => _ClinicalTriageScreenState();
}

class _ClinicalTriageScreenState extends State<ClinicalTriageScreen> {
  final _symptoms = TextEditingController();
  final _painScore = TextEditingController(text: '3');
  TriageResult? _result;
  List<TriageResult> _history = const [];
  Object? _error;
  bool _loading = false;
  bool _historyLoading = true;

  @override
  void initState() {
    super.initState();
    _loadHistory();
  }

  @override
  void dispose() {
    _symptoms.dispose();
    _painScore.dispose();
    super.dispose();
  }

  Future<void> _loadHistory() async {
    setState(() {
      _historyLoading = true;
      _error = null;
    });
    try {
      _history = await AppDependenciesScope.of(context).api.triageHistory();
    } catch (error) {
      _error = error;
    } finally {
      if (mounted) {
        setState(() => _historyLoading = false);
      }
    }
  }

  Future<void> _submit() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final painScore = int.tryParse(_painScore.text.trim()) ?? 0;
      _result = await AppDependenciesScope.of(context).api.submitTriage(
        symptomsText: _symptoms.text.trim(),
        questionnaire: {'painScore': painScore},
      );
      await _loadHistory();
    } catch (error) {
      setState(() => _error = error);
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Clinical AI Triage')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          if (_error != null) ...[
            CareOsErrorBanner.fromError(_error!, onRetry: _loadHistory),
            const SizedBox(height: 14),
          ],
          _InputField(
            label: 'Describe symptoms',
            icon: Icons.psychology_alt_outlined,
            controller: _symptoms,
          ),
          const SizedBox(height: 12),
          _InputField(
            label: 'Pain score 0-10',
            icon: Icons.speed_outlined,
            controller: _painScore,
            keyboardType: TextInputType.number,
          ),
          const SizedBox(height: 14),
          FilledButton.icon(
            onPressed: _loading ? null : _submit,
            icon: const Icon(Icons.auto_awesome),
            label: Text(_loading ? 'Assessing symptoms...' : 'Submit triage'),
          ),
          if (_result != null) ...[
            const SizedBox(height: 18),
            _StatusBanner(
              color: _result!.isHighUrgency ? _danger : _teal,
              title: _result!.isHighUrgency
                  ? 'High urgency result'
                  : 'Triage result',
              detail:
                  '${_result!.recommendedAction}\nSuggested specialty: ${_result!.suggestedSpecialty}',
            ),
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: () => _open(context, const SpecialistNetworkScreen()),
              icon: const Icon(Icons.medical_services),
              label: const Text('Find available specialist'),
            ),
          ],
          const SizedBox(height: 24),
          const _SectionTitle('Triage history'),
          const SizedBox(height: 10),
          if (_historyLoading)
            const CareOsLoadingState(message: 'Loading history')
          else if (_history.isEmpty)
            const CareOsEmptyState(
              icon: Icons.history,
              title: 'No triage history',
              message: 'Submitted symptom checks will appear here.',
            )
          else
            ..._history.map(
              (triage) => _RecordTile(
                icon: triage.isHighUrgency
                    ? Icons.warning_amber_rounded
                    : Icons.assignment_turned_in_outlined,
                title:
                    '${triage.urgency.toUpperCase()} - ${triage.suggestedSpecialty}',
                detail: triage.recommendedAction,
              ),
            ),
          const SizedBox(height: 16),
          const _InfoStrip(
            icon: Icons.info_outline,
            text:
                'AI triage does not replace emergency care. Severe symptoms need urgent medical attention.',
          ),
        ],
      ),
    );
  }
}

class TelehealthCallScreen extends StatefulWidget {
  const TelehealthCallScreen({this.booking, super.key});

  final Booking? booking;

  @override
  State<TelehealthCallScreen> createState() => _TelehealthCallScreenState();
}

class _TelehealthCallScreenState extends State<TelehealthCallScreen> {
  final _message = TextEditingController();
  List<Map<String, dynamic>> _messages = const [];
  Object? _error;
  bool _loading = false;
  bool _micMuted = false;
  bool _cameraOn = true;

  @override
  void initState() {
    super.initState();
    _loadChat();
  }

  @override
  void dispose() {
    _message.dispose();
    super.dispose();
  }

  Future<void> _loadChat() async {
    if (widget.booking == null) {
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      _messages = await AppDependenciesScope.of(
        context,
      ).api.listChatMessages(widget.booking!.id);
    } catch (error) {
      _error = error;
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _send() async {
    if (widget.booking == null || _message.text.trim().isEmpty) {
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await AppDependenciesScope.of(context).api.sendChatMessage(
        bookingId: widget.booking!.id,
        message: _message.text.trim(),
      );
      _message.clear();
      await _loadChat();
    } catch (error) {
      setState(() => _error = error);
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _navy,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            children: [
              _DarkHeader(
                title: widget.booking == null
                    ? 'Demo consultation'
                    : 'Booking ${widget.booking!.id.substring(0, 8)}',
                subtitle: widget.booking?.status ?? 'No booking selected',
              ),
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.symmetric(vertical: 18),
                  children: [
                    Container(
                      height: 260,
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(32),
                        gradient: const LinearGradient(
                          colors: [Color(0xFF1A8177), Color(0xFF0B1720)],
                        ),
                      ),
                      child: Center(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              _cameraOn
                                  ? Icons.person_4_rounded
                                  : Icons.videocam_off,
                              color: Colors.white,
                              size: 96,
                            ),
                            const SizedBox(height: 10),
                            Padding(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 20,
                              ),
                              child: Text(
                                widget.booking?.videoProvider == null
                                    ? 'Video provider unavailable'
                                    : 'Room: ${widget.booking!.videoRoomId}',
                                textAlign: TextAlign.center,
                                style: const TextStyle(color: Colors.white70),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    if (_error != null)
                      CareOsErrorBanner.fromError(_error!, onRetry: _loadChat),
                    if (_loading)
                      const Padding(
                        padding: EdgeInsets.all(12),
                        child: LinearProgressIndicator(),
                      ),
                    if (widget.booking == null)
                      const _InfoStrip(
                        icon: Icons.info_outline,
                        text:
                            'Open a real booking from specialist checkout to enable chat.',
                      )
                    else ...[
                      ..._messages.map(
                        (message) => _ChatBubble(
                          role:
                              message['senderId']?.toString() ?? 'Participant',
                          text: message['message']?.toString() ?? '',
                        ),
                      ),
                      const SizedBox(height: 10),
                      Row(
                        children: [
                          Expanded(
                            child: TextField(
                              controller: _message,
                              decoration: const InputDecoration(
                                hintText: 'Message clinician',
                                filled: true,
                                fillColor: Colors.white,
                              ),
                            ),
                          ),
                          IconButton.filled(
                            onPressed: _send,
                            icon: const Icon(Icons.send),
                          ),
                        ],
                      ),
                    ],
                  ],
                ),
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  _CallButton(
                    icon: _micMuted ? Icons.mic_off : Icons.mic,
                    color: Colors.white12,
                    onTap: () => setState(() => _micMuted = !_micMuted),
                  ),
                  _CallButton(
                    icon: _cameraOn ? Icons.videocam : Icons.videocam_off,
                    color: Colors.white12,
                    onTap: () => setState(() => _cameraOn = !_cameraOn),
                  ),
                  _CallButton(
                    icon: Icons.call_end,
                    color: _danger,
                    onTap: () => Navigator.pop(context),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({super.key});

  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen> {
  Object? _error;
  bool _loading = false;
  String? _message;

  Future<void> _sendTestNotification() async {
    setState(() {
      _loading = true;
      _error = null;
      _message = null;
    });
    try {
      await AppDependenciesScope.of(context).api.dispatchNotification({
        'channel': 'push',
        'target': 'mobile-device-token-pending',
        'template': 'mobile_test',
        'payload': {'source': 'mobile_app'},
      });
      _message =
          'A backend notification record was created. Native push token registration is pending a provider endpoint.';
    } catch (error) {
      _error = error;
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isProduction = AppDependenciesScope.of(context).config.isProduction;
    return Scaffold(
      appBar: AppBar(title: const Text('Notifications')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          ComingSoonState(
            title: isProduction ? 'Notifications' : 'Native push integration',
            message: isProduction
                ? 'Notification preferences will appear here after device registration is enabled.'
                : 'The backend can create notification records, but device-token registration is not enabled yet.',
          ),
          const SizedBox(height: 16),
          if (!isProduction) ...[
            if (_error != null) CareOsErrorBanner.fromError(_error!),
            if (_message != null)
              _InfoStrip(icon: Icons.notifications, text: _message!),
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: _loading ? null : _sendTestNotification,
              icon: const Icon(Icons.notifications_active_outlined),
              label: Text(_loading ? 'Sending...' : 'Create test notification'),
            ),
          ],
        ],
      ),
    );
  }
}

class QaChecklistScreen extends StatelessWidget {
  const QaChecklistScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final env = AppDependenciesScope.of(context).config;
    return Scaffold(
      appBar: AppBar(title: const Text('QA sign-off')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          _StatusBanner(
            color: _teal,
            title: 'Environment',
            detail:
                '${env.environment.name} - ${env.apiBaseUrl}\nRelease builds can switch with --dart-define.',
          ),
          const SizedBox(height: 14),
          const _RecordTile(
            icon: Icons.check_circle_outline,
            title: 'Patient journey',
            detail:
                'Register, consent, profile, triage, specialist search, booking, payment pending state.',
          ),
          const _RecordTile(
            icon: Icons.check_circle_outline,
            title: 'Clinician journey',
            detail:
                'Login supported. Approval and booking queue require admin/provider screens next.',
          ),
          const _RecordTile(
            icon: Icons.network_check,
            title: 'Offline behavior',
            detail:
                'API calls map connection failures to retryable user-safe errors.',
          ),
          const _RecordTile(
            icon: Icons.privacy_tip_outlined,
            title: 'PII/PHI logs',
            detail:
                'Telemetry helper redacts sensitive keys before local breadcrumbs.',
          ),
          const _InfoStrip(
            icon: Icons.phone_android,
            text:
                'Real-device staging sign-off still needs a staging URL, provider credentials, and physical devices.',
          ),
        ],
      ),
    );
  }
}

class SpecialistNetworkScreen extends StatefulWidget {
  const SpecialistNetworkScreen({super.key});

  @override
  State<SpecialistNetworkScreen> createState() =>
      _SpecialistNetworkScreenState();
}

class _SpecialistNetworkScreenState extends State<SpecialistNetworkScreen> {
  final _search = TextEditingController(text: 'General');
  List<SpecialistProfile> _specialists = const [];
  Object? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      _specialists = await AppDependenciesScope.of(context).api
          .searchSpecialists(
            specialty: _search.text.trim().isEmpty ? null : _search.text.trim(),
          );
    } catch (error) {
      _error = error;
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        const _Header(
          title: 'Specialist Network',
          subtitle: 'Browse specialists and care pathways',
        ),
        const SizedBox(height: 14),
        _InputField(
          label: 'Search by name, specialty, or city',
          icon: Icons.search,
          controller: _search,
          onSubmitted: (_) => _load(),
        ),
        const SizedBox(height: 10),
        Align(
          alignment: Alignment.centerLeft,
          child: FilledButton.icon(
            onPressed: _loading ? null : _load,
            icon: const Icon(Icons.search),
            label: const Text('Search specialists'),
          ),
        ),
        const SizedBox(height: 16),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: const [
            _FilterChipLabel('Cardiology'),
            _FilterChipLabel('Pediatrics'),
            _FilterChipLabel('Dermatology'),
            _FilterChipLabel('Emergency'),
          ],
        ),
        const SizedBox(height: 18),
        if (_error != null)
          CareOsErrorBanner.fromError(_error!, onRetry: _load)
        else if (_loading)
          const CareOsLoadingState(message: 'Finding specialists')
        else if (_specialists.isEmpty)
          const CareOsEmptyState(
            icon: Icons.medical_services_outlined,
            title: 'No specialists found',
            message:
                'Try another specialty or ask an admin to approve clinicians.',
          )
        else
          ..._specialists.map(
            (specialist) => _DoctorCard(
              name: specialist.displayName,
              specialty: specialist.specialty,
              rating: formatNaira(specialist.priceKobo),
              button: 'Book consult',
              onTap: () =>
                  _open(context, BookingCheckoutScreen(specialist: specialist)),
            ),
          ),
        const SizedBox(height: 12),
        const ComingSoonState(
          title: 'Longitudinal EHR',
          message:
              'Referral timelines and external provider sharing are outside the current MVP backend.',
        ),
      ],
    );
  }
}

class BookingCheckoutScreen extends StatefulWidget {
  const BookingCheckoutScreen({required this.specialist, super.key});

  final SpecialistProfile specialist;

  @override
  State<BookingCheckoutScreen> createState() => _BookingCheckoutScreenState();
}

class _BookingCheckoutScreenState extends State<BookingCheckoutScreen> {
  Booking? _booking;
  PaymentCharge? _charge;
  PaymentReceipt? _receipt;
  Object? _error;
  bool _loading = false;

  DateTime get _defaultStart =>
      DateTime.now().toUtc().add(const Duration(days: 1, hours: 1));

  Future<void> _book() async {
    final api = AppDependenciesScope.of(context).api;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      _booking = await api.createBooking(
        clinicianId: widget.specialist.userId,
        startsAt: _defaultStart,
      );
      _charge = await api.createCharge(
        bookingId: _booking!.id,
        idempotencyKey: 'mobile-${_booking!.id}',
      );
    } catch (error) {
      _error = error;
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _refreshReceipt() async {
    if (_charge == null) {
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      _receipt = await AppDependenciesScope.of(
        context,
      ).api.getReceipt(_charge!.id);
    } catch (error) {
      _error = error;
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Book consult')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          _DoctorCard(
            name: widget.specialist.displayName,
            specialty: widget.specialist.specialty,
            rating: formatNaira(widget.specialist.priceKobo),
            button: 'Start booking',
            onTap: _loading || _booking != null ? () {} : _book,
          ),
          const SizedBox(height: 16),
          if (_error != null) ...[
            CareOsErrorBanner.fromError(_error!, onRetry: _refreshReceipt),
            const SizedBox(height: 14),
          ],
          if (_loading)
            const CareOsLoadingState(message: 'Working on booking')
          else if (_booking == null)
            const _InfoStrip(
              icon: Icons.event_available,
              text: 'Creates a pending-payment booking for tomorrow.',
            )
          else ...[
            _StatusBanner(
              color: _gold,
              title: 'Booking created',
              detail:
                  'Status: ${_booking!.status}. Payment must confirm before the consult is active.',
            ),
            const SizedBox(height: 12),
            if (_charge != null)
              _InfoStrip(
                icon: Icons.payment,
                text:
                    'Checkout initialized. Complete payment here: ${_charge!.authorizationUrl}',
              ),
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: _refreshReceipt,
              icon: const Icon(Icons.receipt_long),
              label: const Text('Check for receipt'),
            ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: () =>
                  _open(context, TelehealthCallScreen(booking: _booking)),
              icon: const Icon(Icons.video_call),
              label: const Text('Open consult room'),
            ),
          ],
          if (_receipt != null) ...[
            const SizedBox(height: 18),
            _StatusBanner(
              color: _teal,
              title: 'Payment confirmed',
              detail:
                  'Receipt ${_receipt!.receiptNumber} - ${formatNaira(_receipt!.amountKobo)} ${_receipt!.currency}',
            ),
          ],
        ],
      ),
    );
  }
}

class ReferralLedgerScreen extends StatelessWidget {
  const ReferralLedgerScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Referral Ledger')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: const [
          _ReferralTile(
            title: 'LUTH Emergency Unit',
            status: 'Urgent',
            detail: 'Awaiting receiving doctor',
          ),
          _ReferralTile(
            title: 'Evercare Lagos',
            status: 'In progress',
            detail: 'Bed availability confirmed',
          ),
          _ReferralTile(
            title: 'Reddington Hospital',
            status: 'Accepted',
            detail: 'Patient arrival expected 4:20 PM',
          ),
        ],
      ),
    );
  }
}

class InsurancePortalScreen extends StatelessWidget {
  const InsurancePortalScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.all(20),
      child: ComingSoonState(
        title: 'Insurance Portal',
        message:
            'HMO eligibility, pre-authorizations, and claims are not part of the current MVP backend yet.',
      ),
    );
  }
}

class MutualCareFundScreen extends StatelessWidget {
  const MutualCareFundScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.all(20),
      child: ComingSoonState(
        title: 'Mutual Care Fund',
        message:
            'Care fund pooling, requests, and contributions will stay disabled until those backend APIs exist.',
      ),
    );
  }
}

class _InputField extends StatelessWidget {
  const _InputField({
    required this.label,
    required this.icon,
    this.obscureText = false,
    this.controller,
    this.keyboardType,
    this.onSubmitted,
  });

  final String label;
  final IconData icon;
  final bool obscureText;
  final TextEditingController? controller;
  final TextInputType? keyboardType;
  final ValueChanged<String>? onSubmitted;

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      obscureText: obscureText,
      keyboardType: keyboardType,
      onSubmitted: onSubmitted,
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: Icon(icon),
        filled: true,
        fillColor: Colors.white,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide.none,
        ),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.title, required this.subtitle});

  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Image.asset(
          'play-console/careos-play-icon-512.png',
          width: 34,
          height: 34,
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.w900,
                ),
              ),
              Text(subtitle, style: const TextStyle(color: Colors.black54)),
            ],
          ),
        ),
        IconButton.filledTonal(
          onPressed: () => _open(context, const NotificationsScreen()),
          icon: const Icon(Icons.notifications_none),
        ),
      ],
    );
  }
}

class _DarkHeader extends StatelessWidget {
  const _DarkHeader({required this.title, required this.subtitle});

  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const CircleAvatar(
          backgroundColor: _teal,
          child: Icon(Icons.health_and_safety, color: Colors.white),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 20,
                  fontWeight: FontWeight.w800,
                ),
              ),
              Text(subtitle, style: const TextStyle(color: Colors.white60)),
            ],
          ),
        ),
      ],
    );
  }
}

class _VitalsCard extends StatelessWidget {
  const _VitalsCard();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: _cardDecoration(),
      child: const Row(
        children: [
          Expanded(
            child: _VitalMetric(label: 'Heart rate', value: '72', unit: 'bpm'),
          ),
          Expanded(
            child: _VitalMetric(
              label: 'Blood pressure',
              value: '128/82',
              unit: 'mmHg',
            ),
          ),
          Expanded(
            child: _VitalMetric(label: 'O2 sat', value: '98', unit: '%'),
          ),
        ],
      ),
    );
  }
}

class _VitalMetric extends StatelessWidget {
  const _VitalMetric({
    required this.label,
    required this.value,
    required this.unit,
  });

  final String label;
  final String value;
  final String unit;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(color: Colors.black54, fontSize: 12),
        ),
        const SizedBox(height: 5),
        Text(
          value,
          style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w900),
        ),
        Text(unit, style: const TextStyle(color: Colors.black45, fontSize: 11)),
      ],
    );
  }
}

class _PrimaryCard extends StatelessWidget {
  const _PrimaryCard({
    required this.color,
    required this.title,
    required this.subtitle,
    required this.action,
    required this.icon,
    required this.onTap,
  });

  final Color color;
  final String title;
  final String subtitle;
  final String action;
  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: _cardDecoration(color: color),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: Colors.white, size: 34),
          const SizedBox(height: 12),
          Text(
            title,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 21,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            subtitle,
            style: const TextStyle(color: Colors.white70, height: 1.35),
          ),
          const SizedBox(height: 14),
          FilledButton.tonal(onPressed: onTap, child: Text(action)),
        ],
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
    );
  }
}

class _ServiceTile extends StatelessWidget {
  const _ServiceTile(this.title, this.icon, this.onTap);

  final String title;
  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(18),
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: _cardDecoration(),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            CircleAvatar(
              backgroundColor: _mint,
              child: Icon(icon, color: _teal),
            ),
            Text(title, style: const TextStyle(fontWeight: FontWeight.w800)),
          ],
        ),
      ),
    );
  }
}

class _DoctorCard extends StatelessWidget {
  const _DoctorCard({
    required this.name,
    required this.specialty,
    required this.rating,
    required this.button,
    required this.onTap,
  });

  final String name;
  final String specialty;
  final String rating;
  final String button;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: _cardDecoration(),
      child: Row(
        children: [
          const CircleAvatar(
            radius: 28,
            backgroundColor: _mint,
            child: Icon(Icons.person, color: _teal),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(name, style: const TextStyle(fontWeight: FontWeight.w900)),
                Text(
                  specialty,
                  style: const TextStyle(color: Colors.black54, fontSize: 12),
                ),
                Row(
                  children: [
                    const Icon(Icons.star, color: _gold, size: 16),
                    Text(
                      rating,
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                  ],
                ),
              ],
            ),
          ),
          FilledButton(onPressed: onTap, child: Text(button)),
        ],
      ),
    );
  }
}

class _RecordTile extends StatelessWidget {
  const _RecordTile({
    required this.icon,
    required this.title,
    required this.detail,
    this.onTap,
  });

  final IconData icon;
  final String title;
  final String detail;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(18),
      child: Container(
        margin: const EdgeInsets.only(bottom: 10),
        padding: const EdgeInsets.all(14),
        decoration: _cardDecoration(),
        child: Row(
          children: [
            CircleAvatar(
              backgroundColor: _mint,
              child: Icon(icon, color: _teal),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: const TextStyle(fontWeight: FontWeight.w800),
                  ),
                  Text(detail, style: const TextStyle(color: Colors.black54)),
                ],
              ),
            ),
            if (onTap != null) const Icon(Icons.chevron_right),
          ],
        ),
      ),
    );
  }
}

class _InfoStrip extends StatelessWidget {
  const _InfoStrip({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: _mint,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: _teal.withValues(alpha: 0.18)),
      ),
      child: Row(
        children: [
          Icon(icon, color: _teal),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              text,
              style: const TextStyle(fontWeight: FontWeight.w700),
            ),
          ),
        ],
      ),
    );
  }
}

class _StatusBanner extends StatelessWidget {
  const _StatusBanner({
    required this.color,
    required this.title,
    required this.detail,
  });

  final Color color;
  final String title;
  final String detail;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: _cardDecoration(color: color),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 20,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 6),
          Text(detail, style: const TextStyle(color: Colors.white70)),
        ],
      ),
    );
  }
}

class _ChatBubble extends StatelessWidget {
  const _ChatBubble({required this.role, required this.text});

  final String role;
  final String text;

  @override
  Widget build(BuildContext context) {
    final isAi = role.contains('AI');
    return Align(
      alignment: isAi ? Alignment.centerLeft : Alignment.centerRight,
      child: Container(
        width: MediaQuery.sizeOf(context).width * 0.78,
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: isAi ? Colors.white : _teal,
          borderRadius: BorderRadius.circular(18),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              role,
              style: TextStyle(
                color: isAi ? _teal : Colors.white70,
                fontWeight: FontWeight.w900,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              text,
              style: TextStyle(color: isAi ? _ink : Colors.white, height: 1.35),
            ),
          ],
        ),
      ),
    );
  }
}

class _FilterChipLabel extends StatelessWidget {
  const _FilterChipLabel(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    return Chip(
      label: Text(label),
      backgroundColor: Colors.white,
      side: BorderSide(color: _teal.withValues(alpha: 0.22)),
    );
  }
}

class _ReferralTile extends StatelessWidget {
  const _ReferralTile({
    required this.title,
    required this.status,
    required this.detail,
  });

  final String title;
  final String status;
  final String detail;

  @override
  Widget build(BuildContext context) {
    final urgent = status == 'Urgent';
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: _cardDecoration(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  title,
                  style: const TextStyle(fontWeight: FontWeight.w900),
                ),
              ),
              Chip(
                label: Text(status),
                backgroundColor: urgent ? const Color(0xFFFFE9E9) : _mint,
                labelStyle: TextStyle(
                  color: urgent ? _danger : _teal,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ],
          ),
          Text(detail, style: const TextStyle(color: Colors.black54)),
        ],
      ),
    );
  }
}

class _CallButton extends StatelessWidget {
  const _CallButton({
    required this.icon,
    required this.color,
    required this.onTap,
  });

  final IconData icon;
  final Color color;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(999),
      child: CircleAvatar(
        radius: 30,
        backgroundColor: color,
        child: Icon(icon, color: Colors.white),
      ),
    );
  }
}

class _RolePill extends StatelessWidget {
  const _RolePill({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: Chip(
        avatar: Icon(icon, size: 16, color: _teal),
        label: Text(label),
        backgroundColor: Colors.white,
      ),
    );
  }
}

BoxDecoration _cardDecoration({Color color = Colors.white}) {
  return BoxDecoration(
    color: color,
    borderRadius: BorderRadius.circular(18),
    boxShadow: [
      BoxShadow(
        color: Colors.black.withValues(alpha: 0.05),
        blurRadius: 20,
        offset: const Offset(0, 8),
      ),
    ],
  );
}

void _open(BuildContext context, Widget screen) {
  Navigator.of(context).push(MaterialPageRoute(builder: (_) => screen));
}
