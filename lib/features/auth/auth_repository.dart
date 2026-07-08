import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../core/api_client.dart';

class AuthRepository {
  final ApiClient _apiClient = ApiClient();
  final String _sessionKey = "Session_Cookie";

  // --- STRICT COOKIE EXTRACTION ---
  String? _extractCookies(List<String>? setCookieHeaders) {
    if (setCookieHeaders == null || setCookieHeaders.isEmpty) return null;
    
    List<String> validCookies = [];
    
    for (var header in setCookieHeaders) {
      // ERPNext places the cookie key-value at the start before the first semicolon
      final parts = header.split(';');
      if (parts.isNotEmpty) {
        final keyValue = parts[0].trim();
        // STRICT CHECK: Frappe relies heavily on the 'sid' (Session ID) cookie. 
        if (keyValue.startsWith('sid=') || keyValue.startsWith('system_user=')) {
          validCookies.add(keyValue);
        }
      }
    }
    
    return validCookies.isNotEmpty ? validCookies.join('; ') : null;
  }

  Future<Map<String, dynamic>> login(String usr, String pwd) async {
    final cleanUsr = usr.trim().toLowerCase();
    try {
      final response = await _apiClient.postJson('/api/method/login', {
        'usr': cleanUsr,
        'pwd': pwd,
      });

      if (response.statusCode == 200) {
        // Use standard Dio header access
        final rawSetCookies = response.headers['set-cookie']; 
        final cleanCookie = _extractCookies(rawSetCookies);
        
        if (cleanCookie != null) {
          final prefs = await SharedPreferences.getInstance();
          await prefs.setString(_sessionKey, cleanCookie);
        }

        final rolesInfo = await _fetchUserRoles(cleanUsr, cookieHeader: cleanCookie);
        final permissions = await _fetchUserPermissions(cleanUsr, cookieHeader: cleanCookie);

        // Fetch User profile details (full name and gender) from User doctype
        String fullName = "User";
        if (response.data != null && response.data is Map) {
          fullName = response.data['full_name']?.toString() ?? "User";
        }
        String gender = "Male"; // Default fallback
        try {
          final encodedEmail = Uri.encodeComponent(cleanUsr);
          final userResponse = await _apiClient.dio.get(
            '/api/resource/User/$encodedEmail',
            options: Options(
              headers: {
                if (cleanCookie != null) 'Cookie': cleanCookie,
              },
            ),
          );
          if (userResponse.statusCode == 200 && userResponse.data['data'] != null) {
            final userData = userResponse.data['data'];
            if (fullName == "User") {
              fullName = userData['full_name']?.toString() ?? "User";
            }
            gender = userData['gender']?.toString() ?? "Male";
          }
        } catch (e) {
          print("Error fetching user profile details: $e");
        }

        await _saveAuthData(cleanUsr, rolesInfo['role'], permissions, fullName, gender);
        
        return {
          'success': true,
          'role': rolesInfo['role'],
          'debugStatus': rolesInfo['debugStatus'],
          'rawRoles': rolesInfo['rawRoles'] ?? 'N/A',
          'source': rolesInfo['source'] ?? 'N/A',
        };
      }
    } on DioException catch (e) {
      String msg = e.message ?? "Login failed";
      if (e.response?.statusCode == 401 || e.response?.statusCode == 403) {
        msg = "Incorrect credentials, try again";
      }
      return {
        'success': false,
        'message': msg,
      };
    }
    return {'success': false, 'message': "Incorrect credentials, try again"};
  }

