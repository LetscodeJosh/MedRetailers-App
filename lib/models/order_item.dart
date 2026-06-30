class OrderItem {
  bool isChecked;
  String itemCode;
  String deliveryDate;
  int quantity;
  double rate;
  String? itemName;
  String? date;
  String uom;
  String warehouse;
  String notes;

  OrderItem({
    this.isChecked = false,
    this.itemCode = "",
    this.deliveryDate = "",
    this.quantity = 0,
    this.rate = 0.0,
    this.itemName,
    this.date,
    this.uom = "Box",
    this.warehouse = "PIMS MAIN - PE",
    this.notes = "",
  });

  double get amount => quantity * rate;

  factory OrderItem.fromJson(Map<String, dynamic> json) {
    return OrderItem(
      itemCode: json['item_code'] ?? "",
      quantity: (json['qty'] ?? 0).toInt(),
      rate: (json['rate'] ?? 0.0).toDouble(),
      uom: json['uom'] ?? "Box",
      warehouse: json['warehouse'] ?? "PIMS MAIN - PE",
      deliveryDate: json['delivery_date'] ?? "",
      notes: json['description'] ?? "",
      itemName: json['item_name'],
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'item_code': itemCode,
      'qty': quantity,
      'rate': rate,
      'uom': uom,
      'warehouse': warehouse,
      'delivery_date': deliveryDate,
      'description': notes,
    };
  }
}
