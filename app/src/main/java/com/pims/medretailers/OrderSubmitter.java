package com.pims.medretailers;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OrderSubmitter {

    public static void submitFullOrder(Activity activity, String finalcookie) {
        OrderDataManager data = OrderDataManager.getInstance();

        // --- 1. STRICT VALIDATION ---
        if (data.customer == null || data.customer.isEmpty()) {
            AppNotification.show(activity, "Error: Customer is missing. Please select a customer.", AppNotification.Type.ERROR);
            return;
        }

        if (data.company == null || data.company.isEmpty()) {
            data.company = "PMI-Exeltis";
        }

        String exactToday = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        String masterDeliveryDate = exactToday;
        
        // Use the transaction date from the manager if available, otherwise fallback to today
        String transactionDate = (data.transactionDate != null && !data.transactionDate.isEmpty()) 
                ? data.transactionDate : exactToday;

        // --- 2. BUILD CHILD TABLE: ITEMS ---
        JSONArray itemsArr = new JSONArray();
        boolean hasValidItem = false;

        for (OrderItem item : data.items) {
            if(item.getItemName() != null && !item.getItemName().isEmpty() && !item.getItemName().contains("Select")) {
                try {
                    JSONObject iObj = new JSONObject();
                    
                    // If this item has an ID (from Edit Mode), send it to ensure the row is UPDATED
                    // and doesn't revert to default price list values.
                    if (item.getRowName() != null && !item.getRowName().isEmpty()) {
                        iObj.put("name", item.getRowName());
                    }

                    // QA FIX: Use the actual Item Code ID for submission, not the display Name
                    String actualItemCode = (item.getItemCode() != null && !item.getItemCode().isEmpty()) 
                            ? item.getItemCode() : item.getItemName();
                    
                    iObj.put("item_code", actualItemCode);
                    iObj.put("qty", item.getQuantity() > 0 ? item.getQuantity() : 1);
                    iObj.put("rate", item.getRate());
                    
                    // QA FIX: Extremely aggressive price locking to ensure 0.00 rate is accepted.
                    // We set ignore_pricing_rule to 1 and sync ALL rate/price fields to the manual value.
                    iObj.put("price_list_rate", item.getRate());
                    iObj.put("base_price_list_rate", item.getRate());
                    iObj.put("base_rate", item.getRate());
                    iObj.put("ignore_pricing_rule", 1); 
                    
                    // Explicitly calculate and send amounts to prevent server-side re-fetching
                    double rowAmount = item.getQuantity() * item.getRate();
                    iObj.put("amount", rowAmount);
                    iObj.put("base_amount", rowAmount);
                    
                    // QA FIX: Force docstatus 0 for items as well to ensure they are editable
                    iObj.put("docstatus", 0);
                    
                    // If rate is 0, mark as free item to ensure ERPNext doesn't flag it as a missing price
                    if (item.getRate() == 0) {
                        iObj.put("is_free_item", 1);
                    }
                    
                    // Reset all discount fields that might trigger price recalculations
                    iObj.put("discount_percentage", 0.0);
                    iObj.put("discount_amount", 0.0);
                    iObj.put("base_discount_amount", 0.0);
                    iObj.put("pricing_rule", ""); 

                    iObj.put("uom", item.getUom() != null ? item.getUom() : "Box");
                    iObj.put("warehouse", item.getWarehouse() != null ? item.getWarehouse() : "PIMS MAIN - PE");

                    String itemSpecificDate = (item.getDate() != null && !item.getDate().isEmpty()) ? item.getDate() : exactToday;
                    iObj.put("delivery_date", itemSpecificDate);

                    if (!hasValidItem) {
                        masterDeliveryDate = itemSpecificDate;
                    }

                    if (item.getNotes() != null && !item.getNotes().isEmpty()) {
                        iObj.put("description", item.getNotes());
                    }

                    itemsArr.put(iObj);
                    hasValidItem = true;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        if (!hasValidItem) {
            AppNotification.show(activity, "Please add at least one valid item.", AppNotification.Type.ERROR);
            return;
        }

        // --- 3. BUILD MAIN JSON BODY (STEP 1: DRAFT CREATION) ---
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("docstatus", 0); // EXPLICITLY CREATE AS DRAFT FIRST
            jsonBody.put("ignore_pricing_rule", 1); // Document-level lock
            jsonBody.put("customer", data.customer);
            jsonBody.put("company", data.company);
            jsonBody.put("transaction_date", transactionDate);
            jsonBody.put("delivery_date", masterDeliveryDate);
            jsonBody.put("selling_price_list", "Standard Selling");

            // QA FIX: We DO NOT send contact_person or contact_mobile here.
            // Sending a display name crashes ERPNext. ERPNext will auto-fetch the contact details based on the Customer automatically!

            if(data.paymentTerms != null && !data.paymentTerms.isEmpty()) {
                jsonBody.put("payment_terms_template", data.paymentTerms);
            }

            // ADD PAYMENT SCHEDULE IF PRESENT
            if (!data.paymentSchedule.isEmpty()) {
                JSONArray scheduleArr = new JSONArray();
                for (OrderDataManager.PaymentScheduleItem pItem : data.paymentSchedule) {
                    JSONObject pObj = new JSONObject();
                    pObj.put("payment_term", pItem.termName);
                    pObj.put("description", pItem.description);
                    pObj.put("due_date", pItem.dueDate);
                    try {
                        pObj.put("invoice_portion", Double.parseDouble(pItem.portion));
                        pObj.put("payment_amount", Double.parseDouble(pItem.amount));
                    } catch (Exception ignored) {}
                    scheduleArr.put(pObj);
                }
                jsonBody.put("payment_schedule", scheduleArr);
            }

            jsonBody.put("items", itemsArr);

        } catch (JSONException e) {
            e.printStackTrace();
            AppNotification.show(activity, "Error building payload.", AppNotification.Type.ERROR);
            return;
        }

        Log.d("API_PAYLOAD", "Step 1 Payload: " + jsonBody.toString());

        // --- 4. EXECUTE STEP 1 (CREATE OR UPDATE DOCUMENT) ---
        OkHttpClient client = NetworkClient.getInstance();
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(jsonBody.toString(), JSON);

        String url = Config.BASE_URL + "/api/resource/Sales%20Order";
        boolean isEdit = data.isEditMode();
        if (isEdit) {
            url += "/" + data.editingOrderId;
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", finalcookie);

        if (isEdit) {
            requestBuilder.put(body);
        } else {
            requestBuilder.post(body);
        }

        Request request = requestBuilder.build();

        activity.runOnUiThread(() -> AppNotification.show(activity, isEdit ? "Updating Order..." : "Submitting Order...", AppNotification.Type.INFO));

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                activity.runOnUiThread(() -> AppNotification.show(activity, "Network Connection Error", AppNotification.Type.ERROR));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String respBody = response.body().string();

                if (response.isSuccessful()) {
                    try {
                        JSONObject respJson = new JSONObject(respBody);
                        String salesOrderName = isEdit ? data.editingOrderId : respJson.getJSONObject("data").getString("name");
                        Log.d("API_SUCCESS", (isEdit ? "Updated SO: " : "Created SO: ") + salesOrderName);

                        // Sync the ID back to the manager so subsequent edits use PUT
                        if (!isEdit) {
                            data.editingOrderId = salesOrderName;
                        }

                        // QA FIX: If we are in Edit Mode and it's already submitted for approval, we ONLY save (Step 1).
                        // We do NOT re-trigger the workflow step 2.
                        String editStatus = activity.getSharedPreferences("MedRetailerSession", activity.MODE_PRIVATE)
                                .getString("EDIT_APPROVAL_STATUS", "");
                        
                        if (isEdit && editStatus.contains("For Approval by")) {
                            activity.runOnUiThread(() -> {
                                AppNotification.show(activity, "✅ Order Changes Saved!", AppNotification.Type.SUCCESS);
                                OrderDataManager.getInstance().clearData();
                                Intent intent = new Intent(activity, Activity_SO_Landscape.class);
                                intent.putExtra("Session_Cookie", finalcookie);
                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                activity.startActivity(intent);
                                activity.finish();
                            });
                        } else {
                            // Proceed to Workflow trigger for new or rejected orders
                            applyWorkflow(salesOrderName, activity, finalcookie, client, JSON);
                        }

                    } catch (JSONException e) {
                        Log.e("API_ERROR", "Failed to parse document name from response.");
                    }
                } else {
                    displayErpNextError(activity, respBody, isEdit ? "Update Failed" : "Creation Failed");
                }
            }
        });
    }

    /**
     * Silent sync for immediate updates without workflow trigger or navigation.
     */
    public static void saveOrderImmediately(Activity activity, String finalcookie) {
        OrderDataManager data = OrderDataManager.getInstance();

        // 1. Validation (Same as submitFullOrder)
        if (data.customer == null || data.customer.isEmpty()) return;
        if (data.items.isEmpty()) return;

        boolean hasValidItem = false;
        JSONArray itemsArr = new JSONArray();
        String exactToday = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        String masterDeliveryDate = exactToday;
        String transactionDate = (data.transactionDate != null && !data.transactionDate.isEmpty()) ? data.transactionDate : exactToday;

        for (OrderItem item : data.items) {
            if (item.getItemName() != null && !item.getItemName().isEmpty() && !item.getItemName().contains("Select")) {
                try {
                    JSONObject iObj = new JSONObject();
                    if (item.getRowName() != null && !item.getRowName().isEmpty()) iObj.put("name", item.getRowName());
                    String actualItemCode = (item.getItemCode() != null && !item.getItemCode().isEmpty()) ? item.getItemCode() : item.getItemName();
                    iObj.put("item_code", actualItemCode);
                    iObj.put("qty", item.getQuantity() > 0 ? item.getQuantity() : 1);
                    iObj.put("rate", item.getRate());
                    iObj.put("price_list_rate", item.getRate());
                    iObj.put("base_price_list_rate", item.getRate());
                    iObj.put("base_rate", item.getRate());
                    iObj.put("ignore_pricing_rule", 1);
                    double rowAmount = item.getQuantity() * item.getRate();
                    iObj.put("amount", rowAmount);
                    iObj.put("base_amount", rowAmount);
                    iObj.put("docstatus", 0);
                    if (item.getRate() == 0) iObj.put("is_free_item", 1);
                    iObj.put("discount_percentage", 0.0);
                    iObj.put("discount_amount", 0.0);
                    iObj.put("base_discount_amount", 0.0);
                    iObj.put("uom", item.getUom() != null ? item.getUom() : "Box");
                    iObj.put("warehouse", item.getWarehouse() != null ? item.getWarehouse() : "PIMS MAIN - PE");
                    String itemSpecificDate = (item.getDate() != null && !item.getDate().isEmpty()) ? item.getDate() : exactToday;
                    iObj.put("delivery_date", itemSpecificDate);
                    if (!hasValidItem) masterDeliveryDate = itemSpecificDate;
                    if (item.getNotes() != null && !item.getNotes().isEmpty()) iObj.put("description", item.getNotes());
                    itemsArr.put(iObj);
                    hasValidItem = true;
                } catch (JSONException ignored) {}
            }
        }

        if (!hasValidItem) return;

        // 2. Build JSON Body
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("docstatus", 0);
            jsonBody.put("ignore_pricing_rule", 1);
            jsonBody.put("customer", data.customer);
            jsonBody.put("company", data.company != null ? data.company : "PMI-Exeltis");
            jsonBody.put("transaction_date", transactionDate);
            jsonBody.put("delivery_date", masterDeliveryDate);
            jsonBody.put("selling_price_list", "Standard Selling");
            if (data.paymentTerms != null && !data.paymentTerms.isEmpty()) jsonBody.put("payment_terms_template", data.paymentTerms);
            jsonBody.put("items", itemsArr);
        } catch (JSONException e) { return; }

        OkHttpClient client = NetworkClient.getInstance();
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(jsonBody.toString(), JSON);

        boolean isEdit = data.isEditMode();
        String url = Config.BASE_URL + "/api/resource/Sales%20Order";
        if (isEdit) url += "/" + data.editingOrderId;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", finalcookie)
                .method(isEdit ? "PUT" : "POST", body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("SYNC", "Silent Sync Failed: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String resp = response.body().string();
                if (response.isSuccessful()) {
                    try {
                        JSONObject respJson = new JSONObject(resp);
                        String name = isEdit ? data.editingOrderId : respJson.getJSONObject("data").getString("name");
                        if (!isEdit) {
                            data.editingOrderId = name;
                        }
                        Log.d("SYNC", "Silent Sync Success: " + name);
                    } catch (Exception ignored) {}
                } else {
                    Log.e("SYNC", "Silent Sync Error: " + resp);
                }
            }
        });
    }

    // --- 5. EXECUTE STEP 2 (APPLY WORKFLOW) ---
    public static void applyWorkflow(String documentName, Activity activity, String authHeaderValue, OkHttpClient client, MediaType JSON) {
        try {
            JSONObject workflowPayload = new JSONObject();

            JSONObject docObject = new JSONObject();
            docObject.put("doctype", "Sales Order");
            docObject.put("name", documentName);

            workflowPayload.put("doc", docObject);
            workflowPayload.put("action", "Submit for Approval");

            Log.d("API_PAYLOAD", "Step 2 Payload (Workflow): " + workflowPayload.toString());

            RequestBody body = RequestBody.create(workflowPayload.toString(), JSON);

            Request request = new Request.Builder()
                    .url(Config.BASE_URL + "/api/method/frappe.model.workflow.apply_workflow")
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Cookie", authHeaderValue)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    activity.runOnUiThread(() -> AppNotification.show(activity, "Order created but workflow failed to apply due to network.", AppNotification.Type.ERROR));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    final String respBody = response.body().string();

                    activity.runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            AppNotification.show(activity, "✅ Order Submitted for Approval!", AppNotification.Type.SUCCESS);

                            OrderDataManager.getInstance().clearData();

                            Intent intent = new Intent(activity, Activity_SO_Landscape.class);
                            intent.putExtra("Session_Cookie", authHeaderValue);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            activity.startActivity(intent);
                            activity.finish();

                        } else {
                            displayErpNextError(activity, respBody, "Workflow Failed");
                        }
                    });
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // --- HELPER: ERPNEXT v15 ERROR DECODER ---
    private static void displayErpNextError(Activity activity, String respBody, String prefix) {
        String finalError = ErrorUtils.parseErpNextError(respBody);
        Log.e("API_ERROR", "ERPNext Rejected: " + finalError);
        
        activity.runOnUiThread(() -> {
            AppNotification.show(activity, finalError, AppNotification.Type.ERROR);
        });
    }
}