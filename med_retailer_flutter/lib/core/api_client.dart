import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:cookie_jar/cookie_jar.dart';
import 'package:path_provider/path_provider.dart';
import 'dart:io';

class ApiClient {
  static final ApiClient _instance = ApiClient._internal();
  late Dio dio;
  late PersistCookieJar cookieJar;

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
    final appDocDir = await getApplicationDocumentsDirectory();
    final String appDocPath = appDocDir.path;
    cookieJar = PersistCookieJar(
      ignoreExpires: false,
      storage: FileStorage("$appDocPath/.cookies/"),
    );
    dio.interceptors.add(CookieManager(cookieJar));
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
