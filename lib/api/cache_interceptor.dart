import 'package:dio/dio.dart';

class CacheInterceptor extends Interceptor {
  final Map<String, _CacheEntry> _cache = {};

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    if (options.method == 'GET') {
      final queryParameters = options.queryParameters.toString();
      final cacheKey = "${options.path}_$queryParameters";
      
      final entry = _cache[cacheKey];
      if (entry != null && !entry.isExpired) {
        return handler.resolve(entry.response);
      }
    }
    super.onRequest(options, handler);
  }

  @override
  void onResponse(Response response, ResponseInterceptorHandler handler) {
    if (response.requestOptions.method == 'GET') {
      final queryParameters = response.requestOptions.queryParameters.toString();
      final cacheKey = "${response.requestOptions.path}_$queryParameters";
      
      _cache[cacheKey] = _CacheEntry(
        response: response,
        expiry: DateTime.now().add(const Duration(minutes: 5)),
      );
    }
    super.onResponse(response, handler);
  }
}

class _CacheEntry {
  final Response response;
  final DateTime expiry;

  _CacheEntry({required this.response, required this.expiry});

  bool get isExpired => DateTime.now().isAfter(expiry);
}
