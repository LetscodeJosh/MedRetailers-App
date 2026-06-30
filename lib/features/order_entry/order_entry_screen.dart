import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'order_entry_provider.dart';
import 'new_order_repository.dart';
import '../../models/order_item.dart';
import '../../core/app_theme.dart';

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
      _provider.transactionDate = DateTime.now().toString().split(' ')[0];
      _provider.deliveryDate = DateTime.now().toString().split(' ')[0];
    }
  }

  @override
  void dispose() {
    _tabController.dispose();
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
    );

    if (mounted) {
      Navigator.pop(context); // Pop loading
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
          title: Text(_provider.isEditMode ? "Edit Order: ${_provider.editingOrderId}" : "New Sales Order"),
          bottom: TabBar(
            controller: _tabController,
            isScrollable: true,
            tabs: const [
              Tab(text: "Details", icon: Icon(Icons.info_outline)),
              Tab(text: "Items", icon: Icon(Icons.shopping_cart_outlined)),
              Tab(text: "Address", icon: Icon(Icons.location_on_outlined)),
              Tab(text: "Terms", icon: Icon(Icons.description_outlined)),
            ],
          ),
          actions: [
            IconButton(
              icon: const Icon(Icons.check_circle, color: Colors.green, size: 30),
              onPressed: _submitOrder,
              tooltip: "Submit Order",
            ),
          ],
        ),
        body: TabBarView(
          controller: _tabController,
          children: const [
             _OrderDetailsTab(),
             _OrderItemListTab(),
             _AddressTab(),
             _TermsTab(),
          ],
        ),
      ),
    );
  }
}

class _OrderDetailsTab extends StatefulWidget {
  const _OrderDetailsTab();
  @override
  State<_OrderDetailsTab> createState() => _OrderDetailsTabState();
}

class _OrderDetailsTabState extends State<_OrderDetailsTab> {
  final NewOrderRepository _repository = NewOrderRepository();
  List<String> _companies = [];
  bool _isLoadingCompanies = false;

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
    if (_companies.length == 1) {
      if (mounted) context.read<OrderEntryProvider>().updateCompany(_companies[0]);
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
      builder: (context) => FutureBuilder<Map<String, String>>(
        future: _repository.fetchFilteredCustomers(provider.company),
        builder: (context, snapshot) {
          if (!snapshot.hasData) return const Center(child: CircularProgressIndicator());
          final customers = snapshot.data!;
          return AlertDialog(
            title: const Text("Select Customer"),
            content: SizedBox(
              width: double.maxFinite,
              child: ListView.builder(
                shrinkWrap: true,
                itemCount: customers.length,
                itemBuilder: (context, index) {
                  final name = customers.keys.elementAt(index);
                  final id = customers[name]!;
                  return ListTile(
                    title: Text(name),
                    subtitle: Text(id, style: const TextStyle(fontSize: 10, color: Colors.grey)),
                    onTap: () {
                      provider.updateCustomer(name, id);
                      Navigator.pop(context);
                    },
                  );
                },
              ),
            ),
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
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
                onChanged: (val) => provider.updateCompany(val),
              ),
          const SizedBox(height: 24),
          _buildLabel("Customer Selector"),
          InkWell(
            onTap: () => _selectCustomer(context),
            child: IgnorePointer(
              child: TextField(
                controller: TextEditingController(text: provider.customer),
                decoration: const InputDecoration(
                  hintText: "Tap to search customers...",
                  prefixIcon: Icon(Icons.person_search),
                  suffixIcon: Icon(Icons.arrow_drop_down),
                ),
              ),
            ),
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
                        final date = await showDatePicker(context: context, initialDate: DateTime.now(), firstDate: DateTime(2000), lastDate: DateTime(2100));
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
                        final date = await showDatePicker(context: context, initialDate: DateTime.now(), firstDate: DateTime(2000), lastDate: DateTime(2100));
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

class _OrderItemListTab extends StatelessWidget {
  const _OrderItemListTab();

  Future<void> _addItem(BuildContext context) async {
    final provider = context.read<OrderEntryProvider>();
    final repository = NewOrderRepository();
    showDialog(
      context: context,
      builder: (context) => _AddItemDialog(repository: repository, onAdd: (item) => provider.addItem(item)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<OrderEntryProvider>();
    return Column(
      children: [
        Expanded(
          child: provider.items.isEmpty 
            ? const Center(child: Column(mainAxisSize: MainAxisSize.min, children: [Icon(Icons.shopping_basket_outlined, size: 64, color: Colors.grey), SizedBox(height: 16), Text("No items in this order.")]))
            : ListView.builder(
                padding: const EdgeInsets.symmetric(vertical: 16),
                itemCount: provider.items.length,
                itemBuilder: (context, index) {
                  final item = provider.items[index];
                  return Card(
                    margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                    elevation: 2,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    child: ListTile(
                      contentPadding: const EdgeInsets.all(16),
                      title: Text(item.itemCode, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                      subtitle: Padding(
                        padding: const EdgeInsets.only(top: 8.0),
                        child: Text("Qty: ${item.quantity} ${item.uom} | Rate: ₱${item.rate.toStringAsFixed(2)}"),
                      ),
                      trailing: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Text("₱${item.amount.toStringAsFixed(2)}", style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16, color: AppTheme.primaryPurple)),
                          const SizedBox(height: 4),
                          InkWell(
                            onTap: () => provider.removeItem(index),
                            child: const Text("Remove", style: TextStyle(color: Colors.red, fontSize: 12, fontWeight: FontWeight.bold)),
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
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.1), blurRadius: 15, offset: const Offset(0, -5))],
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
            ElevatedButton.icon(
              onPressed: () => _addItem(context),
              icon: const Icon(Icons.add),
              label: const Text("ADD ITEM"),
              style: ElevatedButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16)),
            ),
          ],
        ),
      ),
    );
  }
}

class _AddressTab extends StatelessWidget {
  const _AddressTab();

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<OrderEntryProvider>();
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text("Delivery & Contact", style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: AppTheme.primaryPurple)),
          const SizedBox(height: 24),
          _buildField("Contact Person", (val) => provider.contactPerson = val, provider.contactPerson),
          const SizedBox(height: 16),
          _buildField("Mobile Number", (val) => provider.mobileNumber = val, provider.mobileNumber, keyboardType: TextInputType.phone),
          const SizedBox(height: 16),
          _buildField("Territory", (val) => provider.territory = val, provider.territory),
          const SizedBox(height: 16),
          _buildField("Full Address", (val) => provider.fullAddress = val, provider.fullAddress, maxLines: 3),
        ],
      ),
    );
  }

  Widget _buildField(String label, Function(String) onChanged, String initialValue, {TextInputType? keyboardType, int maxLines = 1}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14, color: Colors.blueGrey)),
        const SizedBox(height: 8),
        TextFormField(
          initialValue: initialValue,
          onChanged: onChanged,
          maxLines: maxLines,
          keyboardType: keyboardType,
          decoration: InputDecoration(hintText: "Enter $label..."),
        ),
      ],
    );
  }
}

