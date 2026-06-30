import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../core/api_client.dart';
import '../../models/sales_order.dart';

class SalesOrderRepository {
  final ApiClient _apiClient = ApiClient();
  static const int pageLength = 20;

  Future<int> fetchTotalOrderCount(String? searchQuery) async {
    try {
      final queryParams = await _buildQueryParams(searchQuery: searchQuery);
      final response = await _apiClient.get(
        '/api/method/frappe.desk.reportview.get_count',
        queryParameters: queryParams,
      );

      if (response.statusCode == 200) {
        return response.data['message'] as int? ?? 0;
      }
    } catch (e) {
      print("Error fetching total order count: $e");
    }
    return 0;
  }

  Future<List<SalesOrder>> fetchSalesOrders({
    required int limitStart,
    String? searchQuery,
  }) async {
    try {
      final queryParams = await _buildQueryParams(
        limitStart: limitStart,
        pageLength: pageLength,
        searchQuery: searchQuery,
      );
      
      queryParams['fields'] = '["name","customer_name","status","workflow_state","delivery_date","territory","grand_total","fulfillment_status"]';
      queryParams['order_by'] = 'modified desc';

      final response = await _apiClient.get(
        '/api/method/frappe.desk.reportview.get',
        queryParameters: queryParams,
      );

      if (response.statusCode == 200) {
        final message = response.data['message'];
        if (message == null) return [];
        final keys = List<String>.from(message['keys']);
        final values = message['values'] as List;

        int idxName = keys.indexOf("name");
        int idxCust = keys.indexOf("customer_name");
        int idxStat = keys.indexOf("status");
        int idxWorkflow = keys.indexOf("workflow_state");
        int idxDate = keys.indexOf("delivery_date");
        int idxTerr = keys.indexOf("territory");
        int idxTotal = keys.indexOf("grand_total");
        int idxFulfill = keys.indexOf("fulfillment_status");

        List<SalesOrder> list = [];
        for (var row in values) {
          final rowList = row as List;
          
          final String id = idxName != -1 ? rowList[idxName]?.toString() ?? "N/A" : "N/A";
          final String customerName = idxCust != -1 ? rowList[idxCust]?.toString() ?? "Unknown Customer" : "Unknown Customer";
          final String status = idxStat != -1 ? rowList[idxStat]?.toString() ?? "Draft" : "Draft";
          final String approvalStatus = idxWorkflow != -1 ? rowList[idxWorkflow]?.toString() ?? status : status;
          final String date = idxDate != -1 ? rowList[idxDate]?.toString() ?? "N/A" : "N/A";
          final String territory = idxTerr != -1 ? rowList[idxTerr]?.toString() ?? "N/A" : "N/A";
          final double grandTotal = idxTotal != -1 ? (double.tryParse(rowList[idxTotal]?.toString() ?? "0") ?? 0.0) : 0.0;
          
          String ofsStatus = idxFulfill != -1 ? rowList[idxFulfill]?.toString() ?? "-" : "-";
          if (ofsStatus == "null" || ofsStatus.trim().isEmpty || ofsStatus == "None" || ofsStatus == "false") {
            ofsStatus = "-";
          }

          list.add(SalesOrder(
            id: id,
            customerName: customerName,
            ofsStatus: ofsStatus,
            approvalStatus: approvalStatus,
            date: date,
            territory: territory,
            grandTotal: grandTotal,
          ));
        }
        return list;
      }
    } catch (e) {
      print("Error fetching sales orders: $e");
    }
    return [];
  }

  Future<bool> applyWorkflowAction(String orderId, String action) async {
    try {
      final response = await _apiClient.postJson(
        '/api/method/frappe.model.workflow.apply_workflow',
        {
          'doc': {
            'doctype': 'Sales Order',
            'name': orderId,
          },
          'action': action,
        },
      );
      return response.statusCode == 200;
    } catch (e) {
      print("Error applying workflow: $e");
    }
    return false;
  }

  Future<Map<String, dynamic>> _buildQueryParams({
    int? limitStart,
    int? pageLength,
    String? searchQuery,
  }) async {
    final prefs = await SharedPreferences.getInstance();
    final role = prefs.getString("User_Role") ?? "MedRep";
    final permsString = prefs.getString("User_Permissions_Map") ?? "{}";
    
    Map<String, dynamic> params = {
      'doctype': 'Sales Order',
    };

    if (limitStart != null) {
      params['start'] = limitStart;
    }
    if (pageLength != null) {
      params['page_length'] = pageLength;
    }

    // Base permission filters
    List<dynamic> finalFilters = [];
    if (role.toLowerCase() != "admin") {
      try {
        final perms = jsonDecode(permsString);
        if (perms['Territory'] != null && (perms['Territory'] as List).isNotEmpty) {
          finalFilters.add(["Sales Order", "territory", "in", perms['Territory']]);
        }
        if (perms['Company'] != null && (perms['Company'] as List).isNotEmpty) {
          finalFilters.add(["Sales Order", "company", "in", perms['Company']]);
        }
      } catch (e) {
        print("Error parsing permissions: $e");
      }
    }

    if (searchQuery != null && searchQuery.trim().isNotEmpty) {
      final query = searchQuery.trim();
      if (query.startsWith("date:") ||
          query.startsWith("range:") ||
          query.startsWith("workflow_state:") ||
          query.startsWith("fulfillment_status:") ||
          query.startsWith("status:")) {
        
        final searchFilters = _buildSearchFilters(query);
        finalFilters.addAll(searchFilters);
        params['filters'] = jsonEncode(finalFilters);
      } else {
        // Global search: Apply permissions to filters, search query to or_filters
        if (finalFilters.isNotEmpty) {
          params['filters'] = jsonEncode(finalFilters);
        }
        
        List<dynamic> orFilters = [
          ["Sales Order", "customer_name", "like", "%$query%"],
          ["Sales Order", "name", "like", "%$query%"],
          ["Sales Order", "territory", "like", "%$query%"],
          ["Sales Order", "workflow_state", "like", "%$query%"],
          ["Sales Order", "fulfillment_status", "like", "%$query%"],
          ["Sales Order", "status", "like", "%$query%"],
        ];
        params['or_filters'] = jsonEncode(orFilters);
      }
    } else {
      if (finalFilters.isNotEmpty) {
        params['filters'] = jsonEncode(finalFilters);
      }
    }

    return params;
  }

  List<dynamic> _buildSearchFilters(String query) {
    List<dynamic> filters = [];
    if (query.startsWith("date:")) {
      filters.add(["Sales Order", "delivery_date", "=", query.substring(5)]);
    } else if (query.startsWith("range:")) {
      final parts = query.substring(6).split(",");
      if (parts.length == 2) {
        filters.add(["Sales Order", "delivery_date", "between", [parts[0], parts[1]]]);
      }
    } else if (query.startsWith("workflow_state:")) {
      filters.add(["Sales Order", "workflow_state", "=", query.substring(15)]);
    } else if (query.startsWith("fulfillment_status:")) {
      final statusVal = query.substring(19);
      if (statusVal == "-") {
        filters.add(["Sales Order", "fulfillment_status", "is", "not set"]);
      } else if (statusVal.isNotEmpty) {
        filters.add(["Sales Order", "fulfillment_status", "=", statusVal]);
      }
    } else if (query.startsWith("status:")) {
      filters.add(["Sales Order", "status", "=", query.substring(7)]);
    }
    return filters;
  }
}
