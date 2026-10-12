package com.pims.medretailers;

import android.content.Intent;
import androidx.appcompat.app.AlertDialog;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import android.util.TypedValue;
import android.view.ViewGroup;

public class Activity_AddressContactDetails extends BaseActivity {

    private static final String TAG = "AddressTab";

    private EditText etCustomerAddressName, etContactPerson, etMobileNumber, etFullAddress, etTerritory;
    private TextView tvPageTitle;
    private androidx.appcompat.widget.AppCompatButton btnSubmit;
    private String finalCookie = "";
    private final OkHttpClient client = new OkHttpClient();
    private boolean isProgrammaticUpdate = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_addresscontactdetails);

        SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);
        if (getIntent().hasExtra("Session_Cookie")) {
            finalCookie = getIntent().getStringExtra("Session_Cookie");
            prefs.edit().putString("Session_Cookie", finalCookie).apply();
        } else {
            finalCookie = prefs.getString("Session_Cookie", "");
        }

        etCustomerAddressName = findViewById(R.id.etCustomerAddressName);
        etContactPerson       = findViewById(R.id.etContactPerson);
        etMobileNumber        = findViewById(R.id.etMobileNumber);
        etFullAddress         = findViewById(R.id.etFullAddress);
        etTerritory           = findViewById(R.id.etTerritory);
        tvPageTitle           = findViewById(R.id.tvPageTitle);
        btnSubmit             = findViewById(R.id.btnSubmitOrder);

        // QA FIX: Change button to "Save Order" if in Edit Mode and Status is for Approval
        String editStatus = prefs.getString("EDIT_APPROVAL_STATUS", "");
        boolean isApprovalPending = editStatus.contains("For Approval by");

        if (OrderDataManager.getInstance().isEditMode() && btnSubmit != null && isApprovalPending) {
            btnSubmit.setText("Save");
            btnSubmit.requestLayout();
        }

        if (btnSubmit != null) {
            setupHoverEffect(btnSubmit);
            btnSubmit.setOnClickListener(v -> {
                OrderDataManager data = OrderDataManager.getInstance();
                data.contactPerson       = etContactPerson.getText().toString().trim();
                data.mobileNumber        = etMobileNumber.getText().toString().trim();
                data.customerAddressName = etCustomerAddressName.getText().toString().trim();
                data.fullAddress         = etFullAddress.getText().toString().trim();
                data.territory           = etTerritory.getText().toString().trim();

                OrderSubmitter.submitFullOrder(this, finalCookie);
            });
        }

        // QOL: Improved Interactivity - Apply hover effect to all interactable input fields
        View[] inputs = {etCustomerAddressName, etContactPerson, etMobileNumber, etFullAddress, etTerritory};
        for (View v : inputs) {
            if (v != null) setupHoverEffect(v);
        }

        ImageView imgTopDecor = findViewById(R.id.imgTopDecor);
        if (imgTopDecor != null) {
            imgTopDecor.setOnClickListener(v -> animateHamburgerMenu(imgTopDecor, () -> showHamburgerMenu(imgTopDecor)));
        }
        setupTabs();
        updateHeaderTitle();
        setupChangeDetection();
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

    private void setupChangeDetection() {
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (!isProgrammaticUpdate) checkChangesAndToggleSaveButton();
            }
        };
        if (etCustomerAddressName != null) etCustomerAddressName.addTextChangedListener(watcher);
        if (etContactPerson != null) etContactPerson.addTextChangedListener(watcher);
        if (etMobileNumber != null) etMobileNumber.addTextChangedListener(watcher);
        if (etFullAddress != null) etFullAddress.addTextChangedListener(watcher);
        if (etTerritory != null) etTerritory.addTextChangedListener(watcher);
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

        String oldCont = data.contactPerson;
        String oldMobi = data.mobileNumber;
        String oldAddrN = data.customerAddressName;
        String oldAddr = data.fullAddress;
        String oldTerr = data.territory;

        if (etContactPerson != null) data.contactPerson = etContactPerson.getText().toString().trim();
        if (etMobileNumber != null) data.mobileNumber = etMobileNumber.getText().toString().trim();
        if (etCustomerAddressName != null) data.customerAddressName = etCustomerAddressName.getText().toString().trim();
        if (etFullAddress != null) data.fullAddress = etFullAddress.getText().toString().trim();
        if (etTerritory != null) data.territory = etTerritory.getText().toString().trim();

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

        data.contactPerson = oldCont;
        data.mobileNumber = oldMobi;
        data.customerAddressName = oldAddrN;
        data.fullAddress = oldAddr;
        data.territory = oldTerr;
    }

    @Override
    protected void onResume() {
        super.onResume();
        OrderDataManager data = OrderDataManager.getInstance();

        String selectedCustomer = data.customer;

        boolean shouldFetch = (data.fullAddress == null || data.fullAddress.isEmpty())
                && selectedCustomer != null
                && !selectedCustomer.isEmpty();

        if (shouldFetch) {
            Log.d(TAG, "Triggering fetch pipeline for customer: " + selectedCustomer);
            executeMasterFetchPipeline(selectedCustomer);
        } else {
            restoreFieldsFromMemory(data);
        }
    }

    private void restoreFieldsFromMemory(OrderDataManager data) {
        isProgrammaticUpdate = true;
        if (etCustomerAddressName != null && data.customerAddressName != null)
            etCustomerAddressName.setText(data.customerAddressName);
        if (etContactPerson != null && data.contactPerson != null)
            etContactPerson.setText(data.contactPerson);
        if (etMobileNumber != null && data.mobileNumber != null)
            etMobileNumber.setText(data.mobileNumber);
        if (etFullAddress != null && data.fullAddress != null)
            etFullAddress.setText(data.fullAddress);
        if (etTerritory != null && data.territory != null)
            etTerritory.setText(data.territory);
        isProgrammaticUpdate = false;
        checkChangesAndToggleSaveButton();
    }

    private String getSafeString(JSONObject json, String key) {
        String val = json.optString(key, "");
        if (val == null || val.equalsIgnoreCase("null") || val.isEmpty()) {
            return "";
        }
        return val.trim();
    }

    // =========================================================================================
    // THE MASTER FETCH PIPELINE
    // Stage 1: Get Customer Data directly via ID (Object response)
    // =========================================================================================
    private void executeMasterFetchPipeline(String customerId) {
        try {
            // QA FIX: Uri.encode perfectly handles spaces to %20 instead of the buggy + sign
            String encodedCustomer = Uri.encode(customerId);

            // Fetch directly by ID to ensure we get the exact Object
            String url = Config.BASE_URL + "/api/resource/Customer/" + encodedCustomer;

            client.newCall(new Request.Builder().url(url).addHeader("Cookie", finalCookie).get().build())
                    .enqueue(new Callback() {
                        @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            Log.e(TAG, "Failed to connect to API", e);
                        }

                        @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            String respData = response.body() != null ? response.body().string() : "";
                            if (!response.isSuccessful()) {
                                Log.e(TAG, "Customer API Error: " + response.code() + " " + respData);
                                return;
                            }

                            try {
                                JSONObject jsonObject = new JSONObject(respData);
                                JSONObject cust = jsonObject.optJSONObject("data");
                                if (cust == null) return;

                                String customerName       = getSafeString(cust, "customer_name");
                                if (customerName.isEmpty()) customerName = customerId; // Fallback to ID

                                String territory          = getSafeString(cust, "territory");
                                String mobileNo           = getSafeString(cust, "mobile_no");

                                // Address references
                                String primaryAddressHtml = getSafeString(cust, "primary_address");
                                String addressId          = getSafeString(cust, "customer_primary_address");

                                // Contact references - including the specific v15 field you mentioned
                                String contactDocName     = getSafeString(cust, "customer_primary_contact");
                                String customerAddress    = getSafeString(cust, "customer_address");

                                // Fallback for addressId: use customer_address if primary is empty
                                if (addressId.isEmpty() && !customerAddress.isEmpty()) {
                                    addressId = customerAddress;
                                }

                                // Incorporating the v15 Contact trick:
                                // If primary contact is empty but customer_address exists, use it as a fallback contact reference.
                                if (contactDocName.isEmpty() && !customerAddress.isEmpty()) {
                                    contactDocName = customerAddress;
                                }

                                // --- ADDRESS ROUTING LOGIC ---
                                if (!primaryAddressHtml.isEmpty()) {
                                    // FAST PATH: HTML Address is already generated by ERPNext
                                    String fullAddress = primaryAddressHtml.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n").trim();
                                    routeToContactFetch(customerId, addressId, territory, mobileNo, fullAddress, contactDocName);
                                }
                                else if (!addressId.isEmpty()) {
                                    // DIRECT PATH: Has an Address ID, fetch the fields manually
                                    fetchSpecificAddress(customerId, territory, mobileNo, addressId, contactDocName);
                                }
                                else {
                                    // SEARCH PATH: No explicit address linked, search Dynamic Links
                                    searchAddressViaDynamicLink(customerId, territory, mobileNo, contactDocName);
                                }

                            } catch (Exception e) {
                                Log.e(TAG, "Customer JSON Parsing Error", e);
                            }
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Master Pipeline Exception", e);
        }
    }

    // =========================================================================================
    // Stage 2A: Fetch Specific Address by ID
    // =========================================================================================
    private void fetchSpecificAddress(String customerId, String territory, String mobileNo, String addressId, String contactDocName) {
        String url = Config.BASE_URL + "/api/resource/Address/" + Uri.encode(addressId);

        client.newCall(new Request.Builder().url(url).addHeader("Cookie", finalCookie).get().build())
                .enqueue(new Callback() {
                    @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        routeToContactFetch(customerId, addressId, territory, mobileNo, "", contactDocName);
                    }
                    @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        String fullAddress = "";
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONObject dataObj = new JSONObject(response.body().string()).optJSONObject("data");
                                if (dataObj != null) {
                                    fullAddress = buildAddressString(dataObj);
                                }
                            } catch (Exception e) { Log.e(TAG, "Address parse error", e); }
                        }
                        routeToContactFetch(customerId, addressId, territory, mobileNo, fullAddress, contactDocName);
                    }
                });
    }

    // =========================================================================================
    // Stage 2B: Search for Unlinked Address via Dynamic Link
    // =========================================================================================
    private void searchAddressViaDynamicLink(String customerId, String territory, String mobileNo, String contactDocName) {
        String encodedCustomer = Uri.encode(customerId);
        String url = Config.BASE_URL + "/api/resource/Address?fields=[%22name%22,%22address_line1%22,%22address_line2%22,%22city%22,%22state%22,%22country%22,%22pincode%22]&filters=[[%22Dynamic%20Link%22,%22link_doctype%22,%22=%22,%22Customer%22],[%22Dynamic%20Link%22,%22link_name%22,%22=%22,%22" + encodedCustomer + "%22]]&limit_page_length=1";

        client.newCall(new Request.Builder().url(url).addHeader("Cookie", finalCookie).get().build())
                .enqueue(new Callback() {
                    @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        routeToContactFetch(customerId, "", territory, mobileNo, "", contactDocName);
                    }
                    @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        String fullAddress = "";
                        String foundAddressId = "";
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONArray dataArray = new JSONObject(response.body().string()).optJSONArray("data");
                                if (dataArray != null && dataArray.length() > 0) {
                                    JSONObject addrObj = dataArray.getJSONObject(0);
                                    fullAddress = buildAddressString(addrObj);
                                    foundAddressId = getSafeString(addrObj, "name");
                                }
                            } catch (Exception e) { Log.e(TAG, "Dynamic Address parse error", e); }
                        }
                        routeToContactFetch(customerId, foundAddressId, territory, mobileNo, fullAddress, contactDocName);
                    }
                });
    }

    // Helper to format Address JSON block
    private String buildAddressString(JSONObject dataObj) {
        StringBuilder sb = new StringBuilder();
        String l1 = getSafeString(dataObj, "address_line1");
        String l2 = getSafeString(dataObj, "address_line2");
        String city = getSafeString(dataObj, "city");
        String state = getSafeString(dataObj, "state");
        String country = getSafeString(dataObj, "country");
        String pincode = getSafeString(dataObj, "pincode");

        if (!l1.isEmpty()) sb.append(l1).append("\n");
        if (!l2.isEmpty()) sb.append(l2).append("\n");
        if (!city.isEmpty()) sb.append(city).append("\n");
        if (!state.isEmpty()) sb.append(state).append("\n");
        if (!country.isEmpty()) sb.append(country).append("\n");
        if (!pincode.isEmpty()) sb.append(pincode);
        return sb.toString().trim();
    }

    // =========================================================================================
    // Stage 3: Routing logic for Contact Fetch
    // =========================================================================================
    private void routeToContactFetch(String customerId, String addressId, String territory, String mobileNo, String fullAddress, String contactDocName) {
        if (!contactDocName.isEmpty()) {
            fetchSpecificContact(customerId, addressId, territory, mobileNo, fullAddress, contactDocName);
        } else {
            searchContactViaDynamicLink(customerId, addressId, territory, mobileNo, fullAddress);
        }
    }

    // =========================================================================================
    // Stage 4A: Fetch Specific Contact by ID
    // =========================================================================================
    private void fetchSpecificContact(String customerId, String addressId, String territory, String mobileNo, String fullAddress, String contactId) {
        String url = Config.BASE_URL + "/api/resource/Contact/" + Uri.encode(contactId);

        client.newCall(new Request.Builder().url(url).addHeader("Cookie", finalCookie).get().build())
                .enqueue(new Callback() {
                    @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        finalizeUI(addressId, territory, mobileNo, fullAddress, "");
                    }
                    @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        String contactName = "";
                        String finalMobile = mobileNo;
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONObject contactData = new JSONObject(response.body().string()).optJSONObject("data");
                                if (contactData != null) {
                                    contactName = parseContactName(contactData);
                                    String cMobile = getSafeString(contactData, "mobile_no");
                                    if (!cMobile.isEmpty()) finalMobile = cMobile;
                                }
                            } catch (Exception e) { Log.e(TAG, "Contact parse error", e); }
                        }
                        finalizeUI(addressId, territory, finalMobile, fullAddress, contactName);
                    }
                });
    }

    // =========================================================================================
    // Stage 4B: Search for Unlinked Contact via Dynamic Link
    // =========================================================================================
    private void searchContactViaDynamicLink(String customerId, String addressId, String territory, String mobileNo, String fullAddress) {
        String url = Config.BASE_URL + "/api/resource/Contact?fields=[%22first_name%22,%22last_name%22,%22full_name%22,%22mobile_no%22]&filters=[[%22Dynamic%20Link%22,%22link_doctype%22,%22=%22,%22Customer%22],[%22Dynamic%20Link%22,%22link_name%22,%22=%22,%22" + Uri.encode(customerId) + "%22]]&limit_page_length=1";

        client.newCall(new Request.Builder().url(url).addHeader("Cookie", finalCookie).get().build())
                .enqueue(new Callback() {
                    @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        finalizeUI(addressId, territory, mobileNo, fullAddress, "");
                    }
                    @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        String contactName = "";
                        String finalMobile = mobileNo;
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONArray dataArray = new JSONObject(response.body().string()).optJSONArray("data");
                                if (dataArray != null && dataArray.length() > 0) {
                                    JSONObject contactData = dataArray.getJSONObject(0);
                                    contactName = parseContactName(contactData);
                                    String cMobile = getSafeString(contactData, "mobile_no");
                                    if (!cMobile.isEmpty()) finalMobile = cMobile;
                                }
                            } catch (Exception e) { Log.e(TAG, "Dynamic Contact parse error", e); }
                        }
                        finalizeUI(addressId, territory, finalMobile, fullAddress, contactName);
                    }
                });
    }

    // Helper to format Contact JSON block
    private String parseContactName(JSONObject contactData) {
        String fullName = getSafeString(contactData, "full_name");
        String firstName = getSafeString(contactData, "first_name");
        String lastName = getSafeString(contactData, "last_name");

        // FIX: If full_name contains the ID (indicated by a hyphen followed by numbers/duplicates), 
        // we prioritize cleaning it or using first/last name.
        if (fullName.contains("-") && !firstName.isEmpty()) {
            return (firstName + " " + lastName).trim();
        }

        if (fullName.isEmpty()) {
            fullName = (firstName + " " + lastName).trim();
        }
        
        return fullName;
    }

    // =========================================================================================
    // FINAL STAGE: Update App UI
    // =========================================================================================
    private void finalizeUI(String addressId, String territory, String mobileNo, String fullAddress, String contactName) {
        OrderDataManager data = OrderDataManager.getInstance();
        
        // Update Memory
        data.customerAddressName = addressId;
        data.mobileNumber        = mobileNo;
        data.fullAddress         = fullAddress;
        data.territory           = territory;
        data.contactPerson       = contactName;

        runOnUiThread(() -> {
            isProgrammaticUpdate = true;
            
            // RECOMMENDATION FIX: Display the actual ERPNext Address ID (e.g. "Sharon Fernandez-Billing")
            if (etCustomerAddressName != null) {
                etCustomerAddressName.setText(addressId);
                if (addressId.isEmpty()) etCustomerAddressName.setHint("Customer Address ID");
            }
            if (etTerritory != null) {
                etTerritory.setText(territory);
                if (territory.isEmpty()) etTerritory.setHint("Territory");
            }
            if (etContactPerson != null) {
                etContactPerson.setText(contactName);
                if (contactName.isEmpty()) etContactPerson.setHint("Contact Person");
            }
            if (etMobileNumber != null) {
                etMobileNumber.setText(mobileNo);
                if (mobileNo.isEmpty()) etMobileNumber.setHint("Mobile Number");
            }
            if (etFullAddress != null) {
                etFullAddress.setText(fullAddress);
                if (fullAddress.isEmpty()) etFullAddress.setHint("Full Address");
            }

            isProgrammaticUpdate = false;
            checkChangesAndToggleSaveButton();
        });
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
    protected void onPause() {
        super.onPause();
        OrderDataManager data = OrderDataManager.getInstance();
        if (etContactPerson != null)       data.contactPerson       = etContactPerson.getText().toString().trim();
        if (etMobileNumber != null)        data.mobileNumber        = etMobileNumber.getText().toString().trim();
        if (etCustomerAddressName != null) data.customerAddressName = etCustomerAddressName.getText().toString().trim();
        if (etFullAddress != null)         data.fullAddress         = etFullAddress.getText().toString().trim();
        if (etTerritory != null)           data.territory           = etTerritory.getText().toString().trim();
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

        // Set initial selection without animation (Address is now index 2)
        TabLayout.Tab currentTab = tabLayout.getTabAt(2);
        if (currentTab != null) {
            currentTab.select();
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                // SAVE DATA BEFORE LEAVING
                OrderDataManager data = OrderDataManager.getInstance();
                if (etContactPerson != null)       data.contactPerson       = etContactPerson.getText().toString().trim();
                if (etMobileNumber != null)        data.mobileNumber        = etMobileNumber.getText().toString().trim();
                if (etCustomerAddressName != null) data.customerAddressName = etCustomerAddressName.getText().toString().trim();
                if (etFullAddress != null)         data.fullAddress         = etFullAddress.getText().toString().trim();
                if (etTerritory != null)           data.territory           = etTerritory.getText().toString().trim();

                int pos = tab.getPosition();
                if (pos == 2) return;

                Intent intent = null;
                switch (pos) {
                    case 0: intent = new Intent(Activity_AddressContactDetails.this, Activity_OrderDetails.class); break;
                    case 1: intent = new Intent(Activity_AddressContactDetails.this, Activity_ItemList_MedRepView.class); break;
                    case 3: intent = new Intent(Activity_AddressContactDetails.this, Activity_TermsConditions.class); break;
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


}