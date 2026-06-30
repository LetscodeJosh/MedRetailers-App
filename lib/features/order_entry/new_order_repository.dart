import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:intl/intl.dart';
import '../../core/api_client.dart';
import '../../models/order_item.dart';

class NewOrderRepository {
  final ApiClient _apiClient = ApiClient();

  Future<List<String>> fetchCompanies() async {
    try {
      final filters = await _buildCompanyFilters();
      final response = await _apiClient.get(
        '/api/resource/Company',
        queryParameters: {
          'fields': '["name","company_name"]',
          'limit_page_length': 999,
          ...filters,
        },
      );

      if (response.statusCode == 200) {
        return (response.data['data'] as List)
            .map((e) => e['name'] as String)
            .toList();
      }
    } catch (e) {
      print("Error fetching companies: $e");
    }
    return [];
  }

  Future<Map<String, String>> fetchFilteredCustomers(String? selectedCompany) async {
    try {
      final filters = await _buildCustomerFilters(selectedCompany);
      final response = await _apiClient.get(
        '/api/resource/Customer',
        queryParameters: {
          'fields': '["name","customer_name"]',
          'limit_page_length': 999,
          ...filters,
        },
      );

      if (response.statusCode == 200) {
        final data = response.data['data'] as List;
        Map<String, String> customers = {};
        for (var item in data) {
          String id = item['name'];
          String name = item['customer_name'] ?? id;
          if (name.toLowerCase() == "null" || name.isEmpty) name = id;
          customers[name] = id;
        }
        return customers;
      }
    } catch (e) {
      print("Error fetching customers: $e");
    }
    return {};
  }

  Future<Map<String, dynamic>> fetchItems() async {
    try {
      final filters = await _buildItemFilters();
      final response = await _apiClient.get(
        '/api/resource/Item%20Price',
        queryParameters: {
          'fields': '["item_code","item_name","uom","price_list_rate","valid_from","valid_upto"]',
          'limit_page_length': 500,
          ...filters,
        },
      );

      if (response.statusCode == 200) {
        final data = response.data['data'] as List;
        final today = DateFormat('yyyy-MM-dd').format(DateTime.now());
        
        List<Map<String, dynamic>> validItems = [];
        for (var item in data) {
          String validFrom = item['valid_from'] ?? "";
          String validUpto = item['valid_upto'] ?? "";
          
          bool isValid = true;
          if (validFrom.isNotEmpty && validFrom != "null") {
            if (today.compareTo(validFrom) < 0) isValid = false;
          }
          if (validUpto.isNotEmpty && validUpto != "null") {
            if (today.compareTo(validUpto) > 0) isValid = false;
          }
          
          if (isValid) {
            validItems.add(item);
          }
        }
        return {'success': true, 'items': validItems};
      }
    } catch (e) {
      print("Error fetching items: $e");
    }
    return {'success': false, 'items': []};
  }

  Future<Map<String, dynamic>> submitOrder({
    required String customer,
    required String company,
    required String transactionDate,
    required String deliveryDate,
    String? paymentTerms,
    required List<OrderItem> items,
    bool isEdit = false,
    String? editingOrderId,
  }) async {
    try {
      final today = DateFormat('yyyy-MM-dd').format(DateTime.now());
      
      // Step 1: Create Draft or Edit
      final payload = {
        'docstatus': 0,
        'customer': customer,
        'company': company,
        'transaction_date': transactionDate,
        'delivery_date': deliveryDate,
        'selling_price_list': 'Standard Selling',
        if (paymentTerms != null && paymentTerms.isNotEmpty)
          'payment_terms_template': paymentTerms,
        'items': items.map((item) => {
          'item_code': item.itemCode,
          'qty': item.quantity,
          'rate': item.rate,
          'uom': item.uom,
          'warehouse': item.warehouse,
          'delivery_date': item.deliveryDate.isNotEmpty ? item.deliveryDate : today,
          if (item.notes.isNotEmpty) 'description': item.notes,
        }).toList(),
      };

      Response response;
      if (isEdit && editingOrderId != null) {
        response = await _apiClient.dio.put('/api/resource/Sales%20Order/$editingOrderId', data: payload);
      } else {
        response = await _apiClient.postJson('/api/resource/Sales%20Order', payload);
      }
      
      if (response.statusCode == 200) {
        final orderName = isEdit ? editingOrderId! : response.data['data']['name'];
        
        final prefs = await SharedPreferences.getInstance();
        final editApprovalStatus = prefs.getString("EDIT_APPROVAL_STATUS") ?? "";
        
        if (isEdit && editApprovalStatus.contains("For Approval by")) {
          // Skip workflow submission if the order is already in a non-terminal approval state
          return {'success': true, 'name': orderName};
        }
        
        // Step 2: Apply Workflow
        final workflowResponse = await _apiClient.postJson(
          '/api/method/frappe.model.workflow.apply_workflow',
          {
            'doc': jsonEncode({'doctype': 'Sales Order', 'name': orderName}),
            'action': 'Submit for Approval',
          },
        );

        if (workflowResponse.statusCode == 200) {
          return {'success': true, 'name': orderName};
        } else {
          return {'success': false, 'message': 'Workflow failed', 'name': orderName};
        }
      }
    } on DioException catch (e) {
      return {'success': false, 'message': _decodeErpNextError(e.response?.data)};
    }
    return {'success': false, 'message': 'Unknown error'};
  }

