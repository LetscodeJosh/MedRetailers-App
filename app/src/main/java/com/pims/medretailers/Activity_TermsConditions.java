package com.pims.medretailers;

import android.app.DatePickerDialog;
import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.tabs.TabLayout;

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

public class Activity_TermsConditions extends BaseActivity {

    private AutoCompleteTextView etPaymentTerms;
    private EditText etInstructions;
    private TextView tvPageTitle;
    private TableLayout tablePaymentSchedule;
    private androidx.appcompat.widget.AppCompatButton btnSubmit;
    private String finalCookie = "";
    private final OkHttpClient client = new OkHttpClient();

    private final List<String> cachedPaymentTerms = new ArrayList<>();

    private String lastFetchedTerm = "";
    private boolean isProgrammaticUpdate = false;

    private final android.os.Handler syncHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable syncRunnable = () -> {
        Log.d("IMMEDIATE_SYNC", "Executing debounced silent sync from TermsConditions...");
        saveTableDataToManager(); // Ensure data is saved to manager before sync
        OrderSubmitter.saveOrderImmediately(this, finalCookie);
    };

    private void triggerImmediateSync() {
        syncHandler.removeCallbacks(syncRunnable);
        syncHandler.postDelayed(syncRunnable, 2000); // 2s debounce
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_termsconditons);

