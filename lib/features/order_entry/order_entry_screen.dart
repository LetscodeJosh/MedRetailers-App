import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'order_entry_provider.dart';
import 'new_order_repository.dart';
import '../../models/order_item.dart';
import '../../core/app_theme.dart';
import '../../core/widgets/glass_button.dart';

class OrderEntryScreen extends StatefulWidget {
  const OrderEntryScreen({super.key});

  @override
  State<OrderEntryScreen> createState() => _OrderEntryScreenState();
}

class _OrderEntryScreenState extends State<OrderEntryScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final NewOrderRepository _repository = NewOrderRepository();
  final OrderEntryProvider _provider = OrderEntryProvider();

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 4, vsync: this);
    if (!_provider.isEditMode) {
      _provider.clearData();
      _provider.transactionDate = DateTime.now().toString().split(' ')[0];
      _provider.deliveryDate = DateTime.now().toString().split(' ')[0];
    }
  }

  @override
  void dispose() {
    _tabController.dispose();
    _provider.clearData();
    super.dispose();
  }

  Future<void> _submitOrder() async {
    if (_provider.customerId == null || _provider.company == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("Please select a company and customer")));
      _tabController.animateTo(0);
      return;
    }
    if (_provider.items.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("Please add at least one item")));
      _tabController.animateTo(1);
      return;
    }

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => const Center(child: CircularProgressIndicator()),
    );

    final result = await _repository.submitOrder(
      customer: _provider.customerId!,
      company: _provider.company!,
      transactionDate: _provider.transactionDate,
      deliveryDate: _provider.deliveryDate,
      paymentTerms: _provider.paymentTerms,
      items: _provider.items,
      isEdit: _provider.isEditMode,
      editingOrderId: _provider.editingOrderId,
      fulfillmentStatus: _provider.fulfillmentStatus,
      // ── Address tab fields — using ERPNext v15 field names ──────────────
      customerAddress: _provider.customerAddressName, // customer_address
      contactPerson:   _provider.contactPerson,       // contact_person (link)
      contactMobile:   _provider.mobileNumber,        // contact_mobile
      territory:       _provider.territory,           // territory
      addressDisplay:  _provider.fullAddress,         // address_display
    );

    if (mounted) {
      Navigator.pop(context);
      if (result['success']) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("✅ Order Submitted Successfully!")));
        _provider.clearData();
        Navigator.pop(context);
      } else {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text("❌ Submission Failed: ${result['message']}")));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider.value(
      value: _provider,
      child: Scaffold(
        appBar: AppBar(
          title: Text(_provider.isEditMode ? "Order: ${_provider.editingOrderId}" : "New Sales Order"),
          bottom: PreferredSize(
            preferredSize: const Size.fromHeight(48),
            child: Align(
              alignment: Alignment.center,
              child: Container(
                constraints: const BoxConstraints(maxWidth: 480),
                child: TabBar(
                  controller: _tabController,
                  isScrollable: false,
                  tabs: const [
                    Tab(text: "Details",  icon: Icon(Icons.info_outline)),
                    Tab(text: "Items",    icon: Icon(Icons.shopping_cart_outlined)),
                    Tab(text: "Address",  icon: Icon(Icons.location_on_outlined)),
                    Tab(text: "Terms",    icon: Icon(Icons.description_outlined)),
                  ],
                ),
              ),
            ),
          ),
          actions: [
            Align(
              alignment: Alignment.center,
              child: Padding(
                padding: const EdgeInsets.only(right: 12.0),
                child: IconButton(
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                  icon: const Icon(Icons.check_circle, color: Colors.green, size: 26),
                  onPressed: _submitOrder,
                  tooltip: "Submit Order",
                ),
              ),
            ),
          ],
        ),
        body: Stack(
          children: [
            Positioned.fill(
              child: Opacity(
                opacity: 0.12,
                child: Image.asset('assets/images/dna_background.jpg', fit: BoxFit.cover),
              ),
            ),
            TabBarView(
              controller: _tabController,
              children: const [
                _OrderDetailsTab(),
                _OrderItemListTab(),
                _AddressTab(),
                _TermsTab(),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// DETAILS TAB
// ─────────────────────────────────────────────────────────────────────────────

class _OrderDetailsTab extends StatefulWidget {
  const _OrderDetailsTab();
  @override
  State<_OrderDetailsTab> createState() => _OrderDetailsTabState();
}

class _OrderDetailsTabState extends State<_OrderDetailsTab> with AutomaticKeepAliveClientMixin {
  final NewOrderRepository _repository = NewOrderRepository();
  List<String> _companies = [];
  bool _isLoadingCompanies = false;

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    _loadCompanies();
  }

  Future<void> _loadCompanies() async {
    setState(() => _isLoadingCompanies = true);
    final comps = await _repository.fetchCompanies();
    setState(() {
      _companies = comps;
      _isLoadingCompanies = false;
    });
    if (_companies.length == 1 && mounted) {
      context.read<OrderEntryProvider>().updateCompany(_companies[0]);
    }
  }

  /// Called immediately after the customer is tapped in the picker.
  /// Reads the NEW repository return keys and maps them into the provider.
  Future<void> _fetchCustomerDetails(String customerId) async {
    if (!mounted) return;
    final provider = context.read<OrderEntryProvider>();

    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Row(
          children: [
            SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white)),
            SizedBox(width: 16),
            Text("Loading customer address and contacts..."),
          ],
        ),
        duration: Duration(seconds: 2),
      ),
    );

    final details = await _repository.fetchCustomerDetails(customerId);
    if (!mounted) return;

    if (details['success'] == true) {
      provider.updateCustomerDetails(
        // ── Map NEW repository keys → provider fields ──────────────────────
        addressName: details['customer_address']       ?? "",  // customer_address
        address:     details['address_display']        ?? "",  // address_display
        terr:        details['territory']              ?? "",  // territory
        contact:     details['contact_person']         ?? "",  // contact_person (link)
        mobile:      details['contact_mobile']         ?? "",  // contact_mobile
        // Human-readable contact name for display in the UI label
        contactDisplay: details['contact_person_display'] ?? customerId,
      );
    }
  }

  Future<void> _selectCustomer(BuildContext context) async {
    final provider = context.read<OrderEntryProvider>();
    if (provider.company == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text("Please select a company first")));
      return;
    }

    showDialog(
      context: context,
      builder: (dialogCtx) => FutureBuilder<Map<String, String>>(
        future: _repository.fetchFilteredCustomers(provider.company),
        builder: (futureCtx, snapshot) {
          if (!snapshot.hasData) return const Center(child: CircularProgressIndicator());
          final allCustomers = snapshot.data!;
          String query = "";

          return StatefulBuilder(
            builder: (statefulCtx, setDialogState) {
              final filtered = allCustomers.entries
                  .where((e) =>
                      e.key.toLowerCase().contains(query.toLowerCase()) ||
                      e.value.toLowerCase().contains(query.toLowerCase()))
                  .toList();

              return AlertDialog(
                title: const Text("Select Customer"),
                content: SizedBox(
                  width: double.maxFinite,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      TextField(
                        decoration: const InputDecoration(
                          hintText: "Search customer name or ID...",
                          prefixIcon: Icon(Icons.search),
                        ),
                        onChanged: (val) => setDialogState(() => query = val),
                      ),
                      const SizedBox(height: 12),
                      Expanded(
                        child: filtered.isEmpty
                            ? const Center(child: Text("No customers found.", style: TextStyle(color: Colors.grey)))
                            : ListView.builder(
                                shrinkWrap: true,
                                itemCount: filtered.length,
                                itemBuilder: (listCtx, index) {
                                  final name = filtered[index].key;
                                  final id   = filtered[index].value;
                                  return ListTile(
                                    title: Text(name),
                                    subtitle: Text(id, style: const TextStyle(fontSize: 10, color: Colors.grey)),
                                    onTap: () {
                                      provider.updateCustomer(name, id);
                                      Navigator.pop(dialogCtx);
                                      _fetchCustomerDetails(id); // triggers auto-fill
                                    },
                                  );
                                },
                              ),
                      ),
                    ],
                  ),
                ),
              );
            },
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final provider = context.watch<OrderEntryProvider>();
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text("Identification", style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: AppTheme.primaryPurple)),
          const SizedBox(height: 24),
          _buildLabel("Entity Company"),
          _isLoadingCompanies
              ? const LinearProgressIndicator()
              : DropdownButtonFormField<String>(
                  value: provider.company,
                  hint: const Text("Select Company"),
                  items: _companies.map((c) => DropdownMenuItem(value: c, child: Text(c))).toList(),
                  onChanged: provider.isEditMode ? null : (val) => provider.updateCompany(val),
                  decoration: InputDecoration(
                    filled: provider.isEditMode,
                    fillColor: provider.isEditMode ? Colors.grey.shade200 : null,
                  ),
                ),
          const SizedBox(height: 24),
          _buildLabel("Customer Selector"),
          InkWell(
            onTap: provider.isEditMode ? null : () => _selectCustomer(context),
            child: IgnorePointer(
              child: TextField(
                controller: TextEditingController(text: provider.customer),
                decoration: InputDecoration(
                  hintText: "Tap to search customers...",
                  prefixIcon: const Icon(Icons.person_search),
                  suffixIcon: const Icon(Icons.arrow_drop_down),
                  filled: provider.isEditMode,
                  fillColor: provider.isEditMode ? Colors.grey.shade200 : null,
                ),
              ),
            ),
          ),
          const SizedBox(height: 24),
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _buildLabel("Approval Status"),
                    TextField(
                      readOnly: true,
                      controller: TextEditingController(text: provider.approvalStatus),
                      decoration: const InputDecoration(
                        prefixIcon: Icon(Icons.rate_review_outlined),
                        fillColor: Color(0x0A000000),
                        filled: true,
                        border: UnderlineInputBorder(),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _buildLabel("Order Fulfillment Status"),
                    TextField(
                      readOnly: true,
                      controller: TextEditingController(text: provider.fulfillmentStatus),
                      decoration: const InputDecoration(
                        prefixIcon: Icon(Icons.assignment_turned_in_outlined),
                        fillColor: Color(0x0A000000),
                        filled: true,
                        border: UnderlineInputBorder(),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 32),
          const Text("Timeline", style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: AppTheme.primaryPurple)),
          const SizedBox(height: 24),
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _buildLabel("Order Date"),
                    TextField(
                      readOnly: true,
                      controller: TextEditingController(text: provider.transactionDate),
                      decoration: const InputDecoration(prefixIcon: Icon(Icons.calendar_today)),
                      onTap: () async {
                        final date = await showDatePicker(
                          context: context,
                          initialDate: DateTime.now(),
                          firstDate: DateTime(2000),
                          lastDate: DateTime(2100),
                        );
                        if (date != null) setState(() => provider.transactionDate = date.toString().split(' ')[0]);
                      },
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _buildLabel("Delivery Date"),
                    TextField(
                      readOnly: true,
                      controller: TextEditingController(text: provider.deliveryDate),
                      decoration: const InputDecoration(prefixIcon: Icon(Icons.local_shipping)),
                      onTap: () async {
                        final date = await showDatePicker(
                          context: context,
                          initialDate: DateTime.now(),
                          firstDate: DateTime(2000),
                          lastDate: DateTime(2100),
                        );
                        if (date != null) setState(() => provider.deliveryDate = date.toString().split(' ')[0]);
                      },
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildLabel(String text) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8.0, left: 4),
      child: Text(text, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14, color: Colors.blueGrey)),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// ITEMS TAB
// ─────────────────────────────────────────────────────────────────────────────

class _OrderItemListTab extends StatefulWidget {
  const _OrderItemListTab();
  @override
  State<_OrderItemListTab> createState() => _OrderItemListTabState();
}

class _OrderItemListTabState extends State<_OrderItemListTab> with AutomaticKeepAliveClientMixin {
  @override
  bool get wantKeepAlive => true;

  Future<void> _addItem(BuildContext context) async {
    final provider    = context.read<OrderEntryProvider>();
    final repository  = NewOrderRepository();
    showDialog(
      context: context,
      builder: (context) => _AddItemDialog(
        repository: repository,
        onAdd: (item) => provider.addItem(item),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final provider = context.watch<OrderEntryProvider>();
    return Column(
      children: [
        Expanded(
          child: provider.items.isEmpty
              ? const Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.shopping_basket_outlined, size: 64, color: Colors.grey),
                      SizedBox(height: 16),
                      Text("No items in this order."),
                    ],
                  ),
                )
              : ListView.builder(
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  itemCount: provider.items.length,
                  itemBuilder: (context, index) {
                    final item = provider.items[index];
                    return Card(
                      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                      elevation: 1,
                      color: Colors.white.withOpacity(0.65),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                        side: BorderSide(color: Colors.white.withOpacity(0.5), width: 1.0),
                      ),
                      child: ListTile(
                        contentPadding: const EdgeInsets.all(16),
                        title: Text(
                          item.itemName != null && item.itemName!.isNotEmpty
                              ? "${item.itemCode} - ${item.itemName}"
                              : item.itemCode,
                          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                        ),
                        subtitle: Padding(
                          padding: const EdgeInsets.only(top: 8.0),
                          child: Text("Qty: ${item.quantity} ${item.uom} | Rate: ₱${item.rate.toStringAsFixed(2)}"),
                        ),
                        trailing: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            Text(
                              "₱${item.amount.toStringAsFixed(2)}",
                              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16, color: AppTheme.primaryPurple),
                            ),
                            const SizedBox(height: 4),
                            Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                InkWell(
                                  onTap: () {
                                    final repository = NewOrderRepository();
                                    showDialog(
                                      context: context,
                                      builder: (dialogCtx) => _EditItemDialog(
                                        repository: repository,
                                        item: item,
                                        onEdit: (updated) {
                                          provider.updateItem(index, updated);
                                        },
                                      ),
                                    );
                                  },
                                  child: const Text("Edit", style: TextStyle(color: Colors.blue, fontSize: 12, fontWeight: FontWeight.bold)),
                                ),
                                const SizedBox(width: 12),
                                InkWell(
                                  onTap: () => provider.removeItem(index),
                                  child: const Text("Remove", style: TextStyle(color: Colors.red, fontSize: 12, fontWeight: FontWeight.bold)),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
        ),
        _buildBottomPanel(context, provider),
      ],
    );
  }

  Widget _buildBottomPanel(BuildContext context, OrderEntryProvider provider) {
    return ClipRect(
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 15, sigmaY: 15),
        child: Container(
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            color: Colors.white.withOpacity(0.7),
            borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
            border: Border(top: BorderSide(color: Colors.white.withOpacity(0.4), width: 1.0)),
            boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.04), blurRadius: 12, offset: const Offset(0, -4))],
          ),
          child: SafeArea(
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text("GRAND TOTAL", style: TextStyle(color: Colors.grey[600], letterSpacing: 1.2, fontWeight: FontWeight.bold, fontSize: 12)),
                    Text("₱${provider.totalAmount.toStringAsFixed(2)}", style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 24, color: AppTheme.primaryPurple)),
                  ],
                ),
                GlassButton(
                  width: 150,
                  onPressed: () => _addItem(context),
                  color: AppTheme.primaryPurple,
                  child: const Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.add, color: Colors.white),
                      SizedBox(width: 8),
                      Text("ADD ITEM", style: TextStyle(fontWeight: FontWeight.bold, color: Colors.white)),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// ADDRESS TAB
// ─────────────────────────────────────────────────────────────────────────────

class _AddressTab extends StatefulWidget {
  const _AddressTab();
  @override
  State<_AddressTab> createState() => _AddressTabState();
}

class _AddressTabState extends State<_AddressTab> with AutomaticKeepAliveClientMixin {
  @override
  bool get wantKeepAlive => true;

  bool   _isFetching                  = false;
  String? _lastAttemptedFetchCustomerId;

  /// Secondary safety net: if the user navigates directly to the Address tab
  /// before _OrderDetailsTabState has had a chance to fetch, this triggers the
  /// fetch automatically using the same corrected key mapping.
  Future<void> _fetchDetailsIfNeeded(OrderEntryProvider provider) async {
    final customerId = provider.customerId;
    if (customerId == null || customerId.isEmpty) return;

    // Skip if we already fetched for this customer or a fetch is in progress
    if (_isFetching || _lastAttemptedFetchCustomerId == customerId) return;

    // Only fetch if the address fields are still empty / default
    final needsFetch = provider.fullAddress.isEmpty || provider.fullAddress == "No Address Listed";
    if (!needsFetch) return;

    setState(() {
      _isFetching                   = true;
      _lastAttemptedFetchCustomerId = customerId;
    });

    final repository = NewOrderRepository();
    final details    = await repository.fetchCustomerDetails(customerId);

    if (mounted) {
      if (details['success'] == true) {
        provider.updateCustomerDetails(
          // ── Map NEW repository keys → provider fields ────────────────────
          addressName:    details['customer_address']        ?? "",
          address:        details['address_display']         ?? "",
          terr:           details['territory']               ?? "",
          contact:        details['contact_person']          ?? "",
          mobile:         details['contact_mobile']          ?? "",
          contactDisplay: details['contact_person_display']  ?? customerId,
        );
      }
      setState(() => _isFetching = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final provider = context.watch<OrderEntryProvider>();

    // Auto-fetch when this tab is rendered and data is missing
    if (provider.customerId != null && provider.customerId!.isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _fetchDetailsIfNeeded(provider);
      });
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Text("Delivery & Contact", style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: AppTheme.primaryPurple)),
              if (_isFetching) ...[
                const SizedBox(width: 12),
                const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
              ],
            ],
          ),
          const SizedBox(height: 24),

          // customer_address — the Address doctype link name
          _buildField(
            label:        "Customer Address",
            value:        provider.customerAddressName,
            onChanged:    (val) => provider.customerAddressName = val,
          ),
          const SizedBox(height: 16),

          // contact_person — shows the human-readable name (contact_person_display)
          // but the provider internally stores the Contact doc link for submission
          _buildField(
            label:        "Contact Person",
            value:        provider.contactPersonDisplay,
            onChanged:    (val) => provider.contactPersonDisplay = val,
          ),
          const SizedBox(height: 16),

          // contact_mobile
          _buildField(
            label:        "Mobile Number",
            value:        provider.mobileNumber,
            onChanged:    (val) => provider.mobileNumber = val,
            keyboardType: TextInputType.phone,
          ),
          const SizedBox(height: 16),

          // territory
          _buildField(
            label:     "Territory",
            value:     provider.territory,
            onChanged: (val) => provider.territory = val,
          ),
          const SizedBox(height: 16),

          // address_display
          _buildField(
            label:     "Full Address",
            value:     provider.fullAddress,
            onChanged: (val) => provider.fullAddress = val,
            maxLines:  4,
          ),
        ],
      ),
    );
  }

  Widget _buildField({
    required String label,
    required String value,
    required Function(String) onChanged,
    TextInputType? keyboardType,
    int maxLines = 1,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14, color: Colors.blueGrey)),
        const SizedBox(height: 8),
        TextFormField(
          // ValueKey forces the field to rebuild when the value changes after fetch
          key:          ValueKey(value),
          initialValue: value,
          readOnly:     true,
          onChanged:    onChanged,
          maxLines:     maxLines,
          keyboardType: keyboardType,
          decoration: InputDecoration(
            hintText:  value.isEmpty ? "Will be filled automatically..." : null,
            fillColor: const Color(0x0A000000),
            filled:    true,
          ),
        ),
      ],
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// TERMS TAB
// ─────────────────────────────────────────────────────────────────────────────

