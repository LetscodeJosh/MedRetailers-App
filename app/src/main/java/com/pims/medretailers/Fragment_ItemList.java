package com.pims.medretailers;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Fragment_ItemList extends Fragment implements OrderAdapter.TotalUpdateListener {

    private RecyclerView recyclerView;
    private OrderAdapter adapter;
    private List<OrderItem> orderList;
    private TextView tvTotalAmount, tvTotalQty;
    private CheckBox cbSelectAll;
    private String finalCookie = "";
    private String loggedInUserRole = "";

    public static List<String> availableItemNames = new ArrayList<>();
    public static HashMap<String, String> itemCodeMap = new HashMap<>();
    public static HashMap<String, Double> itemRateMap = new HashMap<>();

    private List<ProductItem> dynamicProductList = new ArrayList<>();
    private final OkHttpClient client = new OkHttpClient();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_item_list, container, false);

        if (getActivity() != null) {
            SharedPreferences prefs = getActivity().getSharedPreferences("MedRetailerSession", Context.MODE_PRIVATE);
            finalCookie = prefs.getString("Session_Cookie", "");
            loggedInUserRole = prefs.getString("User_Role", "MedRep");
        }

        fetchItemsFromApi();

        orderList = OrderDataManager.getInstance().items;

        recyclerView  = view.findViewById(R.id.recyclerViewItems);
        tvTotalAmount = view.findViewById(R.id.tvTotalAmount);
        tvTotalQty    = view.findViewById(R.id.tvTotalQty);
        cbSelectAll   = view.findViewById(R.id.cbSelectAll);
        ImageView btnAdd    = view.findViewById(R.id.btnimgaddrow);
        ImageView btnDelete = view.findViewById(R.id.btnimgdeleterow);
        ImageView btnSubmit = view.findViewById(R.id.btnimgsubmitforapproval);

        if (recyclerView != null) {
            adapter = new OrderAdapter(orderList, getContext(), this);
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerView.setAdapter(adapter);
        }

        recyclerView.setNestedScrollingEnabled(false);
        recyclerView.setItemViewCacheSize(20);

        if (btnAdd != null) {
            btnAdd.setOnClickListener(v -> showSingleItemAddDialog());
        }

        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> deleteSelectedItems());
        }

        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                OrderDataManager.getInstance().items = this.orderList;
                OrderSubmitter.submitFullOrder(getActivity(), finalCookie);
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

        return view;
    }

    private void fetchItemsFromApi() {
        String filterString = buildItemFilters();
        String url = Config.BASE_URL + "/api/resource/Item%20Price"
                + "?fields=[%22item_code%22,%22item_name%22,%22uom%22,%22price_list_rate%22,%22valid_from%22,%22valid_upto%22]"
                + "&limit_page_length=500"
                + "&order_by=modified%20desc"
                + filterString;

        client.newCall(new Request.Builder()
                .url(url).addHeader("Cookie", finalCookie).get().build()
        ).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) { }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String respData = response.body().string();
                if (response.isSuccessful()) {
                    try {
                        JSONArray dataArray = new JSONObject(respData).getJSONArray("data");
                        List<ProductItem> freshList = new ArrayList<>();
                        freshList.add(new ProductItem("Select Code", "Select an Item...", "", ""));
                        ProductItem.liveCatalog.clear();

                        availableItemNames.clear();
                        itemCodeMap.clear();
                        itemRateMap.clear();

                        String todayStr = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());

                        for (int i = 0; i < dataArray.length(); i++) {
                            JSONObject item = dataArray.getJSONObject(i);
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

                            String code = item.optString("item_code", "");
                            String name = item.optString("item_name", "");
                            String uom  = item.optString("uom", "Box");
                            if (uom == null || uom.equalsIgnoreCase("null") || uom.isEmpty()) {
                                uom = "Box";
                            }
                            double rate = item.optDouble("price_list_rate", 0.0);

                            String displayName = name.isEmpty() || name.equalsIgnoreCase("null") ? code : code + " : " + name;

                            freshList.add(new ProductItem(code, name, "", uom));
                            ProductItem.liveCatalog.put(code, displayName);

                            availableItemNames.add(displayName);
                            itemCodeMap.put(displayName, code);
                            itemRateMap.put(displayName, rate);
                        }

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (isAdded()) {
                                    dynamicProductList.clear();
                                    dynamicProductList.addAll(freshList);
                                    if (adapter != null) adapter.notifyDataSetChanged();
                                }
                            });
                        }
                    } catch (JSONException e) { e.printStackTrace(); }
                }
            }
        });
    }

    private String buildItemFilters() {
        JSONArray andFilters = new JSONArray();
        String filterQuery = "";
        try {
            andFilters.put(new JSONArray().put("price_list").put("=").put("Standard Selling"));
            if (loggedInUserRole != null && !loggedInUserRole.equalsIgnoreCase("Admin")) {
                if (getActivity() != null) {
                    SharedPreferences prefs = getActivity().getSharedPreferences("MedRetailerSession", Context.MODE_PRIVATE);
                    String permsString = prefs.getString("User_Permissions_Map", "{}");
                    JSONObject perms = new JSONObject(permsString);
                    if (perms.has("Company")) {
                        JSONArray allowedCompanies = perms.getJSONArray("Company");
                        if (allowedCompanies.length() > 0) {
                            andFilters.put(new JSONArray().put("company").put("in").put(allowedCompanies));
                        }
                    }
                }
            }
        } catch (JSONException e) { e.printStackTrace(); }
        if (andFilters.length() > 0) {
            try { filterQuery = "&filters=" + java.net.URLEncoder.encode(andFilters.toString(), "UTF-8"); }
            catch (Exception e) { e.printStackTrace(); }
        }
        return filterQuery;
    }

    @Override
    public void onPause() {
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
    }

    private void showSingleItemAddDialog() {
        if (dynamicProductList.isEmpty()) {
            AppNotification.show(getActivity(), "Prices are still loading. Please wait.", AppNotification.Type.INFO);
            return;
        }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        android.widget.ScrollView scrollView = new android.widget.ScrollView(getContext());
        android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 20);

        android.graphics.drawable.GradientDrawable mainBg = new android.graphics.drawable.GradientDrawable();
        mainBg.setColor(android.graphics.Color.parseColor("#F2FFFFFF"));
        mainBg.setCornerRadius(40f);
        layout.setBackground(mainBg);

        TextView title = new TextView(getContext());
        title.setText("Complete Order Form");
        title.setTextSize(20f);
        title.setTextColor(android.graphics.Color.parseColor("#835C9F"));
        title.setPadding(0, 0, 0, 20);
        layout.addView(title);

        android.graphics.drawable.GradientDrawable popupBg = new android.graphics.drawable.GradientDrawable();
        popupBg.setColor(android.graphics.Color.WHITE);
        popupBg.setCornerRadius(20f);

        addFormLabel(layout, "Item Code (Searchable):");
        android.widget.AutoCompleteTextView actvItemCode = new android.widget.AutoCompleteTextView(getContext());
        actvItemCode.setHint("Tap to view all, or type to search...");
        styleModernInput(actvItemCode);
        actvItemCode.setThreshold(1);
        actvItemCode.setDropDownBackgroundDrawable(popupBg);
        actvItemCode.setOnClickListener(v -> actvItemCode.showDropDown());
        actvItemCode.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) actvItemCode.showDropDown(); });

        java.util.List<ProductItem> searchableList = new java.util.ArrayList<>(dynamicProductList);
        if (!searchableList.isEmpty() && searchableList.get(0).code.contains("Select"))
            searchableList.remove(0);

        android.widget.ArrayAdapter<ProductItem> searchAdapter =
                new android.widget.ArrayAdapter<ProductItem>(
                        getContext(), android.R.layout.simple_dropdown_item_1line, searchableList) {
                    @androidx.annotation.NonNull
                    @Override
                    public View getView(int position, @androidx.annotation.Nullable View convertView, @androidx.annotation.NonNull android.view.ViewGroup parent) {
                        TextView tv = (TextView) super.getView(position, convertView, parent);
                        tv.setTextColor(android.graphics.Color.parseColor("#835C9F"));
                        tv.setTextSize(15f);
                        tv.setPadding(40, 30, 40, 30);
                        return tv;
                    }
                };
        actvItemCode.setAdapter(searchAdapter);
        layout.addView(actvItemCode);

        addFormLabel(layout, "Item Name (Auto-filled):");
        android.widget.EditText etItemName = new android.widget.EditText(getContext());
        etItemName.setEnabled(false);
        styleModernInput(etItemName);
        etItemName.setTextColor(android.graphics.Color.parseColor("#835C9F"));
        layout.addView(etItemName);

        addFormLabel(layout, "Delivery Date:");
        android.widget.EditText etDate = new android.widget.EditText(getContext());
        String exactToday = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        etDate.setText(exactToday);
        styleModernInput(etDate);
        etDate.setFocusable(false);
        etDate.setClickable(true);
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
            new android.app.DatePickerDialog(getContext(),
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        String formatted = String.format(java.util.Locale.US, "%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay);
                        etDate.setText(formatted);
                    }, year, month, day).show();
        });
        layout.addView(etDate);

        addFormLabel(layout, "Quantity:");
        android.widget.EditText etQty = new android.widget.EditText(getContext());
        etQty.setHint("e.g. 50");
        etQty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        styleModernInput(etQty);
        layout.addView(etQty);

        addFormLabel(layout, "Unit of Measure (UOM):");
        android.widget.Spinner spUom = new android.widget.Spinner(getContext(), android.widget.Spinner.MODE_DROPDOWN);
        java.util.List<String> uomOptionsList = new java.util.ArrayList<>(java.util.Arrays.asList("Box", "Pieces", "Bottles", "Packs"));

        android.widget.ArrayAdapter<String> uomAdapter =
                new android.widget.ArrayAdapter<String>(
                        getContext(), android.R.layout.simple_spinner_item, uomOptionsList) {
                    @androidx.annotation.NonNull
                    @Override
                    public View getView(int position, @androidx.annotation.Nullable View convertView, @androidx.annotation.NonNull android.view.ViewGroup parent) {
                        TextView tv = (TextView) super.getView(position, convertView, parent);
                        tv.setTextColor(android.graphics.Color.parseColor("#835C9F"));
                        tv.setTextSize(14f);
                        return tv;
                    }
                };
        uomAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spUom.setAdapter(uomAdapter);
        styleModernInput(spUom);
        spUom.setPopupBackgroundDrawable(popupBg);
        layout.addView(spUom);

        addFormLabel(layout, "Rate (₱):");
        android.widget.EditText etRate = new android.widget.EditText(getContext());
        etRate.setHint("0.00");
        etRate.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        styleModernInput(etRate);
        layout.addView(etRate);

        final String[] selectedRawCode = {""};

        actvItemCode.setOnItemClickListener((parent, view, position, id) -> {
            ProductItem selectedItem = (ProductItem) parent.getItemAtPosition(position);
            selectedRawCode[0] = selectedItem.code;
            etItemName.setText(selectedItem.name);
            String lookupKey = selectedItem.name.isEmpty() ? selectedItem.code : selectedItem.code + " : " + selectedItem.name;
            Double exactRate = itemRateMap.get(lookupKey);
            if (exactRate != null) etRate.setText(String.format(java.util.Locale.US, "%.2f", exactRate));
            else etRate.setText("0.00");
            String fetchedUom = selectedItem.uom;
            if (fetchedUom != null && !fetchedUom.isEmpty()) {
                int spinnerPos = uomAdapter.getPosition(fetchedUom);
                if (spinnerPos >= 0) spUom.setSelection(spinnerPos);
            }
        });

        addFormLabel(layout, "Delivery Warehouse:");
        android.widget.EditText etWarehouse = new android.widget.EditText(getContext());
        etWarehouse.setText("PIMS MAIN - PE");
        etWarehouse.setEnabled(false);
        styleModernInput(etWarehouse);
        etWarehouse.setTextColor(android.graphics.Color.parseColor("#666666"));
        layout.addView(etWarehouse);

        addFormLabel(layout, "Additional Notes:");
        android.widget.EditText etNotes = new android.widget.EditText(getContext());
        etNotes.setHint("Type any special instructions here...");
        etNotes.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etNotes.setMinLines(3);
        etNotes.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        styleModernInput(etNotes);
        layout.addView(etNotes);

        scrollView.addView(layout);
        builder.setView(scrollView);

        builder.setPositiveButton("Apply to Table", (dialog, which) -> {
            if (!selectedRawCode[0].isEmpty()) {
                String qStr = etQty.getText().toString();
                String rStr = etRate.getText().toString();
                if (!qStr.isEmpty() && Integer.parseInt(qStr) > 0) {
                    OrderItem newItem = new OrderItem();
                    newItem.setItemName(selectedRawCode[0]);
                    newItem.setDate(etDate.getText().toString());
                    newItem.setQuantity(Integer.parseInt(qStr));
                    newItem.setUom(spUom.getSelectedItem().toString());
                    newItem.setRate(rStr.isEmpty() ? 0.0 : Double.parseDouble(rStr));
                    newItem.setWarehouse(etWarehouse.getText().toString());
                    newItem.setNotes(etNotes.getText().toString());
                    orderList.add(newItem);
                    adapter.notifyDataSetChanged();
                    onTotalChanged();
                }
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.create().show();
    }

    private void addFormLabel(android.widget.LinearLayout layout, String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTextColor(android.graphics.Color.parseColor("#835C9F"));
        tv.setPadding(10, 15, 0, 5);
        layout.addView(tv);
    }

    private void styleModernInput(View view) {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(android.graphics.Color.parseColor("#1A000000"));
        gd.setCornerRadius(20f);
        gd.setStroke(2, android.graphics.Color.parseColor("#4D000000"));
        view.setBackground(gd);
        view.setPadding(40, 35, 40, 35);
    }
}
