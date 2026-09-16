import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class TokenPair {
  const TokenPair({
    required this.accessToken,
    required this.refreshToken,
    required this.expiresInSeconds,
  });

  final String accessToken;
  final String refreshToken;
  final int expiresInSeconds;
}

abstract class TokenStore {
  Future<TokenPair?> read();

  Future<void> write(TokenPair pair);

  Future<void> clear();
}

class SecureTokenStore implements TokenStore {
  SecureTokenStore({FlutterSecureStorage? storage})
    : _storage = storage ?? const FlutterSecureStorage();

  static const _accessTokenKey = 'careos.accessToken';
  static const _refreshTokenKey = 'careos.refreshToken';
  static const _expiresInKey = 'careos.expiresInSeconds';

  final FlutterSecureStorage _storage;

  @override
  Future<TokenPair?> read() async {
    final values = await Future.wait([
      _storage.read(key: _accessTokenKey),
      _storage.read(key: _refreshTokenKey),
      _storage.read(key: _expiresInKey),
    ]);
    final accessToken = values[0];
    final refreshToken = values[1];
    if (accessToken == null || refreshToken == null) {
      return null;
    }
    return TokenPair(
      accessToken: accessToken,
      refreshToken: refreshToken,
      expiresInSeconds: int.tryParse(values[2] ?? '') ?? 0,
    );
  }

  @override
  Future<void> write(TokenPair pair) async {
    await Future.wait([
      _storage.write(key: _accessTokenKey, value: pair.accessToken),
      _storage.write(key: _refreshTokenKey, value: pair.refreshToken),
      _storage.write(
        key: _expiresInKey,
        value: pair.expiresInSeconds.toString(),
      ),
    ]);
  }

  @override
  Future<void> clear() async {
    await Future.wait([
      _storage.delete(key: _accessTokenKey),
      _storage.delete(key: _refreshTokenKey),
      _storage.delete(key: _expiresInKey),
    ]);
  }
}

class MemoryTokenStore implements TokenStore {
  TokenPair? _pair;

  @override
  Future<TokenPair?> read() async => _pair;

  @override
  Future<void> write(TokenPair pair) async {
    _pair = pair;
  }

  @override
  Future<void> clear() async {
    _pair = null;
  }
}
