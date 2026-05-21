import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../core/api_client.dart';

class AuthRepository {
  final ApiClient _apiClient = ApiClient();
  final String _sessionKey = "MedRetailerSession";

  Future<Map<String, dynamic>> login(String usr, String pwd) async {
    try {
      final response = await _apiClient.postJson('/api/method/login', {
        'usr': usr,
        'pwd': pwd,
      });

      if (response.statusCode == 200) {
        // Dio handles cookies automatically via CookieManager
        // But we might want to store roles and permissions too
        final rolesInfo = await _fetchUserRoles(usr);
        final permissions = await _fetchUserPermissions(usr);

        await _saveAuthData(usr, rolesInfo['role'], permissions);
        
        return {
          'success': true,
          'role': rolesInfo['role'],
          'debugStatus': rolesInfo['debugStatus'],
        };
      }
    } on DioException catch (e) {
      return {
        'success': false,
        'message': e.message ?? "Login failed",
      };
    }
    return {'success': false, 'message': "Unknown error"};
  }

  Future<Map<String, dynamic>> _fetchUserRoles(String userEmail) async {
    try {
      final response = await _apiClient.get(
        '/api/method/hr_automation.api.user.get_roles',
        queryParameters: {'user_email': userEmail},
      );

      if (response.statusCode == 200) {
        final rolesArray = response.data['message']['roles'] as List;
        String finalRole = "MedRep";
        int rolePriority = 0;

        for (var role in rolesArray) {
          final roleStr = role.toString().trim();
          if (roleStr.toLowerCase() == "administrator" || roleStr.toLowerCase() == "system manager") {
            if (rolePriority < 4) {
              finalRole = "Admin";
              rolePriority = 4;
            }
          } else if (roleStr.toLowerCase() == "sales master manager") {
            if (rolePriority < 3) {
              finalRole = "GM";
              rolePriority = 3;
            }
          } else if (["sales manager", "sales manager i", "sales manager ii", "sales manager i-a"].contains(roleStr.toLowerCase())) {
            if (rolePriority < 2) {
              finalRole = "DSM";
              rolePriority = 2;
            }
          } else if (roleStr.toLowerCase() == "sales user") {
            if (rolePriority < 1) {
              finalRole = "MedRep";
              rolePriority = 1;
            }
          }
        }
        return {'role': finalRole, 'debugStatus': 'Success'};
      }
    } catch (e) {
      return {'role': 'MedRep', 'debugStatus': 'Error: $e'};
    }
    return {'role': 'MedRep', 'debugStatus': 'Failed'};
  }

  Future<String> _fetchUserPermissions(String userEmail) async {
    try {
      final response = await _apiClient.get(
        '/api/resource/User%20Permission',
        queryParameters: {
          'fields': '["allow","for_value"]',
          'filters': '[["user","=","$userEmail"]]',
          'limit_page_length': 500,
        },
      );

      if (response.statusCode == 200) {
        final data = response.data['data'] as List;
        Map<String, List<String>> grouped = {};
        for (var item in data) {
          final allow = item['allow'];
          final value = item['for_value'];
          grouped.putIfAbsent(allow, () => []).add(value);
        }
        return jsonEncode(grouped);
      }
    } catch (_) {}
    return "{}";
  }

  Future<void> _saveAuthData(String email, String role, String permissions) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString("User_Email", email);
    await prefs.setString("User_Role", role);
    await prefs.setString("User_Permissions_Map", permissions);
  }

  Future<String?> getLoggedRole() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString("User_Role");
  }
}
