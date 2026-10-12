package com.pims.medretailers;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Activity_OrderHost extends BaseActivity implements OrderAdapter.TotalUpdateListener {

    private String finalCookie = "";
    private String loggedInUserRole = "";
    private final OkHttpClient client = NetworkClient.getInstance();

    private TabLayout tabLayout;
    private View layoutOrderDetails, layoutItemList, layoutAddress, layoutTerms;
    private ImageView btnSubmitDetails, btnSubmitItems, btnSubmitAddress, btnSubmitTerms;

    // --- Tab 1: Order Details ---
    private EditText etDate, etDeliveryDate;
    private AutoCompleteTextView etCustomer, etCompany;
    private final HashMap<String, String> customerIdMap = new HashMap<>();

    // --- Tab 2: Item List ---
    private RecyclerView recyclerView;
    private OrderAdapter adapter;
    private List<OrderItem> orderList;
    private TextView tvTotalAmount, tvTotalQty;
    private CheckBox cbSelectAll;
    private boolean isItemsLoading = false;
    private List<ProductItem> dynamicProductList = new ArrayList<>();
    public static List<String> availableItemNames = new ArrayList<>();
    public static HashMap<String, String> itemCodeMap = new HashMap<>();
    public static HashMap<String, Double> itemRateMap = new HashMap<>();

    // --- Tab 3: Address & Contact ---
    private EditText etCustomerAddressName, etContactPerson, etMobileNumber, etFullAddress, etTerritory;

    // --- Tab 4: Terms & Conditions ---
    private AutoCompleteTextView etPaymentTerms;
    private EditText etInstructions;
    private TableLayout tablePaymentSchedule;
    private final List<String> cachedPaymentTerms = new ArrayList<>();
    private boolean isProgrammaticUpdate = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_host);

        initSession();
        initViews();
        setupTabs();
        
        // Initial Data Fetch
        fetchCompanies();
        fetchItemsFromApi();
        fetchPaymentTerms();

        // Ensure data is cleared if this is a truly NEW order
        if (!getIntent().getBooleanExtra("IS_EDIT_MODE", false)) {
            OrderDataManager.getInstance().clearData();
        } else {
            loadExistingData();
        }

        setupChangeDetection();

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showBackToSOListDialog(null);
            }
        });
    }

    private void initSession() {
        SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);
        if (getIntent().hasExtra("Session_Cookie")) {
            finalCookie = getIntent().getStringExtra("Session_Cookie");
            prefs.edit().putString("Session_Cookie", finalCookie).apply();
        } else {
            finalCookie = prefs.getString("Session_Cookie", "");
        }
        loggedInUserRole = prefs.getString("User_Role", "MedRep");
    }

    private void initViews() {
        isProgrammaticUpdate = true;
        tabLayout = findViewById(R.id.tabLayout);
        layoutOrderDetails = findViewById(R.id.layoutOrderDetails);
        layoutItemList = findViewById(R.id.layoutItemList);
        layoutAddress = findViewById(R.id.layoutAddress);
        layoutTerms = findViewById(R.id.layoutTerms);

        ImageView imgTopDecor = findViewById(R.id.imgTopDecor);
        if (imgTopDecor != null) {
            imgTopDecor.setOnClickListener(this::showBackToSOListDialog);
        }

        initOrderDetailsTab();
        initItemListTab();
        initAddressTab();
        initTermsTab();
        isProgrammaticUpdate = false;
    }

    private void initOrderDetailsTab() {
        etCustomer = layoutOrderDetails.findViewById(R.id.etCustomer);
        etDate = layoutOrderDetails.findViewById(R.id.etDate);
        etDeliveryDate = layoutOrderDetails.findViewById(R.id.etDeliveryDate);
        etCompany = layoutOrderDetails.findViewById(R.id.etCompany);
        // Threshold 1 allows instant filtering when typing a single letter like "Z"
        if (etCustomer != null) {
            etCustomer.setThreshold(1);
            etCustomer.setSelectAllOnFocus(true);
        }
        if (etCompany != null) {
            etCompany.setThreshold(1);
            etCompany.setSelectAllOnFocus(true);
        }

        btnSubmitDetails = layoutOrderDetails.findViewById(R.id.btnSubmitOrder);
        setupSaveButtonStyle(btnSubmitDetails);

        // QOL: Improved Interactivity - Apply hover effect to all interactable input fields
        View[] inputs = {etCustomer, etDate, etDeliveryDate, etCompany, layoutOrderDetails.findViewById(R.id.etApprovalStatus), layoutOrderDetails.findViewById(R.id.etFulfillmentStatus), layoutOrderDetails.findViewById(R.id.etPO)};
        for (View v : inputs) {
            if (v != null) setupHoverEffect(v);
        }

        setupDatePicker(etDate, true);
        setupDatePicker(etDeliveryDate, false);
        styleDropdown(etCompany);
        styleDropdown(etCustomer);

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
                OrderDataManager.getInstance().company = selectedCompany;
                fetchFilteredCustomers(selectedCompany);
            });
        }

        if (etCustomer != null) {
            etCustomer.setOnClickListener(v -> {
                if (etCompany.getText().toString().trim().isEmpty() && !loggedInUserRole.equals("Admin")) {
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
                if (safeCustomerId != null) {
                    OrderDataManager.getInstance().customer = safeCustomerId;
                    fetchCustomerDetails(safeCustomerId);
                }
            });
        }

        if (btnSubmitDetails != null) {
            setupHoverEffect(btnSubmitDetails);
            btnSubmitDetails.setOnClickListener(v -> submitOrder());
        }

        // QOL: Improved Interactivity - Apply hover effect to interactable inputs
        if (etPaymentTerms != null) setupHoverEffect(etPaymentTerms);
        if (etInstructions != null) setupHoverEffect(etInstructions);
    }

    private void initItemListTab() {
        orderList = OrderDataManager.getInstance().items;
        recyclerView = layoutItemList.findViewById(R.id.recyclerViewItems);
        tvTotalAmount = layoutItemList.findViewById(R.id.tvTotalAmount);
        tvTotalQty = layoutItemList.findViewById(R.id.tvTotalQty);
        cbSelectAll = layoutItemList.findViewById(R.id.cbSelectAll);
        ImageView btnAdd = layoutItemList.findViewById(R.id.btnimgaddrow);
        ImageView btnDelete = layoutItemList.findViewById(R.id.btnimgdeleterow);
        btnSubmitItems = layoutItemList.findViewById(R.id.btnimgsubmitforapproval);
        setupSaveButtonStyle(btnSubmitItems);

        if (recyclerView != null) {
            adapter = new OrderAdapter(orderList, this, this);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(adapter);
            recyclerView.setNestedScrollingEnabled(false);
        }

        if (btnAdd != null) {
            setupHoverEffect(btnAdd);
            btnAdd.setOnClickListener(v -> showSingleItemAddDialog());
        }

        if (btnDelete != null) {
            setupHoverEffect(btnDelete);
            btnDelete.setOnClickListener(v -> deleteSelectedItems());
        }

        if (btnSubmitItems != null) {
            setupHoverEffect(btnSubmitItems);
            btnSubmitItems.setOnClickListener(v -> submitOrder());
        }

        if (cbSelectAll != null) {
            cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (buttonView.isPressed()) {
                    for (OrderItem item : orderList) item.setChecked(isChecked);
                    if (adapter != null) adapter.notifyDataSetChanged();
                }
            });
        }
        onTotalChanged();
    }

    private void initAddressTab() {
        etCustomerAddressName = layoutAddress.findViewById(R.id.etCustomerAddressName);
        etContactPerson = layoutAddress.findViewById(R.id.etContactPerson);
        etMobileNumber = layoutAddress.findViewById(R.id.etMobileNumber);
        etFullAddress = layoutAddress.findViewById(R.id.etFullAddress);
        etTerritory = layoutAddress.findViewById(R.id.etTerritory);
        btnSubmitAddress = layoutAddress.findViewById(R.id.btnSubmitOrder);
        setupSaveButtonStyle(btnSubmitAddress);

        if (btnSubmitAddress != null) {
            setupHoverEffect(btnSubmitAddress);
            btnSubmitAddress.setOnClickListener(v -> submitOrder());
        }

        // QOL: Improved Interactivity - Apply hover effect to interactable inputs
        if (etCustomerAddressName != null) setupHoverEffect(etCustomerAddressName);
        if (etContactPerson != null) setupHoverEffect(etContactPerson);
        if (etMobileNumber != null) setupHoverEffect(etMobileNumber);
        if (etFullAddress != null) setupHoverEffect(etFullAddress);
        if (etTerritory != null) setupHoverEffect(etTerritory);
    }

    private void initTermsTab() {
        etPaymentTerms = layoutTerms.findViewById(R.id.etPaymentTerms);
        etInstructions = layoutTerms.findViewById(R.id.etInstructions);
        tablePaymentSchedule = layoutTerms.findViewById(R.id.tablePaymentSchedule);
        btnSubmitTerms = layoutTerms.findViewById(R.id.btnSubmitforapproval);
        setupSaveButtonStyle(btnSubmitTerms);

        styleDropdown(etPaymentTerms);
        etPaymentTerms.setOnClickListener(v -> etPaymentTerms.showDropDown());
        etPaymentTerms.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) etPaymentTerms.showDropDown(); });
        etPaymentTerms.setOnItemClickListener((parent, view, position, id) -> {
            String selectedTerm = (String) parent.getItemAtPosition(position);
            fetchPaymentSchedule(selectedTerm);
        });

        if (btnSubmitTerms != null) {
            setupHoverEffect(btnSubmitTerms);
            btnSubmitTerms.setOnClickListener(v -> submitOrder());
        }

        // QOL: Improved Interactivity - Apply hover effect to interactable inputs
        if (etPaymentTerms != null) setupHoverEffect(etPaymentTerms);
        if (etInstructions != null) setupHoverEffect(etInstructions);
    }

    private void setupTabs() {
        // Initialize state for the first tab
        toggleLayoutVisibility(0);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                saveCurrentTabData();
                int position = tab.getPosition();
                toggleLayoutVisibility(position);
                
                // Specific logic for Tab 3 (Address) to fetch if needed
                if (position == 2) {
                    checkAndFetchAddress();
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void toggleLayoutVisibility(int position) {
        layoutOrderDetails.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        layoutItemList.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        layoutAddress.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
        layoutTerms.setVisibility(position == 3 ? View.VISIBLE : View.GONE);
    }

    private void saveCurrentTabData() {
        OrderDataManager data = OrderDataManager.getInstance();
        
        // Save Order Details
        if (etCustomer != null && etCustomer.getText() != null) {
            String typedCustomer = etCustomer.getText().toString().trim();
            String actualId = customerIdMap.get(typedCustomer);
            data.customer = (actualId != null) ? actualId : typedCustomer;
        }
        if (etCompany != null && etCompany.getText() != null)
            data.company = etCompany.getText().toString().trim();
        if (etDate != null) data.transactionDate = etDate.getText().toString().trim();
        if (etDeliveryDate != null) data.deliveryDate = etDeliveryDate.getText().toString().trim();

        // Items are updated in real-time via orderList and onTotalChanged()
        data.items = this.orderList;

        // Save Address Info
        if (etContactPerson != null) data.contactPerson = etContactPerson.getText().toString().trim();
        if (etMobileNumber != null) data.mobileNumber = etMobileNumber.getText().toString().trim();
        if (etCustomerAddressName != null) data.customerAddressName = etCustomerAddressName.getText().toString().trim();
        if (etFullAddress != null) data.fullAddress = etFullAddress.getText().toString().trim();
        if (etTerritory != null) data.territory = etTerritory.getText().toString().trim();

        // Save Terms Info
        if (etPaymentTerms != null) data.paymentTerms = etPaymentTerms.getText().toString();
        if (etInstructions != null) data.instructions = etInstructions.getText().toString();
        saveTableDataToManager();
    }

    private void loadExistingData() {
        OrderDataManager data = OrderDataManager.getInstance();
        isProgrammaticUpdate = true;
        
        // Load Details
        if (etCompany != null) etCompany.setText(data.company, false);
        if (etCustomer != null) etCustomer.setText(data.customer, false);
        if (etDate != null) etDate.setText(data.transactionDate);
        if (etDeliveryDate != null) etDeliveryDate.setText(data.deliveryDate);

        // Load Items
        if (adapter != null) adapter.notifyDataSetChanged();
        onTotalChanged();

        // Load Address
        if (etCustomerAddressName != null) etCustomerAddressName.setText(data.customerAddressName);
        if (etContactPerson != null) etContactPerson.setText(data.contactPerson);
        if (etMobileNumber != null) etMobileNumber.setText(data.mobileNumber);
        if (etFullAddress != null) etFullAddress.setText(data.fullAddress);
        if (etTerritory != null) etTerritory.setText(data.territory);

        // Load Terms
        if (etPaymentTerms != null) etPaymentTerms.setText(data.paymentTerms, false);
        if (etInstructions != null) etInstructions.setText(data.instructions);
        rebuildTable(data.paymentSchedule);
        isProgrammaticUpdate = false;
        checkChangesAndToggleSaveButton();
    }

    private void checkAndFetchAddress() {
        OrderDataManager data = OrderDataManager.getInstance();
        if ((data.fullAddress == null || data.fullAddress.isEmpty()) && data.customer != null && !data.customer.isEmpty()) {
            fetchAddressAndContactForTab(data.customer);
        }
    }

    private void submitOrder() {
        saveCurrentTabData();
        OrderDataManager data = OrderDataManager.getInstance();
        if (data.company == null || data.company.isEmpty()) {
            AppNotification.show(this, "Please select a Company.", AppNotification.Type.ERROR);
            if (tabLayout.getTabAt(0) != null) tabLayout.getTabAt(0).select();
            return;
        }
        if (data.customer == null || data.customer.isEmpty()) {
            AppNotification.show(this, "Please select a Customer.", AppNotification.Type.ERROR);
            if (tabLayout.getTabAt(0) != null) tabLayout.getTabAt(0).select();
            return;
        }
        if (data.items.isEmpty()) {
            AppNotification.show(this, "Please add at least one item.", AppNotification.Type.ERROR);
            if (tabLayout.getTabAt(1) != null) tabLayout.getTabAt(1).select();
            return;
        }
        OrderSubmitter.submitFullOrder(this, finalCookie);
    }

    // ==========================================
    // API CALLS (CONSOLIDATED)
    // ==========================================

    private void fetchCompanies() {
        String url = Config.BASE_URL + "/api/resource/Company?fields=[\"name\",\"company_name\"]&limit_page_length=999";
        // Apply filters based on role if needed (simplified for consolidation)
        Request request = new Request.Builder().url(url).addHeader("Cookie", finalCookie).get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONArray dataArray = new JSONObject(response.body().string()).optJSONArray("data");
                        if (dataArray != null) {
                            List<String> companyNames = new ArrayList<>();
                            for (int i = 0; i < dataArray.length(); i++) {
                                companyNames.add(dataArray.getJSONObject(i).optString("name"));
                            }
                            runOnUiThread(() -> {
                                if (etCompany != null) {
                                    etCompany.setAdapter(new SearchableAdapter(Activity_OrderHost.this, companyNames));
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

    private void fetchFilteredCustomers(String selectedCompany) {
        String url = Config.BASE_URL + "/api/resource/Customer?fields=[\"name\",\"customer_name\"]&limit_page_length=999";
        try {
            JSONArray filters = new JSONArray();
            filters.put(new JSONArray().put("company").put("=").put(selectedCompany));
            // QOL: Filter by Territory if set
            String currentTerritory = OrderDataManager.getInstance().territory;
            if (currentTerritory != null && !currentTerritory.isEmpty()) {
                filters.put(new JSONArray().put("territory").put("=").put(currentTerritory));
            }
            url += "&filters=" + java.net.URLEncoder.encode(filters.toString(), "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }

        Request request = new Request.Builder().url(url).addHeader("Cookie", finalCookie).get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONArray dataArray = new JSONObject(response.body().string()).optJSONArray("data");
                        if (dataArray != null) {
                            List<String> names = new ArrayList<>();
                            customerIdMap.clear();
                            for (int i = 0; i < dataArray.length(); i++) {
                                JSONObject obj = dataArray.getJSONObject(i);
                                String id = obj.optString("name");
                                String dName = obj.optString("customer_name", id);
                                names.add(dName);
                                customerIdMap.put(dName, id);
                            }
                            runOnUiThread(() -> {
                                if (etCustomer != null) {
                                    etCustomer.setAdapter(new SearchableAdapter(Activity_OrderHost.this, names));
                                }
                            });
                        }
                    } catch (JSONException e) { e.printStackTrace(); }
                }
            }
        });
    }

    private void fetchCustomerDetails(String customerId) {
        String url = Config.BASE_URL + "/api/resource/Customer/" + android.net.Uri.encode(customerId);
        Request request = new Request.Builder().url(url).addHeader("Cookie", finalCookie).get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject doc = new JSONObject(response.body().string()).optJSONObject("data");
                        if (doc != null) {
                            OrderDataManager.getInstance().contactPerson = doc.optString("customer_primary_contact", "");
                            OrderDataManager.getInstance().paymentTerms = doc.optString("payment_terms", "");
                            runOnUiThread(() -> {
                                if (etPaymentTerms != null) etPaymentTerms.setText(OrderDataManager.getInstance().paymentTerms, false);
                            });
                        }
                    } catch (JSONException e) { e.printStackTrace(); }
                }
            }
        });
    }

    private void fetchItemsFromApi() {
        isItemsLoading = true;
        String url = Config.BASE_URL + "/api/resource/Item%20Price?fields=[\"item_code\",\"item_name\",\"uom\",\"price_list_rate\",\"valid_from\",\"valid_upto\"]&limit_page_length=500";
        client.newCall(new Request.Builder().url(url).addHeader("Cookie", finalCookie).get().build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { isItemsLoading = false; }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) { isItemsLoading = false; return; }
                try {
                    JSONArray dataArray = new JSONObject(response.body().string()).getJSONArray("data");
                    List<ProductItem> freshList = new ArrayList<>();
                    availableItemNames.clear(); itemCodeMap.clear(); itemRateMap.clear();
                    String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONObject item = dataArray.getJSONObject(i);
                        String code = item.optString("item_code", "");
                        String name = item.optString("item_name", "");
                        double rate = item.optDouble("price_list_rate", 0.0);
                        String uom = item.optString("uom", "Box");
                        if (uom == null || uom.equalsIgnoreCase("null") || uom.isEmpty()) {
                            uom = "Box";
                        }
                        String displayName = name.isEmpty() ? code : code + " : " + name;
                        
                        freshList.add(new ProductItem(code, name, "", uom));
                        availableItemNames.add(displayName);
                        itemCodeMap.put(displayName, code);
                        itemRateMap.put(displayName, rate);
                    }
                    runOnUiThread(() -> {
                        isItemsLoading = false;
                        dynamicProductList.clear();
                        dynamicProductList.addAll(freshList);
                    });
                } catch (Exception e) { isItemsLoading = false; }
            }
        });
    }

    private void fetchAddressAndContactForTab(String customerId) {
        // Combined logic from Activity_AddressContactDetails.java
        String url = Config.BASE_URL + "/api/resource/Customer?fields=[\"customer_name\",\"customer_primary_address\",\"primary_address\",\"customer_primary_contact\",\"territory\",\"mobile_no\"]&filters=[[\"name\",\"=\",\"" + customerId + "\"]]";
        client.newCall(new Request.Builder().url(url).addHeader("Cookie", finalCookie).get().build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) return;
                try {
                    JSONArray data = new JSONObject(response.body().string()).optJSONArray("data");
                    if (data != null && data.length() > 0) {
                        JSONObject cust = data.getJSONObject(0);
                        String territory = cust.optString("territory", "");
                        String mobile = cust.optString("mobile_no", "");
                        String addressHtml = cust.optString("primary_address", "");
                        String fullAddr = addressHtml.replace("<br>", "\n").trim();
                        
                        OrderDataManager odm = OrderDataManager.getInstance();
                        odm.territory = territory;
                        odm.mobileNumber = mobile;
                        odm.fullAddress = fullAddr;
                        odm.customerAddressName = cust.optString("customer_name", "");

                        runOnUiThread(() -> {
                            isProgrammaticUpdate = true;
                            if (etTerritory != null) etTerritory.setText(territory);
                            if (etMobileNumber != null) etMobileNumber.setText(mobile);
                            if (etFullAddress != null) etFullAddress.setText(fullAddr);
                            if (etCustomerAddressName != null) etCustomerAddressName.setText(odm.customerAddressName);
                            isProgrammaticUpdate = false;
                            checkChangesAndToggleSaveButton();
                        });
                    }
                } catch (Exception e) {}
            }
        });
    }

    private void fetchPaymentTerms() {
        String url = Config.BASE_URL + "/api/resource/Payment%20Terms%20Template?fields=[\"name\"]&limit_page_length=999";
        client.newCall(new Request.Builder().url(url).addHeader("Cookie", finalCookie).get().build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) return;
                try {
                    JSONArray dataArray = new JSONObject(response.body().string()).optJSONArray("data");
                    if (dataArray != null) {
                        cachedPaymentTerms.clear();
                        for (int i = 0; i < dataArray.length(); i++) {
                            cachedPaymentTerms.add(dataArray.getJSONObject(i).optString("name"));
                        }
                        runOnUiThread(() -> {
                            if (etPaymentTerms != null) etPaymentTerms.setAdapter(new SearchableAdapter(Activity_OrderHost.this, cachedPaymentTerms));
                        });
                    }
                } catch (Exception e) {}
            }
        });
    }

    private void fetchPaymentSchedule(String templateName) {
        try {
            String url = Config.BASE_URL + "/api/resource/Payment%20Terms%20Template/" + java.net.URLEncoder.encode(templateName, "UTF-8");
            client.newCall(new Request.Builder().url(url).addHeader("Cookie", finalCookie).get().build()).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful()) return;
                    try {
                        JSONObject dataObj = new JSONObject(response.body().string()).optJSONObject("data");
                        if (dataObj != null && dataObj.has("payment_schedule")) {
                            JSONArray scheduleArray = dataObj.getJSONArray("payment_schedule");
                            List<OrderDataManager.PaymentScheduleItem> newSchedule = new ArrayList<>();
                            double totalOrderAmount = 0.0;
                            for (OrderItem item : orderList) totalOrderAmount += item.getAmount();

                            for (int i = 0; i < scheduleArray.length(); i++) {
                                JSONObject row = scheduleArray.getJSONObject(i);
                                String term = row.optString("payment_term", "");
                                String portion = row.optString("invoice_portion", "0");
                                double amt = (Double.parseDouble(portion) / 100) * totalOrderAmount;
                                newSchedule.add(new OrderDataManager.PaymentScheduleItem(term, "", "", portion, String.format(Locale.US, "%.2f", amt)));
                            }
                            runOnUiThread(() -> {
                                OrderDataManager.getInstance().paymentSchedule.clear();
                                OrderDataManager.getInstance().paymentSchedule.addAll(newSchedule);
                                rebuildTable(newSchedule);
                            });
                        }
                    } catch (Exception e) {}
                }
            });
        } catch (Exception e) {}
    }

    // ==========================================
    // UTILITY METHODS
    // ==========================================

    @Override public void onTotalChanged() {
        if (tvTotalAmount == null || tvTotalQty == null) return;
        double totalMoney = 0.0; int totalQty = 0;
        for (OrderItem item : orderList) {
            totalMoney += (item.getQuantity() * item.getRate());
            totalQty += item.getQuantity();
        }
        tvTotalAmount.setText(String.format(Locale.US, "₱ %.2f", totalMoney));
        tvTotalQty.setText(String.valueOf(totalQty));
        checkChangesAndToggleSaveButton();
    }

    private void deleteSelectedItems() {
        List<OrderItem> toRemove = new ArrayList<>();
        for (OrderItem item : orderList) if (item.isChecked()) toRemove.add(item);
        if (!toRemove.isEmpty()) {
            orderList.removeAll(toRemove);
            adapter.notifyDataSetChanged();
            onTotalChanged();
            if (cbSelectAll != null) cbSelectAll.setChecked(false);
        }
    }

    private void showSingleItemAddDialog() {
        // Reuse the logic from Activity_ItemList_MedRepView.java
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 20);

        AutoCompleteTextView actv = new AutoCompleteTextView(this);
        actv.setHint("Search Item Code...");
        styleModernInput(actv);
        actv.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, dynamicProductList));
        layout.addView(actv);

        EditText etQty = new EditText(this);
        etQty.setHint("Quantity");
        etQty.setInputType(InputType.TYPE_CLASS_NUMBER);
        styleModernInput(etQty);
        layout.addView(etQty);

        builder.setView(layout);
        builder.setPositiveButton("Add", (dialog, which) -> {
            String selection = actv.getText().toString();
            String qStr = etQty.getText().toString();
            if (!selection.isEmpty() && !qStr.isEmpty()) {
                OrderItem newItem = new OrderItem();
                // Find matching product for rate/uom
                for (ProductItem p : dynamicProductList) {
                    if ((p.code + " : " + p.name).equals(selection) || p.code.equals(selection)) {
                        newItem.setItemCode(p.code);
                        newItem.setItemName(p.name);
                        newItem.setUom(p.uom);
                        String key = p.name.isEmpty() ? p.code : p.code + " : " + p.name;
                        Double rate = itemRateMap.get(key);
                        newItem.setRate(rate != null ? rate : 0.0);
                        break;
                    }
                }
                newItem.setQuantity(Integer.parseInt(qStr));
                newItem.setDeliveryDate(etDate.getText().toString());
                orderList.add(newItem);
                adapter.notifyDataSetChanged();
                onTotalChanged();
            }
        });
        builder.setNegativeButton("Cancel", null).show();
    }

    private void addPaymentRow(String term, String desc, String date, String portion, String amt) {
        TableRow row = new TableRow(this);
        row.setBackgroundColor(Color.parseColor("#1E1E1E"));
        row.setPadding(0, 16, 0, 16);
        CheckBox cb = new CheckBox(this); row.addView(cb);
        TextView tvNum = new TextView(this); tvNum.setText(String.valueOf(tablePaymentSchedule.getChildCount())); tvNum.setTextColor(Color.WHITE); row.addView(tvNum);
        
        AutoCompleteTextView actvTerm = new AutoCompleteTextView(this);
        actvTerm.setText(term, false); actvTerm.setTextColor(Color.WHITE);
        actvTerm.setAdapter(new SearchableAdapter(this, cachedPaymentTerms));
        actvTerm.setOnClickListener(v -> actvTerm.showDropDown());
        row.addView(actvTerm);

        EditText etDesc = new EditText(this); etDesc.setText(desc); etDesc.setTextColor(Color.LTGRAY); row.addView(etDesc);
        TextView tvDate = new TextView(this); tvDate.setText(date.isEmpty() ? "Select Date" : date); tvDate.setTextColor(Color.parseColor("#ff6b6b"));
        tvDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, day) -> {
                tvDate.setText(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        row.addView(tvDate);
        EditText etPortion = new EditText(this); etPortion.setText(portion); etPortion.setTextColor(Color.WHITE); row.addView(etPortion);
        EditText etAmt = new EditText(this); etAmt.setText(amt); etAmt.setTextColor(Color.WHITE); row.addView(etAmt);
        
        tablePaymentSchedule.addView(row);
    }

    private void deleteSelectedRows() {
        for (int i = tablePaymentSchedule.getChildCount() - 1; i > 0; i--) {
            TableRow row = (TableRow) tablePaymentSchedule.getChildAt(i);
            if (((CheckBox) row.getChildAt(0)).isChecked()) tablePaymentSchedule.removeViewAt(i);
        }
    }

    private void saveTableDataToManager() {
        if (tablePaymentSchedule == null) return;
        OrderDataManager.getInstance().paymentSchedule.clear();
        for (int i = 1; i < tablePaymentSchedule.getChildCount(); i++) {
            TableRow row = (TableRow) tablePaymentSchedule.getChildAt(i);
            String term = ((AutoCompleteTextView) row.getChildAt(2)).getText().toString();
            String portion = ((EditText) row.getChildAt(5)).getText().toString();
            String amt = ((EditText) row.getChildAt(6)).getText().toString();
            OrderDataManager.getInstance().paymentSchedule.add(new OrderDataManager.PaymentScheduleItem(term, "", "", portion, amt));
        }
    }

    private void rebuildTable(List<OrderDataManager.PaymentScheduleItem> items) {
        if (tablePaymentSchedule == null) return;

        // AGGRESSIVE CLEAR: Remove all rows except the header (index 0)
        while (tablePaymentSchedule.getChildCount() > 1) {
            tablePaymentSchedule.removeViewAt(1);
        }

        if (items == null || items.isEmpty()) return;

        for (OrderDataManager.PaymentScheduleItem item : items) {
            addPaymentRow(item.termName, item.description, item.dueDate, item.portion, item.amount);
        }
    }

    private void setupDatePicker(EditText editText, boolean isTransactionDate) {
        editText.setFocusable(false);
        editText.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, day) -> {
                String date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
                editText.setText(date);
                checkChangesAndToggleSaveButton();
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private void setupSaveButtonStyle(ImageView btn) {
        if (btn == null) return;
        SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);
        String editStatus = prefs.getString("EDIT_APPROVAL_STATUS", "");
        boolean isApprovalPending = editStatus.contains("For Approval by");

        if (OrderDataManager.getInstance().isEditMode() && isApprovalPending) {
            btn.setImageResource(R.drawable.saveorderbtn);
            btn.setScaleType(ImageView.ScaleType.FIT_CENTER);
            btn.setAdjustViewBounds(true);

            int paddingInPx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
            btn.setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx);

            android.view.ViewGroup.LayoutParams params = btn.getLayoutParams();
            if (params != null) {
                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                boolean isTablet = dm.widthPixels > 1200;
                int wDp = isTablet ? 180 : 130;
                int hDp = isTablet ? 60 : 50;

                params.width = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, wDp, dm);
                params.height = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, hDp, dm);
                btn.setLayoutParams(params);
            }
        }
    }

    private void setupChangeDetection() {
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (!isProgrammaticUpdate) checkChangesAndToggleSaveButton();
            }
        };

        if (etCustomer != null) etCustomer.addTextChangedListener(watcher);
        if (etCompany != null) etCompany.addTextChangedListener(watcher);
        if (etDate != null) etDate.addTextChangedListener(watcher);
        if (etDeliveryDate != null) etDeliveryDate.addTextChangedListener(watcher);

        if (etContactPerson != null) etContactPerson.addTextChangedListener(watcher);
        if (etMobileNumber != null) etMobileNumber.addTextChangedListener(watcher);
        if (etCustomerAddressName != null) etCustomerAddressName.addTextChangedListener(watcher);
        if (etFullAddress != null) etFullAddress.addTextChangedListener(watcher);
        if (etTerritory != null) etTerritory.addTextChangedListener(watcher);

        if (etPaymentTerms != null) etPaymentTerms.addTextChangedListener(watcher);
        if (etInstructions != null) etInstructions.addTextChangedListener(watcher);
    }

    private void checkChangesAndToggleSaveButton() {
        if (isProgrammaticUpdate) return;
        OrderDataManager data = OrderDataManager.getInstance();
        if (!data.isEditMode()) {
            setAllSaveButtonsVisibility(View.VISIBLE);
            return;
        }

        // Temporarily sync UI to data manager to check for changes
        // Note: Items are already in sync via orderList reference
        String oldCust = data.customer;
        String oldComp = data.company;
        String oldDate = data.transactionDate;
        String oldDelv = data.deliveryDate;
        String oldCont = data.contactPerson;
        String oldMobi = data.mobileNumber;
        String oldAddrN = data.customerAddressName;
        String oldAddr = data.fullAddress;
        String oldTerr = data.territory;
        String oldPayT = data.paymentTerms;
        String oldInst = data.instructions;

        syncUiToDataManager();

        boolean hasChanges = data.hasChanges();
        setAllSaveButtonsVisibility(hasChanges ? View.VISIBLE : View.GONE);

        // Restore to avoid side effects if just checking
        data.customer = oldCust;
        data.company = oldComp;
        data.transactionDate = oldDate;
        data.deliveryDate = oldDelv;
        data.contactPerson = oldCont;
        data.mobileNumber = oldMobi;
        data.customerAddressName = oldAddrN;
        data.fullAddress = oldAddr;
        data.territory = oldTerr;
        data.paymentTerms = oldPayT;
        data.instructions = oldInst;
    }

    private void syncUiToDataManager() {
        OrderDataManager data = OrderDataManager.getInstance();
        if (etCustomer != null) {
            String typed = etCustomer.getText().toString().trim();
            String id = customerIdMap.get(typed);
            data.customer = (id != null) ? id : typed;
        }
        if (etCompany != null) data.company = etCompany.getText().toString().trim();
        if (etDate != null) data.transactionDate = etDate.getText().toString().trim();
        if (etDeliveryDate != null) data.deliveryDate = etDeliveryDate.getText().toString().trim();
        if (etContactPerson != null) data.contactPerson = etContactPerson.getText().toString().trim();
        if (etMobileNumber != null) data.mobileNumber = etMobileNumber.getText().toString().trim();
        if (etCustomerAddressName != null) data.customerAddressName = etCustomerAddressName.getText().toString().trim();
        if (etFullAddress != null) data.fullAddress = etFullAddress.getText().toString().trim();
        if (etTerritory != null) data.territory = etTerritory.getText().toString().trim();
        if (etPaymentTerms != null) data.paymentTerms = etPaymentTerms.getText().toString().trim();
        if (etInstructions != null) data.instructions = etInstructions.getText().toString().trim();
        saveTableDataToManager();
    }

    private void setAllSaveButtonsVisibility(int visibility) {
        if (btnSubmitDetails != null) btnSubmitDetails.setVisibility(visibility);
        if (btnSubmitItems != null) btnSubmitItems.setVisibility(visibility);
        if (btnSubmitAddress != null) btnSubmitAddress.setVisibility(visibility);
        if (btnSubmitTerms != null) btnSubmitTerms.setVisibility(visibility);
    }

    private void styleDropdown(AutoCompleteTextView actv) {
        GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.WHITE); gd.setCornerRadius(15f); gd.setStroke(2, Color.parseColor("#E0E0E0"));
        actv.setDropDownBackgroundDrawable(gd);
    }

    private void styleModernInput(View view) {
        GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.parseColor("#F8F9FA")); gd.setCornerRadius(22f); gd.setStroke(2, Color.parseColor("#DEE2E6"));
        view.setBackground(gd); view.setPadding(40, 35, 40, 35);
    }

    @Override
    protected void setupHoverEffect(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                    break;
            }
            return false; // Return false to allow typing and focus
        });
    }



    private void showBackToSOListDialog(View v) {
        new AlertDialog.Builder(this)
            .setTitle("Exit Order?")
            .setMessage("Unsaved changes will be lost.")
            .setPositiveButton("Exit", (d, w) -> {
                OrderDataManager.getInstance().clearData();
                finish();
            })
            .setNegativeButton("Stay", null)
            .show();
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
        @NonNull @Override public android.widget.Filter getFilter() {
            return new android.widget.Filter() {
                @Override protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    List<String> suggestions = new ArrayList<>();
                    if (constraint == null || constraint.length() == 0) suggestions.addAll(originalItems);
                    else {
                        String filter = constraint.toString().toLowerCase().trim();
                        for (String item : originalItems) if (item.toLowerCase().contains(filter)) suggestions.add(item);
                    }
                    results.values = suggestions; results.count = suggestions.size();
                    return results;
                }
                @Override protected void publishResults(CharSequence constraint, FilterResults results) {
                    filteredItems.clear();
                    if (results.values != null) filteredItems.addAll((List<String>) results.values);
                    notifyDataSetChanged();
                }
            };
        }
    }
}
