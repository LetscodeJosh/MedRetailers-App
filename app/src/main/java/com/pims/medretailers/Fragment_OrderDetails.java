package com.pims.medretailers;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Fragment_OrderDetails extends Fragment {

    private EditText etDate, etDeliveryDate;
    private AutoCompleteTextView etCustomer, etCompany;
    private Spinner spTerritory;
    private ImageView btnSubmit;

    private String finalCookie = "";
    private String loggedInUserRole = "";
    private final OkHttpClient client = new OkHttpClient();

    private final HashMap<String, String> customerIdMap = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_order_details, container, false);

        if (getActivity() != null) {
            SharedPreferences prefs = getActivity().getSharedPreferences("MedRetailerSession", Context.MODE_PRIVATE);
            finalCookie = prefs.getString("Session_Cookie", "");
            loggedInUserRole = prefs.getString("User_Role", "MedRep");
        }

        etCustomer     = view.findViewById(R.id.etCustomer);
        etDate         = view.findViewById(R.id.etDate);
        etDeliveryDate = view.findViewById(R.id.etDeliveryDate);
        etCompany      = view.findViewById(R.id.etCompany);
        spTerritory    = view.findViewById(R.id.spTerritory);
        btnSubmit      = view.findViewById(R.id.btnSubmitOrder);

        setupDatePicker(etDate, true);
        setupDatePicker(etDeliveryDate, false);
        styleDropdown(etCompany);
        styleDropdown(etCustomer);

        OrderDataManager data = OrderDataManager.getInstance();

        fetchCompanies();

        if (data.company != null && !data.company.isEmpty()) {
            fetchFilteredCustomers(data.company);
        } else {
            fetchFilteredCustomers("");
        }

        if (etCompany != null) {
            etCompany.setOnClickListener(v -> etCompany.showDropDown());
            etCompany.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) etCompany.showDropDown();
            });

            etCompany.setOnItemClickListener((parent, view1, position, id) -> {
                String selectedCompany = (String) parent.getItemAtPosition(position);
                etCustomer.setText("", false);
                customerIdMap.clear();
                data.customer = "";
                data.company = selectedCompany;
                AppNotification.show(getActivity(), "Loading customers for " + selectedCompany + "...", AppNotification.Type.INFO);
                fetchFilteredCustomers(selectedCompany);
            });
        }

        if (etCustomer != null) {
            etCustomer.setOnClickListener(v -> {
                if (etCompany.getText().toString().trim().isEmpty() && !loggedInUserRole.equals("Admin")) {
                    AppNotification.show(getActivity(), "Please select a Company first.", AppNotification.Type.ERROR);
                } else {
                    etCustomer.showDropDown();
                }
            });
            etCustomer.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) etCustomer.showDropDown();
            });

            etCustomer.setOnItemClickListener((parent, view1, position, id) -> {
                String selectedCustomer = (String) parent.getItemAtPosition(position);
                String safeCustomerId = customerIdMap.get(selectedCustomer);
                data.customer = safeCustomerId != null ? safeCustomerId : selectedCustomer;
                if (safeCustomerId != null) fetchCustomerDetails(safeCustomerId);
            });
        }

        if (data.isEditMode()) {
            if (etCustomer != null && data.customer != null && !data.customer.isEmpty()) {
                etCustomer.setText(data.customer, false);
            }
            if (etCompany != null && data.company != null && !data.company.isEmpty())
                etCompany.setText(data.company, false);
            if (etDate != null && data.transactionDate != null && !data.transactionDate.isEmpty())
                etDate.setText(data.transactionDate);
            if (etDeliveryDate != null && data.deliveryDate != null && !data.deliveryDate.isEmpty())
                etDeliveryDate.setText(data.deliveryDate);
        }

        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                String typedCustomer = etCustomer.getText().toString().trim();
                String typedCompany = etCompany.getText().toString().trim();

                if (typedCompany.isEmpty()) {
                    AppNotification.show(getActivity(), "Please select a Company.", AppNotification.Type.ERROR);
                    return;
                }
                if (typedCustomer.isEmpty()) {
                    AppNotification.show(getActivity(), "Please select a Customer.", AppNotification.Type.ERROR);
                    return;
                }

                String safeCustomerId = customerIdMap.get(typedCustomer);
                data.customer        = (safeCustomerId != null) ? safeCustomerId : typedCustomer;
                data.company         = typedCompany;
                data.transactionDate = etDate.getText().toString().trim();
                data.deliveryDate    = etDeliveryDate.getText().toString().trim();

                OrderSubmitter.submitFullOrder(getActivity(), finalCookie);
            });
        }

        return view;
    }

    private void styleDropdown(AutoCompleteTextView actv) {
        if (actv == null) return;
        android.graphics.drawable.GradientDrawable popupBg = new android.graphics.drawable.GradientDrawable();
        popupBg.setColor(android.graphics.Color.WHITE);
        popupBg.setCornerRadius(15f);
        popupBg.setStroke(2, android.graphics.Color.parseColor("#E0E0E0"));
        actv.setDropDownBackgroundDrawable(popupBg);
    }

    private String getSafeString(JSONObject json, String key) {
        String val = json.optString(key, "");
        if (val == null || val.equalsIgnoreCase("null") || val.isEmpty()) {
            return "";
        }
        return val.trim();
    }

    private void fetchCompanies() {
        String filterString = buildCompanyFilters();
        String url = Config.BASE_URL + "/api/resource/Company"
                + "?fields=[%22name%22,%22company_name%22]"
                + "&limit_page_length=999"
                + filterString;

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
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    if (etCompany != null && isAdded()) {
                                        SearchableAdapter adapter = new SearchableAdapter(getContext(), companyNames);
                                        etCompany.setAdapter(adapter);
                                        if (companyNames.size() == 1) {
                                            etCompany.setText(companyNames.get(0), false);
                                            OrderDataManager.getInstance().company = companyNames.get(0);
                                            fetchFilteredCustomers(companyNames.get(0));
                                        }
                                    }
                                });
                            }
                        }
                    } catch (JSONException e) { e.printStackTrace(); }
                }
            }
        });
    }

    private String buildCompanyFilters() {
        JSONArray andFilters = new JSONArray();
        String filterQuery = "";
        try {
            andFilters.put(new JSONArray().put("company_name").put("is").put("set"));
            if (loggedInUserRole != null && !loggedInUserRole.equalsIgnoreCase("Admin")) {
                if (getActivity() != null) {
                    SharedPreferences prefs = getActivity().getSharedPreferences("MedRetailerSession", Context.MODE_PRIVATE);
                    String permsString = prefs.getString("User_Permissions_Map", "{}");
                    JSONObject perms = new JSONObject(permsString);
                    if (perms.has("Company")) {
                        JSONArray allowedCompanies = perms.getJSONArray("Company");
                        if (allowedCompanies.length() > 0) {
                            andFilters.put(new JSONArray().put("name").put("in").put(allowedCompanies));
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

    private void fetchFilteredCustomers(String selectedCompany) {
        String filterString = buildCustomerFilters(selectedCompany);
        String url = Config.BASE_URL + "/api/resource/Customer"
                + "?fields=[%22name%22,%22customer_name%22]"
                + "&limit_page_length=999"
                + filterString;

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
                            List<String> customerNames = new ArrayList<>();
                            customerIdMap.clear();
                            for (int i = 0; i < dataArray.length(); i++) {
                                JSONObject obj = dataArray.getJSONObject(i);
                                String actualId = obj.optString("name");
                                String displayName = getSafeString(obj, "customer_name");
                                if (displayName.isEmpty()) {
                                    displayName = actualId;
                                }
                                customerNames.add(displayName);
                                customerIdMap.put(displayName, actualId);
                            }
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    if (etCustomer != null && isAdded()) {
                                        SearchableAdapter adapter = new SearchableAdapter(getContext(), customerNames);
                                        etCustomer.setAdapter(adapter);
                                    }
                                });
                            }
                        }
                    } catch (JSONException e) { e.printStackTrace(); }
                }
            }
        });
    }

    private String buildCustomerFilters(String selectedCompany) {
        JSONArray andFilters = new JSONArray();
        String filterQuery = "";
        try {
            andFilters.put(new JSONArray().put("customer_name").put("is").put("set"));
            if (selectedCompany != null && !selectedCompany.trim().isEmpty()) {
                andFilters.put(new JSONArray().put("company").put("=").put(selectedCompany.trim()));
            }
            if (loggedInUserRole != null && !loggedInUserRole.equalsIgnoreCase("Admin")) {
                if (getActivity() != null) {
                    SharedPreferences prefs = getActivity().getSharedPreferences("MedRetailerSession", Context.MODE_PRIVATE);
                    String permsString = prefs.getString("User_Permissions_Map", "{}");
                    JSONObject perms = new JSONObject(permsString);
                    if (perms.has("Territory")) {
                        JSONArray allowedTerritories = perms.getJSONArray("Territory");
                        if (allowedTerritories.length() > 0) {
                            andFilters.put(new JSONArray().put("territory").put("in").put(allowedTerritories));
                        }
                    }
                    if ((selectedCompany == null || selectedCompany.trim().isEmpty()) && perms.has("Company")) {
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
                            data.contactPerson = getSafeString(doc, "customer_primary_contact");
                            data.paymentTerms = getSafeString(doc, "payment_terms");
                        }
                    } catch (JSONException e) { e.printStackTrace(); }
                }
            }
        });
    }

    private void setupDatePicker(EditText editText, boolean isTransactionDate) {
        if (editText == null) return;
        editText.setFocusable(false);
        editText.setClickable(true);
        editText.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(getContext(), (view, year, month, day) -> {
                String selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
                editText.setText(selectedDate);
                if (isTransactionDate) OrderDataManager.getInstance().transactionDate = selectedDate;
                else OrderDataManager.getInstance().deliveryDate = selectedDate;
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        OrderDataManager data = OrderDataManager.getInstance();
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
    }

    @Override
    public void onPause() {
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
            tv.setPadding(40, 30, 40, 30);
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
                    } else {
                        String filterPattern = constraint.toString().toLowerCase().trim();
                        for (String item : originalItems) {
                            if (item.toLowerCase().contains(filterPattern)) suggestions.add(item);
                        }
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
