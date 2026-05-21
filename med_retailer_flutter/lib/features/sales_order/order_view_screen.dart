import 'dart:convert';
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:dio/dio.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:intl/intl.dart';
import '../../core/api_client.dart';
import '../../core/app_theme.dart';
import '../order_entry/order_entry_provider.dart';
import '../../models/order_item.dart';

class OrderViewScreen extends StatefulWidget {
  final String orderId;
  final bool triggerEditMode;
  
  const OrderViewScreen({
    super.key, 
    required this.orderId, 
    this.triggerEditMode = false,
  });

  @override
  State<OrderViewScreen> createState() => _OrderViewScreenState();
}

class _OrderViewScreenState extends State<OrderViewScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final ApiClient _apiClient = ApiClient();
  Map<String, dynamic>? _orderData;
  bool _isLoading = false;
  bool _isPrinting = false;
  bool _isWorkflowProcessing = false;
  
  // Robust Fallbacks resolved values
  String _contactPerson = "Customer Default";
  String _contactDisplay = "N/A";
  String _contactMobile = "Not Provided";
  String _ltoNo = "Loading...";
  String _businessPermit = "Loading...";

  // Timeline / Activity Log State
  List<TimelineEntry> _timelineEntries = [];
  bool _isLoadingTimeline = false;
  final Map<String, String> _globalUserMap = {};
  String _loggedInUserRole = "MedRep";
  String _loggedInUserEmail = "";
  Timer? _liveTimer;

  // Constants matching Java Workflow
  static const String stateDraft = "Draft";
  static const String stateForDsm = "For Approval by DSM";
  static const String stateForGm = "For Approval by GM";
  static const String stateForNsm1 = "For Approval by NSM-1";
  static const String stateForNsm2 = "For Approval by NSM-2";
  static const String stateRejectedDsm = "Rejected by DSM";
  static const String stateRejectedGm = "Rejected by GM";
  static const String stateRejectedNsm1 = "Rejected by NSM-1";
  static const String stateRejectedNsm2 = "Rejected by NSM-2";
  static const String stateSoApproved = "SO Approved";

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 4, vsync: this);
    _loadInitialSettings();
    
    // Live timer ticking every 30 seconds to update timeline elapsed values reactively
    _liveTimer = Timer.periodic(const Duration(seconds: 30), (timer) {
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    _tabController.dispose();
    _liveTimer?.cancel();
    super.dispose();
  }

  Future<void> _loadInitialSettings() async {
    final prefs = await SharedPreferences.getInstance();
    if (mounted) {
      setState(() {
        _loggedInUserRole = prefs.getString("User_Role") ?? "MedRep";
        _loggedInUserEmail = prefs.getString("User_Email") ?? "";
      });
    }
    await _fetchOrderData();
  }

  Future<void> _fetchOrderData() async {
    if (mounted) setState(() => _isLoading = true);
    try {
      final response = await _apiClient.get('/api/resource/Sales%20Order/${widget.orderId}');
      if (response.statusCode == 200 && mounted) {
        final data = response.data['data'] as Map<String, dynamic>;
        
        // Apply fallbacks immediately
        _resolveContactFallbacks(data);
        
        setState(() {
          _orderData = data;
        });

        // Trigger dynamic Customer License info if missing in Sales Order
        final String customerId = data['customer'] ?? "";
        final String lto = data['lto_no']?.toString() ?? "";
        final String permit = data['business_permit']?.toString() ?? "";
        if (lto.isEmpty || permit.isEmpty) {
          _fetchCustomerLicenseInfo(customerId);
        } else {
          setState(() {
            _ltoNo = lto;
            _businessPermit = permit;
          });
        }

        // Fetch Timeline / Audit Trail Log
        _fetchTimeline();

        // Process automatic redirect if triggerEditMode is set from search list
        if (widget.triggerEditMode) {
          _triggerEditMode();
        }
      }
    } catch (e) {
      print("Error fetching order: $e");
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  // =========================================================================
  // ROBUST FALLBACK ENGINE FOR MISSING CONTACT FIELDS (Matching Java exactly)
  // =========================================================================
  void _resolveContactFallbacks(Map<String, dynamic> data) {
    final String customerName = data['customer_name']?.toString() ?? data['customer']?.toString() ?? "N/A";
    final String custAddressName = data['customer_address']?.toString() ?? "N/A";
    final String rawAddressStr = (data['address_display']?.toString() ?? "").replaceAll(RegExp(r'<[^>]*>'), ' ').trim();

    // 1. Contact Person Fallback
    String rawContact = data['contact_person']?.toString() ?? "";
    String extractedContactPerson = rawContact.isEmpty ? "" : _cleanContactPerson(rawContact);

    if (extractedContactPerson.isEmpty) {
      if (custAddressName.toLowerCase().contains("c/o")) {
        int idx = custAddressName.toLowerCase().indexOf("c/o") + 3;
        extractedContactPerson = custAddressName.substring(idx).replaceAll("-Billing", "").replaceAll("-Shipping", "").trim();
      } else {
        extractedContactPerson = customerName;
      }
    }
    _contactPerson = extractedContactPerson.isEmpty ? "Customer Default" : extractedContactPerson;

    // 2. Contact Details Display Fallback
    String extractedContactDisplay = (data['contact_display']?.toString() ?? "").replaceAll(RegExp(r'<[^>]*>'), ' ').trim();
    if (extractedContactDisplay.isEmpty || extractedContactDisplay.toLowerCase() == "null") {
      extractedContactDisplay = data['contact_email']?.toString() ?? "";
    }
    if (extractedContactDisplay.isEmpty || extractedContactDisplay.toLowerCase() == "null") {
      extractedContactDisplay = _contactPerson;
    }
    _contactDisplay = extractedContactDisplay;

    // 3. Mobile Number Fallback
    String extractedMobile = data['contact_mobile']?.toString() ?? "";
    if (extractedMobile.isEmpty) extractedMobile = data['contact_phone']?.toString() ?? "";
    if (extractedMobile.isEmpty) extractedMobile = data['mobile_no']?.toString() ?? "";
    if (extractedMobile.isEmpty) extractedMobile = data['phone']?.toString() ?? "";

    if (extractedMobile.isEmpty && rawAddressStr.contains("Phone:")) {
      try {
        final afterPhone = rawAddressStr.substring(rawAddressStr.indexOf("Phone:") + 6).trim();
        extractedMobile = afterPhone.split(RegExp(r'\s+'))[0];
      } catch (_) {}
    }
    if (extractedMobile.isEmpty && rawAddressStr.contains("Mobile:")) {
      try {
        final afterMobile = rawAddressStr.substring(rawAddressStr.indexOf("Mobile:") + 7).trim();
        extractedMobile = afterMobile.split(RegExp(r'\s+'))[0];
      } catch (_) {}
    }
    _contactMobile = extractedMobile.isEmpty ? "Not Provided" : extractedMobile;
  }

  String _cleanContactPerson(String rawContact) {
    final cleaned = rawContact.trim();
    if (cleaned.isEmpty) return "";
    int mid = (cleaned.length / 2).floor();
    if (cleaned.length % 2 != 0 && cleaned[mid] == '-') {
      String left = cleaned.substring(0, mid);
      String right = cleaned.substring(mid + 1);
      if (left.toLowerCase() == right.toLowerCase()) {
        return left;
      }
    }
    return cleaned;
  }

  Future<void> _fetchCustomerLicenseInfo(String customerId) async {
    if (customerId.isEmpty) return;
    try {
      final response = await _apiClient.get('/api/resource/Customer/${Uri.encodeComponent(customerId)}');
      if (response.statusCode == 200 && mounted) {
        final custData = response.data['data'] as Map<String, dynamic>;
        setState(() {
          _ltoNo = custData['lto_no']?.toString() ?? "N/A";
          _businessPermit = custData['business_permit']?.toString() ?? "N/A";
          if (_ltoNo.trim().isEmpty) _ltoNo = "N/A";
          if (_businessPermit.trim().isEmpty) _businessPermit = "N/A";
        });
      }
    } catch (e) {
      print("Error fetching customer licenses: $e");
    }
  }

  // =========================================================================
  // DYNAMIC TIMELINE & AUDIT TRAIL LOG FETCHING (Matching Java getdoc)
  // =========================================================================
  Future<void> _fetchTimeline() async {
    if (mounted) setState(() => _isLoadingTimeline = true);
    
    // First, fetch the comprehensive list of system users to ensure full-name mapping parity
    if (_globalUserMap.isEmpty) {
      try {
        final userResponse = await _apiClient.get('/api/resource/User?fields=["name","full_name"]&limit_page_length=1000');
        if (userResponse.statusCode == 200) {
          final uList = userResponse.data['data'] as List? ?? [];
          for (var u in uList) {
            final email = u['name']?.toString().toLowerCase();
            final fullName = u['full_name']?.toString();
            if (email != null && fullName != null) {
              _globalUserMap[email] = fullName;
            }
          }
        }
      } catch (e) {
        print("Error pre-fetching user list: $e");
      }
    }

    try {
      final response = await _apiClient.postJson(
        '/api/method/frappe.desk.form.load.getdoc',
        {
          'doctype': 'Sales Order',
          'name': widget.orderId,
        },
      );

      if (response.statusCode == 200 && mounted) {
        final root = response.data['message'] as Map<String, dynamic>? ?? response.data as Map<String, dynamic>;
        final docinfo = root['docinfo'] as Map<String, dynamic>?;
        final mergedUserInfo = <String, dynamic>{};

        final rootUsers = root['user_info'] as Map<String, dynamic>?;
        if (rootUsers != null) mergedUserInfo.addAll(rootUsers);

        if (docinfo != null) {
          final docUsers = docinfo['users'] as Map<String, dynamic>?;
          if (docUsers != null) mergedUserInfo.addAll(docUsers);

          List<TimelineEntry> entries = [];

          // 1. Comments
          final comments = docinfo['comments'] as List? ?? [];
          for (var c in comments) {
            final type = c['comment_type']?.toString() ?? "Comment";
            entries.add(TimelineEntry(
              owner: c['owner']?.toString() ?? "",
              creation: c['creation']?.toString() ?? "",
              content: c['content']?.toString() ?? "",
              type: type == "Comment" ? "comment" : "system_comment",
            ));
          }

          // 2. Info logs
          final infoLogs = docinfo['info_logs'] as List? ?? [];
          for (var il in infoLogs) {
            entries.add(TimelineEntry(
              owner: il['owner']?.toString() ?? "",
              creation: il['creation']?.toString() ?? "",
              content: il['content']?.toString() ?? "",
              type: "info_log",
            ));
          }

          // 3. Communications
          final communications = docinfo['communications'] as List? ?? [];
          for (var comm in communications) {
            entries.add(TimelineEntry(
              owner: comm['sender']?.toString() ?? "",
              creation: comm['creation']?.toString() ?? "",
              content: comm['content']?.toString() ?? "",
              type: "communication",
            ));
          }

          // 4. Assignments
          final assignments = docinfo['assignments'] as List? ?? [];
          for (var a in assignments) {
            entries.add(TimelineEntry(
              owner: a['owner']?.toString() ?? "",
              creation: a['creation']?.toString() ?? "",
              content: "assigned this document to ${a['assign_to']}",
              type: "assignment",
            ));
          }

          // 5. Versions (Double-escaped JSON timeline changes parser)
          final versions = docinfo['versions'] as List? ?? [];
          for (var v in versions) {
            final dataObj = v['data'];
            String dataStr = "";
            if (dataObj is String) {
              dataStr = dataObj;
            } else if (dataObj != null) {
              dataStr = jsonEncode(dataObj);
            }

            final parsedMessages = _getVersionMessages(dataStr);
            if (parsedMessages.isEmpty) {
              entries.add(TimelineEntry(
                owner: v['owner']?.toString() ?? "",
                creation: v['creation']?.toString() ?? "",
                content: "last edited this",
                type: "version_generic",
              ));
            } else {
              for (var msg in parsedMessages) {
                entries.add(TimelineEntry(
                  owner: v['owner']?.toString() ?? "",
                  creation: v['creation']?.toString() ?? "",
                  content: msg,
                  type: "version",
                ));
              }
            }
          }

          // Sort descending by creation
          entries.sort((a, b) => b.creation.compareTo(a.creation));

          // Resolve display representations
          for (var entry in entries) {
            final rawUser = _getUserFullName(entry.owner, mergedUserInfo, _loggedInUserEmail);
            final prefix = rawUser.toLowerCase() == "you" ? "You " : "$rawUser ";
            
            if (entry.type == "comment") {
              final text = entry.content.replaceAll(RegExp(r'<[^>]*>'), '');
              entry.displayHtml = "$rawUser commented\n\n$text";
            } else if (entry.type == "version" || entry.type == "version_generic") {
              entry.displayHtml = "$prefix${entry.content}";
            } else if (entry.type == "communication") {
              final text = entry.content.replaceAll(RegExp(r'<[^>]*>'), '');
              entry.displayHtml = "${prefix}sent: $text";
            } else {
              final text = entry.content.replaceAll(RegExp(r'<[^>]*>'), '');
              if (text.contains(rawUser) || rawUser.toLowerCase() == "you") {
                entry.displayHtml = text;
              } else {
                entry.displayHtml = "$prefix$text";
              }
            }
          }

          // Add final base creation log entry
          final creator = _orderData != null ? _orderData!['owner']?.toString() ?? "" : "";
          final creationTime = _orderData != null ? _orderData!['creation']?.toString() ?? "" : "";
          final creatorName = _getUserFullName(creator, mergedUserInfo, _loggedInUserEmail);
          
          entries.add(TimelineEntry(
            owner: creator,
            creation: creationTime,
            content: "created this",
            type: "creation",
            displayHtml: "$creatorName created this",
          ));

          setState(() {
            _timelineEntries = entries;
          });
        }
      }
    } catch (e) {
      print("Error parsing timeline logs: $e");
    } finally {
      if (mounted) setState(() => _isLoadingTimeline = false);
    }
  }

  List<String> _getVersionMessages(String dataStr) {
    List<String> messages = [];
    if (dataStr.trim().isEmpty || dataStr == "{}" || dataStr == "[]") {
      return messages;
    }

    try {
      var parsedData = dataStr.trim();
      if (parsedData.startsWith('"') && parsedData.endsWith('"')) {
        parsedData = parsedData.substring(1, parsedData.length - 1)
            .replaceAll('\\"', '"')
            .replaceAll('\\\\', '\\')
            .replaceAll('\\n', '\n');
      }
      parsedData = parsedData.trim();

      if (parsedData.startsWith('[')) {
        final decoded = jsonDecode(parsedData) as List;
        final arrayMsg = _parseVersionArray(decoded);
        if (arrayMsg != null) messages.add(arrayMsg);
      } else if (parsedData.startsWith('{')) {
        final decoded = jsonDecode(parsedData) as Map<String, dynamic>;
        messages.addAll(_parseVersionObject(decoded));
      }
    } catch (e) {
      print("Failed to parse version data: $e");
    }
    return messages;
  }

  String? _parseVersionArray(List changed) {
    try {
      List<String> changeParts = [];
      for (var c in changed) {
        if (c is! List || c.isEmpty) continue;
        String fieldName = c[0].toString();
        if (_isRedundantField(fieldName)) continue;

        String field = _prettifyFieldName(fieldName);
        String oldVal = _formatFieldValueStrict(fieldName, c.length > 1 ? c[1]?.toString() ?? "" : "");
        String newVal = _formatFieldValueStrict(fieldName, c.length > 2 ? c[2]?.toString() ?? "" : "");

        changeParts.add("$field from $oldVal to $newVal");
      }
      if (changeParts.isNotEmpty) {
        return "changed the value of ${changeParts.join(', ')}";
      }
    } catch (e) {
      print("Error parsing version array: $e");
    }
    return null;
  }

  List<String> _parseVersionObject(Map<String, dynamic> obj) {
    List<String> parts = [];
    try {
      if (obj.containsKey('changed')) {
        final changed = obj['changed'] as List?;
        if (changed != null) {
          List<String> changeParts = [];
          for (var c in changed) {
            if (c is! List || c.isEmpty) continue;
            String fieldName = c[0].toString();

            if (fieldName == "docstatus") {
              String docStatus = c.length > 2 ? c[2]?.toString() ?? "" : "";
              if (docStatus == "1") parts.add("submitted this document");
              else if (docStatus == "2") parts.add("cancelled this document");
              continue;
            }

            if (_isRedundantField(fieldName)) continue;

            String field = _prettifyFieldName(fieldName);
            String oldVal = _formatFieldValueStrict(fieldName, c.length > 1 ? c[1]?.toString() ?? "" : "");
            String newVal = _formatFieldValueStrict(fieldName, c.length > 2 ? c[2]?.toString() ?? "" : "");
            changeParts.add("$field from $oldVal to $newVal");
          }
          if (changeParts.isNotEmpty) {
            parts.add("changed the value of ${changeParts.join(', ')}");
          }
        }
      }

      if (obj.containsKey('row_changed')) {
        final rowChangedArray = obj['row_changed'] as List?;
        if (rowChangedArray != null && rowChangedArray.isNotEmpty) {
          List<String> rowChangeParts = [];
          for (var tuple in rowChangedArray) {
            if (tuple is! List || tuple.length < 4) continue;
            int rowIndex = int.tryParse(tuple[1]?.toString() ?? "0") ?? 0;
            String rowLabel = "#${rowIndex + 1}";

            final fieldChanges = tuple[3] as List?;
            if (fieldChanges == null) continue;

            for (var fc in fieldChanges) {
              if (fc is! List || fc.length < 3) continue;
              String fieldName = fc[0].toString();
              if (fieldName.isEmpty || _isRedundantField(fieldName)) continue;

              String fieldPrettified = _prettifyFieldName(fieldName);
              String oldVal = _formatFieldValueStrict(fieldName, fc[1]?.toString() ?? "");
              String newVal = _formatFieldValueStrict(fieldName, fc[2]?.toString() ?? "");
              rowChangeParts.add("$fieldPrettified from $oldVal to $newVal in row $rowLabel");
            }
          }
          if (rowChangeParts.isNotEmpty) {
            parts.add("changed the values for ${rowChangeParts.join(', ')}");
          }
        }
      }

      if (obj.containsKey('added')) {
        final added = obj['added'] as List?;
        if (added != null) {
          List<String> addedParts = [];
          for (var a in added) {
            if (a is List && a.isNotEmpty) {
              addedParts.add(_prettifyFieldName(a[0].toString()));
            }
          }
          if (addedParts.isNotEmpty) {
            parts.add("added rows for ${addedParts.join(', ')}");
          }
        }
      }

      if (obj.containsKey('removed')) {
        final removed = obj['removed'] as List?;
        if (removed != null) {
          List<String> removedParts = [];
          for (var r in removed) {
            if (r is List && r.isNotEmpty) {
              removedParts.add(_prettifyFieldName(r[0].toString()));
            }
          }
          if (removedParts.isNotEmpty) {
            parts.add("removed rows for ${removedParts.join(', ')}");
          }
        }
      }
    } catch (e) {
      print("Error parsing version object: $e");
    }
    return parts;
  }

  bool _isRedundantField(String field) {
    final f = field.toLowerCase();
    const redundant = [
      "total", "net_total", "base_net_total", "base_in_words", "in_words",
      "amount_eligible_for_commission", "rounded_total", "base_rounded_total",
      "taxes_and_charges_added", "taxes_and_charges_deducted",
      "base_taxes_and_charges_added", "base_taxes_and_charges_deducted",
      "modified", "modified_by"
    ];
    return redundant.contains(f);
  }

  String _formatFieldValueStrict(String field, String value) {
    if (value == "null" || value == "None" || value.trim().isEmpty) {
      return '""';
    }

    final f = field.toLowerCase();

    if (f.contains("rate") || f.contains("amount") || f.contains("price") || f.contains("total") || f.contains("value") || f.contains("incentives")) {
      try {
        final val = double.parse(value);
        if (val == 0) return "0";
        final formatter = NumberFormat.currency(symbol: "₱ ", decimalDigits: 2);
        return formatter.format(val);
      } catch (_) {
        return value;
      }
    }

    if (f.contains("qty") || f.contains("quantity") || f.contains("stock") || f.contains("commission") || f.contains("portion")) {
      try {
        final val = double.parse(value);
        if (val == val.toInt()) {
          return val.toInt().toString();
        } else {
          return val.toStringAsFixed(2);
        }
      } catch (_) {
        return value;
      }
    }

    if (RegExp(r'^\d{4}-\d{2}-\d{2}$').hasMatch(value)) {
      try {
        final parts = value.split("-");
        return "${parts[1]}-${parts[2]}-${parts[0]}"; 
      } catch (_) {}
    }

    return value;
  }

  String _prettifyFieldName(String field) {
    if (field.isEmpty) return field;

    if (field == "transaction_date") return "Date";
    if (field == "total_qty") return "Total Quantity";
    if (field == "base_grand_total") return "Grand Total (PHP)";
    if (field == "grand_total") return "Grand Total";
    if (field == "base_total") return "Total (PHP)";
    if (field == "delivery_date") return "Delivery Date";
    if (field == "status") return "Status";
    if (field == "fulfillment_status" || field == "order_fulfillment_status") return "Order Fulfillment Status";
    if (field == "workflow_state" || field == "approval_status") return "Approval Status";
    if (field == "items") return "Items";
    if (field == "payment_schedule") return "Payment Schedule";
    if (field == "sales_team") return "Sales Team";
    if (field == "po_no" || field == "customer_purchase_order") return "Customer's Purchase Order";
    if (field == "cost_center") return "Cost Center";
    if (field == "qty_warehouse") return "Qty (Warehouse)";
    if (field == "qty_company") return "Qty (Company)";
    if (field == "contact_person") return "Contact Person";
    if (field == "contact_display" || field == "contact") return "Contact";
    if (field == "contact_mobile" || field == "phone") return "Phone";
    if (field == "customer_address") return "Customer Address";
    if (field == "address_display" || field == "address") return "Address";
    if (field == "shipping_address_name") return "Shipping Address Name";
    if (field == "taxes_and_charges" || field == "sales_taxes_and_charges_template") return "Sales Taxes and Charges Template";
    if (field == "stock_uom_rate" || field == "rate_of_stock_uom") return "Rate of Stock UOM";
    if (field == "grant_commission") return "Grant Commission";
    if (field == "projected_qty") return "Projected Qty";
    if (field == "reserve_stock") return "Reserve Stock";

    final words = field.split("_");
    return words.map((w) {
      if (w.isEmpty) return "";
      return w[0].toUpperCase() + w.substring(1);
    }).join(" ");
  }

  String _getUserFullName(String ownerId, Map<String, dynamic> userInfoMap, String currentEmail) {
    if (ownerId.isEmpty) return "Unknown";
    if (ownerId.toLowerCase() == currentEmail.toLowerCase()) return "You";

    final lowerId = ownerId.toLowerCase();
    final ownerPrefix = lowerId.contains("@") ? lowerId.split("@")[0] : lowerId;

    if (_globalUserMap.containsKey(lowerId)) return _globalUserMap[lowerId]!;
    for (var emailKey in _globalUserMap.keys) {
      final keyPrefix = emailKey.contains("@") ? emailKey.split("@")[0] : emailKey;
      if (keyPrefix == ownerPrefix) return _globalUserMap[emailKey]!;
    }

    for (var key in userInfoMap.keys) {
      final lowerKey = key.toLowerCase();
      final keyPrefix = lowerKey.contains("@") ? lowerKey.split("@")[0] : lowerKey;

      if (lowerKey == lowerId || keyPrefix == ownerPrefix) {
        final fuzzyObj = userInfoMap[key];
        if (fuzzyObj is Map) {
          final fn = fuzzyObj['full_name']?.toString() ?? fuzzyObj['fullname']?.toString() ?? "";
          if (fn.trim().isNotEmpty) return fn.trim();
        }
      }
    }

    return ownerId;
  }

  String _getTimeAgo(String dateStr) {
    if (dateStr.isEmpty) return "";

    try {
      final cleanDate = dateStr.contains(".") ? dateStr.split(".")[0] : dateStr;
      final parsedDate = DateTime.parse(cleanDate);
      final now = DateTime.now();
      final difference = now.difference(parsedDate);

      final seconds = difference.inSeconds;
      if (seconds < 0) return "Just now";
      if (seconds < 60) return "Just now";

      final minutes = difference.inMinutes;
      if (minutes < 60) return "$minutes ${minutes == 1 ? 'minute' : 'minutes'} ago";

      final hours = difference.inHours;
      if (hours < 24) return "$hours ${hours == 1 ? 'hour' : 'hours'} ago";

      final days = difference.inDays;
      if (days == 1) return "yesterday";
      if (days < 7) return "$days days ago";

      final weeks = (days / 7).floor();
      if (days < 30) return "$weeks ${weeks == 1 ? 'week' : 'weeks'} ago";

      final months = (days / 30).floor();
      if (months < 12) return "$months ${months == 1 ? 'month' : 'months'} ago";

      final years = (months / 12).floor();
      return "$years ${years == 1 ? 'year' : 'years'} ago";
    } catch (_) {
      return dateStr;
    }
  }

  // =========================================================================
  // PRINT/DOWNLOAD AND WORKFLOW APPLY CAPABILITIES
  // =========================================================================
  void _showPrintConfirmationDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        content: Padding(
          padding: const EdgeInsets.symmetric(vertical: 12.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.print, size: 64, color: AppTheme.primaryPurple),
              const SizedBox(height: 16),
              const Text(
                "Download Invoice?",
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.black87),
              ),
              const SizedBox(height: 12),
              const Text(
                "Do you want to download the PDF invoice for this order?",
                textAlign: TextAlign.center,
                style: TextStyle(color: Colors.grey),
              ),
              const SizedBox(height: 24),
              Row(
                children: [
                  Expanded(
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFFE0E0E0),
                        foregroundColor: Colors.black87,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(30)),
                        padding: const EdgeInsets.symmetric(vertical: 14),
                      ),
                      onPressed: () => Navigator.pop(context),
                      child: const Text("NO", style: TextStyle(fontWeight: FontWeight.bold)),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTheme.primaryPurple,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(30)),
                        padding: const EdgeInsets.symmetric(vertical: 14),
                      ),
                      onPressed: () {
                        Navigator.pop(context);
                        _downloadAndPrintInvoice();
                      },
                      child: const Text("YES", style: TextStyle(fontWeight: FontWeight.bold)),
                    ),
                  ),
                ],
              )
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _downloadAndPrintInvoice() async {
    setState(() => _isPrinting = true);
    
    // Scale-rotate printer micro-animation simulation delay
    await Future.delayed(const Duration(milliseconds: 600));

    try {
      final appDocDir = await getTemporaryDirectory();
      final savePath = "${appDocDir.path}/${widget.orderId}.pdf";

      final response = await _apiClient.dio.download(
        '/api/method/frappe.utils.print_format.download_pdf',
        savePath,
        queryParameters: {
          'doctype': 'Sales Order',
          'name': widget.orderId,
          'format': 'Standard',
          'no_letterhead': '0',
        },
      );

      if (response.statusCode == 200) {
        if (mounted) {
          setState(() => _isPrinting = false);
          // Brings up native AirPrint / Share Sheet on iOS & Android
          await Share.shareXFiles([XFile(savePath)], text: 'Sales Order Invoice: ${widget.orderId}');
        }
      } else {
        throw Exception("Server returned code ${response.statusCode}");
      }
    } catch (e) {
      print("Error downloading invoice: $e");
      if (mounted) {
        setState(() => _isPrinting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("Error generating invoice PDF: $e"), backgroundColor: Colors.red),
        );
      }
    }
  }

  // =========================================================================
  // ORDER EDIT PRE-POPULATION ENGINE (Matches Java populateDataManager)
  // =========================================================================
  void _triggerEditMode() {
    if (_orderData == null) return;
    
    final provider = OrderEntryProvider();
    provider.clearData();

    provider.isEditMode = true;
    provider.editingOrderId = widget.orderId;
    provider.customer = _orderData!['customer_name']?.toString() ?? _orderData!['customer']?.toString() ?? "";
    provider.customerId = _orderData!['customer']?.toString() ?? "";
    provider.company = _orderData!['company']?.toString() ?? "";
    provider.transactionDate = _orderData!['transaction_date']?.toString() ?? "";
    provider.deliveryDate = _orderData!['delivery_date']?.toString() ?? "";
    provider.paymentTerms = _orderData!['payment_terms_template']?.toString();
    provider.customerAddressName = _orderData!['customer_address']?.toString() ?? "";
    provider.territory = _orderData!['territory']?.toString() ?? "";
    provider.fullAddress = (_orderData!['address_display']?.toString() ?? "").replaceAll(RegExp(r'<[^>]*>'), '\n').trim();
    provider.contactPerson = _contactPerson;
    provider.mobileNumber = _contactMobile;

    final itemsList = _orderData!['items'] as List? ?? [];
    provider.items = itemsList.map((item) => OrderItem.fromJson(item)).toList();

    // Store state keys to SharedPreferences
    SharedPreferences.getInstance().then((prefs) {
      final workflowState = _orderData!['workflow_state']?.toString() ?? "";
      final docStatus = _orderData!['status']?.toString() ?? "Draft";
      final status = workflowState.isNotEmpty ? workflowState : docStatus;
      final fulfillmentStatus = _orderData!['fulfillment_status']?.toString() ?? "Not Fulfilled";

      prefs.setString("EDIT_APPROVAL_STATUS", status);
      prefs.setString("EDIT_FULFILLMENT_STATUS", fulfillmentStatus);

      // Pushing order entry screen and refetching on returning
      Navigator.pushNamed(context, '/order_entry').then((_) => _fetchOrderData());
    });
  }

  // =========================================================================
  // WORKFLOW ACTION HANDLERS
  // =========================================================================
  bool _isMedRep() => _loggedInUserRole.toLowerCase() == "medrep";
  bool _isDSM() {
    final role = _loggedInUserRole.toUpperCase();
    return role == "DSM" || role == "DSM_I" || role == "DSM_IA";
  }
  bool _isGM() => _loggedInUserRole.toLowerCase() == "gm";
  bool _isNSM1() {
    final role = _loggedInUserRole.toUpperCase();
    return role == "NSM-1" || role == "NSM1";
  }
  bool _isNSM2() {
    final role = _loggedInUserRole.toUpperCase();
    return role == "NSM-2" || role == "NSM2";
  }
  bool _isAdmin() => _loggedInUserRole.toLowerCase() == "admin";

  bool _isRejectedOrder(String state) {
    final s = state.toLowerCase();
    return s.contains("rejected");
  }

  bool _isOrderActionable() {
    if (_orderData == null) return false;
    final workflowState = _orderData!['workflow_state']?.toString() ?? "";
    final docStatus = _orderData!['status']?.toString() ?? "Draft";
    final approvalStatus = workflowState.isNotEmpty ? workflowState : docStatus;

    if (approvalStatus == stateSoApproved) return false;

    final fulfillmentStatus = _orderData!['fulfillment_status']?.toString() ?? "Not Fulfilled";
    final isTerminalFulfillment = fulfillmentStatus.toLowerCase() == "delivered" ||
        fulfillmentStatus.toLowerCase() == "cancelled";

    return !isTerminalFulfillment;
  }

  bool _isOrderActionableByRole() {
    if (_orderData == null) return false;
    final workflowState = _orderData!['workflow_state']?.toString() ?? "";
    final docStatus = _orderData!['status']?.toString() ?? "Draft";
    final status = workflowState.isNotEmpty ? workflowState : docStatus;

    if (_isMedRep()) {
      return _isRejectedOrder(status);
    }
    if (_isDSM()) {
      return status == stateForDsm;
    }
    if (_isGM()) {
      return status == stateForGm;
    }
    if (_isNSM1()) {
      return status == stateForNsm1;
    }
    if (_isNSM2()) {
      return status == stateForNsm2;
    }
    if (_isAdmin()) {
      return _isOrderActionable();
    }
    return false;
  }

  String _determineAdminPhase() {
    if (_orderData == null) return "MEDREP_PHASE";
    final workflowState = _orderData!['workflow_state']?.toString() ?? "";
    final docStatus = _orderData!['status']?.toString() ?? "Draft";
    final state = workflowState.isNotEmpty ? workflowState : docStatus;

    if (state == stateDraft || _isRejectedOrder(state)) {
      return "MEDREP_PHASE";
    } else if (state == stateForDsm) {
      return "DSM_PHASE";
    } else {
      return "GM_PHASE";
    }
  }

  void _showRoleBasedActionDialog() {
    if (_orderData == null) return;
    
    String dialogTitle = "Action";
    List<String> options = [];

    switch (_loggedInUserRole.toUpperCase()) {
      case "DSM":
        dialogTitle = "DSM Action";
        options = ["Approve & Pass to Invoicer", "Approve & Pass to GM", "Reject"];
        break;
      case "DSM_I":
        dialogTitle = "DSM Action";
        options = ["Approve & Pass to Invoicer", "Approve & Pass to NSM-1", "Reject"];
        break;
      case "DSM_IA":
        dialogTitle = "DSM Action";
        options = ["Approve & Pass to Invoicer", "Approve & Pass to NSM-2", "Reject"];
        break;
      case "NSM1":
      case "NSM-1":
        dialogTitle = "NSM-1 Action";
        options = ["Approve & Pass to Invoicer", "Approve & Pass to NSM-2", "Reject"];
        break;
      case "NSM2":
      case "NSM-2":
        dialogTitle = "NSM-2 Action";
        options = ["Approve", "Reject"];
        break;
      case "GM":
        dialogTitle = "GM Action";
        options = ["Approve", "Reject"];
        break;
      case "ADMIN":
        final phase = _determineAdminPhase();
        if (phase == "MEDREP_PHASE") {
          dialogTitle = "Phase 1 (Submit)";
          options = ["Submit for Approval"];
        } else if (phase == "DSM_PHASE") {
          dialogTitle = "Phase 2 (DSM)";
          options = ["Approve & Pass to Invoicer", "Approve & Pass to GM", "Reject"];
        } else {
          dialogTitle = "Phase 3 (GM)";
          options = ["Approve", "Reject"];
        }
        break;
      default:
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text("Your role does not have permission to perform actions.")),
        );
        return;
    }

    showDialog(
      context: context,
      builder: (context) => Dialog(
        backgroundColor: Colors.transparent,
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 40, horizontal: 24),
          decoration: BoxDecoration(
            color: Colors.white.withOpacity(0.95),
            borderRadius: BorderRadius.circular(30),
            border: Border.all(color: Colors.white, width: 2),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                dialogTitle,
                style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.black87),
              ),
              const SizedBox(height: 32),
              ...options.map((option) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 6.0),
                child: Material(
                  color: AppTheme.primaryPurple,
                  borderRadius: BorderRadius.circular(30),
                  child: InkWell(
                    borderRadius: BorderRadius.circular(30),
                    onTap: () {
                      Navigator.pop(context);
                      _performWorkflowAction(option);
                    },
                    child: Container(
                      width: double.infinity,
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      alignment: Alignment.center,
                      child: Text(
                        option,
                        style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
                      ),
                    ),
                  ),
                ),
              )),
              const SizedBox(height: 16),
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text("CANCEL", style: TextStyle(color: Colors.grey, fontWeight: FontWeight.bold)),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _performWorkflowAction(String action) async {
    setState(() => _isWorkflowProcessing = true);
    
    try {
      final response = await _apiClient.postJson(
        '/api/method/frappe.model.workflow.apply_workflow',
        {
          'doc': {
            'doctype': 'Sales Order',
            'name': widget.orderId,
          },
          'action': action,
        },
      );

      if (response.statusCode == 200 && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text("✅ Action completed successfully"), backgroundColor: Colors.green),
        );
        _fetchOrderData();
      }
    } catch (e) {
      print("Workflow error: $e");
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("Action Failed: $e"), backgroundColor: Colors.red),
        );
      }
    } finally {
      if (mounted) setState(() => _isWorkflowProcessing = false);
    }
  }

  // =========================================================================
  // UNIVERSAL MENU (stay / logout)
  // =========================================================================
  void _showUniversalMenu() {
    showDialog(
      context: context,
      builder: (context) => Dialog(
        backgroundColor: Colors.transparent,
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 32, horizontal: 24),
          decoration: BoxDecoration(
            color: const Color(0xFFF8F9FA),
            borderRadius: BorderRadius.circular(30),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const CircleAvatar(
                radius: 32,
                backgroundColor: Color(0xFFE0E0E0),
                child: Icon(Icons.person, size: 40, color: Colors.grey),
              ),
              const SizedBox(height: 24),
              Material(
                color: AppTheme.primaryPurple,
                borderRadius: BorderRadius.circular(20),
                child: InkWell(
                  borderRadius: BorderRadius.circular(20),
                  onTap: () async {
                    Navigator.pop(context); // Pop menu
                    final prefs = await SharedPreferences.getInstance();
                    await prefs.clear();
                    if (mounted) {
                      Navigator.of(context).pushNamedAndRemoveUntil('/login', (route) => false);
                    }
                  },
                  child: Container(
                    width: double.infinity,
                    padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 24),
                    alignment: Alignment.center,
                    child: const Text(
                      "Logout Account",
                      style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 8),
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text("Stay", style: TextStyle(color: Colors.grey)),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // =========================================================================
  // VIEW BUILDERS
  // =========================================================================
  Color _getApprovalColor(String status) {
    final s = status.toLowerCase();
    if (s == "draft") return Colors.grey;
    if (s.contains("rejected")) return Colors.red;
    if (s == "so approved") return Colors.green;
    return Colors.orange;
  }

  Color _getFulfillmentColor(String status) {
    final s = status.toLowerCase();
    if (s == "delivered" || s == "paid") return Colors.green;
    if (s == "cancelled" || s == "returned") return Colors.red;
    if (s == "-") return Colors.grey;
    return Colors.blue;
  }

  @override
  Widget build(BuildContext context) {
    final String customerName = _orderData != null 
        ? _orderData!['customer_name']?.toString() ?? _orderData!['customer']?.toString() ?? "Loading..."
        : "Loading...";

    final workflowState = _orderData != null ? _orderData!['workflow_state']?.toString() ?? "" : "";
    final docStatus = _orderData != null ? _orderData!['status']?.toString() ?? "Draft" : "Draft";
    final approvalStatus = workflowState.isNotEmpty ? workflowState : docStatus;

    final isApproved = approvalStatus == stateSoApproved;
    final isDraft = approvalStatus == stateDraft;
    final showEditActions = !isApproved || isDraft;

    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text("Order: ${widget.orderId}", style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            Text(customerName, style: const TextStyle(fontSize: 12, color: Colors.white70)),
          ],
        ),
        actions: [
          // Print / Invoice Button
          IconButton(
            icon: _isPrinting 
                ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2))
                : const Icon(Icons.print),
            onPressed: _isPrinting ? null : _showPrintConfirmationDialog,
            tooltip: "Print Invoice",
          ),
          // Hamburger Menu
          IconButton(
            icon: const Icon(Icons.more_vert),
            onPressed: _showUniversalMenu,
            tooltip: "Universal Menu",
          )
        ],
        bottom: TabBar(
          controller: _tabController,
          isScrollable: true,
          tabs: const [
            Tab(text: "Details", icon: Icon(Icons.info_outline)),
            Tab(text: "Address", icon: Icon(Icons.location_on_outlined)),
            Tab(text: "Terms", icon: Icon(Icons.description_outlined)),
            Tab(text: "More Info", icon: Icon(Icons.assignment_ind_outlined)),
          ],
        ),
      ),
      body: _isLoading 
        ? const Center(child: CircularProgressIndicator(color: AppTheme.primaryPurple))
        : _orderData == null 
          ? const Center(child: Text("Failed to load order data."))
          : TabBarView(
              controller: _tabController,
              children: [
                _buildDetailsTab(approvalStatus),
                _buildAddressTab(),
                _buildTermsTab(),
                _buildMoreInfoTab(),
              ],
            ),
      bottomNavigationBar: _orderData == null ? null : _buildStickyBottomActions(showEditActions, approvalStatus),
    );
  }

  Widget _buildStickyBottomActions(bool showEditActions, String approvalStatus) {
    final showApproveActions = _isOrderActionableByRole();
    if (!showEditActions && !showApproveActions) return const SizedBox.shrink();

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 10, offset: const Offset(0, -3))],
      ),
      child: SafeArea(
        child: Row(
          children: [
            if (showEditActions) 
              Expanded(
                child: ElevatedButton.icon(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFFDFDFC7),
                    foregroundColor: Colors.black87,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                  icon: const Icon(Icons.edit, size: 18),
                  label: const Text("Edit Order", style: TextStyle(fontWeight: FontWeight.bold)),
                  onPressed: _triggerEditMode,
                ),
              ),
            if (showEditActions && showApproveActions) const SizedBox(width: 12),
            if (showApproveActions)
              Expanded(
                child: ElevatedButton.icon(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTheme.primaryPurple,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                  icon: _isWorkflowProcessing
                      ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2))
                      : const Icon(Icons.check_circle_outline, size: 18),
                  label: Text(_isMedRep() ? "Resubmit Order" : "Actions", style: const TextStyle(fontWeight: FontWeight.bold)),
                  onPressed: _isWorkflowProcessing ? null : () {
                    if (_isMedRep()) {
                      _triggerEditMode();
                    } else {
                      _showRoleBasedActionDialog();
                    }
                  },
                ),
              ),
          ],
        ),
      ),
    );
  }

  // =========================================================================
  // TAB BUILDERS
  // =========================================================================
  Widget _buildDetailsTab(String approvalStatus) {
    final fulfillmentStatus = _orderData!['fulfillment_status']?.toString() ?? "-";
    final taxCategory = _orderData!['tax_category']?.toString() ?? "Standard";

    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 20.0),
      children: [
        // Meta Tag Headers
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            _buildBadge("Approval: $approvalStatus", _getApprovalColor(approvalStatus)),
            _buildBadge("Fulfillment: $fulfillmentStatus", _getFulfillmentColor(fulfillmentStatus)),
            _buildBadge("Tax Status: $taxCategory", Colors.blueGrey),
          ],
        ),
        const SizedBox(height: 24),
        
        // Identity Information
        const Text("Form Details", style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.primaryPurple)),
        const SizedBox(height: 12),
        Card(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              children: [
                _buildInfoRow("Customer ID", _orderData!['customer']),
                _buildInfoRow("Entity Company", _orderData!['company']),
                _buildInfoRow("Order Date", _orderData!['transaction_date']),
                _buildInfoRow("Delivery Date", _orderData!['delivery_date']),
                _buildInfoRow("LTO Number", _ltoNo),
                _buildInfoRow("Business Permit", _businessPermit),
              ],
            ),
          ),
        ),
        const SizedBox(height: 24),

        // Items Table
        const Text("Items Ordered", style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.primaryPurple)),
        const SizedBox(height: 12),
        _buildItemsTable(),
        const SizedBox(height: 32),

        // Timeline Trail log
        const Text("Activity Timeline", style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.primaryPurple)),
        const SizedBox(height: 16),
        _buildTimelineTrail(),
      ],
    );
  }

  Widget _buildAddressTab() {
    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 20.0),
      children: [
        const Text("Delivery & Contact Details", style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.primaryPurple)),
        const SizedBox(height: 12),
        Card(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              children: [
                _buildInfoRow("Address Code", _orderData!['customer_address']),
                _buildInfoRow("Territory", _orderData!['territory']),
                _buildInfoRow("Full Address", (_orderData!['address_display']?.toString() ?? "No Address Provided").replaceAll(RegExp(r'<[^>]*>'), ' ').trim()),
                const Divider(),
                _buildInfoRow("Contact Person", _contactPerson),
                _buildInfoRow("Contact Details", _contactDisplay),
                _buildInfoRow("Mobile / Phone", _contactMobile),
              ],
            ),
          ),
        ),
        const SizedBox(height: 32),

        // Timeline Trail log
        const Text("Activity Timeline", style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.primaryPurple)),
        const SizedBox(height: 16),
        _buildTimelineTrail(),
      ],
    );
  }

  Widget _buildTermsTab() {
    final paymentSchedule = _orderData!['payment_schedule'] as List? ?? [];

    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 20.0),
      children: [
        const Text("Instructions & Payment", style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.primaryPurple)),
        const SizedBox(height: 12),
        Card(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildInfoRow("PO Reference", _orderData!['po_no']?.toString().isEmpty == false ? _orderData!['po_no'] : "No Reference"),
                _buildInfoRow("Payment Terms", _orderData!['payment_terms_template'] ?? "No Template"),
                const SizedBox(height: 12),
                const Text("Additional Instructions:", style: TextStyle(fontWeight: FontWeight.bold, color: Colors.grey, fontSize: 12)),
                const SizedBox(height: 6),
                Text(_orderData!['notes']?.toString().trim().isNotEmpty == true 
                    ? _orderData!['notes'] 
                    : (_orderData!['terms']?.toString().trim().isNotEmpty == true ? _orderData!['terms'] : "No notes provided."),
                  style: const TextStyle(fontSize: 14),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 24),

        // Payment Schedule Table
        if (paymentSchedule.isNotEmpty) ...[
          const Text("Payment Schedule", style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.primaryPurple)),
          const SizedBox(height: 12),
          _buildPaymentScheduleTable(paymentSchedule),
          const SizedBox(height: 32),
        ],

        // Timeline Trail log
        const Text("Activity Timeline", style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.primaryPurple)),
        const SizedBox(height: 16),
        _buildTimelineTrail(),
      ],
    );
  }

  Widget _buildMoreInfoTab() {
    final salesTeam = _orderData!['sales_team'] as List? ?? [];

    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 20.0),
      children: [
        // Sales Team Allocation Table
        if (salesTeam.isNotEmpty) ...[
          const Text("Sales Allocation", style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.primaryPurple)),
          const SizedBox(height: 12),
          _buildSalesTeamTable(salesTeam),
          const SizedBox(height: 32),
        ] else ...[
          const Card(
            child: Padding(
              padding: EdgeInsets.all(24.0),
              child: Center(child: Text("No Sales Team Allocations Defined", style: TextStyle(color: Colors.grey))),
            ),
          ),
          const SizedBox(height: 24),
        ],

        // Timeline Trail log
        const Text("Activity Timeline", style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.primaryPurple)),
        const SizedBox(height: 16),
        _buildTimelineTrail(),
      ],
    );
  }

  // =========================================================================
  // SUB WIDGET TABLE GENERATORS (Alternating Zebra striped rows)
  // =========================================================================
  Widget _buildBadge(String label, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(30),
        border: Border.all(color: color.withOpacity(0.5), width: 1),
      ),
      child: Text(
        label,
        style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.bold),
      ),
    );
  }

  Widget _buildInfoRow(String label, dynamic value) {
    final String displayVal = value?.toString() ?? "N/A";
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 130, 
            child: Text(label, style: const TextStyle(color: Colors.grey, fontSize: 13, fontWeight: FontWeight.w500)),
          ),
          Expanded(
            child: Text(
              displayVal.trim().isEmpty ? "N/A" : displayVal, 
              style: const TextStyle(fontWeight: FontWeight.w500, fontSize: 13, color: Colors.black87),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildItemsTable() {
    final items = _orderData!['items'] as List? ?? [];
    if (items.isEmpty) {
      return const Card(child: Padding(padding: EdgeInsets.all(16), child: Center(child: Text("No items found"))));
    }

    int idx = 1;
    int totalQty = 0;
    double totalPrice = 0.0;

    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: Table(
          columnWidths: const {
            0: FixedColumnWidth(40),
            1: FlexColumnWidth(4),
            2: FixedColumnWidth(80),
            3: FixedColumnWidth(60),
            4: FixedColumnWidth(80),
            5: FixedColumnWidth(90),
          },
          defaultVerticalAlignment: TableCellVerticalAlignment.middle,
          children: [
            // Header Row
            TableRow(
              decoration: const BoxDecoration(color: Color(0xFFF2F2F2)),
              children: [
                _tableHeaderCell("#"),
                _tableHeaderCell("Item"),
                _tableHeaderCell("Delivery"),
                _tableHeaderCell("Qty"),
                _tableHeaderCell("Rate"),
                _tableHeaderCell("Amount"),
              ],
            ),
            
            // Item Rows
            ...items.map((item) {
              final code = item['item_code']?.toString() ?? "N/A";
              final name = item['item_name']?.toString() ?? "";
              final desc = item['description']?.toString() ?? "";
              final delDate = item['delivery_date']?.toString() ?? "---";
              final int qty = (item['qty'] ?? 0).toInt();
              final double rate = (item['rate'] ?? 0.0).toDouble();
              final double amount = item['amount'] != null ? (item['amount'] as num).toDouble() : (qty * rate);

              totalQty += qty;
              totalPrice += amount;

              final String itemDetail = name.isNotEmpty && name.toLowerCase() != code.toLowerCase() 
                  ? "$code : $name" 
                  : code;

              final bool isEven = (idx % 2 == 0);
              final rowIdx = idx++;

              return TableRow(
                decoration: BoxDecoration(color: isEven ? const Color(0xFFF9F9F9) : Colors.white),
                children: [
                  _tableCell(rowIdx.toString(), alignment: Alignment.center),
                  TableCell(
                    child: Padding(
                      padding: const EdgeInsets.all(8.0),
                      child: GestureDetector(
                        onTap: () {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text("Item: $code\nDescription: $desc"),
                              duration: const Duration(seconds: 4),
                              action: SnackBarAction(label: "OK", onPressed: () {}),
                            ),
                          );
                        },
                        child: Text(
                          itemDetail,
                          style: const TextStyle(color: AppTheme.primaryPurple, fontWeight: FontWeight.w600, fontSize: 11),
                        ),
                      ),
                    ),
                  ),
                  _tableCell(delDate, alignment: Alignment.center),
                  _tableCell(qty.toString(), alignment: Alignment.center),
                  _tableCell(rate.toStringAsFixed(2), alignment: Alignment.centerRight),
                  _tableCell(amount.toStringAsFixed(2), alignment: Alignment.centerRight),
                ],
              );
            }),

            // Totals Row
            TableRow(
              decoration: const BoxDecoration(color: Color(0xFFE8E8E8)),
              children: [
                _tableCell(""),
                _tableCell("Grand Total", isBold: true),
                _tableCell(""),
                _tableCell(totalQty.toString(), isBold: true, alignment: Alignment.center),
                _tableCell(""),
                _tableCell("₱${totalPrice.toStringAsFixed(2)}", isBold: true, alignment: Alignment.centerRight),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPaymentScheduleTable(List schedule) {
    int idx = 1;
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: Table(
          columnWidths: const {
            0: FixedColumnWidth(40),
            1: FlexColumnWidth(2),
            2: FlexColumnWidth(2),
            3: FixedColumnWidth(90),
            4: FixedColumnWidth(70),
            5: FixedColumnWidth(90),
          },
          defaultVerticalAlignment: TableCellVerticalAlignment.middle,
          children: [
            TableRow(
              decoration: const BoxDecoration(color: Color(0xFFF2F2F2)),
              children: [
                _tableHeaderCell("#"),
                _tableHeaderCell("Term"),
                _tableHeaderCell("Description"),
                _tableHeaderCell("Due Date"),
                _tableHeaderCell("Portion"),
                _tableHeaderCell("Amount"),
              ],
            ),
            ...schedule.map((item) {
              final term = item['payment_term']?.toString() ?? "N/A";
              final desc = item['description']?.toString() ?? "";
              final dueDate = item['due_date']?.toString() ?? "---";
              final double portion = (item['invoice_portion'] ?? 0.0).toDouble();
              final double amount = (item['payment_amount'] ?? 0.0).toDouble();
              
              final bool isEven = (idx % 2 == 0);
              final rowIdx = idx++;

              return TableRow(
                decoration: BoxDecoration(color: isEven ? const Color(0xFFF9F9F9) : Colors.white),
                children: [
                  _tableCell(rowIdx.toString(), alignment: Alignment.center),
                  _tableCell(term),
                  _tableCell(desc),
                  _tableCell(dueDate, alignment: Alignment.center),
                  _tableCell("${portion.toStringAsFixed(2)}%", alignment: Alignment.centerRight),
                  _tableCell("₱${amount.toStringAsFixed(2)}", alignment: Alignment.centerRight),
                ],
              );
            }),
          ],
        ),
      ),
    );
  }

  Widget _buildSalesTeamTable(List salesTeam) {
    int idx = 1;
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: Table(
          columnWidths: const {
            0: FixedColumnWidth(40),
            1: FlexColumnWidth(3),
            2: FixedColumnWidth(80),
            3: FixedColumnWidth(100),
            4: FixedColumnWidth(100),
          },
          defaultVerticalAlignment: TableCellVerticalAlignment.middle,
          children: [
            TableRow(
              decoration: const BoxDecoration(color: Color(0xFFF2F2F2)),
              children: [
                _tableHeaderCell("#"),
                _tableHeaderCell("Sales Person"),
                _tableHeaderCell("Allocation"),
                _tableHeaderCell("Alloc. Amount"),
                _tableHeaderCell("Incentives"),
              ],
            ),
            ...salesTeam.map((item) {
              final person = item['sales_person']?.toString() ?? "N/A";
              final double pct = (item['allocated_percentage'] ?? 0.0).toDouble();
              final double amount = (item['allocated_amount'] ?? 0.0).toDouble();
              final double incentives = (item['incentives'] ?? 0.0).toDouble();
              
              final bool isEven = (idx % 2 == 0);
              final rowIdx = idx++;

              return TableRow(
                decoration: BoxDecoration(color: isEven ? const Color(0xFFF9F9F9) : Colors.white),
                children: [
                  _tableCell(rowIdx.toString(), alignment: Alignment.center),
                  _tableCell(person),
                  _tableCell("${pct.toStringAsFixed(2)}%", alignment: Alignment.centerRight),
                  _tableCell("₱${amount.toStringAsFixed(2)}", alignment: Alignment.centerRight),
                  _tableCell("₱${incentives.toStringAsFixed(2)}", alignment: Alignment.centerRight),
                ],
              );
            }),
          ],
        ),
      ),
    );
  }

  Widget _tableHeaderCell(String label) {
    return TableCell(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8.0, vertical: 12.0),
        child: Text(
          label,
          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 11, color: Colors.black54),
        ),
      ),
    );
  }

  Widget _tableCell(String value, {bool isBold = false, Alignment alignment = Alignment.centerLeft}) {
    return TableCell(
      child: Align(
        alignment: alignment,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8.0, vertical: 10.0),
          child: Text(
            value,
            style: TextStyle(
              fontWeight: isBold ? FontWeight.bold : FontWeight.normal,
              fontSize: 11,
              color: Colors.black87,
            ),
          ),
        ),
      ),
    );
  }

  // =========================================================================
  // TIMELINE AUDIT LIST GENERATOR WIDGETS
  // =========================================================================
  Widget _buildTimelineTrail() {
    if (_isLoadingTimeline) {
      return const Center(child: Padding(padding: EdgeInsets.all(16.0), child: CircularProgressIndicator(strokeWidth: 2)));
    }
    if (_timelineEntries.isEmpty) {
      return const Center(child: Padding(padding: EdgeInsets.all(16.0), child: Text("No timeline log found.", style: TextStyle(color: Colors.grey))));
    }

    return ListView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: _timelineEntries.length,
      itemBuilder: (context, index) {
        final entry = _timelineEntries[index];
        final isFirst = (index == 0);
        final isLast = (index == _timelineEntries.length - 1);
        return _buildTimelineItem(entry, isFirst, isLast);
      },
    );
  }

  Widget _buildTimelineItem(TimelineEntry entry, bool isFirst, bool isLast) {
    final bool isComment = (entry.type == "comment");
    final String timeAgo = _getTimeAgo(entry.creation);
    
    // Split comment logic to highlight correctly
    String baseContent = entry.displayHtml;
    Widget messageWidget;

    if (isComment) {
      if (baseContent.contains("commented\n\n")) {
        final parts = baseContent.split("commented\n\n");
        messageWidget = Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(parts[0], style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
                Text(" commented • $timeAgo", style: const TextStyle(color: Colors.grey, fontSize: 11)),
              ],
            ),
            const SizedBox(height: 8),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: Colors.grey.shade200),
              ),
              child: Text(parts[1], style: const TextStyle(fontSize: 13, color: Colors.black87)),
            ),
          ],
        );
      } else {
        messageWidget = Text(baseContent, style: const TextStyle(fontSize: 13));
      }
    } else {
      // Normal logs
      messageWidget = RichText(
        text: TextSpan(
          style: const TextStyle(color: Colors.black87, fontSize: 12),
          children: [
            TextSpan(text: baseContent, style: const TextStyle(color: Color(0xFF444444))),
            TextSpan(text: " • $timeAgo", style: const TextStyle(color: Colors.grey, fontSize: 11)),
          ],
        ),
      );
    }

    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Left Line & Circle Indicator
          SizedBox(
            width: 48,
            child: Stack(
              alignment: Alignment.center,
              children: [
                // Top Vertical Line
                Positioned(
                  top: 0,
                  bottom: isComment ? 12 : 8,
                  left: 23.5,
                  child: Container(
                    width: 1.5,
                    color: Colors.grey.shade300,
                  ),
                ),
                
                // Centered Dot / Profile Avatar
                Align(
                  alignment: Alignment.center,
                  child: isComment 
                      ? const CircleAvatar(
                          radius: 12,
                          backgroundColor: Color(0xFFE0E0E0),
                          child: Icon(Icons.person, size: 14, color: Colors.grey),
                        )
                      : Container(
                          width: 8,
                          height: 8,
                          decoration: const BoxDecoration(
                            color: Color(0xFF835C9F),
                            shape: BoxShape.circle,
                          ),
                        ),
                ),
              ],
            ),
          ),
          
          // Right Message details
          Expanded(
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 10.0, horizontal: 8.0),
              child: messageWidget,
            ),
          ),
        ],
      ),
    );
  }
}

class TimelineEntry {
  final String owner;
  final String creation;
  final String content;
  final String type;
  String displayHtml;

  TimelineEntry({
    required this.owner,
    required this.creation,
    required this.content,
    required this.type,
    this.displayHtml = "",
  });
}
