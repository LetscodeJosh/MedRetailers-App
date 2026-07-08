import 'package:flutter/material.dart';
import '../../models/order_item.dart';

class OrderEntryProvider extends ChangeNotifier {
  static final OrderEntryProvider _instance = OrderEntryProvider._internal();

  factory OrderEntryProvider() {
    return _instance;
  }

  OrderEntryProvider._internal();

  String? customer;
  String? customerId;
  String? company;
  String transactionDate = "";
  String deliveryDate = "";
  String? paymentTerms;
  List<OrderItem> items = [];

  // ── Address & Contact fields (ERPNext v15 field names) ──────────────────
  String customerAddressName  = "";  // customer_address  → Address doc link
  String contactPerson        = "";  // contact_person    → Contact doc link (sent to ERPNext on submit)
  String contactPersonDisplay = "";  // UI-only: human-readable contact name shown in the Address tab
  String mobileNumber         = "";  // contact_mobile
  String fullAddress          = "";  // address_display   → plain text
  String territory            = "";  // territory

  bool    isEditMode      = false;
  String? editingOrderId;

  String approvalStatus    = "Draft";
  String fulfillmentStatus = "-";

  List<Map<String, dynamic>> paymentSchedule = [];

  void clearData() {
    customer            = null;
    customerId          = null;
    company             = null;
    transactionDate     = "";
    deliveryDate        = "";
    paymentTerms        = null;
    items               = [];

    customerAddressName  = "";
    contactPerson        = "";
    contactPersonDisplay = "";
    mobileNumber         = "";
    fullAddress          = "";
    territory            = "";

    isEditMode       = false;
    editingOrderId   = null;
    approvalStatus   = "Draft";
    fulfillmentStatus = "-";
    paymentSchedule  = [];
    notifyListeners();
  }

  void updateCompany(String? val) {
    company    = val;
    customer   = null;
    customerId = null;
    notifyListeners();
  }

  void updateFulfillmentStatus(String val) {
    fulfillmentStatus = val;
    notifyListeners();
  }

  void updateCustomer(String name, String id) {
    customer   = name;
    customerId = id;
    notifyListeners();
  }

  /// Called after fetchCustomerDetails() returns.
  /// Maps the repository's ERPNext v15 keys into the provider fields.
  ///
  ///   addressName     ← details['customer_address']
  ///   address         ← details['address_display']
  ///   terr            ← details['territory']
  ///   contact         ← details['contact_person']        (Contact doc link)
  ///   mobile          ← details['contact_mobile']
  ///   contactDisplay  ← details['contact_person_display'] (readable name for UI)
  void updateCustomerDetails({
    required String addressName,
    required String address,
    required String terr,
    required String contact,
    required String mobile,
    String contactDisplay = "",
  }) {
    customerAddressName  = addressName;
    fullAddress          = address;
    territory            = terr;
    contactPerson        = contact;
    mobileNumber         = mobile;
    // Show the readable name in the UI; fall back to the doc link if empty
    contactPersonDisplay = contactDisplay.isNotEmpty ? contactDisplay : contact;
    notifyListeners();
  }

  void addItem(OrderItem item) {
    items.add(item);
    notifyListeners();
  }

  void removeItem(int index) {
    items.removeAt(index);
    notifyListeners();
  }

  void updateItem(int index, OrderItem item) {
    if (index >= 0 && index < items.length) {
      items[index] = item;
      notifyListeners();
    }
  }

  double get totalAmount => items.fold(0, (sum, item) => sum + item.amount);
  int    get totalQty    => items.fold(0, (sum, item) => sum + item.quantity);
}