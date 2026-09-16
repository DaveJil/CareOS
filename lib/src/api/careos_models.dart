import 'token_store.dart';

enum CareOsRole {
  patient,
  clinician,
  hospitalAdmin,
  hmoStaff,
  fundAdmin,
  superAdmin;

  String get wireName => switch (this) {
    CareOsRole.patient => 'patient',
    CareOsRole.clinician => 'clinician',
    CareOsRole.hospitalAdmin => 'hospital_admin',
    CareOsRole.hmoStaff => 'hmo_staff',
    CareOsRole.fundAdmin => 'fund_admin',
    CareOsRole.superAdmin => 'super_admin',
  };

  static CareOsRole fromWireName(String value) {
    return CareOsRole.values.firstWhere(
      (role) => role.wireName == value,
      orElse: () => CareOsRole.patient,
    );
  }
}

class CareOsUser {
  const CareOsUser({
    required this.id,
    required this.email,
    required this.role,
    this.phone,
  });

  factory CareOsUser.fromJson(Map<String, dynamic> json) {
    return CareOsUser(
      id: json['id'].toString(),
      email: json['email'].toString(),
      phone: json['phone']?.toString(),
      role: CareOsRole.fromWireName(json['role'].toString()),
    );
  }

  final String id;
  final String email;
  final String? phone;
  final CareOsRole role;
}

class AuthSession {
  const AuthSession({required this.user, required this.tokens});

  factory AuthSession.fromJson(Map<String, dynamic> json) {
    return AuthSession(
      user: CareOsUser.fromJson(json['user'] as Map<String, dynamic>),
      tokens: TokenPair(
        accessToken: json['accessToken'].toString(),
        refreshToken: json['refreshToken'].toString(),
        expiresInSeconds: json['expiresInSeconds'] as int? ?? 0,
      ),
    );
  }

  final CareOsUser user;
  final TokenPair tokens;
}

class TriageResult {
  const TriageResult({
    required this.id,
    required this.urgency,
    required this.suggestedSpecialty,
    required this.recommendedAction,
    required this.riskScore,
    required this.reviewStatus,
  });

  factory TriageResult.fromJson(Map<String, dynamic> json) {
    return TriageResult(
      id: json['id'].toString(),
      urgency: json['urgency'].toString(),
      suggestedSpecialty: json['suggestedSpecialty'].toString(),
      recommendedAction: json['recommendedAction'].toString(),
      riskScore: json['riskScore'] as int? ?? 0,
      reviewStatus: json['reviewStatus'].toString(),
    );
  }

  final String id;
  final String urgency;
  final String suggestedSpecialty;
  final String recommendedAction;
  final int riskScore;
  final String reviewStatus;

  bool get isHighUrgency => urgency == 'high';
}

class SpecialistProfile {
  const SpecialistProfile({
    required this.userId,
    required this.displayName,
    required this.specialty,
    required this.priceKobo,
    required this.approved,
    this.bio,
  });

  factory SpecialistProfile.fromJson(Map<String, dynamic> json) {
    return SpecialistProfile(
      userId: json['userId'].toString(),
      displayName: json['displayName'].toString(),
      specialty: json['specialty'].toString(),
      bio: json['bio']?.toString(),
      priceKobo: json['priceKobo'] as int? ?? 0,
      approved: json['approved'] == true,
    );
  }

  final String userId;
  final String displayName;
  final String specialty;
  final String? bio;
  final int priceKobo;
  final bool approved;
}

class Booking {
  const Booking({
    required this.id,
    required this.patientId,
    required this.clinicianId,
    required this.status,
    required this.priceKobo,
    this.videoProvider,
    this.videoRoomId,
    this.videoToken,
  });

  factory Booking.fromJson(Map<String, dynamic> json) {
    return Booking(
      id: json['id'].toString(),
      patientId: json['patientId'].toString(),
      clinicianId: json['clinicianId'].toString(),
      status: json['status'].toString(),
      priceKobo: json['priceKobo'] as int? ?? 0,
      videoProvider: json['videoProvider']?.toString(),
      videoRoomId: json['videoRoomId']?.toString(),
      videoToken: json['videoToken']?.toString(),
    );
  }

  final String id;
  final String patientId;
  final String clinicianId;
  final String status;
  final int priceKobo;
  final String? videoProvider;
  final String? videoRoomId;
  final String? videoToken;
}

class PaymentCharge {
  const PaymentCharge({
    required this.id,
    required this.bookingId,
    required this.status,
    required this.amountKobo,
    required this.authorizationUrl,
    required this.providerReference,
  });

  factory PaymentCharge.fromJson(Map<String, dynamic> json) {
    return PaymentCharge(
      id: json['id'].toString(),
      bookingId: json['bookingId'].toString(),
      status: json['status'].toString(),
      amountKobo: json['amountKobo'] as int? ?? 0,
      authorizationUrl: json['authorizationUrl']?.toString() ?? '',
      providerReference: json['providerReference'].toString(),
    );
  }

  final String id;
  final String bookingId;
  final String status;
  final int amountKobo;
  final String authorizationUrl;
  final String providerReference;
}

class PaymentReceipt {
  const PaymentReceipt({
    required this.receiptNumber,
    required this.paymentId,
    required this.bookingId,
    required this.amountKobo,
    required this.currency,
  });

  factory PaymentReceipt.fromJson(Map<String, dynamic> json) {
    return PaymentReceipt(
      receiptNumber: json['receiptNumber'].toString(),
      paymentId: json['paymentId'].toString(),
      bookingId: json['bookingId'].toString(),
      amountKobo: json['amountKobo'] as int? ?? 0,
      currency: json['currency'].toString(),
    );
  }

  final String receiptNumber;
  final String paymentId;
  final String bookingId;
  final int amountKobo;
  final String currency;
}
