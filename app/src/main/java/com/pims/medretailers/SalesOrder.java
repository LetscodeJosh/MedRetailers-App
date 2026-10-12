package com.pims.medretailers;

public class SalesOrder {
    private String id;              // "name" in API
    private String customerName;    // "customer_name" or "customer"
    private String ofsStatus;       // "delivery_status" (Order Fulfillment)
    private String approvalStatus;  // "status" or "workflow_state"
    private String date;            // "transaction_date"
    private String territory;       // "territory"
    private double grandTotal;      // "grand_total"
    private boolean isSelected;

    public SalesOrder(String id, String customerName, String ofsStatus, String approvalStatus, String date, String territory, double grandTotal) {
        this.id = id;
        this.customerName = customerName;
        this.ofsStatus = ofsStatus;
        this.approvalStatus = approvalStatus;
        this.date = date;
        this.territory = territory;
        this.grandTotal = grandTotal;
        this.isSelected = false;
    }

    // Getters
    public String getId() { return id; }
    public String getCustomerName() { return customerName; }
    public String getOfsStatus() { return ofsStatus; }
    public String getApprovalStatus() { return approvalStatus; }
    public String getDate() { return date; }
    public String getTerritory() { return territory; }
    public double getGrandTotal() { return grandTotal; }
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
}