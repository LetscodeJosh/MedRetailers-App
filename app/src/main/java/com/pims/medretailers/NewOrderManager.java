package com.pims.medretailers;

import java.util.ArrayList;
import java.util.List;

public class NewOrderManager {
    private static NewOrderManager instance;

    // Tab 1: Order Details
    public String customer = "";
    public String transactionDate = "";
    public String deliveryDate = "";
    public String territory = "";
    public String approvalStatus = "";
    public String company = "";
    public String orderFulfillment = "";

    // Tab 2: Items
    public List<OrderItem> items = new ArrayList<>();

    // Tab 3: Address & Contact
    public String mobileNumber = "";
    public String billingAddress = ""; // textarea

    // Tab 4: Terms
    public String paymentTerm = "";
    public String additionalInstruction = "";

    private NewOrderManager() {}

    public static synchronized NewOrderManager getInstance() {
        if (instance == null) {
            instance = new NewOrderManager();
        }
        return instance;
    }

    public void clearData() {
        customer = ""; transactionDate = ""; deliveryDate = ""; territory = "";
        approvalStatus = ""; company = ""; orderFulfillment = "";
        items.clear();
        mobileNumber = ""; billingAddress = "";
        paymentTerm = ""; additionalInstruction = "";
    }
}