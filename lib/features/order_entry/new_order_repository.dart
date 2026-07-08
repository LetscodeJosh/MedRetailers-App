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

  /// Fetches all address-tab fields for a selected customer.
  ///
  /// ERPNext v15 field mapping:
  ///   customer          → the customer name (already known, passed in)
  ///   customer_address  → Customer.customer_primary_address (link to Address doctype)
  ///   contact_person    → Customer.customer_primary_contact (link to Contact doctype)
  ///   contact_mobile    → resolved from the linked Contact document
  ///   territory         → Customer.territory
  ///   address_display   → rendered HTML block from the linked Address document
  ///
  /// The method fills each field using the exact ERPNext v15 doctype fields,
  /// with fallback lookups so the address tab always populates.
  Future<Map<String, dynamic>> fetchCustomerDetails(String customerId) async {
    try {
      final encodedId = Uri.encodeComponent(customerId);
      print("Fetching customer details for: $customerId (encoded: $encodedId)");

      // ─── Step 1: Fetch Customer doctype ────────────────────────────────────
      // Request ALL relevant ERPNext v15 fields in one call to avoid extra
      // round-trips.  The fields we care about for the address tab are:
      //   territory, customer_primary_address, customer_primary_contact
      final custResponse = await _apiClient.get(
        '/api/resource/Customer/$encodedId',
        queryParameters: {
          'fields': '['
              '"territory",'
              '"customer_primary_address",'
              '"customer_primary_contact"'
              ']',
        },
      );

      // ── Parsed Customer fields ─────────────────────────────────────────────
      // These are the raw ERPNext v15 field names we read from the Customer doc
      String territory         = "All Territories";   // ERPNext: territory
      String customerAddress   = "";                  // ERPNext: customer_address (Address link)
      String contactPersonLink = "";                  // ERPNext: contact_person  (Contact link)

      if (custResponse.statusCode == 200 && custResponse.data['data'] != null) {
        final custData = custResponse.data['data'];

        // territory → straight read
        territory = custData['territory']?.toString().isNotEmpty == true
            ? custData['territory'].toString()
            : "All Territories";

        // customer_address → stored as customer_primary_address in the Customer doc
        customerAddress = custData['customer_primary_address']?.toString() ?? "";

        // contact_person → stored as customer_primary_contact in the Customer doc
        contactPersonLink = custData['customer_primary_contact']?.toString() ?? "";
      }

      // ─── Step 2: Resolve address_display from the linked Address doc ───────
      // ERPNext v15 stores the formatted address block in Address.address_display
      // (an HTML field).  We strip the HTML tags so the Flutter UI gets clean text.
      String addressDisplay = "";  // ERPNext: address_display

      if (customerAddress.isNotEmpty) {
        // Preferred path: fetch the named Address document directly
        try {
          final addrResponse = await _apiClient.get(
            '/api/resource/Address/${Uri.encodeComponent(customerAddress)}',
            queryParameters: {
              'fields': '["name","address_display","territory"]',
            },
          );
          if (addrResponse.statusCode == 200 && addrResponse.data['data'] != null) {
            final addrData = addrResponse.data['data'];

            final rawHtml = addrData['address_display']?.toString() ?? "";
            addressDisplay = _stripHtml(rawHtml);

            // Some Address docs carry their own territory — prefer it if set
            if (addrData['territory'] != null &&
                addrData['territory'].toString().isNotEmpty) {
              territory = addrData['territory'].toString();
            }
          }
        } catch (e) {
          print("Error fetching Address doc ($customerAddress): $e");
        }
      }

      // Fallback: query the Address list linked to this customer via Dynamic Link
      if (addressDisplay.isEmpty) {
        try {
          final addrListResp = await _apiClient.get(
            '/api/resource/Address',
            queryParameters: {
              'filters': jsonEncode([
                ["Dynamic Link", "link_doctype", "=", "Customer"],
                ["Dynamic Link", "link_name", "=", customerId],
              ]),
              'fields': '["name","address_display","territory"]',
              'limit_page_length': 1,
            },
          );
          if (addrListResp.statusCode == 200) {
            final list = addrListResp.data['data'] as List? ?? [];
            if (list.isNotEmpty) {
              final addrData = list[0] as Map<String, dynamic>;

              // Update customerAddress name if we found one via the Dynamic Link
              if (customerAddress.isEmpty) {
                customerAddress = addrData['name']?.toString() ?? "";
              }

              final rawHtml = addrData['address_display']?.toString() ?? "";
              addressDisplay = _stripHtml(rawHtml);

              if (addrData['territory'] != null &&
                  addrData['territory'].toString().isNotEmpty) {
                territory = addrData['territory'].toString();
              }
            }
          }
        } catch (e) {
          print("Error fetching Address list for customer: $e");
        }
      }

      // ─── Step 3: Resolve contact_person name & contact_mobile ─────────────
      // ERPNext v15:
      //   contact_person  → Contact.full_name  (or first_name + last_name)
      //   contact_mobile  → Contact.mobile_no
      String contactPerson = "";  // ERPNext: contact_person
      String contactMobile = "";  // ERPNext: contact_mobile

      if (contactPersonLink.isNotEmpty) {
        // Preferred path: fetch the named Contact document directly
        try {
          final contactResp = await _apiClient.get(
            '/api/resource/Contact/${Uri.encodeComponent(contactPersonLink)}',
            queryParameters: {
              'fields': '["name","full_name","first_name","last_name","mobile_no","phone"]',
            },
          );
          if (contactResp.statusCode == 200 && contactResp.data['data'] != null) {
            final contactData = contactResp.data['data'];

            // Resolve display name: prefer full_name, fall back to first+last
            final fullName  = contactData['full_name']?.toString() ?? "";
            final firstName = contactData['first_name']?.toString() ?? "";
            final lastName  = contactData['last_name']?.toString() ?? "";

            contactPerson = fullName.isNotEmpty
                ? fullName
                : "$firstName $lastName".trim();

            // Resolve mobile: prefer mobile_no, fall back to phone
            contactMobile = contactData['mobile_no']?.toString().isNotEmpty == true
                ? contactData['mobile_no'].toString()
                : contactData['phone']?.toString() ?? "";
          }
        } catch (e) {
          print("Error fetching Contact doc ($contactPersonLink): $e");
        }
      }

      // Fallback: query the Contact list linked to this customer via Dynamic Link
      if (contactPerson.isEmpty || contactMobile.isEmpty) {
        try {
          final contactListResp = await _apiClient.get(
            '/api/resource/Contact',
            queryParameters: {
              'filters': jsonEncode([
                ["Dynamic Link", "link_doctype", "=", "Customer"],
                ["Dynamic Link", "link_name", "=", customerId],
              ]),
              'fields': '["name","full_name","first_name","last_name","mobile_no","phone"]',
              'limit_page_length': 1,
            },
          );
          if (contactListResp.statusCode == 200) {
            final list = contactListResp.data['data'] as List? ?? [];
            if (list.isNotEmpty) {
              final contactData = list[0] as Map<String, dynamic>;

              // Update contactPersonLink if it was empty
              if (contactPersonLink.isEmpty) {
                contactPersonLink = contactData['name']?.toString() ?? "";
              }

              if (contactPerson.isEmpty) {
                final fullName  = contactData['full_name']?.toString() ?? "";
                final firstName = contactData['first_name']?.toString() ?? "";
                final lastName  = contactData['last_name']?.toString() ?? "";
                contactPerson = fullName.isNotEmpty
                    ? fullName
                    : "$firstName $lastName".trim();
              }

              if (contactMobile.isEmpty) {
                contactMobile = contactData['mobile_no']?.toString().isNotEmpty == true
                    ? contactData['mobile_no'].toString()
                    : contactData['phone']?.toString() ?? "";
              }
            }
          }
        } catch (e) {
          print("Error fetching Contact list for customer: $e");
        }
      }

      print("Customer details resolved → "
            "customer_address=$customerAddress | "
            "address_display=$addressDisplay | "
            "contact_person=$contactPerson | "
            "contact_mobile=$contactMobile | "
            "territory=$territory");

      // ─── Return using the EXACT ERPNext v15 field names ───────────────────
      // Key names match the Sales Order doctype fields 1-to-1 so the UI and
      // submitOrder() can forward them without any further renaming.
      return {
        'success': true,
        // ERPNext field: customer_address
        'customer_address': customerAddress.isNotEmpty ? customerAddress : "",
        // ERPNext field: contact_person  (the Contact document name/link)
        'contact_person': contactPersonLink.isNotEmpty ? contactPersonLink : "",
        // ERPNext field: contact_mobile
        'contact_mobile': contactMobile.isNotEmpty ? contactMobile : "No Phone Listed",
        // ERPNext field: territory
        'territory': territory,
        // ERPNext field: address_display  (plain text, HTML stripped)
        'address_display': addressDisplay.isNotEmpty ? addressDisplay : "No Address Listed",
        // Human-readable display name for the contact (used by the UI label only)
        'contact_person_display': contactPerson.isNotEmpty ? contactPerson : customerId,
      };
    } catch (e) {
      print("Error in fetchCustomerDetails: $e");
      return {
        'success': false,
        'customer_address': "",
        'contact_person': "",
        'contact_mobile': "No Phone Listed",
        'territory': "All Territories",
        'address_display': "No Address Listed",
        'contact_person_display': customerId,
      };
    }
  }

  // ─── Payment Terms ────────────────────────────────────────────────────────

  Future<List<String>> fetchPaymentTermsTemplates() async {
    try {
      final response = await _apiClient.get(
        '/api/resource/Payment Terms Template',
        queryParameters: {'limit_page_length': 99},
      );
      if (response.statusCode == 200 && response.data['data'] != null) {
        return (response.data['data'] as List)
            .map((e) => e['name'] as String)
            .toList();
      }
    } catch (e) {
      print("Error fetching payment terms templates: $e");
    }
    return [];
  }

  Future<List<Map<String, dynamic>>> fetchPaymentTermsDetails(String templateName) async {
    try {
      final response = await _apiClient.get(
        '/api/resource/Payment Terms Template/$templateName',
      );
      if (response.statusCode == 200 && response.data['data'] != null) {
        final terms = response.data['data']['terms'] as List? ?? [];
        return terms.map((t) => Map<String, dynamic>.from(t)).toList();
      }
    } catch (e) {
      print("Error fetching template details: $e");
    }
    return [];
  }

  // ─── Items ────────────────────────────────────────────────────────────────

  Future<Map<String, dynamic>> fetchItems() async {
    try {
      final filters = await _buildItemFilters();
      final response = await _apiClient.get(
        '/api/resource/Item Price',
        queryParameters: {
          'fields': '["item_code","item_name","uom","price_list_rate","valid_from","valid_upto"]',
          'limit_page_length': 500,
          'order_by': 'modified desc',
          ...filters,
        },
      );
      if (response.statusCode == 200) {
        final data = response.data['data'] as List;
        final today = DateFormat('yyyy-MM-dd').format(DateTime.now());

        List<Map<String, dynamic>> validItems = [];
        for (var item in data) {
          String validFrom = item['valid_from']?.toString() ?? "null";
          String validUpto = item['valid_upto']?.toString() ?? "null";

          bool isValid = true;
          if (validFrom != "null" && validFrom.isNotEmpty) {
            if (today.compareTo(validFrom) < 0) isValid = false;
          }
          if (validUpto != "null" && validUpto.isNotEmpty) {
            if (today.compareTo(validUpto) > 0) isValid = false;
          }

          if (isValid) validItems.add(item);
        }
        return {'success': true, 'items': validItems};
      }
    } on DioException catch (e) {
      print("API Error Fetching Items: ${e.response?.data}");

      // Fallback: If 403 Forbidden on Item Price, fall back to Item doctype
      if (e.response?.statusCode == 403) {
        try {
          print("Attempting fallback to Item doctype...");
          final fallbackResponse = await _apiClient.get(
            '/api/resource/Item',
            queryParameters: {
              'fields': '["name","item_code","item_name","stock_uom","standard_rate","valuation_rate"]',
              'filters': '[[\"disabled\",\"=\",0]]',
              'limit_page_length': 500,
            },
          );
          if (fallbackResponse.statusCode == 200) {
            final data = fallbackResponse.data['data'] as List;
            List<Map<String, dynamic>> fallbackItems = [];
            for (var item in data) {
              final code = item['item_code'] ?? item['name'] ?? '';
              if (code.isNotEmpty) {
                final double rate =
                    (item['standard_rate'] ?? item['valuation_rate'] ?? 0.0)
                        .toDouble();
                fallbackItems.add({
                  'item_code': code,
                  'item_name': item['item_name'] ?? code,
                  'uom': item['stock_uom'] ?? 'Box',
                  'price_list_rate': rate,
                  'valid_from': '',
                  'valid_upto': '',
                });
              }
            }
            return {'success': true, 'items': fallbackItems};
          }
        } catch (fallbackErr) {
          print("Error during Item doctype fallback fetch: $fallbackErr");
        }
      }
    } catch (e) {
      print("Error fetching items: $e");
    }
    return {'success': false, 'items': []};
  }

  // ─── Submit / Edit Order ──────────────────────────────────────────────────

  Future<Map<String, dynamic>> submitOrder({
    required String customer,
    required String company,
    required String transactionDate,
    required String deliveryDate,
    String? paymentTerms,
    required List<OrderItem> items,
    bool isEdit = false,
    String? editingOrderId,
    String? fulfillmentStatus,
    // Address-tab fields — names match ERPNext v15 Sales Order fields exactly
    String? customerAddress,    // customer_address
    String? contactPerson,      // contact_person  (Contact doc name/link)
    String? contactMobile,      // contact_mobile
    String? territory,          // territory
    String? addressDisplay,     // address_display
  }) async {
    try {
      final today = DateFormat('yyyy-MM-dd').format(DateTime.now());

      String? cleanContactPerson = contactPerson;
      if (cleanContactPerson != null && 
          (cleanContactPerson.isEmpty || cleanContactPerson.toLowerCase() == "customer default")) {
        cleanContactPerson = null;
      }

      // Step 1: Build payload with basic Sales Order fields
      final payload = {
        'docstatus': 0,
        'customer': customer,
        'company': company,
        'transaction_date': transactionDate,
        'delivery_date': deliveryDate,
        'selling_price_list': 'Standard Selling',
        if (paymentTerms != null && paymentTerms.isNotEmpty)
          'payment_terms_template': paymentTerms,
        if (fulfillmentStatus != null)
          'fulfillment_status': fulfillmentStatus,
        if (customerAddress != null && customerAddress.isNotEmpty)
          'customer_address': customerAddress,
        if (cleanContactPerson != null && cleanContactPerson.isNotEmpty)
          'contact_person': cleanContactPerson,
        if (contactMobile != null && contactMobile.isNotEmpty)
          'contact_mobile': contactMobile,
        if (territory != null && territory.isNotEmpty)
          'territory': territory,
        'items': items.map((item) => {
          'item_code': item.itemCode,
          'qty': item.quantity,
          'rate': item.rate,
          'is_free_item': item.rate == 0.0 ? 1 : 0,
          if (item.rate == 0.0) 'price_list_rate': 0.0,
          if (item.rate == 0.0) 'amount': 0.0,
          'uom': item.uom,
          'warehouse': item.warehouse,
          'delivery_date': item.deliveryDate.isNotEmpty ? item.deliveryDate : today,
          if (item.notes.isNotEmpty) 'description': item.notes,
        }).toList(),
      };

      Response response;
      if (isEdit && editingOrderId != null) {
        response = await _apiClient.dio.put(
          '/api/resource/Sales%20Order/$editingOrderId',
          data: payload,
        );
      } else {
        response = await _apiClient.postJson('/api/resource/Sales%20Order', payload);
      }

      if (response.statusCode == 200) {
        final orderName = isEdit ? editingOrderId! : response.data['data']['name'];

        final prefs = await SharedPreferences.getInstance();
        final editApprovalStatus = prefs.getString("EDIT_APPROVAL_STATUS") ?? "";

        if (isEdit && editApprovalStatus.contains("For Approval by")) {
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

  // ─── Helpers ──────────────────────────────────────────────────────────────

  /// Strips HTML tags and converts <br> variants to newlines.
  /// Used to clean address_display before showing it in the Flutter UI.
  String _stripHtml(String html) {
    return html
        .replaceAll(RegExp(r'<br\s*/?>', caseSensitive: false), '\n')
        .replaceAll(RegExp(r'<[^>]*>'), '')
        .trim();
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
      if ((selectedCompany == null || selectedCompany.isEmpty) &&
          perms['Company'] != null &&
          (perms['Company'] as List).isNotEmpty) {
        andFilters.add(["company", "in", perms['Company']]);
      }
    }
    return {'filters': jsonEncode(andFilters)};
  }

  Future<Map<String, dynamic>> _buildItemFilters() async {
    final prefs = await SharedPreferences.getInstance();
    final role = prefs.getString("User_Role") ?? "MedRep";
    final permsString = prefs.getString("User_Permissions_Map") ?? "{}";
    List<dynamic> andFilters = [["price_list", "=", "Standard Selling"]];
    if (role.toLowerCase() != "admin") {
      try {
        final perms = jsonDecode(permsString);
        if (perms['Company'] != null) {
          List<dynamic> allowedCompanies = List<dynamic>.from(perms['Company']);
          if (allowedCompanies.isNotEmpty) {
            andFilters.add(["company", "in", allowedCompanies]);
          }
        }
      } catch (e) {
        print("Error parsing permissions for item filter: $e");
      }
    }
    return {'filters': jsonEncode(andFilters)};
  }
}