class _TermsTab extends StatefulWidget {
  const _TermsTab();
  @override
  State<_TermsTab> createState() => _TermsTabState();
}

class _TermsTabState extends State<_TermsTab> with AutomaticKeepAliveClientMixin {
  final NewOrderRepository _repository = NewOrderRepository();
  List<String> _templates         = [];
  bool          _isLoadingTemplates = false;

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    _loadTemplates();
  }

  Future<void> _loadTemplates() async {
    if (mounted) setState(() => _isLoadingTemplates = true);
    final list = await _repository.fetchPaymentTermsTemplates();
    if (mounted) setState(() { _templates = list; _isLoadingTemplates = false; });
  }

  Future<void> _onTemplateSelected(BuildContext context, String? val) async {
    if (val == null) return;
    final provider = context.read<OrderEntryProvider>();
    provider.paymentTerms = val;

    final terms = await _repository.fetchPaymentTermsDetails(val);
    final total = provider.totalAmount;
    final schedule = terms.map((t) {
      final double portion = (t['invoice_portion'] ?? 0.0).toDouble();
      return {
        'payment_term':   t['payment_term']?.toString() ?? "N/A",
        'description':    t['description']?.toString()  ?? "",
        'invoice_portion': portion,
        'payment_amount': (total * portion) / 100.0,
      };
    }).toList();

    if (mounted) {
      setState(() => provider.paymentSchedule = schedule);
      provider.notifyListeners();
    }
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final provider = context.watch<OrderEntryProvider>();
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text("Payment Terms Template", style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: AppTheme.primaryPurple)),
          const SizedBox(height: 16),
          _isLoadingTemplates
              ? const LinearProgressIndicator()
              : DropdownButtonFormField<String>(
                  value: _templates.contains(provider.paymentTerms) ? provider.paymentTerms : null,
                  hint: const Text("Select Payment Terms Template"),
                  items: _templates.map((t) => DropdownMenuItem(value: t, child: Text(t))).toList(),
                  onChanged: (val) => _onTemplateSelected(context, val),
                  decoration: const InputDecoration(prefixIcon: Icon(Icons.payment)),
                ),
          const SizedBox(height: 32),
          const Text("Payment Schedule Breakdown", style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppTheme.primaryPurple)),
          const SizedBox(height: 12),
          provider.paymentSchedule.isEmpty
              ? Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(color: Colors.grey.shade100, borderRadius: BorderRadius.circular(8)),
                  child: const Center(child: Text("Select a Payment Terms Template to view the schedule breakdown.", style: TextStyle(color: Colors.grey))),
                )
              : LayoutBuilder(
                  builder: (context, constraints) {
                    final double tableWidth = constraints.maxWidth > 550 ? constraints.maxWidth : 550;
                    return SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      child: Container(
                        width: tableWidth,
                        decoration: BoxDecoration(borderRadius: BorderRadius.circular(12), border: Border.all(color: Colors.grey.shade200)),
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(12),
                          child: Table(
                            columnWidths: const {
                              0: FixedColumnWidth(130),
                              1: FlexColumnWidth(),
                              2: FixedColumnWidth(90),
                              3: FixedColumnWidth(130),
                            },
                            defaultVerticalAlignment: TableCellVerticalAlignment.middle,
                            children: [
                              const TableRow(
                                decoration: BoxDecoration(color: Color(0xFFF2F2F2)),
                                children: [
                                  Padding(padding: EdgeInsets.all(12), child: Text("Term",        style: TextStyle(fontWeight: FontWeight.bold))),
                                  Padding(padding: EdgeInsets.all(12), child: Text("Description", style: TextStyle(fontWeight: FontWeight.bold))),
                                  Padding(padding: EdgeInsets.all(12), child: Text("Portion",     style: TextStyle(fontWeight: FontWeight.bold), textAlign: TextAlign.right)),
                                  Padding(padding: EdgeInsets.all(12), child: Text("Amount",      style: TextStyle(fontWeight: FontWeight.bold), textAlign: TextAlign.right)),
                                ],
                              ),
                              ...provider.paymentSchedule.map((t) => TableRow(
                                children: [
                                  Padding(padding: const EdgeInsets.all(12), child: Text(t['payment_term']?.toString() ?? "N/A")),
                                  Padding(padding: const EdgeInsets.all(12), child: Text(t['description']?.toString()  ?? "")),
                                  Padding(padding: const EdgeInsets.all(12), child: Text("${t['invoice_portion']}%",                               textAlign: TextAlign.right)),
                                  Padding(padding: const EdgeInsets.all(12), child: Text("₱${t['payment_amount']?.toStringAsFixed(2)}", textAlign: TextAlign.right, style: const TextStyle(fontWeight: FontWeight.bold))),
                                ],
                              )),
                            ],
                          ),
                        ),
                      ),
                    );
                  },
                ),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// ADD ITEM DIALOG
