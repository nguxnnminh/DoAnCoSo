import 'dart:convert';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

// Android emulator: 10.0.2.2 → host machine localhost
// Web/desktop: localhost
// Override with --dart-define=BASE_URL=http://...
const String kBaseUrl = String.fromEnvironment('BASE_URL', defaultValue: 'http://10.0.2.2:8080');
const String kTokenKey = 'nova_jwt';

final apiClientProvider = Provider<ApiClient>((ref) => ApiClient());

class ApiClient {
  late final Dio _dio;
  late final CookieJar _cookieJar;
  final _storage = const FlutterSecureStorage();

  // In-memory copy of the token, updated synchronously on save/clear so that
  // requests fired immediately after login don't race the async storage write.
  String? _token;

  ApiClient() {
    _cookieJar = CookieJar();
    _dio = Dio(BaseOptions(
      baseUrl: kBaseUrl,
      connectTimeout: const Duration(seconds: 15),
      receiveTimeout: const Duration(seconds: 30),
      headers: {'Accept': 'application/json', 'Content-Type': 'application/json'},
    ));
    _dio.interceptors.addAll([
      CookieManager(_cookieJar),
      _AuthInterceptor(this),
      LogInterceptor(requestBody: true, responseBody: true, logPrint: (o) => print(o)),
    ]);
  }

  Dio get dio => _dio;

  /// Current token: in-memory value if set, else falls back to storage.
  Future<String?> getToken() async => _token ??= await _storage.read(key: kTokenKey);

  Future<void> saveToken(String token) async {
    _token = token;
    await _storage.write(key: kTokenKey, value: token);
  }

  Future<void> clearToken() async {
    _token = null;
    await _storage.delete(key: kTokenKey);
  }

  /// Returns true if [token] is a valid, non-expired JWT.
  static bool isTokenValid(String token) {
    try {
      final parts = token.split('.');
      if (parts.length != 3) return false;
      // Base64url decode the payload (add padding if needed)
      final payload = parts[1];
      final normalized = base64Url.normalize(payload);
      final decoded = jsonDecode(utf8.decode(base64Url.decode(normalized))) as Map;
      final exp = decoded['exp'];
      if (exp == null) return false;
      final expiry = DateTime.fromMillisecondsSinceEpoch((exp as int) * 1000);
      return DateTime.now().isBefore(expiry);
    } catch (_) {
      return false;
    }
  }
}

class _AuthInterceptor extends Interceptor {
  final ApiClient _client;
  _AuthInterceptor(this._client);

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) async {
    final token = await _client.getToken();
    if (token != null) {
      if (ApiClient.isTokenValid(token)) {
        options.headers['Authorization'] = 'Bearer $token';
      } else {
        // Token expired — clear it so app shows login state
        await _client.clearToken();
      }
    }
    handler.next(options);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) async {
    if (err.response?.statusCode == 401) {
      await _client.clearToken();
    }
    handler.next(err);
  }
}
