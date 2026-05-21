import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../models/sales_order.dart';
import 'sales_order_repository.dart';
import 'order_view_screen.dart';
import '../../core/app_theme.dart';

class SalesOrderListScreen extends StatefulWidget {
  const SalesOrderListScreen({super.key});

  @override
  State<SalesOrderListScreen> createState() => _SalesOrderListScreenState();
}

class _SalesOrderListScreenState extends State<SalesOrderListScreen> with SingleTickerProviderStateMixin {
  final SalesOrderRepository _repository = SalesOrderRepository();
  final TextEditingController _searchController = TextEditingController();
  
  List<SalesOrder> _orders = [];
  bool _isLoading = false;
  int _currentLimitStart = 0;
  int _totalCount = 0;
  bool _isLastPage = false;
  
  String _userRole = "MedRep";
  String _userEmail = "";
  String _fullName = "User";
  String _sessionCookie = "";
  
  String _currentSearchQuery = "";
  
  // Universal Menu Animations
  bool _isMenuOpen = false;

  final List<String> _approvalStatuses = [
    "For Approval by DSM", "For Approval by GM", "For Approval by NSM-1", "For Approval by NSM-2",
    "SO Approved", "Rejected by DSM", "Rejected by GM", "Rejected by NSM-1", "Rejected by NSM-2", "Draft"
  ];

  final List<String> _fulfillmentStatuses = [
    "-", "For Processing", "Dispatched", "Delivered", "For Payment Validation", "Paid", "Returned", "For re-deliver", "Cancelled"
  ];

  @override
  void initState() {
    super.initState();
    _loadInitialData();
  }

