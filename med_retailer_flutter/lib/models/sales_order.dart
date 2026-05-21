class SalesOrder {
  final String id;
  final String customerName;
  final String ofsStatus;
  final String approvalStatus;
  final String date;
  final String territory;
  final double grandTotal;
  bool isSelected;

  SalesOrder({
    required this.id,
    required this.customerName,
    required this.ofsStatus,
    required this.approvalStatus,
    required this.date,
    required this.territory,
    required this.grandTotal,
    this.isSelected = false,
  });

  factory SalesOrder.fromJson(Map<String, dynamic> json) {
    return SalesOrder(
      id: json['name'] ?? "N/A",
      customerName: json['customer_name'] ?? "Unknown Customer",
      ofsStatus: json['fulfillment_status'] ?? "N/A",
      approvalStatus: json['workflow_state'] ?? "Draft",
      date: json['transaction_date'] ?? "N/A",
      territory: json['territory'] ?? "N/A",
      grandTotal: (json['grand_total'] ?? 0.0).toDouble(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'name': id,
      'customer_name': customerName,
      'fulfillment_status': ofsStatus,
      'workflow_state': approvalStatus,
      'transaction_date': date,
      'territory': territory,
      'grand_total': grandTotal,
    };
  }
}
