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

  // Address & Contact
  String customerAddressName = "";
  String contactPerson = "";
  String mobileNumber = "";
  String fullAddress = "";
  String territory = "";

  bool isEditMode = false;
  String? editingOrderId;

  void clearData() {
    customer = null;
    customerId = null;
    company = null;
    transactionDate = "";
    deliveryDate = "";
    paymentTerms = null;
    items = [];
    
    customerAddressName = "";
    contactPerson = "";
    mobileNumber = "";
    fullAddress = "";
    territory = "";

    isEditMode = false;
    editingOrderId = null;
    notifyListeners();
  }

  void updateCompany(String? val) {
    company = val;
    // Reset customer when company changes
    customer = null;
    customerId = null;
    notifyListeners();
  }

  void updateCustomer(String name, String id) {
    customer = name;
    customerId = id;
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

  double get totalAmount => items.fold(0, (sum, item) => sum + item.amount);
  int get totalQty => items.fold(0, (sum, item) => sum + item.quantity);
}