  Future<void> _loadInitialData() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _userRole = prefs.getString("User_Role") ?? "MedRep";
      _userEmail = prefs.getString("User_Email") ?? "";
      _fullName = prefs.getString("Full_Name") ?? "User";
      _sessionCookie = prefs.getString("Session_Cookie") ?? "";
      _isLoading = true;
    });
    await _fetchData();
    setState(() => _isLoading = false);
  }

  Future<void> _fetchData() async {
    final count = await _repository.fetchTotalOrderCount(_currentSearchQuery);
    final orders = await _repository.fetchSalesOrders(
      limitStart: _currentLimitStart,
      searchQuery: _currentSearchQuery,
    );

    setState(() {
      _totalCount = count;
      _orders = orders;
      _isLastPage = orders.length < SalesOrderRepository.pageLength;
    });
  }

  void _onSearch(String query) {
    setState(() {
      _currentSearchQuery = query;
      _searchController.text = query;
      _currentLimitStart = 0;
      _isLoading = true;
    });
    _fetchData().then((_) {
      setState(() => _isLoading = false);
    });
  }

  void _nextPage() {
    if (!_isLastPage && !_isLoading) {
      setState(() {
        _currentLimitStart += SalesOrderRepository.pageLength;
        _isLoading = true;
      });
      _fetchData().then((_) {
        setState(() => _isLoading = false);
      });
    }
  }

  void _prevPage() {
    if (_currentLimitStart >= SalesOrderRepository.pageLength && !_isLoading) {
      setState(() {
        _currentLimitStart -= SalesOrderRepository.pageLength;
        _isLoading = true;
      });
      _fetchData().then((_) {
        setState(() => _isLoading = false);
      });
    }
  }

  bool _isAllSelected() {
    if (_orders.isEmpty) return false;
    return _orders.every((o) => o.isSelected);
  }

  void _toggleSelectAll(bool? val) {
    if (val == null) return;
    setState(() {
      for (var o in _orders) {
        o.isSelected = val;
      }
    });
  }

  List<SalesOrder> get _selectedOrders => _orders.where((o) => o.isSelected).toList();

  bool _isOrderActionable(SalesOrder order) {
    final workflowState = order.approvalStatus;
    final fulfillmentStatus = order.ofsStatus;

    final isFullyApproved = workflowState.toLowerCase() == "so approved";
    final isTerminalFulfillment = fulfillmentStatus.toLowerCase() == "delivered" ||
        fulfillmentStatus.toLowerCase() == "cancelled";

    return !(isFullyApproved && isTerminalFulfillment);
  }

  bool _isOrderActionableByRole(SalesOrder order) {
    final status = order.approvalStatus;

    if (_isMedRep()) {
      return _isRejectedOrder(order);
    }
    if (_isDSM()) {
      return status.toLowerCase() == "for approval by dsm";
    }
    if (_isGM()) {
      return status.toLowerCase() == "for approval by gm";
    }
    if (_isNSM1()) {
      final s = status.toLowerCase();
      return s == "for approval by nsm-1" || s == "for approval by nsm1";
    }
    if (_isNSM2()) {
      final s = status.toLowerCase();
      return s == "for approval by nsm-2" || s == "for approval by nsm2";
    }
    if (_isAdmin()) {
      return _isOrderActionable(order);
    }
    return false;
  }

  bool _isRejectedOrder(SalesOrder order) {
    final state = order.approvalStatus.toLowerCase();
    return state.contains("rejected");
  }

  bool _isMedRep() => _userRole.toLowerCase() == "medrep";
  bool _isDSM() {
    final role = _userRole.toUpperCase();
    return role == "DSM" || role == "DSM_I" || role == "DSM_IA";
  }
  bool _isGM() => _userRole.toLowerCase() == "gm";
  bool _isNSM1() {
    final role = _userRole.toUpperCase();
    return role == "NSM-1" || role == "NSM1";
  }
  bool _isNSM2() {
    final role = _userRole.toUpperCase();
    return role == "NSM-2" || role == "NSM2";
  }
  bool _isAdmin() => _userRole.toLowerCase() == "admin";

  String _determineAdminPhase(List<SalesOrder> orders) {
    bool hasMedRepPhase = false;
    bool hasDSMPhase = false;
    bool hasGMPhase = false;

    for (var order in orders) {
      if (!_isOrderActionable(order)) continue;
      final state = order.approvalStatus.toLowerCase();

      if (state == "draft" || state.contains("rejected")) {
        hasMedRepPhase = true;
      } else if (state == "for approval by dsm") {
        hasDSMPhase = true;
      } else {
        hasGMPhase = true;
      }
    }

    int activePhases = 0;
    if (hasMedRepPhase) activePhases++;
    if (hasDSMPhase) activePhases++;
    if (hasGMPhase) activePhases++;

    if (activePhases > 1) return "MIXED_PHASE";
    if (hasMedRepPhase) return "MEDREP_PHASE";
    if (hasDSMPhase) return "DSM_PHASE";
    return "GM_PHASE";
  }

  void _showMixedStateErrorDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text("Invalid Selection", style: TextStyle(fontWeight: FontWeight.bold)),
        content: const Text("You have selected orders in different workflow states. Please select orders that share the same status to perform a bulk action."),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text("OK", style: TextStyle(color: Color(0xFF835C9F))),
          )
        ],
      ),
    );
  }

  void _openRejectedOrderForEditing(SalesOrder order) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text("Opening rejected order: ${order.id}"),
        backgroundColor: Colors.blueAccent,
      ),
    );
    
    // Redirect to OrderViewScreen with triggering edit mode
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => OrderViewScreen(orderId: order.id, triggerEditMode: true),
      ),
    ).then((_) => _loadInitialData());
  }

  void _showSearchOptionsPopup() {
    showDialog(
      context: context,
      builder: (context) => Dialog(
        backgroundColor: Colors.transparent,
        child: Container(
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            color: const Color(0xFFF8F9FA),
            borderRadius: BorderRadius.circular(30),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                "Choose Search Type:",
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF835C9F),
                ),
              ),
              const SizedBox(height: 24),
              _popupDialogButton("Search by Text (Name, ID)", () {
                Navigator.pop(context);
                FocusScope.of(context).requestFocus(FocusNode());
              }),
              _popupDialogButton("Search by Date / Range", () {
                Navigator.pop(context);
                _showDateSearchOptions();
              }),
              _popupDialogButton("Search by Approval Status", () {
                Navigator.pop(context);
                _showFilterDialog("Choose Approval Status", _approvalStatuses, (status) {
                  _onSearch("workflow_state:$status");
                });
              }),
              _popupDialogButton("Search by Order Fulfillment Status", () {
                Navigator.pop(context);
                _showFilterDialog("Choose Fulfillment Status", _fulfillmentStatuses, (status) {
                  _onSearch(status);
                });
              }),
              const SizedBox(height: 12),
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

  Widget _popupDialogButton(String text, VoidCallback onTap) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8.0),
      child: Material(
        color: const Color(0xFF835C9F),
        borderRadius: BorderRadius.circular(30),
        child: InkWell(
          borderRadius: BorderRadius.circular(30),
          onTap: onTap,
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 24),
            alignment: Alignment.center,
            child: Text(
              text,
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
            ),
          ),
        ),
      ),
    );
  }

  void _showDateSearchOptions() {
    showDialog(
      context: context,
      builder: (context) => SimpleDialog(
        title: const Text("Select Date Search Type"),
        children: [
          SimpleDialogOption(
            onPressed: () async {
              Navigator.pop(context);
              final date = await showDatePicker(
                context: context,
                initialDate: DateTime.now(),
                firstDate: DateTime(2020),
                lastDate: DateTime(2030),
              );
              if (date != null) {
                final dateStr = date.toString().split(' ')[0];
                _onSearch("date:$dateStr");
              }
            },
            child: const Text("Pick Single Date"),
          ),
          SimpleDialogOption(
            onPressed: () async {
              Navigator.pop(context);
              final range = await showDateRangePicker(
                context: context,
                firstDate: DateTime(2020),
                lastDate: DateTime(2030),
              );
              if (range != null) {
                final startStr = range.start.toString().split(' ')[0];
                final endStr = range.end.toString().split(' ')[0];
                _onSearch("range:$startStr,$endStr");
              }
            },
            child: const Text("Pick Date Range"),
          ),
          SimpleDialogOption(
            onPressed: () {
              Navigator.pop(context);
              _onSearch("");
            },
            child: const Text("Clear Date Filter"),
          ),
        ],
      ),
    );
  }

  void _showFilterDialog(String title, List<String> items, Function(String) onSelected) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: SizedBox(
          width: double.maxFinite,
          child: ListView.builder(
            shrinkWrap: true,
            itemCount: items.length,
            itemBuilder: (context, index) {
              return ListTile(
                title: Text(items[index]),
                onTap: () {
                  Navigator.pop(context);
                  onSelected(items[index]);
                },
              );
            },
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text("Cancel"),
          ),
        ],
      ),
    );
  }

  void _showHamburgerMenu() {
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
              // Profile
              const CircleAvatar(
                radius: 32,
                backgroundColor: Color(0xFFE0E0E0),
                child: Icon(Icons.person, size: 40, color: Colors.grey),
              ),
              const SizedBox(height: 16),
              Text(
                _fullName,
                style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.black87),
              ),
              Text(
                _userEmail,
                style: const TextStyle(fontSize: 14, color: Colors.grey),
              ),
              const SizedBox(height: 24),
              
              // Menu Button: Logout
              Material(
                color: const Color(0xFF835C9F),
                borderRadius: BorderRadius.circular(20),
                child: InkWell(
                  borderRadius: BorderRadius.circular(20),
                  onTap: () async {
                    Navigator.pop(context); // pop menu
                    final prefs = await SharedPreferences.getInstance();
                    await prefs.clear();
                    if (mounted) {
                      Navigator.of(context).pushReplacementNamed('/login');
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

  void _showRoleBasedActionDialog() {
    final selected = _selectedOrders;
    final actionable = selected.where(_isOrderActionableByRole).toList();

    if (actionable.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("All selected orders are completed or not actionable.")),
      );
      return;
    }

    String dialogTitle = "Action";
    List<String> options = [];

    switch (_userRole.toUpperCase()) {
      case "DSM":
        dialogTitle = "DSM Action — ${actionable.length} Order(s)";
        options = ["Approve & Pass to Invoicer", "Approve & Pass to GM", "Reject"];
        break;
      case "DSM_I":
        dialogTitle = "DSM Action — ${actionable.length} Order(s)";
        options = ["Approve & Pass to Invoicer", "Approve & Pass to NSM-1", "Reject"];
        break;
      case "DSM_IA":
        dialogTitle = "DSM Action — ${actionable.length} Order(s)";
        options = ["Approve & Pass to Invoicer", "Approve & Pass to NSM-2", "Reject"];
        break;
      case "NSM1":
        dialogTitle = "NSM-1 Action — ${actionable.length} Order(s)";
        options = ["Approve & Pass to Invoicer", "Approve & Pass to NSM-2", "Reject"];
        break;
      case "NSM2":
        dialogTitle = "NSM-2 Action — ${actionable.length} Order(s)";
        options = ["Approve", "Reject"];
        break;
      case "GM":
        dialogTitle = "GM Action — ${actionable.length} Order(s)";
        options = ["Approve", "Reject"];
        break;
      case "ADMIN":
        final adminPhase = _determineAdminPhase(actionable);
        if (adminPhase == "MIXED_PHASE") {
          _showMixedStateErrorDialog();
          return;
        }
        if (adminPhase == "MEDREP_PHASE") {
          dialogTitle = "Phase 1 (Submit) — ${actionable.length} Order(s)";
          options = ["Submit for Approval"];
        } else if (adminPhase == "DSM_PHASE") {
          dialogTitle = "Phase 2 (DSM) — ${actionable.length} Order(s)";
          options = ["Approve & Pass to Invoicer", "Approve & Pass to GM", "Reject"];
        } else {
          dialogTitle = "Phase 3 (GM) — ${actionable.length} Order(s)";
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
                  color: const Color(0xFF835C9F),
                  borderRadius: BorderRadius.circular(30),
                  child: InkWell(
                    borderRadius: BorderRadius.circular(30),
                    onTap: () {
                      Navigator.pop(context);
                      _performBulkWorkflowAction(actionable, option);
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

  Future<void> _performBulkWorkflowAction(List<SalesOrder> actionable, String action) async {
    setState(() => _isLoading = true);

    int successCount = 0;
    int failureCount = 0;
    String lastError = "";

    for (var order in actionable) {
      final isSuccess = await _repository.applyWorkflowAction(order.id, action);
      if (isSuccess) {
        successCount++;
      } else {
        failureCount++;
        lastError = "Action failed on some orders";
      }
    }

    if (mounted) {
      setState(() {
        _isLoading = false;
        // Unselect everything after action
        for (var o in _orders) {
          o.isSelected = false;
        }
      });

      if (failureCount > 0) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text("Action Completed: $successCount Success, $failureCount Failed.\nError: $lastError"),
            backgroundColor: Colors.redAccent,
          ),
        );
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text("Action Completed: $successCount Orders Processed"),
            backgroundColor: Colors.green,
          ),
        );
      }

      _onSearch(_currentSearchQuery);
    }
  }

  Color _getApprovalColor(String status) {
    final s = status.toLowerCase();
    if (s == "draft") return Colors.grey;
    if (s.contains("rejected")) return Colors.red;
    if (s == "so approved") return Colors.green;
    return Colors.orange; // For Approval by DSM, GM, etc.
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
    final selected = _selectedOrders;
    
    // Bottom bar actions logic
    bool showActionsButton = false;
    String actionsButtonLabel = "";

    if (selected.isNotEmpty) {
      if (_isMedRep()) {
        final rejectedCount = selected.where(_isRejectedOrder).length;
        if (rejectedCount == 1) {
          showActionsButton = true;
          actionsButtonLabel = "Edit & Resubmit";
        } else if (rejectedCount > 1) {
          showActionsButton = true;
          actionsButtonLabel = "Select 1 to Edit";
        }
      } else {
        // Manager / Admin: Action button is visible if all selected are actionable by role
        final allActionable = selected.every(_isOrderActionableByRole);
        if (allActionable) {
          showActionsButton = true;
          actionsButtonLabel = "Actions";
        }
      }
    }

    return Scaffold(
      body: Stack(
        children: [
          // Background DNA
          Positioned.fill(
            child: Opacity(
              opacity: 0.15,
              child: Image.asset(
                'assets/images/dna_background.jpg',
                fit: BoxFit.cover,
              ),
            ),
          ),
          
          SafeArea(
            child: Column(
              children: [
                // Header Bar
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      // Hamburger Menu Button
                      IconButton(
                        icon: AnimatedSwitcher(
                          duration: const Duration(milliseconds: 250),
                          child: Icon(
                            _isMenuOpen ? Icons.close : Icons.menu,
                            key: ValueKey<bool>(_isMenuOpen),
                            size: 32,
                            color: const Color(0xFF835C9F),
                          ),
                        ),
                        onPressed: _showHamburgerMenu,
                      ),
                      const Text(
                        "Sales Orders",
                        style: TextStyle(
                          fontSize: 22,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF835C9F),
                        ),
                      ),
                      Image.asset(
                        'assets/images/medretailer_official_logo.png',
                        width: 100,
                        height: 40,
                        fit: BoxFit.contain,
                      ),
                    ],
                  ),
                ),
                
                const Divider(height: 1, thickness: 0.5),

                // Search Bar & Filter Button
                Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Row(
                    children: [
                      Expanded(
                        child: Container(
                          decoration: BoxDecoration(
                            color: Colors.white.withOpacity(0.8),
                            borderRadius: BorderRadius.circular(30),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withOpacity(0.05),
                                blurRadius: 4,
                                offset: const Offset(0, 2),
                              )
                            ],
                          ),
                          child: TextField(
                            controller: _searchController,
                            onSubmitted: _onSearch,
                            decoration: InputDecoration(
                              hintText: "Search SO# or Customer...",
                              prefixIcon: const Icon(Icons.search, color: Color(0xFF835C9F)),
                              suffixIcon: _searchController.text.isNotEmpty
                                  ? IconButton(
                                      icon: const Icon(Icons.clear, color: Colors.grey),
                                      onPressed: () {
                                        _searchController.clear();
                                        _onSearch("");
                                      },
                                    )
                                  : null,
                              border: InputBorder.none,
                              contentPadding: const EdgeInsets.symmetric(vertical: 14, horizontal: 20),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      GestureDetector(
                        onTap: _showSearchOptionsPopup,
                        child: Container(
                          padding: const EdgeInsets.all(12),
                          decoration: const BoxDecoration(
                            color: Color(0xFF835C9F),
                            shape: BoxShape.circle,
                          ),
                          child: const Icon(Icons.filter_list, color: Colors.white),
                        ),
                      )
                    ],
                  ),
                ),

                // Selection Header (Header Checkbox) & Stats
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: [
                          Checkbox(
                            value: _isAllSelected(),
                            activeColor: const Color(0xFF835C9F),
                            onChanged: _toggleSelectAll,
                          ),
                          const Text("Select All", style: TextStyle(fontWeight: FontWeight.bold)),
                        ],
                      ),
                      Row(
                        children: [
                          Text(
                            "(Total: $_totalCount)",
                            style: const TextStyle(color: Colors.grey, fontWeight: FontWeight.bold),
                          ),
                          const SizedBox(width: 8),
                          // Page number & buttons
                          IconButton(
                            icon: const Icon(Icons.chevron_left, size: 28),
                            onPressed: _currentLimitStart > 0 && !_isLoading ? _prevPage : null,
                          ),
                          Text(
                            "Page ${(_currentLimitStart ~/ SalesOrderRepository.pageLength) + 1}",
                            style: const TextStyle(fontWeight: FontWeight.bold),
                          ),
                          IconButton(
                            icon: const Icon(Icons.chevron_right, size: 28),
                            onPressed: !_isLastPage && !_isLoading ? _nextPage : null,
                          ),
                        ],
                      ),
                    ],
                  ),
                ),

                // Main Table / List View
                Expanded(
                  child: _isLoading
                      ? const Center(child: CircularProgressIndicator(color: Color(0xFF835C9F)))
                      : _orders.isEmpty
                          ? const Center(
                              child: Text(
                                "No matching records found.",
                                style: TextStyle(fontSize: 16, color: Colors.grey, fontWeight: FontWeight.bold),
                              ),
                            )
                          : ListView.builder(
                              itemCount: _orders.length,
                              padding: const EdgeInsets.symmetric(horizontal: 12),
                              itemBuilder: (context, index) {
                                final order = _orders[index];
                                final isActionable = _isOrderActionableByRole(order);
                                
                                return Card(
                                  margin: const EdgeInsets.symmetric(vertical: 6),
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(16),
                                    side: BorderSide(
                                      color: order.isSelected
                                          ? const Color(0xFF835C9F)
                                          : Colors.black.withOpacity(0.05),
                                      width: order.isSelected ? 1.5 : 0.5,
                                    ),
                                  ),
                                  elevation: 2,
                                  color: Colors.white.withOpacity(0.9),
                                  child: InkWell(
                                    borderRadius: BorderRadius.circular(16),
                                    onTap: () {
                                      Navigator.push(
                                        context,
                                        MaterialPageRoute(
                                          builder: (_) => OrderViewScreen(orderId: order.id),
                                        ),
                                      ).then((_) => _loadInitialData());
                                    },
                                    child: Padding(
                                      padding: const EdgeInsets.all(12.0),
                                      child: Row(
                                        children: [
                                          // Checkbox
                                          Checkbox(
                                            value: order.isSelected,
                                            activeColor: const Color(0xFF835C9F),
                                            onChanged: (val) {
                                              setState(() {
                                                order.isSelected = val ?? false;
                                              });
                                            },
                                          ),
                                          const SizedBox(width: 8),
                                          
                                          // Details Info
                                          Expanded(
                                            child: Column(
                                              crossAxisAlignment: CrossAxisAlignment.start,
                                              children: [
                                                Row(
                                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                                  children: [
                                                    Text(
                                                      order.id,
                                                      style: const TextStyle(
                                                        fontFamily: 'monospace',
                                                        fontWeight: FontWeight.bold,
                                                        fontSize: 15,
                                                        color: Color(0xFF835C9F),
                                                      ),
                                                    ),
                                                    Text(
                                                      "₱${order.grandTotal.toStringAsFixed(2)}",
                                                      style: const TextStyle(
                                                        fontWeight: FontWeight.bold,
                                                        fontSize: 15,
                                                        color: Color(0xFF835C9F),
                                                      ),
                                                    ),
                                                  ],
                                                ),
                                                const SizedBox(height: 6),
                                                Text(
                                                  order.customerName,
                                                  maxLines: 1,
                                                  overflow: TextOverflow.ellipsis,
                                                  style: const TextStyle(
                                                    fontWeight: FontWeight.bold,
                                                    fontSize: 14,
                                                    color: Colors.black87,
                                                  ),
                                                ),
                                                const SizedBox(height: 6),
                                                Row(
                                                  children: [
                                                    Text(
                                                      order.date,
                                                      style: const TextStyle(fontSize: 12, color: Colors.grey, fontStyle: FontStyle.italic),
                                                    ),
                                                    const Text(" · ", style: TextStyle(color: Colors.grey)),
                                                    Text(
                                                      order.territory,
                                                      style: const TextStyle(fontSize: 12, color: Colors.grey),
                                                    ),
                                                  ],
                                                ),
                                                const SizedBox(height: 8),
                                                Row(
                                                  children: [
                                                    // Approval status badge
                                                    _badge(order.approvalStatus, _getApprovalColor(order.approvalStatus)),
                                                    const SizedBox(width: 6),
                                                    // OFS Status badge
                                                    _badge(order.ofsStatus, _getFulfillmentColor(order.ofsStatus)),
                                                  ],
                                                ),
                                              ],
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                  ),
                                );
                              },
                            ),
                ),
                
                // Bottom bar
                if (showActionsButton)
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withOpacity(0.1),
                          blurRadius: 10,
                          offset: const Offset(0, -5),
                        )
                      ],
                    ),
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF835C9F),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(30),
                        ),
                      ),
                      onPressed: () {
                        if (_isMedRep()) {
                          final selectedRejected = selected.where(_isRejectedOrder).toList();
                          if (selectedRejected.length == 1) {
                            _openRejectedOrderForEditing(selectedRejected[0]);
                          } else {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text("Please select only ONE rejected order to edit at a time.")),
                            );
                          }
                        } else {
                          _showRoleBasedActionDialog();
                        }
                      },
                      child: Text(
                        actionsButtonLabel.toUpperCase(),
                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                      ),
                    ),
                  )
                else
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withOpacity(0.1),
                          blurRadius: 10,
                          offset: const Offset(0, -5),
                        )
                      ],
                    ),
                    child: ElevatedButton.icon(
                      icon: const Icon(Icons.add, color: Colors.black87),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFFDFDFC7),
                        foregroundColor: Colors.black87,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                          side: const BorderSide(color: Colors.black12, width: 1),
                        ),
                      ),
                      onPressed: () {
                        Navigator.of(context).pushNamed('/order_entry').then((_) => _loadInitialData());
                      },
                      label: const Text(
                        "CREATE NEW ORDER",
                        style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _badge(String text, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        border: Border.all(color: color.withOpacity(0.5), width: 1),
        borderRadius: BorderRadius.circular(4),
      ),
      child: Text(
        text,
        style: TextStyle(
          color: color,
          fontSize: 10,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}