  Future<Map<String, dynamic>> _fetchUserRoles(String userEmail, {String? cookieHeader}) async {
    final cleanEmail = userEmail.trim().toLowerCase();
    final encodedEmail = Uri.encodeComponent(cleanEmail);

    // 1. Try Custom HR Automation Roles endpoint (Android app logic)
    try {
      final response = await _apiClient.dio.get(
        '/api/method/hr_automation.api.user.get_roles',
        queryParameters: {'user_email': cleanEmail},
        options: Options(
          headers: {
            if (cookieHeader != null) 'Cookie': cookieHeader,
          },
        ),
      );

      if (response.statusCode == 200 && response.data['message'] != null) {
        final message = response.data['message'];
        if (message['roles'] != null) {
          final rolesList = message['roles'] as List;
          final rolesArray = rolesList.map((r) => r.toString()).toList();
          final result = _parseRolesList(rolesArray);
          result['rawRoles'] = rolesArray.join(', ');
          result['source'] = 'hr_automation.api.user.get_roles';
          return result;
        }
      }
    } catch (e) {
      print("Custom HR Automation roles fetch failed: $e");
    }

    // 2. Try User resource (standard ERPNext v15 REST API)
    try {
      final response = await _apiClient.dio.get(
        '/api/resource/User/$encodedEmail',
        options: Options(
          headers: {
            if (cookieHeader != null) 'Cookie': cookieHeader,
          },
        ),
      );

      if (response.statusCode == 200 && response.data['data'] != null) {
        final rolesList = response.data['data']['roles'] as List?;
        if (rolesList != null && rolesList.isNotEmpty) {
          final rolesArray = rolesList.map((r) => r['role'].toString()).toList();
          final result = _parseRolesList(rolesArray);
          result['rawRoles'] = rolesArray.join(', ');
          result['source'] = 'User Resource';
          return result;
        }
      }
    } catch (e) {
      print("User resource query failed: $e");
    }

    // 3. Try Has Role table via frappe.client.get_list
    try {
      final response = await _apiClient.dio.post(
        '/api/method/frappe.client.get_list',
        data: {
          'doctype': 'Has Role',
          'filters': {'parent': cleanEmail, 'parenttype': 'User'},
          'fields': ['role'],
          'limit_page_length': 100,
        },
        options: Options(
          headers: {
            if (cookieHeader != null) 'Cookie': cookieHeader,
          },
        ),
      );

      if (response.statusCode == 200 && response.data['message'] != null) {
        final rolesList = response.data['message'] as List;
        if (rolesList.isNotEmpty) {
          final rolesArray = rolesList.map((r) => r['role'].toString()).toList();
          final result = _parseRolesList(rolesArray);
          result['rawRoles'] = rolesArray.join(', ');
          result['source'] = 'Has Role';
          return result;
        }
      }
    } catch (e) {
      print("Has Role query failed: $e");
    }

    return {'role': 'MedRep', 'debugStatus': 'Failed all roles fetches', 'rawRoles': 'NONE', 'source': 'fallback'};
  }

  Map<String, dynamic> _parseRolesList(List rolesList) {
    String finalRole = "MedRep";
    int rolePriority = 0;

    for (var roleItem in rolesList) {
      final role = roleItem.toString().trim();
      final lower = role.toLowerCase();

      // Priority 10: Admin / System Manager
      if (lower == 'administrator' || lower == 'system manager') {
        if (rolePriority < 10) {
          finalRole = "Admin";
          rolePriority = 10;
        }
      } 
      // Priority 8: GM / Sales Master Manager
      else if (lower == 'sales master manager' || lower == 'gm') {
        if (rolePriority < 8) {
          finalRole = "GM";
          rolePriority = 8;
        }
      } 
      // Priority 7: NSM-1 / NSM1 / Sales Manager I
      else if (lower == 'nsm-1' || lower == 'nsm1' || lower == 'sales manager i') {
        if (rolePriority < 7) {
          finalRole = "NSM-1";
          rolePriority = 7;
        }
      } 
      // Priority 6: NSM-2 / NSM2 / Sales Manager II
      else if (lower == 'nsm-2' || lower == 'nsm2' || lower == 'sales manager ii') {
        if (rolePriority < 6) {
          finalRole = "NSM-2";
          rolePriority = 6;
        }
      } 
      // Priority 4: DSM / Sales Manager / Sales Manager I-A
      else if (lower == 'sales manager' || lower == 'dsm' || lower == 'sales manager i-a') {
        if (rolePriority < 4) {
          finalRole = "DSM";
          rolePriority = 4;
        }
      } 
      // Priority 2: MedRep / Sales User / Sales Representative
      else if (lower == 'sales user' || lower == 'sales representative' || lower == 'med rep' || lower == 'medrep') {
        if (rolePriority < 2) {
          finalRole = "MedRep";
          rolePriority = 2;
        }
      }
    }
    return {'role': finalRole, 'debugStatus': 'Success'};
  }

  Future<String> _fetchUserPermissions(String userEmail, {String? cookieHeader}) async {
    try {
      final cleanEmail = userEmail.trim().toLowerCase();
      final response = await _apiClient.dio.get(
        '/api/resource/User%20Permission',
        queryParameters: {
          'fields': '["allow","for_value"]',
          'filters': '[["user","=","$cleanEmail"]]',
          'limit_page_length': 500,
        },
        options: Options(
          headers: {
            if (cookieHeader != null) 'Cookie': cookieHeader,
          },
        ),
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

  Future<void> _saveAuthData(String email, String role, String permissions, String fullName, String gender) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString("User_Email", email);
    await prefs.setString("User_Role", role);
    await prefs.setString("User_Permissions_Map", permissions);
    await prefs.setString("Full_Name", fullName);
    await prefs.setString("User_Gender", gender);
  }

  Future<String?> getLoggedRole() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString("User_Role");
  }
}
