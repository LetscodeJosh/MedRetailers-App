package com.pims.medretailers;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
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
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Fragment_TermsConditions extends Fragment {

    private AutoCompleteTextView etPaymentTerms;
    private EditText etInstructions;
    private TableLayout tablePaymentSchedule;
    private Button btnAddRow, btnDeleteRow;
    private ImageView btnSubmit;
    private String finalCookie = "";
    private final OkHttpClient client = new OkHttpClient();
    private final List<String> cachedPaymentTerms = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terms_conditions, container, false);

        if (getActivity() != null) {
            SharedPreferences prefs = getActivity().getSharedPreferences("MedRetailerSession", Context.MODE_PRIVATE);
            finalCookie = prefs.getString("Session_Cookie", "");
        }

        etPaymentTerms       = view.findViewById(R.id.etPaymentTerms);
        etInstructions       = view.findViewById(R.id.etInstructions);
        tablePaymentSchedule = view.findViewById(R.id.tablePaymentSchedule);
        btnSubmit            = view.findViewById(R.id.btnSubmitOrder);

        android.graphics.drawable.GradientDrawable popupBg = new android.graphics.drawable.GradientDrawable();
        popupBg.setColor(android.graphics.Color.WHITE);
        popupBg.setCornerRadius(15f);
        popupBg.setStroke(2, android.graphics.Color.parseColor("#E0E0E0"));
        etPaymentTerms.setDropDownBackgroundDrawable(popupBg);

        etPaymentTerms.setOnClickListener(v -> etPaymentTerms.showDropDown());
        etPaymentTerms.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) etPaymentTerms.showDropDown(); });

        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                saveDataToManager();
                OrderSubmitter.submitFullOrder(getActivity(), finalCookie);
            });
        }

        fetchPaymentTerms();

        etPaymentTerms.setOnItemClickListener((parent, view1, position, id) -> {
            String selectedTerm = (String) parent.getItemAtPosition(position);
            fetchPaymentSchedule(selectedTerm);
        });

        return view;
    }

    private void saveDataToManager() {
        OrderDataManager data = OrderDataManager.getInstance();
        if (etPaymentTerms != null) data.paymentTerms = etPaymentTerms.getText().toString();
        if (etInstructions != null) data.instructions = etInstructions.getText().toString();
        saveTableDataToManager();
    }

    private void fetchPaymentTerms() {
        String url = Config.BASE_URL + "/api/resource/Payment%20Terms%20Template?fields=[%22name%22]&limit_page_length=999";
        Request request = new Request.Builder().url(url).get().addHeader("Cookie", finalCookie).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String respBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JSONArray dataArray = new JSONObject(respBody).getJSONArray("data");
                        cachedPaymentTerms.clear();
                        for (int i = 0; i < dataArray.length(); i++) {
                            String termName = dataArray.getJSONObject(i).optString("name", "");
                            if (!termName.isEmpty()) cachedPaymentTerms.add(termName);
                        }
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (isAdded()) {
                                    SearchableAdapter adapter = new SearchableAdapter(getContext(), cachedPaymentTerms);
                                    etPaymentTerms.setAdapter(adapter);
                                    updateTableRowAdapters();
                                }
                            });
                        }
                    } catch (JSONException e) { e.printStackTrace(); }
                }
            }
        });
    }

    private void updateTableRowAdapters() {
        if (tablePaymentSchedule == null) return;
        for (int i = 1; i < tablePaymentSchedule.getChildCount(); i++) {
            TableRow row = (TableRow) tablePaymentSchedule.getChildAt(i);
            if (row.getChildCount() >= 7 && row.getChildAt(2) instanceof AutoCompleteTextView) {
                AutoCompleteTextView actv = (AutoCompleteTextView) row.getChildAt(2);
                SearchableAdapter adapter = new SearchableAdapter(getContext(), cachedPaymentTerms);
                actv.setAdapter(adapter);
            }
        }
    }

    private void fetchPaymentSchedule(String paymentTermName) {
        try {
            String encodedTerm = java.net.URLEncoder.encode(paymentTermName, "UTF-8").replace("+", "%20");
            String url = Config.BASE_URL + "/api/resource/Payment%20Terms%20Template/" + encodedTerm;
            Request request = new Request.Builder().url(url).get().addHeader("Cookie", finalCookie).build();
            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    final String respBody = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        try {
                            JSONObject dataObj = new JSONObject(respBody).optJSONObject("data");
                            if (dataObj != null && dataObj.has("payment_schedule")) {
                                JSONArray scheduleArray = dataObj.getJSONArray("payment_schedule");
                                final List<OrderDataManager.PaymentScheduleItem> newSchedule = new ArrayList<>();
                                double realTotalOrderAmount = 0.0;
                                for (OrderItem item : OrderDataManager.getInstance().items) realTotalOrderAmount += item.getAmount();

                                for (int i = 0; i < scheduleArray.length(); i++) {
                                    JSONObject row = scheduleArray.getJSONObject(i);
                                    String term = row.optString("payment_term", "");
                                    String desc = row.optString("description", "");
                                    String portion = row.optString("invoice_portion", "0");
                                    String amount = "0.00";
                                    try {
                                        double port = Double.parseDouble(portion);
                                        amount = String.format(Locale.US, "%.2f", (port / 100) * realTotalOrderAmount);
                                    } catch (Exception ignored) { }
                                    newSchedule.add(new OrderDataManager.PaymentScheduleItem(term, desc, "", portion, amount));
                                }
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> { if (isAdded()) rebuildTable(newSchedule); });
                                }
                            }
                        } catch (JSONException e) { e.printStackTrace(); }
                    }
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void onResume() {
        super.onResume();
        OrderDataManager data = OrderDataManager.getInstance();
        if (etPaymentTerms != null) etPaymentTerms.setText(data.paymentTerms, false);
        if (etInstructions != null) etInstructions.setText(data.instructions);
        rebuildTable(data.paymentSchedule);
    }

    @Override
    public void onPause() {
        super.onPause();
        saveDataToManager();
    }

    private void addPaymentRow(String term, String desc, String date, String portion, String amt) {
        TableRow row = new TableRow(getContext());
        row.setBackgroundColor(Color.parseColor("#1E1E1E"));
        row.setPadding(0, 16, 0, 16);

        CheckBox cb = new CheckBox(getContext());
        cb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#AAAAAA")));
        row.addView(cb);

        TextView tvNum = new TextView(getContext());
        tvNum.setText(String.valueOf(tablePaymentSchedule.getChildCount()));
        tvNum.setTextColor(Color.WHITE);
        tvNum.setPadding(16, 0, 16, 0);
        row.addView(tvNum);

        AutoCompleteTextView etRowTerm = new AutoCompleteTextView(getContext());
        etRowTerm.setText(term, false);
        etRowTerm.setTextColor(Color.WHITE);
        etRowTerm.setHint("Select...");
        etRowTerm.setHintTextColor(Color.GRAY);
        etRowTerm.setPadding(16, 16, 16, 16);
        etRowTerm.setBackgroundColor(Color.TRANSPARENT);

        SearchableAdapter rowAdapter = new SearchableAdapter(getContext(), cachedPaymentTerms);
        etRowTerm.setAdapter(rowAdapter);
        etRowTerm.setOnClickListener(v -> etRowTerm.showDropDown());
        etRowTerm.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) etRowTerm.showDropDown(); });
        row.addView(etRowTerm);

        EditText etRowDesc = new EditText(getContext());
        etRowDesc.setText(desc);
        etRowDesc.setTextColor(Color.LTGRAY);
        etRowDesc.setPadding(16, 16, 16, 16);
        etRowDesc.setBackgroundColor(Color.TRANSPARENT);
        row.addView(etRowDesc);

        TextView tvRowDate = new TextView(getContext());
        tvRowDate.setText(date.isEmpty() ? "Select Date" : date);
        tvRowDate.setTextColor(Color.parseColor("#ff6b6b"));
        tvRowDate.setPadding(16, 16, 16, 16);
        tvRowDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(getContext(), (view, year, month, day) -> {
                String selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
                tvRowDate.setText(selectedDate);
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        row.addView(tvRowDate);

        EditText etRowPortion = new EditText(getContext());
        etRowPortion.setText(portion);
        etRowPortion.setTextColor(Color.WHITE);
        etRowPortion.setGravity(Gravity.CENTER);
        etRowPortion.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etRowPortion.setBackgroundColor(Color.TRANSPARENT);
        row.addView(etRowPortion);

        EditText etRowAmount = new EditText(getContext());
        etRowAmount.setText(amt);
        etRowAmount.setTextColor(Color.WHITE);
        etRowAmount.setGravity(Gravity.END);
        etRowAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etRowAmount.setBackgroundColor(Color.TRANSPARENT);
        row.addView(etRowAmount);

        tablePaymentSchedule.addView(row);
    }

    private void deleteSelectedRows() {
        for (int i = tablePaymentSchedule.getChildCount() - 1; i > 0; i--) {
            TableRow row = (TableRow) tablePaymentSchedule.getChildAt(i);
            CheckBox cb = (CheckBox) row.getChildAt(0);
            if (cb.isChecked()) tablePaymentSchedule.removeViewAt(i);
        }
        for (int i = 1; i < tablePaymentSchedule.getChildCount(); i++) {
            TableRow row = (TableRow) tablePaymentSchedule.getChildAt(i);
            ((TextView) row.getChildAt(1)).setText(String.valueOf(i));
        }
    }

    private void saveTableDataToManager() {
        OrderDataManager data = OrderDataManager.getInstance();
        data.paymentSchedule.clear();
        if (tablePaymentSchedule == null) return;
        for (int i = 1; i < tablePaymentSchedule.getChildCount(); i++) {
            TableRow row = (TableRow) tablePaymentSchedule.getChildAt(i);
            if (row.getChildCount() >= 7) {
                String term    = ((TextView) row.getChildAt(2)).getText().toString().trim();
                String desc    = ((TextView) row.getChildAt(3)).getText().toString().trim();
                String date    = ((TextView) row.getChildAt(4)).getText().toString().trim();
                String portion = ((TextView) row.getChildAt(5)).getText().toString().trim();
                String amt     = ((TextView) row.getChildAt(6)).getText().toString().trim();
                if (!term.isEmpty()) data.paymentSchedule.add(new OrderDataManager.PaymentScheduleItem(term, desc, date, portion, amt));
            }
        }
    }

    private void rebuildTable(List<OrderDataManager.PaymentScheduleItem> items) {
        if (tablePaymentSchedule == null) return;
        int childCount = tablePaymentSchedule.getChildCount();
        if (childCount > 1) tablePaymentSchedule.removeViews(1, childCount - 1);
        for (OrderDataManager.PaymentScheduleItem item : items) {
            addPaymentRow(item.termName, item.description, item.dueDate, item.portion, item.amount);
        }
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
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            TextView tv = (TextView) super.getView(position, convertView, parent);
            tv.setTextColor(Color.parseColor("#835C9F"));
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
                    if (constraint == null || constraint.length() == 0) suggestions.addAll(originalItems);
                    else {
                        String filterPattern = constraint.toString().toLowerCase().trim();
                        for (String item : originalItems) if (item.toLowerCase().contains(filterPattern)) suggestions.add(item);
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