// ─────────────────────────────────────────────────────────────────────────────

class _AddItemDialog extends StatefulWidget {
  final NewOrderRepository repository;
  final Function(OrderItem) onAdd;
  const _AddItemDialog({required this.repository, required this.onAdd});
  @override
  State<_AddItemDialog> createState() => _AddItemDialogState();
}

class _AddItemDialogState extends State<_AddItemDialog> {
  String? _selectedCode;
  String? _selectedName;
  double  _originalRate = 0.0;
  double  _rate = 0.0;
  String  _uom  = "Box";
  bool    _isFree = false;
  bool    _isMedRep = true;
  final   _qtyController = TextEditingController(text: "1");
  late    Future<Map<String, dynamic>> _itemsFuture;

  @override
  void initState() {
    super.initState();
    _itemsFuture = widget.repository.fetchItems();
    SharedPreferences.getInstance().then((prefs) {
      final role = prefs.getString("User_Role") ?? "MedRep";
      if (mounted) {
        setState(() {
          _isMedRep = role.toLowerCase() == "medrep" || role.toLowerCase() == "admin";
        });
      }
    });
  }

  void _showProductSearchDialog(BuildContext context, List items) {
    String searchQuery = "";
    showDialog(
      context: context,
      builder: (dialogCtx) => StatefulBuilder(
        builder: (context, setDialogState) {
          final filtered = items.where((i) {
            final code = (i['item_code'] ?? "").toString().toLowerCase();
            final name = (i['item_name'] ?? "").toString().toLowerCase();
            return code.contains(searchQuery.toLowerCase()) || name.contains(searchQuery.toLowerCase());
          }).toList();

          return AlertDialog(
            title: const Text("Search Product"),
            content: SizedBox(
              width: double.maxFinite,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(
                    decoration: const InputDecoration(hintText: "Search item code or name...", prefixIcon: Icon(Icons.search)),
                    onChanged: (val) => setDialogState(() => searchQuery = val),
                  ),
                  const SizedBox(height: 12),
                  Expanded(
                    child: filtered.isEmpty
                        ? const Center(child: Text("No items found", style: TextStyle(color: Colors.grey)))
                        : ListView.builder(
                            shrinkWrap: true,
                            itemCount: filtered.length,
                            itemBuilder: (context, index) {
                              final i    = filtered[index];
                              final code = i['item_code'] ?? "";
                              final name = i['item_name'] ?? "";
                              return ListTile(
                                title: Text("$code - $name"),
                                subtitle: Text("UOM: ${i['uom']} | Price: ₱${(i['price_list_rate'] ?? 0.0).toStringAsFixed(2)}"),
                                onTap: () {
                                  setState(() {
                                    _selectedCode = code;
                                    _selectedName = name;
                                    _originalRate = (i['price_list_rate'] ?? 0.0).toDouble();
                                    _rate         = _isFree ? 0.0 : _originalRate;
                                    _uom          = i['uom'] ?? "Box";
                                  });
                                  Navigator.pop(dialogCtx);
                                },
                              );
                            },
                          ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<Map<String, dynamic>>(
      future: _itemsFuture,
      builder: (context, snapshot) {
        if (!snapshot.hasData) return const Center(child: CircularProgressIndicator());
        final items = snapshot.data!['items'] as List;
        return AlertDialog(
          title: const Text("Select Product"),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                GestureDetector(
                  onTap: () => _showProductSearchDialog(context, items),
                  child: InputDecorator(
                    decoration: const InputDecoration(labelText: "Product", prefixIcon: Icon(Icons.search), suffixIcon: Icon(Icons.arrow_drop_down)),
                    child: Text(
                      _selectedCode != null ? "$_selectedCode - ${_selectedName ?? ''}" : "Tap to select product...",
                      style: TextStyle(color: _selectedCode != null ? Colors.black87 : Colors.grey[600]),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: _qtyController,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: "Quantity", prefixIcon: Icon(Icons.add_shopping_cart)),
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Checkbox(
                      value: _isFree,
                      activeColor: AppTheme.primaryPurple,
                      onChanged: (_selectedCode == null || !_isMedRep) ? null : (val) {
                        setState(() {
                          _isFree = val ?? false;
                          _rate = _isFree ? 0.0 : _originalRate;
                        });
                      },
                    ),
                    const Text("Free Item"),
                  ],
                ),
                const SizedBox(height: 16),
                DropdownButtonFormField<String>(
                  value: _uom,
                  decoration: const InputDecoration(labelText: "UOM"),
                  items: ["Box", "Piece", "Bottle", "Vial", "Pack", "Tablet", "Capsule"]
                      .map((u) => DropdownMenuItem(value: u, child: Text(u)))
                      .toList(),
                  onChanged: (val) {
                    if (val != null) {
                      setState(() => _uom = val);
                    }
                  },
                ),
                const SizedBox(height: 24),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(color: AppTheme.primaryPurple.withOpacity(0.05), borderRadius: BorderRadius.circular(8)),
                  child: Column(
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text("Unit Price:"),
                          Text(
                            _selectedCode == null
                                ? ""
                                : (_isFree ? "₱ 0.00" : "₱${_rate.toStringAsFixed(2)}"),
                            style: const TextStyle(fontWeight: FontWeight.bold),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context), child: const Text("CANCEL")),
            GlassButton(
              width: 160,
              onPressed: _selectedCode == null
                  ? null
                  : () {
                      final qty = int.tryParse(_qtyController.text) ?? 1;
                      widget.onAdd(OrderItem(
                        itemCode:  _selectedCode!,
                        itemName:  _selectedName ?? "",
                        quantity:  qty,
                        rate:      _rate,
                        uom:       _uom,
                      ));
                      Navigator.pop(context);
                    },
              color: AppTheme.primaryPurple,
              child: const Text("ADD TO CART", maxLines: 1, style: TextStyle(fontWeight: FontWeight.bold, color: Colors.white)),
            ),
          ],
        );
      },
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// EDIT ITEM DIALOG
// ─────────────────────────────────────────────────────────────────────────────

class _EditItemDialog extends StatefulWidget {
  final NewOrderRepository repository;
  final OrderItem item;
  final Function(OrderItem) onEdit;
  const _EditItemDialog({required this.repository, required this.item, required this.onEdit});
  @override
  State<_EditItemDialog> createState() => _EditItemDialogState();
}

class _EditItemDialogState extends State<_EditItemDialog> {
  String? _selectedCode;
  String? _selectedName;
  double  _originalRate = 0.0;
  double  _rate = 0.0;
  String  _uom  = "Box";
  bool    _isFree = false;
  bool    _isMedRep = true;
  late TextEditingController _qtyController;
  late Future<Map<String, dynamic>> _itemsFuture;

  @override
  void initState() {
    super.initState();
    _selectedCode = widget.item.itemCode;
    _selectedName = widget.item.itemName;
    _rate = widget.item.rate;
    _isFree = widget.item.rate == 0.0;
    _uom = widget.item.uom;
    _qtyController = TextEditingController(text: widget.item.quantity.toString());
    _itemsFuture = widget.repository.fetchItems();
    
    SharedPreferences.getInstance().then((prefs) {
      final role = prefs.getString("User_Role") ?? "MedRep";
      if (mounted) {
        setState(() {
          _isMedRep = role.toLowerCase() == "medrep" || role.toLowerCase() == "admin";
        });
      }
    });
    
    // Attempt to resolve the original rate of the item
    _itemsFuture.then((data) {
      final items = data['items'] as List? ?? [];
      final matching = items.firstWhere(
        (i) => i['item_code'] == widget.item.itemCode,
        orElse: () => null,
      );
      if (matching != null && mounted) {
        setState(() {
          _originalRate = (matching['price_list_rate'] ?? 0.0).toDouble();
          if (!_isFree) {
            _rate = _originalRate;
          }
        });
      }
    });
  }

  void _showProductSearchDialog(BuildContext context, List items) {
    String searchQuery = "";
    showDialog(
      context: context,
      builder: (dialogCtx) => StatefulBuilder(
        builder: (context, setDialogState) {
          final filtered = items.where((i) {
            final code = (i['item_code'] ?? "").toString().toLowerCase();
            final name = (i['item_name'] ?? "").toString().toLowerCase();
            return code.contains(searchQuery.toLowerCase()) || name.contains(searchQuery.toLowerCase());
          }).toList();

          return AlertDialog(
            title: const Text("Search Product"),
            content: SizedBox(
              width: double.maxFinite,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(
                    decoration: const InputDecoration(hintText: "Search item code or name...", prefixIcon: Icon(Icons.search)),
                    onChanged: (val) => setDialogState(() => searchQuery = val),
                  ),
                  const SizedBox(height: 12),
                  Expanded(
                    child: filtered.isEmpty
                        ? const Center(child: Text("No items found", style: TextStyle(color: Colors.grey)))
                        : ListView.builder(
                            shrinkWrap: true,
                            itemCount: filtered.length,
                            itemBuilder: (context, index) {
                              final i    = filtered[index];
                              final code = i['item_code'] ?? "";
                              final name = i['item_name'] ?? "";
                              return ListTile(
                                title: Text("$code - $name"),
                                subtitle: Text("UOM: ${i['uom']} | Price: ₱${(i['price_list_rate'] ?? 0.0).toStringAsFixed(2)}"),
                                onTap: () {
                                  setState(() {
                                    _selectedCode = code;
                                    _selectedName = name;
                                    _originalRate = (i['price_list_rate'] ?? 0.0).toDouble();
                                    _rate         = _isFree ? 0.0 : _originalRate;
                                    _uom          = i['uom'] ?? "Box";
                                  });
                                  Navigator.pop(dialogCtx);
                                },
                              );
                            },
                          ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<Map<String, dynamic>>(
      future: _itemsFuture,
      builder: (context, snapshot) {
        if (!snapshot.hasData) return const Center(child: CircularProgressIndicator());
        final items = snapshot.data!['items'] as List;
        return AlertDialog(
          title: const Text("Edit Product"),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                GestureDetector(
                  onTap: () => _showProductSearchDialog(context, items),
                  child: InputDecorator(
                    decoration: const InputDecoration(labelText: "Product", prefixIcon: Icon(Icons.search), suffixIcon: Icon(Icons.arrow_drop_down)),
                    child: Text(
                      _selectedCode != null ? "$_selectedCode - ${_selectedName ?? ''}" : "Tap to select product...",
                      style: TextStyle(color: _selectedCode != null ? Colors.black87 : Colors.grey[600]),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: _qtyController,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: "Quantity", prefixIcon: Icon(Icons.add_shopping_cart)),
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Checkbox(
                      value: _isFree,
                      activeColor: AppTheme.primaryPurple,
                      onChanged: (_selectedCode == null || !_isMedRep) ? null : (val) {
                        setState(() {
                          _isFree = val ?? false;
                          _rate = _isFree ? 0.0 : _originalRate;
                        });
                      },
                    ),
                    const Text("Free Item"),
                  ],
                ),
                const SizedBox(height: 16),
                DropdownButtonFormField<String>(
                  value: ["Box", "Piece", "Bottle", "Vial", "Pack", "Tablet", "Capsule"].contains(_uom) ? _uom : "Box",
                  decoration: const InputDecoration(labelText: "UOM"),
                  items: ["Box", "Piece", "Bottle", "Vial", "Pack", "Tablet", "Capsule"]
                      .map((u) => DropdownMenuItem(value: u, child: Text(u)))
                      .toList(),
                  onChanged: (val) {
                    if (val != null) {
                      setState(() => _uom = val);
                    }
                  },
                ),
                const SizedBox(height: 24),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(color: AppTheme.primaryPurple.withOpacity(0.05), borderRadius: BorderRadius.circular(8)),
                  child: Column(
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text("Unit Price:"),
                          Text(
                            _selectedCode == null 
                                ? "" 
                                : (_isFree ? "₱ 0.00" : "₱${_rate.toStringAsFixed(2)}"),
                            style: const TextStyle(fontWeight: FontWeight.bold),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context), child: const Text("CANCEL")),
            GlassButton(
              width: 180,
              onPressed: _selectedCode == null
                  ? null
                  : () {
                      final qty = int.tryParse(_qtyController.text) ?? 1;
                      widget.onEdit(OrderItem(
                        itemCode:  _selectedCode!,
                        itemName:  _selectedName ?? "",
                        quantity:  qty,
                        rate:      _rate,
                        uom:       _uom,
                      ));
                      Navigator.pop(context);
                    },
              color: AppTheme.primaryPurple,
              child: const Text("SAVE CHANGES", maxLines: 1, style: TextStyle(fontWeight: FontWeight.bold, color: Colors.white)),
            ),
          ],
        );
      },
    );
  }
}