        // 1. SESSION
        SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);
        if (getIntent().hasExtra("Session_Cookie")) {
            finalCookie = getIntent().getStringExtra("Session_Cookie");
            prefs.edit().putString("Session_Cookie", finalCookie).apply();
        } else {
            finalCookie = prefs.getString("Session_Cookie", "");
        }

        // 2. BIND VIEWS
        etPaymentTerms       = findViewById(R.id.etPaymentTerms);
        etInstructions       = findViewById(R.id.etInstructions);
        tvPageTitle          = findViewById(R.id.tvPageTitle);
        tablePaymentSchedule = findViewById(R.id.tablePaymentSchedule);
        btnSubmit            = findViewById(R.id.btnSubmitforapproval);

        etInstructions.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                OrderDataManager.getInstance().instructions = s.toString();
                triggerImmediateSync();
                if (!isProgrammaticUpdate) checkChangesAndToggleSaveButton();
            }
        });

        // QA FIX: Change button to "Save Order" if in Edit Mode and Status is for Approval
        String editStatus = prefs.getString("EDIT_APPROVAL_STATUS", "");
        boolean isApprovalPending = editStatus.contains("For Approval by");

        if (OrderDataManager.getInstance().isEditMode() && btnSubmit != null && isApprovalPending) {
            btnSubmit.setText("Save");
            btnSubmit.requestLayout();
        }

        ImageView imgTopDecor = findViewById(R.id.imgTopDecor);
        if (imgTopDecor != null) {
            imgTopDecor.setOnClickListener(v -> animateHamburgerMenu(imgTopDecor, () -> showHamburgerMenu(imgTopDecor)));
        }

        updateHeaderTitle();
        styleDropdown(etPaymentTerms);

        etPaymentTerms.setOnClickListener(v -> etPaymentTerms.showDropDown());
        etPaymentTerms.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) etPaymentTerms.showDropDown();
        });


        if (btnSubmit != null) {
            setupHoverEffect(btnSubmit);
            btnSubmit.setOnClickListener(v -> {
                syncHandler.removeCallbacks(syncRunnable);
                OrderDataManager data = OrderDataManager.getInstance();
                data.paymentTerms  = etPaymentTerms.getText().toString();
                data.instructions  = etInstructions.getText().toString();
                saveTableDataToManager();
                OrderSubmitter.submitFullOrder(this, finalCookie);
            });
        }

        // QOL: Improved Interactivity - Apply hover effect to interactable inputs
        if (etPaymentTerms != null) setupHoverEffect(etPaymentTerms);
        if (etInstructions != null) setupHoverEffect(etInstructions);

        fetchPaymentTerms();

        etPaymentTerms.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isProgrammaticUpdate) return;

                String term = s.toString().trim();

                if (term.isEmpty()) {
                    if (!lastFetchedTerm.isEmpty()) {
                        lastFetchedTerm = "";
                        rebuildTable(new ArrayList<>());
                    }
                } else if (cachedPaymentTerms.contains(term) && !term.equalsIgnoreCase(lastFetchedTerm)) {
                    lastFetchedTerm = term;
                    OrderDataManager.getInstance().paymentTerms = term;
                    fetchPaymentSchedule(term);
                    triggerImmediateSync();
                    checkChangesAndToggleSaveButton();
                } else {
                    // If they are typing a custom term not in the API list, unlock the table
                    OrderDataManager.getInstance().paymentTerms = term;
                    rebuildTable(new ArrayList<>());
                    triggerImmediateSync();
                    checkChangesAndToggleSaveButton();
                }
            }
        });

        // 4. TABS
        setupTabs();
    }

    private void styleDropdown(AutoCompleteTextView actv) {
        if (actv == null) return;

        // Modern Rounded Background with App Theme Border
        android.graphics.drawable.GradientDrawable popupBg = new android.graphics.drawable.GradientDrawable();
        popupBg.setColor(android.graphics.Color.WHITE);
        popupBg.setCornerRadius(20f);
        popupBg.setStroke(3, android.graphics.Color.parseColor("#835C9F"));
        actv.setDropDownBackgroundDrawable(popupBg);

        // QOL: Ensure the list appears BELOW or ABOVE the field.
        // The vertical offset and height adjustments below ensure the field remains visible.

        // Add a slight vertical offset to avoid obstructing the input field's border
        actv.setDropDownVerticalOffset((int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics()));

        // Optimization for Screen Types:
        // Adjust dropdown height dynamically to ensure it's usable on phones and expansive on tablets.
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenHeight = dm.heightPixels;
        boolean isTablet = dm.widthPixels > 1200; // Common threshold for tablet-like width

        // Set max height for dropdown: 45% for tablets, 35% for phones to keep keyboard visibility
        int maxH = isTablet ? (int)(screenHeight * 0.45) : (int)(screenHeight * 0.35);
        actv.setDropDownHeight(maxH);

        // Ensure the dropdown width matches the field width for a clean, non-obstructive layout
        actv.setDropDownWidth(android.view.ViewGroup.LayoutParams.MATCH_PARENT);
    }

    // Determines if the table should be locked based on if it's an official API term
    private boolean isTableEditable() {
        String currentTerm = etPaymentTerms.getText().toString().trim();
        return currentTerm.isEmpty() || !cachedPaymentTerms.contains(currentTerm);
    }

    private void fetchPaymentTerms() {
        String url = Config.BASE_URL + "/api/resource/Payment%20Terms%20Template"
                + "?fields=[%22name%22]"
                + "&limit_page_length=999";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Cookie", finalCookie)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("TermsConditions", "Network failure fetching terms", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String respBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonObject = new JSONObject(respBody);
                        if (jsonObject.has("data")) {
                            JSONArray dataArray = jsonObject.getJSONArray("data");
                            cachedPaymentTerms.clear();
                            for (int i = 0; i < dataArray.length(); i++) {
                                JSONObject obj = dataArray.getJSONObject(i);
                                String termName = obj.optString("name", "");
                                if (!termName.isEmpty()) cachedPaymentTerms.add(termName);
                            }
                            runOnUiThread(() -> {
                                if (etPaymentTerms != null) {
                                    SearchableAdapter adapter = new SearchableAdapter(Activity_TermsConditions.this, cachedPaymentTerms);
                                    etPaymentTerms.setAdapter(adapter);

                                    String currentText = etPaymentTerms.getText().toString().trim();
                                    if (!currentText.isEmpty() && cachedPaymentTerms.contains(currentText) && !currentText.equals(lastFetchedTerm)) {
                                        lastFetchedTerm = currentText;
                                        fetchPaymentSchedule(currentText);
                                    } else {
                                        // Update editability state now that API data is loaded
                                        rebuildTable(OrderDataManager.getInstance().paymentSchedule);
                                    }
                                }
                                updateTableRowAdapters();
                            });
                        }
                    } catch (JSONException e) {
                        Log.e("TermsConditions", "JSON Error fetching terms", e);
                    }
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
                SearchableAdapter adapter = new SearchableAdapter(this, cachedPaymentTerms);
                actv.setAdapter(adapter);
            }
        }
    }

    private void fetchPaymentSchedule(String paymentTermName) {
        try {
            String encodedTerm = java.net.URLEncoder.encode(paymentTermName, "UTF-8").replace("+", "%20");
            String url = Config.BASE_URL + "/api/resource/Payment%20Terms%20Template/" + encodedTerm;

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Cookie", finalCookie)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {}

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    final String respBody = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonObject = new JSONObject(respBody);
                            JSONObject dataObj = jsonObject.optJSONObject("data");

                            if (dataObj != null) {
                                JSONArray scheduleArray = null;

                                if (dataObj.has("terms")) {
                                    scheduleArray = dataObj.getJSONArray("terms");
                                } else if (dataObj.has("payment_schedule")) {
                                    scheduleArray = dataObj.getJSONArray("payment_schedule");
                                }

                                if (scheduleArray != null) {
                                    final List<OrderDataManager.PaymentScheduleItem> newSchedule = new ArrayList<>();
                                    OrderDataManager data = OrderDataManager.getInstance();

                                    double realTotalOrderAmount = 0.0;
                                    if (data.items != null) {
                                        for (int j = 0; j < data.items.size(); j++) {
                                            realTotalOrderAmount += data.items.get(j).getAmount();
                                        }
                                    }

                                    for (int i = 0; i < scheduleArray.length(); i++) {
                                        JSONObject row = scheduleArray.getJSONObject(i);
                                        String term = row.optString("payment_term", "");
                                        String desc = row.optString("description", "");
                                        String portion = row.optString("invoice_portion", "0");
                                        int creditDays = row.optInt("credit_days", 0);

                                        String amount = "0.00";
                                        try {
                                            double port = Double.parseDouble(portion);
                                            amount = String.format(java.util.Locale.US, "%.2f", (port / 100) * realTotalOrderAmount);
                                        } catch (NumberFormatException ex) {}

                                        // Calculate Due Date based on Transaction Date + credit_days
                                        String dueDate = "";
                                        try {
                                            String baseDateStr = data.transactionDate;
                                            if (baseDateStr == null || baseDateStr.isEmpty()) {
                                                baseDateStr = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
                                            }
                                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
                                            java.util.Date date = sdf.parse(baseDateStr);
                                            Calendar cal = Calendar.getInstance();
                                            cal.setTime(date);
                                            cal.add(Calendar.DAY_OF_MONTH, creditDays);
                                            dueDate = sdf.format(cal.getTime());
                                        } catch (Exception e) {
                                            dueDate = data.transactionDate; // fallback
                                        }

                                        newSchedule.add(new OrderDataManager.PaymentScheduleItem(term, desc, dueDate, portion, amount));
                                    }

                                    runOnUiThread(() -> {
                                        // Update manager list too so it's ready for navigation/submission
                                        OrderDataManager.getInstance().paymentSchedule.clear();
                                        OrderDataManager.getInstance().paymentSchedule.addAll(newSchedule);

                                        rebuildTable(newSchedule);
                                        AppNotification.show(Activity_TermsConditions.this, "Schedule loaded for " + paymentTermName, AppNotification.Type.SUCCESS);
                                    });
                                }
                            }
                        } catch (JSONException e) {}
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        OrderDataManager data = OrderDataManager.getInstance();
        isProgrammaticUpdate = true;

        if (etPaymentTerms != null) etPaymentTerms.setText(data.paymentTerms, false);
        if (etInstructions != null) etInstructions.setText(data.instructions);

        // BUG FIX: Only fetch fresh schedule if the manager's list is empty
        // OR if the items are needed for recalculation. 
        // We use the existing data if available to prevent duplication during navigation.
        String term = data.paymentTerms != null ? data.paymentTerms.trim() : "";
        if (!term.isEmpty() && cachedPaymentTerms.contains(term) && data.paymentSchedule.isEmpty()) {
            fetchPaymentSchedule(term);
        } else {
            rebuildTable(data.paymentSchedule);
        }

        lastFetchedTerm = data.paymentTerms;
        isProgrammaticUpdate = false;
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

    @Override
    protected void onPause() {
        super.onPause();
        OrderDataManager data = OrderDataManager.getInstance();
        if (etPaymentTerms != null)
            data.paymentTerms = etPaymentTerms.getText().toString();
        if (etInstructions != null)
            data.instructions = etInstructions.getText().toString();
        saveTableDataToManager();
    }

    // =========================================================================
    // DYNAMIC PAYMENT ROW LOGIC (Handles Both Editable and Locked States)
    // =========================================================================
    private void addPaymentRow(String term, String desc, String date, String portion, String amt, boolean isEditable) {
        TableRow row = new TableRow(this);
        row.setBackgroundColor(Color.WHITE);
        row.setPadding(0, 16, 0, 16);

        // Optimization: Adjust text size based on screen width
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        boolean isTablet = dm.widthPixels > 1200;
        float tableTextSize = isTablet ? 16f : 12f;

        // 0. Checkbox
        CheckBox cb = new CheckBox(this);
        cb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#835C9F")));
        cb.setEnabled(isEditable);
        row.addView(cb);

        // 1. Row Number
        TextView tvNum = new TextView(this);
        tvNum.setText(String.valueOf(tablePaymentSchedule.getChildCount()));
        tvNum.setTextColor(Color.parseColor("#333333"));
        tvNum.setTextSize(TypedValue.COMPLEX_UNIT_SP, tableTextSize);
        tvNum.setPadding(16, 0, 16, 0);
        row.addView(tvNum);

        // 2. Payment Term Dropdown
        AutoCompleteTextView etRowTerm = new AutoCompleteTextView(this);
        etRowTerm.setText(term, false);
        etRowTerm.setHint("Select...");
        etRowTerm.setHintTextColor(Color.GRAY);
        etRowTerm.setPadding(16, 16, 16, 16);
        etRowTerm.setBackgroundColor(Color.TRANSPARENT);
        etRowTerm.setMinimumWidth(300);
        etRowTerm.setTextSize(TypedValue.COMPLEX_UNIT_SP, tableTextSize);

        etRowTerm.setEnabled(isEditable);
        etRowTerm.setTextColor(Color.parseColor("#333333"));

        if (isEditable) {
            GradientDrawable rowPopupBg = new GradientDrawable();
            rowPopupBg.setColor(Color.WHITE);
            rowPopupBg.setCornerRadius(15f);
            etRowTerm.setDropDownBackgroundDrawable(rowPopupBg);

            SearchableAdapter rowAdapter = new SearchableAdapter(this, cachedPaymentTerms);
            etRowTerm.setAdapter(rowAdapter);
            etRowTerm.setOnClickListener(v -> etRowTerm.showDropDown());
            etRowTerm.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) etRowTerm.showDropDown(); });
        }
        row.addView(etRowTerm);

        // 3. Description
        EditText etRowDesc = new EditText(this);
        etRowDesc.setText(desc);
        etRowDesc.setPadding(16, 16, 16, 16);
        etRowDesc.setBackgroundColor(Color.TRANSPARENT);
        etRowDesc.setMinimumWidth(350);
        etRowDesc.setEnabled(isEditable);
        etRowDesc.setTextColor(Color.parseColor("#333333"));
        etRowDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, tableTextSize);
        row.addView(etRowDesc);

        // 4. Clickable Date Picker
        TextView tvRowDate = new TextView(this);
        tvRowDate.setText(date.isEmpty() ? "Select Date" : date);
        tvRowDate.setPadding(16, 16, 16, 16);
        tvRowDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, tableTextSize);

        if (isEditable) {
            tvRowDate.setTextColor(Color.parseColor("#835C9F"));
            tvRowDate.setOnClickListener(v -> {
                Calendar c = Calendar.getInstance();
                new DatePickerDialog(this, (view, year, month, day) -> {
                    String selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
                    tvRowDate.setText(selectedDate);
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
            });
        } else {
            tvRowDate.setTextColor(Color.parseColor("#666666"));
            tvRowDate.setOnClickListener(null);
        }
        row.addView(tvRowDate);

        // 5. Portion %
        EditText etRowPortion = new EditText(this);
        etRowPortion.setText(portion);
        etRowPortion.setGravity(Gravity.CENTER);
        etRowPortion.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etRowPortion.setBackgroundColor(Color.TRANSPARENT);
        etRowPortion.setPadding(16, 16, 16, 16);
        etRowPortion.setEnabled(isEditable);
        etRowPortion.setTextColor(Color.parseColor("#333333"));
        etRowPortion.setTextSize(TypedValue.COMPLEX_UNIT_SP, tableTextSize);
        row.addView(etRowPortion);

        // 6. Amount
        EditText etRowAmount = new EditText(this);
        etRowAmount.setText(amt);
        etRowAmount.setGravity(Gravity.END);
        etRowAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etRowAmount.setBackgroundColor(Color.TRANSPARENT);
        etRowAmount.setPadding(16, 16, 16, 16);
        etRowAmount.setEnabled(isEditable);
        etRowAmount.setTextColor(Color.parseColor("#333333"));
        etRowAmount.setTextSize(TypedValue.COMPLEX_UNIT_SP, tableTextSize);
        row.addView(etRowAmount);

        // 7. Icon
        androidx.appcompat.widget.AppCompatImageView imgEdit = new androidx.appcompat.widget.AppCompatImageView(this);
        imgEdit.setImageResource(android.R.drawable.ic_menu_edit);
        androidx.core.widget.ImageViewCompat.setImageTintList(imgEdit, android.content.res.ColorStateList.valueOf(Color.parseColor("#835C9F")));

        imgEdit.setVisibility(isEditable ? View.VISIBLE : View.INVISIBLE);
        row.addView(imgEdit);

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
                String portion = ((TextView) row.getChildAt(5)).getText().toString().replace("%", "").replace(",", "").trim();
                String amt     = ((TextView) row.getChildAt(6)).getText().toString().replace("₱", "").replace(",", "").trim();

                if (!term.isEmpty() || !amountIsZero(amt)) {
                    data.paymentSchedule.add(new OrderDataManager.PaymentScheduleItem(term, desc, date, portion, amt));
                }
            }
        }
    }

    private boolean amountIsZero(String amt) {
        try { return Double.parseDouble(amt) <= 0; } catch (Exception e) { return true; }
    }

    private void rebuildTable(List<OrderDataManager.PaymentScheduleItem> items) {
        if (tablePaymentSchedule == null) return;

        // AGGRESSIVE CLEAR: Remove all rows except the header (index 0)
        while (tablePaymentSchedule.getChildCount() > 1) {
            tablePaymentSchedule.removeViewAt(1);
        }

        if (items == null || items.isEmpty()) return;

        boolean isEditable = isTableEditable();

        // Use a temporary list to avoid issues if the input list is modified during iteration
        List<OrderDataManager.PaymentScheduleItem> itemsToDisplay = new ArrayList<>(items);

        for (OrderDataManager.PaymentScheduleItem item : itemsToDisplay) {
            addPaymentRow(item.termName, item.description, item.dueDate, item.portion, item.amount, isEditable);
        }
        checkChangesAndToggleSaveButton();
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

        String oldPayT = data.paymentTerms;
        String oldInst = data.instructions;
        List<OrderDataManager.PaymentScheduleItem> oldSched = new ArrayList<>(data.paymentSchedule);

        if (etPaymentTerms != null) data.paymentTerms = etPaymentTerms.getText().toString().trim();
        if (etInstructions != null) data.instructions = etInstructions.getText().toString().trim();
        saveTableDataToManager();

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

        data.paymentTerms = oldPayT;
        data.instructions = oldInst;
        data.paymentSchedule.clear();
        data.paymentSchedule.addAll(oldSched);
    }

    // =========================================================================
    // TABS & DIALOGS
    // =========================================================================
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

    private void setupTabs() {
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        if (tabLayout == null) return;

        // Set initial selection without animation (Terms is now index 3)
        TabLayout.Tab currentTab = tabLayout.getTabAt(3);
        if (currentTab != null) {
            currentTab.select();
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                // SAVE DATA BEFORE LEAVING
                OrderDataManager data = OrderDataManager.getInstance();
                if (etPaymentTerms != null) data.paymentTerms = etPaymentTerms.getText().toString();
                if (etInstructions != null) data.instructions = etInstructions.getText().toString();
                saveTableDataToManager();

                // If a sync was pending, execute it immediately before switching
                syncHandler.removeCallbacks(syncRunnable);
                syncRunnable.run();

                int pos = tab.getPosition();
                if (pos == 3) return;

                Intent intent = null;
                switch (pos) {
                    case 0: intent = new Intent(Activity_TermsConditions.this, Activity_OrderDetails.class); break;
                    case 1: intent = new Intent(Activity_TermsConditions.this, Activity_ItemList_MedRepView.class); break;
                    case 2: intent = new Intent(Activity_TermsConditions.this, Activity_AddressContactDetails.class); break;
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

            // QOL: Use DP-based padding for consistent touch targets across screen sizes
            int hPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getContext().getResources().getDisplayMetrics());
            int vPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getContext().getResources().getDisplayMetrics());
            tv.setPadding(hPadding, vPadding, hPadding, vPadding);

            // Optimization for Tablets: Slightly larger text for better readability
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