class _TermsTab extends StatelessWidget {
  const _TermsTab();

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<OrderEntryProvider>();
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text("Additional Notes", style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: AppTheme.primaryPurple)),
          const SizedBox(height: 24),
          TextFormField(
            initialValue: provider.paymentTerms,
            maxLines: 10,
            onChanged: (val) => provider.paymentTerms = val,
            decoration: const InputDecoration(
              hintText: "Enter any special instructions or terms here...",
              alignLabelWithHint: true,
            ),
          ),
        ],
      ),
    );
  }
}

class _AddItemDialog extends StatefulWidget {
  final NewOrderRepository repository;
  final Function(OrderItem) onAdd;
  const _AddItemDialog({required this.repository, required this.onAdd});
  @override
  State<_AddItemDialog> createState() => _AddItemDialogState();
}

class _AddItemDialogState extends State<_AddItemDialog> {
  String? _selectedCode;
  double _rate = 0.0;
  String _uom = "Box";
  final _qtyController = TextEditingController(text: "1");

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<Map<String, dynamic>>(
      future: widget.repository.fetchItems(),
      builder: (context, snapshot) {
        if (!snapshot.hasData) return const Center(child: CircularProgressIndicator());
        final items = snapshot.data!['items'] as List;
        return AlertDialog(
          title: const Text("Select Product"),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                DropdownButtonFormField<String>(
                  decoration: const InputDecoration(labelText: "Product Code"),
                  isExpanded: true,
                  items: items.map((i) => DropdownMenuItem(value: i['item_code'] as String, child: Text("${i['item_code']} (${i['uom']})"))).toList(),
                  onChanged: (val) {
                    final item = items.firstWhere((element) => element['item_code'] == val);
                    setState(() {
                      _selectedCode = val;
                      _rate = (item['price_list_rate'] ?? 0.0).toDouble();
                      _uom = item['uom'] ?? "Box";
                    });
                  },
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: _qtyController,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: "Quantity", prefixIcon: Icon(Icons.add_shopping_cart)),
                ),
                const SizedBox(height: 24),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(color: AppTheme.primaryPurple.withOpacity(0.05), borderRadius: BorderRadius.circular(8)),
                  child: Column(
                    children: [
                      Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [const Text("Unit Price:"), Text("₱${_rate.toStringAsFixed(2)}", style: const TextStyle(fontWeight: FontWeight.bold))]),
                      const Divider(),
                      Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [const Text("UOM:"), Text(_uom, style: const TextStyle(fontWeight: FontWeight.bold))]),
                    ],
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context), child: const Text("CANCEL")),
            ElevatedButton(
              onPressed: _selectedCode == null ? null : () {
                final qty = int.tryParse(_qtyController.text) ?? 1;
                widget.onAdd(OrderItem(itemCode: _selectedCode!, quantity: qty, rate: _rate, uom: _uom));
                Navigator.pop(context);
              },
              child: const Text("ADD TO CART"),
            ),
          ],
        );
      },
    );
  }
}
