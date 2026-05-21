package com.pims.medretailers;

public class OrderItem {
    private boolean isChecked;
    private String rowName = ""; // Internal ERPNext ID for the child row
    private String itemCode = "";
    private String deliveryDate = "";
    private int quantity = 1;
    private double rate = 0.0;
    private String itemName;
    private String date;

    private String uom = "Box";
    private String warehouse = "PIMS MAIN - PE";
    private String notes = "";

    public OrderItem() {} // Constructor

    public String getRowName() { return rowName; }
    public void setRowName(String name) { this.rowName = name; }

    public boolean isChecked() { return isChecked; }
    public void setChecked(boolean checked) { isChecked = checked; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getDeliveryDate() { return deliveryDate; }
    public void setDeliveryDate(String date) { this.deliveryDate = date; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getRate() { return rate; }
    public void setRate(double rate) { this.rate = rate; }

    public String getItemName() {return itemName; }
    public void setItemName(String itemName) {this.itemName = itemName;}

    // Auto-calculate amount
    public double getAmount() { return quantity * rate; }

    public String getDate() {return date;}
    public void setDate(String date) {
        this.date = date;
    }

    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }

    public String getWarehouse() { return warehouse; }
    public void setWarehouse(String warehouse) { this. warehouse = warehouse; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }



}