  String _decodeErpNextError(dynamic data) {
    if (data == null) return "Unknown Server Error";
    try {
      if (data['_server_messages'] != null) {
        final messages = jsonDecode(data['_server_messages']) as List;
        if (messages.isNotEmpty) {
          final msg = messages[0]['message'] as String;
          return msg.replaceAll(RegExp(r'<[^>]*>'), '');
        }
      }
      if (data['exc_type'] != null) return "ERPNext Error: ${data['exc_type']}";
    } catch (_) {}
    return "Error: $data";
  }

  Future<Map<String, dynamic>> _buildCompanyFilters() async {
    final prefs = await SharedPreferences.getInstance();
    final role = prefs.getString("User_Role") ?? "MedRep";
    final permsString = prefs.getString("User_Permissions_Map") ?? "{}";
    final perms = jsonDecode(permsString);

    if (role.toLowerCase() == "admin") return {};

    List<dynamic> andFilters = [["company_name", "is", "set"]];
    if (perms['Company'] != null && (perms['Company'] as List).isNotEmpty) {
      andFilters.add(["name", "in", perms['Company']]);
    }
    return {'filters': jsonEncode(andFilters)};
  }

  Future<Map<String, dynamic>> _buildCustomerFilters(String? selectedCompany) async {
    final prefs = await SharedPreferences.getInstance();
    final role = prefs.getString("User_Role") ?? "MedRep";
    final permsString = prefs.getString("User_Permissions_Map") ?? "{}";
    final perms = jsonDecode(permsString);

    List<dynamic> andFilters = [["customer_name", "is", "set"]];
    if (selectedCompany != null && selectedCompany.isNotEmpty) {
      andFilters.add(["company", "=", selectedCompany]);
    }

    if (role.toLowerCase() != "admin") {
      if (perms['Territory'] != null && (perms['Territory'] as List).isNotEmpty) {
        andFilters.add(["territory", "in", perms['Territory']]);
      }
      if ((selectedCompany == null || selectedCompany.isEmpty) && perms['Company'] != null && (perms['Company'] as List).isNotEmpty) {
        andFilters.add(["company", "in", perms['Company']]);
      }
    }
    return {'filters': jsonEncode(andFilters)};
  }

  Future<Map<String, dynamic>> _buildItemFilters() async {
    final prefs = await SharedPreferences.getInstance();
    final role = prefs.getString("User_Role") ?? "MedRep";
    final permsString = prefs.getString("User_Permissions_Map") ?? "{}";
    final perms = jsonDecode(permsString);

    List<dynamic> andFilters = [["price_list", "=", "Standard Selling"]];
    if (role.toLowerCase() != "admin") {
      if (perms['Company'] != null && (perms['Company'] as List).isNotEmpty) {
        andFilters.add(["company", "in", perms['Company']]);
      }
    }
    return {'filters': jsonEncode(andFilters)};
  }
}
