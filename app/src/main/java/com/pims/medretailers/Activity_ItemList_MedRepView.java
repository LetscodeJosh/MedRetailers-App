package com.pims.medretailers;

import android.content.Intent;
import androidx.appcompat.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import android.util.TypedValue;
import android.view.ViewGroup;

public class Activity_ItemList_MedRepView extends BaseActivity
        implements OrderAdapter.TotalUpdateListener {

    private RecyclerView recyclerView;
    private OrderAdapter adapter;
    private List<OrderItem> orderList;
    private TextView tvTotalAmount, tvTotalQty, tvPageTitle;
    private CheckBox cbSelectAll;
    private androidx.appcompat.widget.AppCompatButton btnSubmit;
    private String finalCookie = "";
    private String loggedInUserRole = "";

    // Global variables for Row Dropdowns
    public static List<String> availableItemNames = new ArrayList<>();
    public static List<String> availableWarehouses = new ArrayList<>();
    public static HashMap<String, String> itemCodeMap = new HashMap<>();
    public static HashMap<String, Double> itemRateMap = new HashMap<>();

    private List<ProductItem> dynamicProductList = new ArrayList<>();
    private final OkHttpClient client = new OkHttpClient();
    public static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    // State flags to track API loading status
    private boolean isItemsLoading = false;
    private boolean isItemsLoadFailed = false;

    private final android.os.Handler syncHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable syncRunnable = () -> {
        Log.d("IMMEDIATE_SYNC", "Executing debounced silent sync...");
        // Re-sync the singleton items to ensure the latest local list is used
        OrderDataManager.getInstance().items = new ArrayList<>(this.orderList);
        OrderSubmitter.saveOrderImmediately(this, finalCookie);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_itemlist_medrepview);

        SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);
        if (getIntent().hasExtra("Session_Cookie")) {
            finalCookie = getIntent().getStringExtra("Session_Cookie");
            prefs.edit().putString("Session_Cookie", finalCookie).apply();
        } else {
            finalCookie = prefs.getString("Session_Cookie", "");
        }
        loggedInUserRole = prefs.getString("User_Role", "MedRep");

        fetchItemsFromApi();
        fetchWarehousesFromApi();

        ImageView imgTopDecor = findViewById(R.id.imgTopDecor);
        if (imgTopDecor != null) {
            imgTopDecor.setOnClickListener(this::showHamburgerMenu);
        }

        orderList = OrderDataManager.getInstance().items;

        recyclerView  = findViewById(R.id.recyclerViewItems);
        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        tvTotalQty    = findViewById(R.id.tvTotalQty);
        tvPageTitle   = findViewById(R.id.tvPageTitle);
        cbSelectAll   = findViewById(R.id.cbSelectAll);
        ImageView btnAdd    = findViewById(R.id.btnimgaddrow);
        ImageView btnDelete = findViewById(R.id.btnimgdeleterow);
        btnSubmit = findViewById(R.id.btnimgsubmitforapproval);

        String editStatus = prefs.getString("EDIT_APPROVAL_STATUS", "");
        boolean isApprovalPending = editStatus.contains("For Approval by");

        if (OrderDataManager.getInstance().isEditMode() && btnSubmit != null && isApprovalPending) {
            btnSubmit.setText("Save");
            btnSubmit.requestLayout();
        }

        if (recyclerView != null) {
            adapter = new OrderAdapter(orderList, this, this);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(adapter);
        }

        recyclerView.setNestedScrollingEnabled(false);
        recyclerView.setItemViewCacheSize(20);

        if (btnAdd != null) {
            setupHoverEffect(btnAdd);
            btnAdd.setOnClickListener(v -> {
                if (isItemsLoading) {
                    AppNotification.show(this, "Items are still loading, please wait...", AppNotification.Type.INFO);
                }
                showSingleItemAddDialog();
            });
        }

        if (btnDelete != null) {
            setupHoverEffect(btnDelete);
            btnDelete.setOnClickListener(v -> deleteSelectedItems());
        }

        if (btnSubmit != null) {
            setupHoverEffect(btnSubmit);
            btnSubmit.setOnClickListener(v -> {
                // Cancel any pending immediate sync to avoid race conditions
                syncHandler.removeCallbacks(syncRunnable);

                // QA FIX: Re-sync orderList to singleton before submission
                // to ensure any manual edits in the RecyclerView are captured
                OrderDataManager.getInstance().items = new ArrayList<>(this.orderList);

                OrderSubmitter.submitFullOrder(this, finalCookie);
            });
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
        setupTabs();
        updateHeaderTitle();
        checkChangesAndToggleSaveButton();
    }

    private void updateHeaderTitle() {
        if (tvPageTitle == null) return;
        OrderDataManager data = OrderDataManager.getInstance();

        if (data.isEditMode() && data.customerDisplayName != null && !data.customerDisplayName.isEmpty()) {
            tvPageTitle.setText(data.customerDisplayName);
            tvPageTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvPageTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
            tvPageTitle.setGravity(android.view.Gravity.CENTER);

            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) tvPageTitle.getLayoutParams();
            lp.horizontalBias = 0.5f;
            lp.setMarginStart(0);
            lp.setMarginEnd(0);
            tvPageTitle.setLayoutParams(lp);
        } else {
            tvPageTitle.setText("Order Details");
            tvPageTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
            tvPageTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
        }
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
        
        // Temporarily sync current orderList to manager to check for changes
        List<OrderItem> oldItems = new ArrayList<>(data.items);
        data.items = new ArrayList<>(this.orderList);
        
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
        
        // Restore original items in manager to maintain state purity
        data.items = oldItems;
    }

    private void fetchWarehousesFromApi() {
        try {
            JSONObject payload = new JSONObject();
            payload.put("doctype", "Warehouse");
            payload.put("fields", new JSONArray().put("name"));
            payload.put("limit_page_length", 500);

            RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA);
            Request request = new Request.Builder()
                    .url(Config.BASE_URL + "/api/method/frappe.client.get_list")
                    .addHeader("Cookie", finalCookie)
                    .addHeader("Accept", "application/json")
                    .post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e("API_WAREHOUSE", "Failed to fetch warehouses: " + e.getMessage());
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            String respData = response.body().string();
                            JSONObject json = new JSONObject(respData);
                            JSONArray data = json.optJSONArray("message");
                            if (data != null) {
                                List<String> fetched = new ArrayList<>();
                                for (int i = 0; i < data.length(); i++) {
                                    fetched.add(data.getJSONObject(i).getString("name"));
                                }
                                runOnUiThread(() -> {
                                    availableWarehouses.clear();
                                    availableWarehouses.addAll(fetched);
                                    if (availableWarehouses.isEmpty()) {
                                        availableWarehouses.add("PIMS MAIN - PE");
                                    }
                                });
                            }
                        } catch (Exception e) { e.printStackTrace(); }
                    }
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void fetchItemsFromApi() {
        isItemsLoading = true;
        isItemsLoadFailed = false;

        try {
            JSONObject payload = new JSONObject();
            payload.put("doctype", "Item Price");
            payload.put("fields", new JSONArray()
                    .put("item_code").put("item_name").put("uom").put("brand")
                    .put("price_list_rate").put("valid_from").put("valid_upto"));
            payload.put("limit_page_length", 99999);

            JSONArray filters = new JSONArray();
            filters.put(new JSONArray().put("price_list").put("=").put("Standard Selling"));

            payload.put("filters", filters);

            RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA);
            Request request = new Request.Builder()
                    .url(Config.BASE_URL + "/api/method/frappe.client.get_list")
                    .addHeader("Cookie", finalCookie)
                    .addHeader("Accept", "application/json")
                    .post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    fallbackFetchFromItemDocType();
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        fallbackFetchFromItemDocType();
                        return;
                    }
                    try {
                        String respData = response.body() != null ? response.body().string() : "";
                        JSONObject jsonObject = new JSONObject(respData);
                        JSONArray dataArray = jsonObject.optJSONArray("message");

                        if (dataArray != null && dataArray.length() > 0) {
                            processItemsData(dataArray, true);
                        } else {
                            fallbackFetchFromItemDocType();
                        }
                    } catch (Exception e) {
                        fallbackFetchFromItemDocType();
                    }
                }
            });
        } catch (Exception e) {
            fallbackFetchFromItemDocType();
        }
    }


    private void fallbackFetchFromItemDocType() {
        try {
            JSONObject payload = new JSONObject();
            payload.put("doctype", "Item");
            payload.put("fields", new JSONArray()
                    .put("name").put("item_code").put("item_name")
                    .put("stock_uom").put("standard_rate").put("brand"));
            payload.put("limit_page_length", 99999);

            JSONArray filters = new JSONArray();
            filters.put(new JSONArray().put("disabled").put("=").put(0));
            filters.put(new JSONArray().put("is_sales_item").put("=").put(1));

            payload.put("filters", filters);

            RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA);
            Request request = new Request.Builder()
                    .url(Config.BASE_URL + "/api/method/frappe.client.get_list")
                    .addHeader("Cookie", finalCookie)
                    .addHeader("Accept", "application/json")
                    .post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    notifyLoadFailure();
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        notifyLoadFailure();
                        return;
                    }
                    try {
                        String respData = response.body() != null ? response.body().string() : "";
                        JSONObject jsonObject = new JSONObject(respData);
                        JSONArray dataArray = jsonObject.optJSONArray("message");

                        if (dataArray != null) {
                            processItemsData(dataArray, false);
                        } else {
                            notifyLoadFailure();
                        }
                    } catch (Exception e) {
                        notifyLoadFailure();
                    }
                }
            });
        } catch (Exception e) {
            notifyLoadFailure();
        }
    }


    private void processItemsData(JSONArray dataArray, boolean isItemPrice) {
        try {
            SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);
            String permsString = prefs.getString("User_Permissions_Map", "{}");
            JSONObject permsJson = new JSONObject(permsString);
            JSONArray brandPerms = permsJson.optJSONArray("Brand");

            List<ProductItem> freshList = new ArrayList<>();
            freshList.add(new ProductItem("Select Code", "Select an Item...", "", ""));

            ProductItem.liveCatalog.clear();
            availableItemNames.clear();
            itemCodeMap.clear();
            itemRateMap.clear();

            availableItemNames.add("");

            String todayStr = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
            java.util.Map<String, String> latestValidFromMap = new java.util.HashMap<>();

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);

                if (brandPerms != null && brandPerms.length() > 0) {
                    String itemBrand = item.optString("brand", "");
                    boolean hasAccess = false;
                    for (int j = 0; j < brandPerms.length(); j++) {
                        if (brandPerms.getString(j).equalsIgnoreCase(itemBrand)) {
                            hasAccess = true;
                            break;
                        }
                    }
                    if (!hasAccess) continue;
                }

                if (isItemPrice) {
                    String validFrom = item.optString("valid_from", "null");
                    String validUpto = item.optString("valid_upto", "null");

                    boolean isValid = true;
                    if (!validFrom.equals("null") && !validFrom.isEmpty()) {
                        if (todayStr.compareTo(validFrom) < 0) isValid = false;
                    }
                    if (!validUpto.equals("null") && !validUpto.isEmpty()) {
                        if (todayStr.compareTo(validUpto) > 0) isValid = false;
                    }
                    if (!isValid) continue;
                }

                String code = item.optString("item_code", item.optString("name", ""));
                String name = item.optString("item_name", "");
                String uom  = item.optString(isItemPrice ? "uom" : "stock_uom", "Box");
                if (uom == null || uom.equalsIgnoreCase("null") || uom.isEmpty()) {
                    uom = "Box";
                }
                double rate = item.optDouble(isItemPrice ? "price_list_rate" : "standard_rate", 0.0);

                String displayName = name.isEmpty() || name.equalsIgnoreCase("null") ? code : code + " : " + name;

                if (isItemPrice) {
                    String validFrom = item.optString("valid_from", "null");
                    String existingValidFrom = latestValidFromMap.get(displayName);
                    if (existingValidFrom != null && validFrom.compareTo(existingValidFrom) <= 0) {
                        continue;
                    }
                    latestValidFromMap.put(displayName, validFrom);
                }

                boolean exists = false;
                for (int j = 0; j < freshList.size(); j++) {
                    if (freshList.get(j).code.equals(code)) {
                        freshList.set(j, new ProductItem(code, name, "", uom));
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    freshList.add(new ProductItem(code, name, "", uom));
                    availableItemNames.add(displayName);
                }

                ProductItem.liveCatalog.put(code, displayName);
                itemCodeMap.put(displayName, code);
                itemRateMap.put(displayName, rate);
            }

            runOnUiThread(() -> {
                isItemsLoading = false;
                isItemsLoadFailed = false;
                dynamicProductList.clear();
                dynamicProductList.addAll(freshList);
                if (adapter != null) adapter.notifyDataSetChanged();
            });

        } catch (Exception e) {
            notifyLoadFailure();
        }
    }

    private void notifyLoadFailure() {
        runOnUiThread(() -> {
            isItemsLoading = false;
            isItemsLoadFailed = true;
            AppNotification.show(Activity_ItemList_MedRepView.this, "Network restrictions applied. Tap + to retry.");
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        OrderDataManager.getInstance().items = this.orderList;
    }

    private void deleteSelectedItems() {
        List<OrderItem> toRemove = new ArrayList<>();
        for (OrderItem item : orderList) {
            if (item.isChecked()) toRemove.add(item);
        }
        if (!toRemove.isEmpty()) {
            orderList.removeAll(toRemove);

            adapter.notifyDataSetChanged();
            onTotalChanged();
            if (cbSelectAll != null) cbSelectAll.setChecked(false);
        }
    }

    @Override
    public void onTotalChanged() {
        if (tvTotalAmount == null || tvTotalQty == null) return;

        double totalMoney = 0.0;
        int totalQty = 0;

        for (OrderItem item : orderList) {
            double rowAmount = item.getQuantity() * item.getRate();
            totalMoney += rowAmount;
            totalQty   += item.getQuantity();
        }

        tvTotalAmount.setText(String.format(java.util.Locale.US, "₱ %.2f", totalMoney));
        tvTotalQty.setText(String.valueOf(totalQty));

        checkChangesAndToggleSaveButton();

        // IMMEDIATE SYNC: Trigger debounced sync whenever totals change (edits happen)
        syncHandler.removeCallbacks(syncRunnable);
        syncHandler.postDelayed(syncRunnable, 2000); // 2-second debounce
    }

    private void setupTabs() {
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        if (tabLayout == null) return;

        // Set initial selection without animation (Items is index 1)
        TabLayout.Tab currentTab = tabLayout.getTabAt(1);
        if (currentTab != null) {
            currentTab.select();
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                // SAVE DATA BEFORE LEAVING
                OrderDataManager.getInstance().items = new ArrayList<>(orderList);

                // If a sync was pending, execute it immediately before switching
                syncHandler.removeCallbacks(syncRunnable);
                syncRunnable.run();

                int pos = tab.getPosition();
                if (pos == 1) return;

                Intent intent = null;
                switch (pos) {
                    case 0: intent = new Intent(Activity_ItemList_MedRepView.this, Activity_OrderDetails.class); break;
                    case 2: intent = new Intent(Activity_ItemList_MedRepView.this, Activity_AddressContactDetails.class); break;
                    case 3: intent = new Intent(Activity_ItemList_MedRepView.this, Activity_TermsConditions.class); break;
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



    private void showSingleItemAddDialog() {
        if (dynamicProductList.isEmpty() && !isItemsLoading) {
            fetchItemsFromApi();
        }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 20);

        android.graphics.drawable.GradientDrawable mainBg = new android.graphics.drawable.GradientDrawable();
        mainBg.setColor(android.graphics.Color.WHITE);
        mainBg.setCornerRadius(40f);
        layout.setBackground(mainBg);

        TextView title = new TextView(this);
        title.setText("Complete Order Form");
        title.setTextSize(20f);
        title.setTypeface(null, android.graphics.Typeface.NORMAL);
        title.setTextColor(android.graphics.Color.parseColor("#835C9F"));
        title.setPadding(0, 0, 0, 20);
        layout.addView(title);

        android.graphics.drawable.GradientDrawable popupBg = new android.graphics.drawable.GradientDrawable();
        popupBg.setColor(android.graphics.Color.WHITE);
        popupBg.setCornerRadius(20f);

        addFormLabel(layout, "Item Code (Searchable):");
        android.widget.AutoCompleteTextView actvItemCode = new android.widget.AutoCompleteTextView(this);
        actvItemCode.setHint("Tap to view all, or type to search...");
        styleModernInput(actvItemCode);
        actvItemCode.setTextColor(android.graphics.Color.parseColor("#835C9F"));
        actvItemCode.setHintTextColor(android.graphics.Color.parseColor("#99835C9F"));
        actvItemCode.setThreshold(1);

        // QOL: Optimized Dropdown for Tablets & Smartphones
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        boolean isTablet = dm.widthPixels > 1200;
        int screenHeight = dm.heightPixels;

        // Set width to match the field for a clean look
        actvItemCode.setDropDownWidth(android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        
        // Limit height to prevent covering the whole screen
        int maxH = isTablet ? (int)(screenHeight * 0.4) : (int)(screenHeight * 0.35);
        actvItemCode.setDropDownHeight(maxH);

        // Offset to prevent overlapping field border
        actvItemCode.setDropDownVerticalOffset((int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics()));

        actvItemCode.setDropDownBackgroundDrawable(popupBg);
        actvItemCode.setOnClickListener(v -> actvItemCode.showDropDown());
        actvItemCode.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) actvItemCode.showDropDown(); });

        java.util.List<ProductItem> searchableList = new java.util.ArrayList<>(dynamicProductList);
        if (!searchableList.isEmpty() && searchableList.get(0).code.contains("Select"))
            searchableList.remove(0);

        android.widget.ArrayAdapter<ProductItem> searchAdapter =
                new android.widget.ArrayAdapter<ProductItem>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(searchableList)) {
                    private List<ProductItem> original = new ArrayList<>(searchableList);
                    private List<ProductItem> filtered = new ArrayList<>(searchableList);

                    @Override
                    public int getCount() { return filtered.size(); }
                    @Override
                    public ProductItem getItem(int pos) { return filtered.get(pos); }

                    @NonNull
                    @Override
                    public android.widget.Filter getFilter() {
                        return new android.widget.Filter() {
                            @Override
                            protected FilterResults performFiltering(CharSequence constraint) {
                                FilterResults results = new FilterResults();
                                List<ProductItem> suggestions = new ArrayList<>();

                                if (constraint == null || constraint.length() == 0) {
                                    suggestions.addAll(original);
                                } else {
                                    String pattern = constraint.toString().toLowerCase().trim();
                                    List<ProductItem> startsWith = new ArrayList<>();
                                    List<ProductItem> contains = new ArrayList<>();

                                    for (ProductItem pi : original) {
                                        String code = pi.code.toLowerCase();
                                        String name = pi.name.toLowerCase();
                                        String full = pi.toString().toLowerCase();

                                        if (code.startsWith(pattern) || name.startsWith(pattern)) {
                                            startsWith.add(pi);
                                        } else if (full.contains(pattern)) {
                                            contains.add(pi);
                                        }
                                    }
                                    suggestions.addAll(startsWith);
                                    suggestions.addAll(contains);
                                }
                                results.values = suggestions;
                                results.count = suggestions.size();
                                return results;
                            }

                            @Override
                            protected void publishResults(CharSequence constraint, FilterResults results) {
                                filtered.clear();
                                if (results.values != null) {
                                    filtered.addAll((List<ProductItem>) results.values);
                                }
                                notifyDataSetChanged();
                            }

                            @Override
                            public CharSequence convertResultToString(Object resultValue) {
                                return resultValue.toString();
                            }
                        };
                    }

                    @NonNull
                    @Override
                    public View getView(int position, @androidx.annotation.Nullable View convertView,
                                        @NonNull android.view.ViewGroup parent) {
                        TextView tv = (TextView) super.getView(position, convertView, parent);
                        if (position < filtered.size()) {
                            tv.setText(filtered.get(position).toString());
                        }
                        tv.setTextColor(android.graphics.Color.parseColor("#835C9F"));
                        
                        // QOL: Adaptive Text Size and Padding for tablets vs phones
                        android.util.DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
                        boolean isTablet = dm.widthPixels > 1200;

                        if (isTablet) {
                            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                            int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, dm);
                            tv.setPadding(padding, padding, padding, padding);
                        } else {
                            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                            int hPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, dm);
                            int vPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, dm);
                            tv.setPadding(hPadding, vPadding, hPadding, vPadding);
                        }

                        // Ensure text is readable and multiline if needed
                        tv.setSingleLine(false);
                        tv.setMaxLines(3);
                        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);

                        return tv;
                    }
                };
        actvItemCode.setAdapter(searchAdapter);
        layout.addView(actvItemCode);

        addFormLabel(layout, "Item Name (Auto-filled):");
        android.widget.EditText etItemName = new android.widget.EditText(this);
        etItemName.setFocusable(false);
        etItemName.setFocusableInTouchMode(false);
        etItemName.setClickable(false);
        etItemName.setSingleLine(false);
        etItemName.setMaxLines(3);
        styleModernInput(etItemName);
        layout.addView(etItemName);

        addFormLabel(layout, "Delivery Date:");
        android.widget.EditText etDate = new android.widget.EditText(this);
        String exactToday = new java.text.SimpleDateFormat("yyyy-MM-dd",
                java.util.Locale.US).format(new java.util.Date());
        etDate.setText(exactToday);
        etDate.setFocusable(false);
        etDate.setFocusableInTouchMode(false);
        etDate.setClickable(true);
        etDate.setCursorVisible(false);
        styleModernInput(etDate);
        etDate.setOnClickListener(v -> {
            int year, month, day;
            try {
                String[] parts = etDate.getText().toString().split("-");
                year  = Integer.parseInt(parts[0]);
                month = Integer.parseInt(parts[1]) - 1;
                day   = Integer.parseInt(parts[2]);
            } catch (Exception e) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                year  = cal.get(java.util.Calendar.YEAR);
                month = cal.get(java.util.Calendar.MONTH);
                day   = cal.get(java.util.Calendar.DAY_OF_MONTH);
            }
            new android.app.DatePickerDialog(this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        String formatted = String.format(java.util.Locale.US,
                                "%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay);
                        etDate.setText(formatted);
                    }, year, month, day).show();
        });
        layout.addView(etDate);

        addFormLabel(layout, "Quantity:");
        android.widget.EditText etQty = new android.widget.EditText(this);
        etQty.setText("1");
        etQty.setHint("e.g. 50");
        etQty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        styleModernInput(etQty);
        layout.addView(etQty);

        addFormLabel(layout, "Unit of Measure (UOM):");
        android.widget.Spinner spUom = new android.widget.Spinner(this,
                android.widget.Spinner.MODE_DROPDOWN);
        java.util.List<String> uomOptionsList = new java.util.ArrayList<>(
                java.util.Arrays.asList("Box", "Pieces", "Bottles", "Packs"));

        android.widget.ArrayAdapter<String> uomAdapter =
                new android.widget.ArrayAdapter<String>(
                        this, android.R.layout.simple_spinner_item, uomOptionsList) {
                    @NonNull
                    @Override
                    public View getView(int position, @androidx.annotation.Nullable View convertView,
                                        @NonNull android.view.ViewGroup parent) {
                        TextView tv = (TextView) super.getView(position, convertView, parent);
                        tv.setTextColor(android.graphics.Color.parseColor("#835C9F"));
                        tv.setTypeface(null, android.graphics.Typeface.NORMAL);
                        tv.setTextSize(14f);
                        return tv;
                    }
                    @Override
                    public View getDropDownView(int position,
                                                @androidx.annotation.Nullable View convertView,
                                                @NonNull android.view.ViewGroup parent) {
                        TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                        tv.setTextColor(android.graphics.Color.BLACK);
                        tv.setBackgroundColor(android.graphics.Color.WHITE);
                        tv.setPadding(50, 40, 50, 40);
                        tv.setTextSize(15f);
                        return tv;
                    }
                };
        uomAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spUom.setAdapter(uomAdapter);
        styleModernInput(spUom);
        spUom.setPopupBackgroundDrawable(popupBg);
        layout.addView(spUom);

        addFormLabel(layout, "Rate (₱):");
        android.widget.EditText etRate = new android.widget.EditText(this);
        etRate.setHint("0.00");
        etRate.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        styleModernInput(etRate);
        layout.addView(etRate);

        final String[] selectedRawCode = {""};

        actvItemCode.setOnItemClickListener((parent, view, position, id) -> {
            ProductItem selectedItem = (ProductItem) parent.getItemAtPosition(position);
            selectedRawCode[0] = selectedItem.code;
            etItemName.setText(selectedItem.name);

            String lookupKey = selectedItem.name.isEmpty()
                    ? selectedItem.code
                    : selectedItem.code + " : " + selectedItem.name;
            Double exactRate = itemRateMap.get(lookupKey);

            if (exactRate != null) {
                etRate.setText(String.format(java.util.Locale.US, "%.2f", exactRate));
            } else {
                etRate.setText("0.00");
            }

            String fetchedUom = selectedItem.uom;
            if (fetchedUom != null && !fetchedUom.isEmpty() && !fetchedUom.equalsIgnoreCase("null")) {
                int spinnerPos = uomAdapter.getPosition(fetchedUom);
                if (spinnerPos >= 0) {
                    spUom.setSelection(spinnerPos);
                } else {
                    uomAdapter.add(fetchedUom);
                    uomAdapter.notifyDataSetChanged();
                    spUom.setSelection(uomAdapter.getPosition(fetchedUom));
                }
            }
        });

        actvItemCode.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void afterTextChanged(android.text.Editable s) {
                if (s.length() == 0) {
                    selectedRawCode[0] = "";
                    etItemName.setText("");
                    etRate.setText("");
                    spUom.setSelection(0);
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        addFormLabel(layout, "Delivery Warehouse:");
        android.widget.Spinner spWarehouse = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> warehouseAdapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, new ArrayList<>(availableWarehouses));
        warehouseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spWarehouse.setAdapter(warehouseAdapter);
        styleModernInput(spWarehouse);
        layout.addView(spWarehouse);

        if (availableWarehouses.isEmpty()) {
            warehouseAdapter.add("PIMS MAIN - PE");
            warehouseAdapter.notifyDataSetChanged();
        } else {
            int defPos = warehouseAdapter.getPosition("PIMS MAIN - PE");
            if (defPos >= 0) spWarehouse.setSelection(defPos);
        }

        addFormLabel(layout, "Additional Notes:");
        android.widget.EditText etNotes = new android.widget.EditText(this);
        etNotes.setHint("Type any special instructions here...");
        etNotes.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etNotes.setMinLines(3);
        etNotes.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        styleModernInput(etNotes);
        layout.addView(etNotes);

        scrollView.addView(layout);
        builder.setView(scrollView);

        builder.setPositiveButton("Add to/Update item list", (dialog, which) -> {
            if (!selectedRawCode[0].isEmpty()) {
                String qStr = etQty.getText().toString();
                String rStr = etRate.getText().toString();
                String finalDate = etDate.getText().toString();

                if (!qStr.isEmpty() && Integer.parseInt(qStr) > 0) {

                    OrderItem newItem = new OrderItem();
                    newItem.setRowName(""); // New items don't have a row name ID yet
                    newItem.setItemName(selectedRawCode[0]);
                    newItem.setItemCode(selectedRawCode[0]);
                    newItem.setDate(finalDate);
                    newItem.setDeliveryDate(finalDate);
                    newItem.setQuantity(Integer.parseInt(qStr));
                    newItem.setUom(spUom.getSelectedItem().toString());
                    newItem.setRate(rStr.isEmpty() ? 0.0 : Double.parseDouble(rStr));
                    newItem.setWarehouse(spWarehouse.getSelectedItem().toString());
                    newItem.setNotes(etNotes.getText().toString());

                    orderList.add(newItem);
                    
                    // Notify adapter of changes
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                    onTotalChanged();
                    recyclerView.scrollToPosition(orderList.size() - 1);
                    AppNotification.show(this, "Item added! Code: " + selectedRawCode[0]);
                } else {
                    AppNotification.show(this, "Quantity cannot be blank or 0.");
                }
            } else {
                AppNotification.show(this, "Please search and select an Item Code first.");
            }
        });

        builder.setNegativeButton("Cancel", null);

        android.app.AlertDialog finalDialog = builder.create();
        if (finalDialog.getWindow() != null) {
            finalDialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            finalDialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        finalDialog.setOnShowListener(d -> {
            finalDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
            finalDialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
        });
        finalDialog.show();
    }

    private void addFormLabel(LinearLayout layout, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setTextColor(android.graphics.Color.parseColor("#835C9F"));
        tv.setPadding(10, 15, 0, 5);
        layout.addView(tv);
    }

    private void styleModernInput(View view) {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(android.graphics.Color.parseColor("#F8F9FA"));
        gd.setCornerRadius(22f);
        gd.setStroke(2, android.graphics.Color.parseColor("#DEE2E6"));
        view.setBackground(gd);
        view.setPadding(40, 35, 40, 35);

        if (view instanceof android.widget.TextView) {
            android.widget.TextView tv = (android.widget.TextView) view;
            tv.setTextColor(android.graphics.Color.parseColor("#835C9F"));
            tv.setHintTextColor(android.graphics.Color.parseColor("#99835C9F"));
            tv.setTextSize(14f);
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
            OrderDataManager.getInstance().clearData();
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
        Intent intent = new Intent(this, Activity_SO_Landscape.class);
        intent.putExtra("Session_Cookie", finalCookie);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
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
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        v.performClick();
                    }
                    break;
            }
            return true;
        });
    }
}