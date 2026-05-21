package com.pims.medretailers;

import android.app.DatePickerDialog;
import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Activity_OrderDetails extends BaseActivity {

    private EditText etDate, etDeliveryDate, etApprovalStatus, etFulfillmentStatus, etOrderID;
    private TextView tvOrderIDLabel, tvPageTitle;
    private AutoCompleteTextView etCustomer, etCompany;
    private Spinner spTerritory;
    private androidx.appcompat.widget.AppCompatButton btnSubmit;

    private String finalCookie = "";
    private String loggedInUserRole = "";
    private final OkHttpClient client = new OkHttpClient();

    // UI Lock flag to prevent "Ghost" buttons during data population
    private boolean isProgrammaticallyUpdating = false;

    private final HashMap<String, String> customerIdMap = new HashMap<>();

    private final android.os.Handler syncHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable syncRunnable = () -> {
        Log.d("IMMEDIATE_SYNC", "Executing debounced silent sync from OrderDetails...");
        OrderSubmitter.saveOrderImmediately(this, finalCookie);
    };

    private void triggerImmediateSync() {
        syncHandler.removeCallbacks(syncRunnable);
        syncHandler.postDelayed(syncRunnable, 2000); // 2s debounce
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_orderdetails);

        SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);
        if (getIntent().hasExtra("Session_Cookie")) {
            finalCookie = getIntent().getStringExtra("Session_Cookie");
            prefs.edit().putString("Session_Cookie", finalCookie).apply();
        } else {
            finalCookie = prefs.getString("Session_Cookie", "");
        }
        loggedInUserRole = prefs.getString("User_Role", "MedRep");

        etCustomer     = findViewById(R.id.etCustomer);
        etDate         = findViewById(R.id.etDate);
        etDeliveryDate = findViewById(R.id.etDeliveryDate);
        etCompany      = findViewById(R.id.etCompany);
        spTerritory    = findViewById(R.id.spTerritory);
        btnSubmit      = findViewById(R.id.btnSubmitOrder);

        if (etCustomer != null) {
            etCustomer.setThreshold(1);
            etCustomer.setSelectAllOnFocus(true);
        }
        if (etCompany != null) {
            etCompany.setThreshold(1);
            etCompany.setSelectAllOnFocus(true);
        }

        etApprovalStatus = findViewById(R.id.etApprovalStatus);
        etFulfillmentStatus = findViewById(R.id.etFulfillmentStatus);
        etOrderID = findViewById(R.id.etOrderID);
        tvOrderIDLabel = findViewById(R.id.tvOrderIDLabel);
        tvPageTitle = findViewById(R.id.tvPageTitle);

        updateHeaderTitle();

        String editStatus = prefs.getString("EDIT_APPROVAL_STATUS", "");
        boolean isApprovalPending = editStatus.contains("For Approval by");

        if (OrderDataManager.getInstance().isEditMode() && btnSubmit != null && isApprovalPending) {
            btnSubmit.setText("Save");
            btnSubmit.requestLayout();
        }

        setupDatePicker(etDate, true);
        setupDatePicker(etDeliveryDate, false);
        setupTabs();
        styleDropdown(etCompany);
        styleDropdown(etCustomer);

        setupChangeDetection();

        View[] inputs = {etCustomer, etDate, etDeliveryDate, etCompany, etApprovalStatus, etFulfillmentStatus, findViewById(R.id.etPO)};
        for (View v : inputs) {
            if (v != null) setupHoverEffect(v);
        }

        OrderDataManager data = OrderDataManager.getInstance();

        fetchCompanies();

        if (data.company != null && !data.company.isEmpty()) {
            fetchFilteredCustomers(data.company);
        } else {
            fetchFilteredCustomers("");
        }

        if (etCompany != null) {
            etCompany.setOnClickListener(v -> {
                etCompany.selectAll();
                etCompany.showDropDown();
                etCompany.requestFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etCompany, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            });
            etCompany.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    etCompany.selectAll();
                    etCompany.showDropDown();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(etCompany, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            });

            etCompany.setOnItemClickListener((parent, view, position, id) -> {
                String selectedCompany = (String) parent.getItemAtPosition(position);
                etCustomer.setText("", false);
                customerIdMap.clear();
                data.customer = "";
                data.company = selectedCompany;
                AppNotification.show(this, "Loading customers for " + selectedCompany + "...", AppNotification.Type.INFO);
                fetchFilteredCustomers(selectedCompany);
                triggerImmediateSync();
            });
        }

        if (etCustomer != null) {
            etCustomer.setOnClickListener(v -> {
                boolean isAdmin = loggedInUserRole != null &&
                        (loggedInUserRole.equalsIgnoreCase("Admin") || loggedInUserRole.equalsIgnoreCase("Administrator"));

                if (etCompany.getText().toString().trim().isEmpty() && !isAdmin) {
                    AppNotification.show(this, "Please select a Company first.", AppNotification.Type.ERROR);
                } else {
                    etCustomer.selectAll();
                    etCustomer.showDropDown();
                    etCustomer.requestFocus();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(etCustomer, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            });
            etCustomer.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    etCustomer.selectAll();
                    etCustomer.showDropDown();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(etCustomer, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            });

            etCustomer.setOnItemClickListener((parent, view, position, id) -> {
                String selectedCustomer = (String) parent.getItemAtPosition(position);
                String safeCustomerId = customerIdMap.get(selectedCustomer);
                OrderDataManager dataMgr = OrderDataManager.getInstance();

                if (safeCustomerId != null) {
                    // FIX: If customer changed, reset dependent address/contact fields to trigger fresh fetch in Address Tab
                    if (!safeCustomerId.equals(dataMgr.customer)) {
                        dataMgr.customerAddressName = "";
                        dataMgr.fullAddress = "";
                        dataMgr.contactPerson = "";
                        dataMgr.mobileNumber = "";
                        dataMgr.territory = "";
                        
                        // Clear UI fields in Address Tab context by resetting memory
                        Log.d("ORDER_DETAILS", "Customer changed. Resetting dependent fields.");
                    }
                    
                    dataMgr.customer = safeCustomerId;
                    fetchCustomerDetails(safeCustomerId);
                    triggerImmediateSync();
                }
            });
        }

        if (data.isEditMode()) {
            isProgrammaticallyUpdating = true; // Fix applied here
            if (etCustomer != null && data.customer != null && !data.customer.isEmpty())
                etCustomer.setText(data.customer, false);
            if (etCompany != null && data.company != null && !data.company.isEmpty())
                etCompany.setText(data.company, false);
            if (etDate != null && data.transactionDate != null && !data.transactionDate.isEmpty())
                etDate.setText(data.transactionDate);
            if (etDeliveryDate != null && data.deliveryDate != null && !data.deliveryDate.isEmpty())
                etDeliveryDate.setText(data.deliveryDate);
            isProgrammaticallyUpdating = false;

            if (etOrderID != null && tvOrderIDLabel != null) {
                etOrderID.setText(data.editingOrderId);
                etOrderID.setVisibility(View.VISIBLE);
                tvOrderIDLabel.setVisibility(View.VISIBLE);
            }

            String appStatus = prefs.getString("EDIT_APPROVAL_STATUS", "Draft");
            String fulStatus = prefs.getString("EDIT_FULFILLMENT_STATUS", "Not Fulfilled");

            if (etApprovalStatus != null) {
                etApprovalStatus.setText(appStatus);
                etApprovalStatus.setVisibility(View.VISIBLE);
            }
            if (etFulfillmentStatus != null) {
                etFulfillmentStatus.setText(fulStatus);
                etFulfillmentStatus.setVisibility(View.VISIBLE);
            }

        } else {
            if (data.transactionDate == null || data.transactionDate.isEmpty()) {
                String today = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
                data.transactionDate = today;
                if (etDate != null) etDate.setText(today);
            }

            if (etApprovalStatus != null) {
                etApprovalStatus.setText("Draft");
                etApprovalStatus.setVisibility(View.VISIBLE);
            }
            if (etFulfillmentStatus != null) {
                etFulfillmentStatus.setText("-");
                etFulfillmentStatus.setVisibility(View.VISIBLE);
            }
        }

        if (btnSubmit != null) {
            setupHoverEffect(btnSubmit);
            btnSubmit.setOnClickListener(v -> {
                String typedCustomer = etCustomer.getText().toString().trim();
                String typedCompany = etCompany.getText().toString().trim();

                if (typedCompany.isEmpty()) {
                    AppNotification.show(this, "Please select a Company.", AppNotification.Type.ERROR);
                    return;
                }
                if (typedCustomer.isEmpty()) {
                    AppNotification.show(this, "Please select a Customer.", AppNotification.Type.ERROR);
                    return;
                }

                String safeCustomerId = customerIdMap.get(typedCustomer);
                data.customer        = (safeCustomerId != null) ? safeCustomerId : typedCustomer;
                data.company         = typedCompany;
                data.transactionDate = etDate.getText().toString().trim();
                data.deliveryDate    = etDeliveryDate.getText().toString().trim();

                OrderSubmitter.submitFullOrder(this, finalCookie);
            });
        }

        ImageView imgTopDecor = findViewById(R.id.imgTopDecor);
        if (imgTopDecor != null) {
            imgTopDecor.setOnClickListener(v -> animateHamburgerMenu(imgTopDecor, () -> showHamburgerMenu(imgTopDecor)));
        }
    }

    @Override
    public void onBackPressed() {
        showBackToSOListConfirmDialog();
    }

    private void showHamburgerMenu(View v) {
        animateHamburgerMenu((ImageView) v, () -> showUniversalMenu(v));
    }

    private void showBackToSOListConfirmDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setPadding(40, 40, 40, 40);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(80, 100, 80, 100);
        layout.setGravity(android.view.Gravity.CENTER);

        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(android.graphics.Color.parseColor("#CCFFFFFF"));
        gd.setCornerRadius(100f);
        gd.setStroke(4, android.graphics.Color.parseColor("#AAFFFFFF"));
        layout.setBackground(gd);
        root.addView(layout);

        TextView title = new TextView(this);
        title.setText("Finish Ordering?");
        title.setTextSize(26f);
        title.setGravity(android.view.Gravity.CENTER);
        title.setTextColor(android.graphics.Color.parseColor("#333333"));
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        title.setPadding(0, 0, 0, 20);
        layout.addView(title);

        TextView message = new TextView(this);
        message.setText("Would you like to return to the Sales Order list? Unsaved changes will be cleared.");
        message.setGravity(android.view.Gravity.CENTER);
        message.setTextColor(android.graphics.Color.parseColor("#666666"));
        message.setTextSize(16f);
        message.setPadding(0, 0, 0, 80);
        layout.addView(message);

        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.HORIZONTAL);
        btnLayout.setGravity(android.view.Gravity.CENTER);

        TextView btnCancel = new TextView(this);
        btnCancel.setText("STAY");
        btnCancel.setPadding(70, 30, 70, 30);
        btnCancel.setTextColor(android.graphics.Color.GRAY);
        btnCancel.setTextSize(14f);
        btnCancel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        TextView btnExit = new TextView(this);
        btnExit.setText("Exit to SO List");
        btnExit.setPadding(80, 30, 80, 30);
        btnExit.setTextColor(android.graphics.Color.WHITE);
        btnExit.setTextSize(14f);
        btnExit.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
        btnBg.setColor(android.graphics.Color.parseColor("#835C9F"));
        btnBg.setCornerRadius(40f);
        btnExit.setBackground(btnBg);

        btnLayout.addView(btnCancel);
        btnLayout.addView(btnExit);
        layout.addView(btnLayout);

        builder.setView(root);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(view -> dialog.dismiss());
        btnExit.setOnClickListener(view -> {
            dialog.dismiss();
            navigateToSOList();
        });
        dialog.show();
    }

    @Override
    protected void performLogout() {
        super.performLogout();
    }

    @Override
    protected void navigateToSOList() {
        OrderDataManager.getInstance().clearData();
        Intent intent = new Intent(this, Activity_SO_Landscape.class);
        intent.putExtra("Session_Cookie", finalCookie);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void styleDropdown(AutoCompleteTextView actv) {
        if (actv == null) return;

        android.graphics.drawable.GradientDrawable popupBg = new android.graphics.drawable.GradientDrawable();
        popupBg.setColor(android.graphics.Color.WHITE);
        popupBg.setCornerRadius(20f);
        popupBg.setStroke(3, android.graphics.Color.parseColor("#835C9F"));
        actv.setDropDownBackgroundDrawable(popupBg);

        actv.setDropDownVerticalOffset((int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics()));

        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenHeight = dm.heightPixels;
        boolean isTablet = dm.widthPixels > 1200;

        int maxH = isTablet ? (int)(screenHeight * 0.45) : (int)(screenHeight * 0.35);
        actv.setDropDownHeight(maxH);

        actv.setDropDownWidth(android.view.ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void fetchCompanies() {
        String filterString = buildCompanyFilters();
        String url = Config.BASE_URL + "/api/resource/Company"
                + "?fields=%5B%22name%22,%22company_name%22%5D"
                + filterString
                + "&limit_page_length=999999";

        Log.d("ORDER_DETAILS_API", "Company URL: " + url);

        Request request = new Request.Builder()
                .url(url).get()
                .addHeader("Cookie", finalCookie)
                .addHeader("Accept", "application/json").build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONArray dataArray = new JSONObject(response.body().string()).optJSONArray("data");
                        if (dataArray != null) {
                            List<String> companyNames = new ArrayList<>();
                            for (int i = 0; i < dataArray.length(); i++) {
                                companyNames.add(dataArray.getJSONObject(i).optString("name"));
                            }

                            Collections.sort(companyNames, String.CASE_INSENSITIVE_ORDER);

                            runOnUiThread(() -> {
                                if (etCompany != null) {
                                    SearchableAdapter adapter = new SearchableAdapter(Activity_OrderDetails.this, companyNames);
                                    etCompany.setAdapter(adapter);

                                    if (companyNames.size() == 1) {
                                        etCompany.setText(companyNames.get(0), false);
                                        fetchFilteredCustomers(companyNames.get(0));
                                    }
                                }
                            });
                        }
                    } catch (JSONException e) { e.printStackTrace(); }
                }
            }
        });
    }

    private String buildCompanyFilters() {
        JSONArray andFilters = new JSONArray();
        String filterQuery = "";

        boolean isAdmin = loggedInUserRole != null &&
                (loggedInUserRole.equalsIgnoreCase("Admin") || loggedInUserRole.equalsIgnoreCase("Administrator"));

        try {
            andFilters.put(new JSONArray().put("Company").put("company_name").put("is").put("set"));

            if (!isAdmin) {
                SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);
                String permsString = prefs.getString("User_Permissions_Map", "{}");
                JSONObject perms = new JSONObject(permsString);

                if (perms.has("Company")) {
                    JSONArray allowedCompanies = perms.getJSONArray("Company");
                    if (allowedCompanies.length() > 0) {
                        andFilters.put(new JSONArray().put("Company").put("name").put("in").put(allowedCompanies));
                    }
                }
            }
        } catch (JSONException e) { e.printStackTrace(); }

        if (andFilters.length() > 0) {
            try {
                filterQuery = "&filters=" + java.net.URLEncoder.encode(andFilters.toString(), "UTF-8").replace("+", "%20");
            }
            catch (Exception e) { e.printStackTrace(); }
        }
        return filterQuery;
    }

    private void fetchFilteredCustomers(String selectedCompany) {
        String filterString = buildCustomerFilters(selectedCompany);

        String url = Config.BASE_URL + "/api/resource/Customer"
                + "?fields=%5B%22name%22,%22customer_name%22%5D"
                + filterString
                + "&limit_page_length=999999";

        Log.d("ORDER_DETAILS_API", "Customer URL: " + url);

        Request request = new Request.Builder()
                .url(url).get()
                .addHeader("Cookie", finalCookie)
                .addHeader("Accept", "application/json").build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONArray dataArray = new JSONObject(response.body().string()).optJSONArray("data");
                        if (dataArray != null) {
                            final List<String> customerNames = new ArrayList<>();
                            final HashMap<String, String> tempMap = new HashMap<>();

                            for (int i = 0; i < dataArray.length(); i++) {
                                JSONObject obj = dataArray.getJSONObject(i);
                                String actualId = obj.optString("name");
                                String displayName = obj.optString("customer_name");

                                if (displayName.isEmpty() || displayName.equalsIgnoreCase("null")) {
                                    displayName = actualId;
                                }

                                customerNames.add(displayName);
                                tempMap.put(displayName, actualId);
                            }

                            Collections.sort(customerNames, String.CASE_INSENSITIVE_ORDER);

                            runOnUiThread(() -> {
                                customerIdMap.clear();
                                customerIdMap.putAll(tempMap);
                                if (etCustomer != null) {
                                    SearchableAdapter adapter = new SearchableAdapter(Activity_OrderDetails.this, customerNames);
                                    etCustomer.setAdapter(adapter);
                                }
                                updateHeaderTitle(); // Refresh title with display name
                                checkChangesAndToggleSaveButton();
                            });
                        }
                    } catch (JSONException e) { e.printStackTrace(); }
                }
            }
        });
    }

    private String buildCustomerFilters(String selectedCompany) {
        JSONArray andFilters = new JSONArray();
        String filterQuery = "";

        boolean isAdmin = loggedInUserRole != null &&
                (loggedInUserRole.equalsIgnoreCase("Admin") || loggedInUserRole.equalsIgnoreCase("Administrator"));

        try {
            andFilters.put(new JSONArray().put("Customer").put("customer_name").put("is").put("set"));

            if (!isAdmin) {
                if (selectedCompany != null && !selectedCompany.trim().isEmpty()) {
                    andFilters.put(new JSONArray().put("Customer").put("company").put("=").put(selectedCompany.trim()));
                }

                SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);
                String permsString = prefs.getString("User_Permissions_Map", "{}");
                JSONObject perms = new JSONObject(permsString);

                if (perms.has("Territory")) {
                    JSONArray allowedTerritories = perms.getJSONArray("Territory");
                    if (allowedTerritories.length() > 0) {
                        andFilters.put(new JSONArray().put("Customer").put("territory").put("in").put(allowedTerritories));
                    }
                }

                if ((selectedCompany == null || selectedCompany.trim().isEmpty()) && perms.has("Company")) {
                    JSONArray allowedCompanies = perms.getJSONArray("Company");
                    if (allowedCompanies.length() > 0) {
                        andFilters.put(new JSONArray().put("Customer").put("company").put("in").put(allowedCompanies));
                    }
                }
            }
        } catch (JSONException e) { e.printStackTrace(); }

        if (andFilters.length() > 0) {
            try {
                filterQuery = "&filters=" + java.net.URLEncoder.encode(andFilters.toString(), "UTF-8").replace("+", "%20");
            }
            catch (Exception e) { e.printStackTrace(); }
        }
        return filterQuery;
    }

    private void fetchCustomerDetails(String customerId) {
        String url = Config.BASE_URL + "/api/resource/Customer/" + android.net.Uri.encode(customerId);
        Request request = new Request.Builder().url(url).get()
                .addHeader("Cookie", finalCookie)
                .addHeader("Accept", "application/json").build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject doc = new JSONObject(response.body().string()).optJSONObject("data");
                        if (doc != null) {
                            OrderDataManager data = OrderDataManager.getInstance();
                            // FIX: Only store contact person if it's NOT a raw ID to avoid doubling/displaying ugly IDs
                            String primaryContact = doc.optString("customer_primary_contact", "");
                            if (primaryContact.contains("-") || primaryContact.matches(".*\\d.*")) {
                                // It looks like an ID, keep memory clean so Address Tab fetches the display name properly
                                data.contactPerson = "";
                            } else {
                                data.contactPerson = primaryContact;
                            }

                            data.paymentTerms = doc.optString("payment_terms", "");

                            if (!data.paymentTerms.isEmpty()) {
                                fetchPaymentSchedule(data.paymentTerms);
                            } else {
                                data.paymentSchedule.clear();
                            }
                        }
                    } catch (JSONException e) { e.printStackTrace(); }
                }
            }
        });
    }

    private void fetchPaymentSchedule(String paymentTermName) {
        try {
            String encodedTerm = java.net.URLEncoder.encode(paymentTermName, "UTF-8").replace("+", "%20");
            String url = Config.BASE_URL + "/api/resource/Payment%20Terms%20Template/" + encodedTerm;

            Request request = new Request.Builder().url(url).get()
                    .addHeader("Cookie", finalCookie).build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            JSONObject dataObj = new JSONObject(response.body().string()).optJSONObject("data");
                            if (dataObj != null) {
                                JSONArray scheduleArray = dataObj.optJSONArray("terms");
                                if (scheduleArray == null) scheduleArray = dataObj.optJSONArray("payment_schedule");

                                if (scheduleArray != null) {
                                    OrderDataManager data = OrderDataManager.getInstance();
                                    data.paymentSchedule.clear();

                                    double realTotalAmount = 0.0;
                                    for (OrderItem item : data.items) realTotalAmount += item.getAmount();

                                    for (int i = 0; i < scheduleArray.length(); i++) {
                                        JSONObject row = scheduleArray.getJSONObject(i);
                                        String term = row.optString("payment_term", "");
                                        String desc = row.optString("description", "");
                                        String portion = row.optString("invoice_portion", "0");
                                        int creditDays = row.optInt("credit_days", 0);

                                        String amount = "0.00";
                                        try {
                                            double port = Double.parseDouble(portion);
                                            amount = String.format(Locale.US, "%.2f", (port / 100) * realTotalAmount);
                                        } catch (Exception ignored) {}

                                        String dueDate = "";
                                        try {
                                            String baseDateStr = data.transactionDate;
                                            if (baseDateStr == null || baseDateStr.isEmpty()) {
                                                baseDateStr = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
                                            }
                                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
                                            java.util.Date bDate = sdf.parse(baseDateStr);
                                            Calendar cal = Calendar.getInstance();
                                            cal.setTime(bDate);
                                            cal.add(Calendar.DAY_OF_MONTH, creditDays);
                                            dueDate = sdf.format(cal.getTime());
                                        } catch (Exception e) { dueDate = data.transactionDate; }

                                        data.paymentSchedule.add(new OrderDataManager.PaymentScheduleItem(term, desc, dueDate, portion, amount));
                                    }
                                }
                            }
                        } catch (Exception e) { e.printStackTrace(); }
                    }
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void setupDatePicker(EditText editText, boolean isTransactionDate) {
        if (editText == null) return;
        editText.setFocusable(false);
        editText.setClickable(true);
        editText.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, day) -> {
                String selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
                editText.setText(selectedDate);
                if (isTransactionDate) {
                    OrderDataManager data = OrderDataManager.getInstance();
                    data.transactionDate = selectedDate;
                    if (data.paymentTerms != null && !data.paymentTerms.isEmpty()) {
                        fetchPaymentSchedule(data.paymentTerms);
                    }
                    triggerImmediateSync();
                }
                else {
                    OrderDataManager.getInstance().deliveryDate = selectedDate;
                    triggerImmediateSync();
                }
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private void updateHeaderTitle() {
        if (tvPageTitle == null) return;
        OrderDataManager data = OrderDataManager.getInstance();
        
        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) tvPageTitle.getLayoutParams();

        if (data.isEditMode()) {
            String displayName = data.customerDisplayName;
            if (displayName == null || displayName.isEmpty()) {
                displayName = data.customer;
                // Resolve ID to display name if map is ready
                for (java.util.Map.Entry<String, String> entry : customerIdMap.entrySet()) {
                    if (entry.getValue().equals(data.customer)) {
                        displayName = entry.getKey();
                        data.customerDisplayName = displayName;
                        break;
                    }
                }
            }
            tvPageTitle.setText(displayName);
            tvPageTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvPageTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
            tvPageTitle.setGravity(android.view.Gravity.CENTER);

            // Center align
            lp.horizontalBias = 0.5f;
            lp.setMarginStart(0);
            lp.setMarginEnd(0);
        } else {
            tvPageTitle.setText("Order Details");
            tvPageTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
            tvPageTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
            tvPageTitle.setGravity(android.view.Gravity.CENTER);

            // Center align
            lp.horizontalBias = 0.5f;
            lp.setMarginStart(0);
            lp.setMarginEnd(0);
        }
        tvPageTitle.setLayoutParams(lp);
    }

    @Override
    protected void onResume() {
        super.onResume();
        OrderDataManager data = OrderDataManager.getInstance();
        isProgrammaticallyUpdating = true; // FIX APPLIED HERE
        if (etCustomer != null && data.customer != null && !data.customer.isEmpty()) {
            String displayNameToSet = data.customer;
            for (java.util.Map.Entry<String, String> entry : customerIdMap.entrySet()) {
                if (entry.getValue().equals(data.customer)) {
                    displayNameToSet = entry.getKey();
                    break;
                }
            }
            etCustomer.setText(displayNameToSet, false);
        }
        if (etCompany != null && data.company != null && !data.company.isEmpty())
            etCompany.setText(data.company, false);
        if (etDate != null && data.transactionDate != null && !data.transactionDate.isEmpty())
            etDate.setText(data.transactionDate);
        if (etDeliveryDate != null && data.deliveryDate != null && !data.deliveryDate.isEmpty())
            etDeliveryDate.setText(data.deliveryDate);
        isProgrammaticallyUpdating = false;

        if (data.isEditMode()) {
            SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);
            String appStatus = prefs.getString("EDIT_APPROVAL_STATUS", "Draft");
            String fulStatus = prefs.getString("EDIT_FULFILLMENT_STATUS", "Not Fulfilled");

            if (etOrderID != null && tvOrderIDLabel != null) {
                etOrderID.setText(data.editingOrderId);
                etOrderID.setVisibility(View.VISIBLE);
                tvOrderIDLabel.setVisibility(View.VISIBLE);
            }

            if (etApprovalStatus != null) {
                etApprovalStatus.setText(appStatus);
                etApprovalStatus.setVisibility(View.VISIBLE);
            }
            if (etFulfillmentStatus != null) {
                etFulfillmentStatus.setText(fulStatus);
                etFulfillmentStatus.setVisibility(View.VISIBLE);
            }
        } else {
            if (etApprovalStatus != null) {
                etApprovalStatus.setText("Draft");
                etApprovalStatus.setVisibility(View.VISIBLE);
            }
            if (etFulfillmentStatus != null) {
                etFulfillmentStatus.setText("-");
                etFulfillmentStatus.setVisibility(View.VISIBLE);
            }
        }
        checkChangesAndToggleSaveButton();
    }

    private void setupChangeDetection() {
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                // CRITICAL FIX: Block false programmatic UI triggers completely
                if (isProgrammaticallyUpdating) return;
                checkChangesAndToggleSaveButton();
            }
        };
        if (etCustomer != null) etCustomer.addTextChangedListener(watcher);
        if (etCompany != null) etCompany.addTextChangedListener(watcher);
        if (etDate != null) etDate.addTextChangedListener(watcher);
        if (etDeliveryDate != null) etDeliveryDate.addTextChangedListener(watcher);
    }

    private void checkChangesAndToggleSaveButton() {
        if (btnSubmit == null) return;
        OrderDataManager data = OrderDataManager.getInstance();

        if (!data.isEditMode()) {
            btnSubmit.setVisibility(View.VISIBLE);
            btnSubmit.setEnabled(true);
            btnSubmit.setAlpha(1.0f);
            return;
        }

        String oldCust = data.customer;
        String oldComp = data.company;
        String oldDate = data.transactionDate;
        String oldDelv = data.deliveryDate;

        if (etCustomer != null && etCustomer.getText() != null) {
            String typed = etCustomer.getText().toString().trim();
            String id = customerIdMap.get(typed);

            if (id != null) {
                data.customer = id;
            } else if (!typed.isEmpty() && !customerIdMap.isEmpty()) {
                data.customer = typed;
            }
        }
        if (etCompany != null && etCompany.getText() != null) {
            data.company = etCompany.getText().toString().trim();
        }
        if (etDate != null && etDate.getText() != null) {
            data.transactionDate = etDate.getText().toString().trim();
        }
        if (etDeliveryDate != null && etDeliveryDate.getText() != null) {
            data.deliveryDate = etDeliveryDate.getText().toString().trim();
        }

        boolean hasChanges = data.hasChanges();

        if (hasChanges) {
            btnSubmit.setVisibility(View.VISIBLE);
            btnSubmit.setEnabled(true);
            btnSubmit.setAlpha(1.0f);
        } else {
            btnSubmit.setVisibility(View.GONE);
            btnSubmit.setEnabled(false);
            btnSubmit.setAlpha(0.0f);
        }

        data.customer = oldCust;
        data.company = oldComp;
        data.transactionDate = oldDate;
        data.deliveryDate = oldDelv;
    }

    @Override
    protected void onPause() {
        super.onPause();
        OrderDataManager data = OrderDataManager.getInstance();
        if (etCustomer != null && etCustomer.getText() != null) {
            String typedCustomer = etCustomer.getText().toString().trim();
            String actualId = customerIdMap.get(typedCustomer);
            data.customer = (actualId != null) ? actualId : typedCustomer;
        }
        if (etCompany != null && etCompany.getText() != null)
            data.company  = etCompany.getText().toString().trim();
    }

    private void setupTabs() {
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        if (tabLayout == null) return;

        TabLayout.Tab currentTab = tabLayout.getTabAt(0);
        if (currentTab != null) {
            currentTab.select();
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int pos = tab.getPosition();
                if (pos == 0) return;

                OrderDataManager data = OrderDataManager.getInstance();
                if (etCustomer != null && etCustomer.getText() != null) {
                    String typedCustomer = etCustomer.getText().toString().trim();
                    String actualId = customerIdMap.get(typedCustomer);
                    data.customer = (actualId != null) ? actualId : typedCustomer;
                }
                if (etCompany != null && etCompany.getText() != null)
                    data.company  = etCompany.getText().toString().trim();
                if (etDate != null) data.transactionDate = etDate.getText().toString();
                if (etDeliveryDate != null) data.deliveryDate = etDeliveryDate.getText().toString();

                syncHandler.removeCallbacks(syncRunnable);
                syncRunnable.run();

                Intent intent = null;
                switch (pos) {
                    case 1: intent = new Intent(Activity_OrderDetails.this, Activity_ItemList_MedRepView.class); break;
                    case 2: intent = new Intent(Activity_OrderDetails.this, Activity_AddressContactDetails.class); break;
                    case 3: intent = new Intent(Activity_OrderDetails.this, Activity_TermsConditions.class); break;
                }
                if (intent != null) {
                    intent.putExtra("Session_Cookie", finalCookie);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                    finish();
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    @Override
    protected void setupHoverEffect(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.8f).setDuration(50).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(50).start();
                    break;
            }
            return false;
        });
    }

    public class SearchableAdapter extends ArrayAdapter<String> {
        private List<String> originalItems;
        private List<String> filteredItems;

        public SearchableAdapter(@NonNull Context context, @NonNull List<String> items) {
            super(context, android.R.layout.simple_dropdown_item_1line, items);
            this.originalItems = new ArrayList<>(items);
            this.filteredItems = new ArrayList<>(items);
        }

        @Override public int getCount() { return filteredItems.size(); }
        @Override public String getItem(int position) { return filteredItems.get(position); }

        @NonNull @Override
        public View getView(int position, @androidx.annotation.Nullable View convertView, @NonNull ViewGroup parent) {
            TextView tv = (TextView) super.getView(position, convertView, parent);
            tv.setTextColor(android.graphics.Color.parseColor("#835C9F"));

            int hPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getContext().getResources().getDisplayMetrics());
            int vPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getContext().getResources().getDisplayMetrics());
            tv.setPadding(hPadding, vPadding, hPadding, vPadding);

            if (getContext().getResources().getDisplayMetrics().widthPixels > 1200) {
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            } else {
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            }

            return tv;
        }

        @NonNull @Override
        public android.widget.Filter getFilter() {
            return new android.widget.Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    List<String> suggestions = new ArrayList<>();

                    if (constraint == null || constraint.length() == 0) {
                        suggestions.addAll(originalItems);
                        Collections.sort(suggestions, String.CASE_INSENSITIVE_ORDER);
                    } else {
                        String filterPattern = constraint.toString().toLowerCase().trim();
                        List<String> startsWithList = new ArrayList<>();
                        List<String> containsList = new ArrayList<>();

                        for (String item : originalItems) {
                            String lowerItem = item.toLowerCase();
                            if (lowerItem.startsWith(filterPattern)) {
                                startsWithList.add(item);
                            } else if (lowerItem.contains(filterPattern)) {
                                containsList.add(item);
                            }
                        }

                        Collections.sort(startsWithList, String.CASE_INSENSITIVE_ORDER);
                        Collections.sort(containsList, String.CASE_INSENSITIVE_ORDER);

                        suggestions.addAll(startsWithList);
                        suggestions.addAll(containsList);
                    }

                    results.values = suggestions;
                    results.count = suggestions.size();
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    filteredItems.clear();
                    if (results.values != null) filteredItems.addAll((List<String>) results.values);
                    notifyDataSetChanged();
                }
            };
        }
    }
}