package com.pims.medretailers;

import java.util.ArrayList;
import java.util.List;

public class OrderDataManager {
    private static OrderDataManager instance;

    // --- Tab 1: Order Details ---
    public String customer = "";
    public String customerDisplayName = ""; // To store descriptive name for headers
    public String transactionDate = "";
    public String deliveryDate = "";
    public String company = "PMI-EXELTIS";

    public String territory = "";

    public List<OrderItem> items = new ArrayList<>();

    public String contactPerson = "";
    public String mobileNumber = "";
    public String fullAddress = "";
    public String customerAddressName = "";

    public String paymentTerms = "";
    public String instructions = "";
    public List<PaymentScheduleItem> paymentSchedule = new ArrayList<>();

    // ✅ EDIT MODE — empty = new order, non-empty = editing rejected order
    public String editingOrderId = "";

    // Snapshot for change detection
    private String originalDataSnapshot = "";

    public static class PaymentScheduleItem {
        public String termName;
        public String description;
        public String dueDate;
        public String portion;
        public String amount;

        public PaymentScheduleItem(String term, String desc,
                                   String date, String portion, String amt) {
            this.termName    = term;
            this.description = desc;
            this.dueDate     = date;
            this.portion     = portion;
            this.amount      = amt;
        }

        @androidx.annotation.NonNull
        @Override
        public String toString() {
            return termName + "|" + description + "|" + dueDate + "|" + portion + "|" + amount;
        }
    }

    private OrderDataManager() {
        // Start with an empty list instead of a placeholder
    }

    public static synchronized OrderDataManager getInstance() {
        if (instance == null) instance = new OrderDataManager();
        return instance;
    }

    // ✅ Check if currently in edit mode
    public boolean isEditMode() {
        return editingOrderId != null && !editingOrderId.isEmpty();
    }

    public void captureSnapshot() {
        this.originalDataSnapshot = serialize();
    }

    public boolean hasChanges() {
        if (!isEditMode()) return true; // Always show for new orders
        return !originalDataSnapshot.equals(serialize());
    }

    private String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("cust:").append(normalize(customer)).append(";");
        sb.append("tDate:").append(normalize(transactionDate)).append(";");
        sb.append("dDate:").append(normalize(deliveryDate)).append(";");
        sb.append("comp:").append(normalize(company)).append(";");
        sb.append("terr:").append(normalize(territory)).append(";");
        sb.append("cont:").append(normalize(contactPerson)).append(";");
        sb.append("mobi:").append(normalize(mobileNumber)).append(";");
        sb.append("addr:").append(normalize(fullAddress)).append(";");
        sb.append("addrN:").append(normalize(customerAddressName)).append(";");
        sb.append("payT:").append(normalize(paymentTerms)).append(";");
        sb.append("inst:").append(normalize(instructions)).append(";");
        
        sb.append("items:[");
        for (OrderItem item : items) {
            sb.append(normalize(item.getItemCode())).append(",")
              .append(item.getQuantity()).append(",")
              .append(item.getRate()).append(",")
              .append(normalize(item.getUom())).append(",")
              .append(normalize(item.getWarehouse())).append(",")
              .append(normalize(item.getDeliveryDate())).append(",")
              .append(normalize(item.getNotes())).append("|");
        }
        sb.append("];");

        sb.append("sched:[");
        for (PaymentScheduleItem psi : paymentSchedule) {
            sb.append(psi.toString()).append(";");
        }
        sb.append("];");

        return sb.toString();
    }

    /**
     * Normalizes strings for comparison to prevent "Ghost Changes" 
     * caused by whitespace differences, nulls, or line-break formatting.
     */
    private String normalize(String s) {
        if (s == null || s.equalsIgnoreCase("null")) return "";
        // Trim, remove carriage returns, and normalize multiple spaces/newlines
        return s.trim()
                .replace("\r", "")
                .replaceAll("\n+", "\n")
                .replaceAll(" +", " ");
    }

    public void clearData() {
        customer = ""; customerDisplayName = ""; transactionDate = ""; deliveryDate = "";
        territory = ""; company = "PMI-EXELTIS";
        contactPerson = ""; mobileNumber = "";
        fullAddress = ""; customerAddressName = "";
        paymentTerms = ""; instructions = "";
        editingOrderId = ""; // ✅ Always clear edit mode on new order
        originalDataSnapshot = "";
        items.clear();
        // Do not add a placeholder item, keep it blank
        paymentSchedule.clear();
    }
}
