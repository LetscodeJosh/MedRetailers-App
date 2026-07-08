import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:cookie_jar/cookie_jar.dart';
import 'package:path_provider/path_provider.dart';
import 'package:flutter/foundation.dart';
import 'dart:io';
import 'package:shared_preferences/shared_preferences.dart';

class ApiClient {
  static final ApiClient _instance = ApiClient._internal();
  late Dio dio;
  late CookieJar cookieJar;

  factory ApiClient() {
    return _instance;
  }

  ApiClient._internal() {
    dio = Dio(BaseOptions(
      baseUrl: 'https://mirror.medretailers.com',
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(seconds: 30),
      headers: {
        'Accept': 'application/json',
      },
    ));
    
    // We will initialize the cookie jar in an 'init' method
    // because it requires async path_provider access.
  }

  Future<void> init() async {
    if (kIsWeb) {
      cookieJar = CookieJar();
    } else {
      final appDocDir = await getApplicationDocumentsDirectory();
      final String appDocPath = appDocDir.path;
      cookieJar = PersistCookieJar(
        ignoreExpires: false,
        storage: FileStorage("$appDocPath/.cookies/"),
      );
      dio.interceptors.add(CookieManager(cookieJar));
    }

    // Manual session cookie interceptor to guarantee session transmission
    dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        try {
          final prefs = await SharedPreferences.getInstance();
          final sessionCookie = prefs.getString("Session_Cookie");
          if (sessionCookie != null && sessionCookie.isNotEmpty) {
            if (options.headers['Cookie'] == null) {
              options.headers['Cookie'] = sessionCookie;
            }
          }
        } catch (_) {}
        return handler.next(options);
      },
    ));

    dio.interceptors.add(LogInterceptor(responseBody: true, requestBody: true));
  }

  // Helper for direct JSON posts (like the login and workflow apply)
  Future<Response> postJson(String path, dynamic data) async {
    return await dio.post(
      path,
      data: data,
      options: Options(contentType: Headers.jsonContentType),
    );
  }

  Future<Response> get(String path, {Map<String, dynamic>? queryParameters}) async {
    return await dio.get(path, queryParameters: queryParameters);
  }
}
