package com.pims.medretailers;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.google.android.material.tabs.TabLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Activity_CompleteView_Details extends BaseActivity {

    private TabLayout tabLayout;
    private View viewDetails, viewAddress, viewTerms, viewMoreInfo;
    private TextView tvHeaderCustomerName;

    private TextView tvSalesOrderIdVal, tvApprovalStatus, tvFulfillmentStatus, tvTaxStatus, tvCustomerVal, tvDateVal, tvDeliveryDateVal, tvTaxIdVal, tvLtoNoVal, tvBusinessPermitVal, tvCompanyVal, tvGrandTotalQty, tvGrandTotalAmount;
    private TableLayout tableItems;
    private View layoutEditActions;
    private android.widget.ImageView btnEditOrder;
    private androidx.appcompat.widget.AppCompatButton btnApproveActions;

    private TextView tvCustAddressName, tvFullAddress, tvTerritory, tvContactPerson, tvContactDisplay, tvMobileNo;
    private TextView tvPaymentTermsTemplate;
    private TableLayout tablePaymentSchedule;
    private TextView tvSoReference;
    private TableLayout tableSalesTeam;
    private android.widget.ImageView btnPrintInvoicer;

    private String salesOrderId = "";
    private String finalCookie = "";
    private String loggedInUserRole = "";
    private final OkHttpClient client = new OkHttpClient();

    private static final String WORKFLOW_API =
            Config.BASE_URL + "/api/method/frappe.model.workflow.apply_workflow";

    private static final String ACTION_SUBMIT_FOR_APPROVAL = "Submit for Approval";
    private static final String ACTION_APPROVE_PASS_TO_INVOICER = "Approve & Pass to Invoicer";
    private static final String ACTION_APPROVE_PASS_TO_GM = "Approve & Pass to GM";
    private static final String ACTION_APPROVE_PASS_TO_NSM1 = "Approve & Pass to NSM-1";
    private static final String ACTION_APPROVE_PASS_TO_NSM2 = "Approve & Pass to NSM-2";
    private static final String ACTION_APPROVE = "Approve";
    private static final String ACTION_REJECT = "Reject";

    private static final String STATE_DRAFT = "Draft";
    private static final String STATE_FOR_DSM = "For Approval by DSM";
    private static final String STATE_FOR_GM = "For Approval by GM";
    private static final String STATE_FOR_NSM1 = "For Approval by NSM-1";
    private static final String STATE_FOR_NSM2 = "For Approval by NSM-2";
    private static final String STATE_REJECTED_DSM = "Rejected by DSM";
    private static final String STATE_REJECTED_GM = "Rejected by GM";
    private static final String STATE_REJECTED_NSM1 = "Rejected by NSM-1";
    private static final String STATE_REJECTED_NSM2 = "Rejected by NSM-2";
    private static final String STATE_SO_APPROVED = "SO Approved";

    private static final String FULFILLMENT_DELIVERED = "Delivered";
    private static final String FULFILLMENT_CANCELLED = "Cancelled";

    private static final String ADMIN_PHASE_MEDREP = "MEDREP_PHASE";
    private static final String ADMIN_PHASE_DSM = "DSM_PHASE";
    private static final String ADMIN_PHASE_GM = "GM_PHASE";
    private static final String ADMIN_PHASE_MIXED = "MIXED_PHASE";

    private final HashMap<String, String> globalUserMap = new HashMap<>();
    private boolean isFirstLoad = true;
    private boolean shouldTriggerEdit = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_completeview_details);

        Intent intent = getIntent();
        if (intent != null) {
            salesOrderId = intent.getStringExtra("SALES_ORDER_ID");
            if (salesOrderId == null) salesOrderId = intent.getStringExtra("SO_ID");
            finalCookie = intent.getStringExtra("Session_Cookie");
            shouldTriggerEdit = intent.getBooleanExtra("TRIGGER_EDIT_MODE", false);
        }

        if (finalCookie == null || finalCookie.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);
            finalCookie = prefs.getString("Session_Cookie", "");
            loggedInUserRole = prefs.getString("User_Role", "MedRep");
        } else {
            SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);
            loggedInUserRole = prefs.getString("User_Role", "MedRep");
        }

        ImageView imgTopDecor = findViewById(R.id.imgTopDecor);
        if (imgTopDecor != null) {
            imgTopDecor.setOnClickListener(v -> animateHamburgerMenu(imgTopDecor, () -> showHamburgerMenu(imgTopDecor)));
        }

        initializeViews();
        setupTabs();
        fetchOrderData();
    }

    @Override
    public void onBackPressed() {
        navigateToSOList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isFirstLoad) {
            fetchOrderData();
        }
        isFirstLoad = false;
    }

    private void initializeViews() {
        tabLayout = findViewById(R.id.tabLayout);
        tvHeaderCustomerName = findViewById(R.id.tvHeaderCustomerName);

        viewDetails = findViewById(R.id.include_details);
        viewAddress = findViewById(R.id.include_address);
        viewTerms = findViewById(R.id.include_terms);
        viewMoreInfo = findViewById(R.id.include_moreinfo);

        tvSalesOrderIdVal = findViewById(R.id.tvSalesOrderIdVal);
        tvApprovalStatus = findViewById(R.id.tvApprovalStatus);
        tvFulfillmentStatus = findViewById(R.id.tvFulfillmentStatus);
        tvTaxStatus = findViewById(R.id.tvTaxStatus);
        tvCustomerVal = findViewById(R.id.tvCustomerVal);
        tvDateVal = findViewById(R.id.tvDateVal);
        tvDeliveryDateVal = findViewById(R.id.tvDeliveryDateVal);
        tvTaxIdVal = findViewById(R.id.tvTaxIdVal);
        tvLtoNoVal = findViewById(R.id.tvLtoNoVal);
        tvBusinessPermitVal = findViewById(R.id.tvBusinessPermitVal);
        tvSoReference = findViewById(R.id.tvSoReference);
        tvCompanyVal = findViewById(R.id.tvCompanyVal);
        tvGrandTotalQty = findViewById(R.id.tvGrandTotalQty);
        tvGrandTotalAmount = findViewById(R.id.tvGrandTotalAmount);
        tableItems = findViewById(R.id.tableCompleteViewItems);

        layoutEditActions = findViewById(R.id.layoutEditActions);
        btnEditOrder = findViewById(R.id.btnEditOrder);
        btnApproveActions = findViewById(R.id.btnApproveActions);

        tvCustAddressName = findViewById(R.id.tvCustAddressName);
        tvFullAddress = findViewById(R.id.tvFullAddress);
        tvTerritory = findViewById(R.id.tvTerritory);
        tvContactPerson = findViewById(R.id.tvContactPerson);
        tvContactDisplay = findViewById(R.id.tvContactDisplay);
        tvMobileNo = findViewById(R.id.tvMobileNo);

        tvPaymentTermsTemplate = findViewById(R.id.tvPaymentTermsTemplate);
        tablePaymentSchedule = findViewById(R.id.tablePaymentSchedule);

        tableSalesTeam = findViewById(R.id.tableSalesTeam);
        btnPrintInvoicer = findViewById(R.id.btnPrintInvoicer);

        if (btnPrintInvoicer != null) {
            setupHoverEffect(btnPrintInvoicer);
            btnPrintInvoicer.setOnClickListener(v -> showPrintConfirmationDialog());
        }

        if (btnEditOrder != null) {
            setupHoverEffect(btnEditOrder);
        }

        if (tvHeaderCustomerName != null) {
            tvHeaderCustomerName.setText("Customer: Loading...");
        }
    }

    private void setupTabs() {
        if (tabLayout == null) return;

        tabLayout.removeAllTabs();
        tabLayout.addTab(tabLayout.newTab().setText("Details"));
        tabLayout.addTab(tabLayout.newTab().setText("Address"));
        tabLayout.addTab(tabLayout.newTab().setText("Terms"));
        tabLayout.addTab(tabLayout.newTab().setText("More Info"));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int pos = tab.getPosition();

                if(viewDetails != null) viewDetails.setVisibility(View.GONE);
                if(viewAddress != null) viewAddress.setVisibility(View.GONE);
                if(viewTerms != null) viewTerms.setVisibility(View.GONE);
                if(viewMoreInfo != null) viewMoreInfo.setVisibility(View.GONE);

                switch (pos) {
                    case 0: if(viewDetails != null) viewDetails.setVisibility(View.VISIBLE); break;
                    case 1: if(viewAddress != null) viewAddress.setVisibility(View.VISIBLE); break;
                    case 2: if(viewTerms != null) viewTerms.setVisibility(View.VISIBLE); break;
                    case 3: if(viewMoreInfo != null) viewMoreInfo.setVisibility(View.VISIBLE); break;
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void showPrintConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(60, 60, 60, 60);
        layout.setGravity(Gravity.CENTER);

        ImageView dialogIcon = new ImageView(this);
        dialogIcon.setImageResource(R.drawable.printer_icon);
        dialogIcon.setLayoutParams(new android.widget.LinearLayout.LayoutParams(150, 150));
        dialogIcon.setPadding(0, 0, 0, 30);
        layout.addView(dialogIcon);

        TextView title = new TextView(this);
        title.setText("Download Invoice?");
        title.setTextSize(20f);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Color.parseColor("#333333"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView msg = new TextView(this);
        msg.setText("Do you want to download the PDF invoice for this order?");
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, 20, 0, 60);
        msg.setTextColor(Color.GRAY);
        layout.addView(msg);

        android.widget.LinearLayout btnLayout = new android.widget.LinearLayout(this);
        btnLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        btnLayout.setGravity(Gravity.CENTER);
        btnLayout.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView btnYes = createDialogButton("YES", "#835C9F", Color.WHITE);
        TextView btnNo = createDialogButton("NO", "#E0E0E0", Color.DKGRAY);

        android.widget.LinearLayout.LayoutParams noParams = new android.widget.LinearLayout.LayoutParams(0, 100, 1f);
        noParams.setMargins(0, 0, 20, 0);
        btnNo.setLayoutParams(noParams);

        android.widget.LinearLayout.LayoutParams yesParams = new android.widget.LinearLayout.LayoutParams(0, 100, 1f);
        yesParams.setMargins(20, 0, 0, 0);
        btnYes.setLayoutParams(yesParams);

        btnLayout.addView(btnNo);
        btnLayout.addView(btnYes);
        layout.addView(btnLayout);

        builder.setView(layout);
        AlertDialog dialog = builder.create();

        btnYes.setOnClickListener(v -> {
            dialog.dismiss();
            startPrinterAnimation();
            downloadAndOpenOrderPdf();
        });

        btnNo.setOnClickListener(v -> dialog.dismiss());

        if (dialog.getWindow() != null) {
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(50f);
            dialog.getWindow().setBackgroundDrawable(bg);
        }
        dialog.show();
    }

    private TextView createDialogButton(String text, String bgColor, int textColor) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setGravity(Gravity.CENTER);
        btn.setTextColor(textColor);
        btn.setTextSize(14f);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);

        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(Color.parseColor(bgColor));
        gd.setCornerRadius(30f);
        btn.setBackground(gd);

        setupHoverEffect(btn);
        return btn;
    }

    private void startPrinterAnimation() {
        if (btnPrintInvoicer == null) return;

        if (btnPrintInvoicer.getDrawable() instanceof AnimationDrawable) {
            ((AnimationDrawable) btnPrintInvoicer.getDrawable()).start();
        }

        btnPrintInvoicer.animate()
                .scaleX(1.15f)
                .scaleY(1.15f)
                .setDuration(250)
                .withEndAction(() -> btnPrintInvoicer.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .rotationBy(360f)
                        .setDuration(600)
                        .start())
                .start();
    }

    private void downloadAndOpenOrderPdf() {
        if (salesOrderId == null || salesOrderId.isEmpty()) {
            Toast.makeText(this, "Order ID missing, cannot print.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Generating Sales Order PDF...", Toast.LENGTH_SHORT).show();

        String pdfUrl = Config.BASE_URL + "/api/method/frappe.utils.print_format.download_pdf"
                + "?doctype=Sales%20Order"
                + "&name=" + salesOrderId
                + "&format=Standard"
                + "&no_letterhead=0";

        android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(android.net.Uri.parse(pdfUrl));
        request.setTitle("Sales Order - " + salesOrderId);
        request.setDescription("Downloading Sales Order PDF from ERPNext");
        request.addRequestHeader("Cookie", finalCookie);
        request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, salesOrderId + ".pdf");
        request.setAllowedOverMetered(true);
        request.setAllowedOverRoaming(true);

        android.app.DownloadManager downloadManager = (android.app.DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (downloadManager != null) {
            downloadManager.enqueue(request);
            Toast.makeText(this, "Download started. Check your notification bar.", Toast.LENGTH_LONG).show();
        }
    }

    private String getSafeString(JSONObject json, String key) {
        String val = json.optString(key, "");
        if (val == null || val.equalsIgnoreCase("null") || val.isEmpty()) {
            return "";
        }
        return val.trim();
    }

    private String cleanContactPerson(String rawContact) {
        if (rawContact == null || rawContact.isEmpty()) return "";
        rawContact = rawContact.trim();

        int mid = rawContact.length() / 2;
        if (rawContact.length() % 2 != 0 && rawContact.charAt(mid) == '-') {
            String left = rawContact.substring(0, mid);
            String right = rawContact.substring(mid + 1);
            if (left.equalsIgnoreCase(right)) {
                return left;
            }
        }
        return rawContact;
    }

    private void fetchOrderData() {
        if (salesOrderId == null || salesOrderId.isEmpty()) {
            Toast.makeText(this, "Error: No Sales Order ID", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = Config.BASE_URL + "/api/resource/Sales%20Order/" + salesOrderId;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", finalCookie)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(Activity_CompleteView_Details.this, "Connection Failed", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String responseData = response.body() != null ? response.body().string() : "";

                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonObject = new JSONObject(responseData);
                        final JSONObject data = jsonObject.getJSONObject("data");

                        final String customerId = getSafeString(data, "customer");
                        final String customerName = getSafeString(data, "customer_name").isEmpty() ? (customerId.isEmpty() ? "N/A" : customerId) : getSafeString(data, "customer_name");
                        final String transactionDate = getSafeString(data, "transaction_date").isEmpty() ? "N/A" : getSafeString(data, "transaction_date");
                        final String deliveryDate = getSafeString(data, "delivery_date").isEmpty() ? "N/A" : getSafeString(data, "delivery_date");
                        final String fulfillmentStatus = getSafeString(data, "fulfillment_status").isEmpty() ? "Not Fulfilled" : getSafeString(data, "fulfillment_status");
                        final String company = getSafeString(data, "company").isEmpty() ? "N/A" : getSafeString(data, "company");
                        final String taxId = getSafeString(data, "tax_id").isEmpty() ? "N/A" : getSafeString(data, "tax_id");
                        final String taxCategory = getSafeString(data, "tax_category").isEmpty() ? "Standard" : getSafeString(data, "tax_category");

                        String workflowState = getSafeString(data, "workflow_state");
                        String docStatus = getSafeString(data, "status").isEmpty() ? "Draft" : getSafeString(data, "status");
                        final String finalApprovalStatus = !workflowState.isEmpty() ? workflowState : docStatus;

                        final String custAddressName = getSafeString(data, "customer_address").isEmpty() ? "N/A" : getSafeString(data, "customer_address");
                        final String territory = getSafeString(data, "territory").isEmpty() ? "N/A" : getSafeString(data, "territory");

                        String rawAddressStr = getSafeString(data, "address_display").replaceAll("<[^>]*>", " ").trim();
                        final String addressDisplay = rawAddressStr.isEmpty() ? "No Address Provided" : rawAddressStr;

                        // ---------------------------------------------------------------------
                        // ROBUST FALLBACK ENGINE FOR MISSING CONTACT FIELDS
                        // ---------------------------------------------------------------------
                        String rawContact = getSafeString(data, "contact_person");
                        String extractedContactPerson = rawContact.isEmpty() ? "" : cleanContactPerson(rawContact);

                        if (extractedContactPerson.isEmpty()) {
                            if (custAddressName.toLowerCase().contains("c/o")) {
                                int idx = custAddressName.toLowerCase().indexOf("c/o") + 3;
                                extractedContactPerson = custAddressName.substring(idx).replace("-Billing", "").replace("-Shipping", "").trim();
                            } else {
                                extractedContactPerson = customerName;
                            }
                        }
                        final String contactPerson = extractedContactPerson.isEmpty() ? "Customer Default" : extractedContactPerson;

                        String extractedContactDisplay = getSafeString(data, "contact_display").replaceAll("<[^>]*>", " ").trim();
                        if (extractedContactDisplay.isEmpty() || extractedContactDisplay.equalsIgnoreCase("null")) {
                            extractedContactDisplay = getSafeString(data, "contact_email");
                        }
                        if (extractedContactDisplay.isEmpty() || extractedContactDisplay.equalsIgnoreCase("null")) {
                            extractedContactDisplay = contactPerson;
                        }
                        final String contactDisplay = extractedContactDisplay;

                        String extractedMobile = getSafeString(data, "contact_mobile");
                        if (extractedMobile.isEmpty()) extractedMobile = getSafeString(data, "contact_phone");
                        if (extractedMobile.isEmpty()) extractedMobile = getSafeString(data, "mobile_no");
                        if (extractedMobile.isEmpty()) extractedMobile = getSafeString(data, "phone");

                        if (extractedMobile.isEmpty() && rawAddressStr.contains("Phone:")) {
                            try {
                                String afterPhone = rawAddressStr.substring(rawAddressStr.indexOf("Phone:") + 6).trim();
                                extractedMobile = afterPhone.split("\\s+")[0];
                            } catch (Exception e) {}
                        }
                        if (extractedMobile.isEmpty() && rawAddressStr.contains("Mobile:")) {
                            try {
                                String afterMobile = rawAddressStr.substring(rawAddressStr.indexOf("Mobile:") + 7).trim();
                                extractedMobile = afterMobile.split("\\s+")[0];
                            } catch (Exception e) {}
                        }
                        final String contactMobile = extractedMobile.isEmpty() ? "Not Provided" : extractedMobile;
                        // ---------------------------------------------------------------------

                        final String paymentTermsTemplate = getSafeString(data, "payment_terms_template").isEmpty() ? "No Template" : getSafeString(data, "payment_terms_template");
                        final String poNo = getSafeString(data, "po_no").isEmpty() ? "No Reference" : getSafeString(data, "po_no");

                        final String orderId = getSafeString(data, "name").isEmpty() ? salesOrderId : getSafeString(data, "name");

                        final JSONArray itemsArray = data.optJSONArray("items");
                        final JSONArray paymentArray = data.optJSONArray("payment_schedule");
                        final JSONArray salesTeamArray = data.optJSONArray("sales_team");

                        runOnUiThread(() -> {
                            if (tvSalesOrderIdVal != null) tvSalesOrderIdVal.setText(orderId);
                            if (tvHeaderCustomerName != null) tvHeaderCustomerName.setText(customerName);

                            if (tvCustomerVal != null) tvCustomerVal.setText(customerName);
                            if (tvDateVal != null) tvDateVal.setText(transactionDate);
                            if (tvDeliveryDateVal != null) tvDeliveryDateVal.setText(deliveryDate);
                            if (tvCompanyVal != null) tvCompanyVal.setText(company);
                            if (tvTaxIdVal != null) tvTaxIdVal.setText(taxId);
                            if (tvSoReference != null) tvSoReference.setText(poNo);

                            String ltoNo = getSafeString(data, "lto_no");
                            String businessPermit = getSafeString(data, "business_permit");

                            if (ltoNo.isEmpty() || businessPermit.isEmpty()) {
                                fetchCustomerLicenseInfo(customerId);
                            } else {
                                if (tvLtoNoVal != null) tvLtoNoVal.setText(ltoNo);
                                if (tvBusinessPermitVal != null) tvBusinessPermitVal.setText(businessPermit);
                            }

                            if (tvApprovalStatus != null) {
                                tvApprovalStatus.setText(finalApprovalStatus);
                            }

                            if (tvFulfillmentStatus != null) {
                                tvFulfillmentStatus.setText(fulfillmentStatus);
                            }

                            if (tvTaxStatus != null) {
                                tvTaxStatus.setText(taxCategory);
                            }

                            if (tvCustAddressName != null) tvCustAddressName.setText(custAddressName);
                            if (tvTerritory != null) tvTerritory.setText(territory);
                            if (tvFullAddress != null) tvFullAddress.setText(addressDisplay);

                            if (tvContactPerson != null) tvContactPerson.setText(contactPerson);
                            if (tvContactDisplay != null) tvContactDisplay.setText(contactDisplay);
                            if (tvMobileNo != null) tvMobileNo.setText(contactMobile);

                            if (tvPaymentTermsTemplate != null) tvPaymentTermsTemplate.setText(paymentTermsTemplate);

                            boolean isApproved = finalApprovalStatus.equalsIgnoreCase("SO Approved");
                            boolean isDraft = finalApprovalStatus.equalsIgnoreCase("Draft");

                            if (!isApproved || isDraft) {
                                if (layoutEditActions != null) layoutEditActions.setVisibility(View.VISIBLE);
                                setupEditActions(data);
                            } else {
                                if (layoutEditActions != null) layoutEditActions.setVisibility(View.GONE);
                            }

                            populateItemsTable(itemsArray);
                            populatePaymentScheduleTable(paymentArray);
                            populateSalesTeamTable(salesTeamArray);

                            if (shouldTriggerEdit) {
                                shouldTriggerEdit = false;
                                triggerEditMode(data);
                            }

                            setupWorkflowActions(data);

                            boolean isActionable = (btnApproveActions != null && btnApproveActions.getVisibility() == View.VISIBLE)
                                    || (layoutEditActions != null && layoutEditActions.getVisibility() == View.VISIBLE);
                            updateHeaderAlignment(isActionable);

                            fetchActivityTrail(data);
                        });

                    } catch (JSONException e) {
                        Log.e("API_PARSE", "Error parsing detailed JSON", e);
                        runOnUiThread(() -> Toast.makeText(Activity_CompleteView_Details.this, "Data Parsing Error", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    runOnUiThread(() -> {
                        String erpError = ErrorUtils.parseErpNextError(responseData);
                        AppNotification.show(Activity_CompleteView_Details.this, erpError, AppNotification.Type.ERROR);
                    });
                }
            }
        });
    }

    private void setupEditActions(JSONObject fullDoc) {
        if (btnEditOrder != null) {
            btnEditOrder.setOnClickListener(v -> triggerEditMode(fullDoc));
        }
    }

    private void triggerEditMode(JSONObject fullDoc) {
        String workflowState = getSafeString(fullDoc, "workflow_state");
        String docStatus = getSafeString(fullDoc, "status").isEmpty() ? "Draft" : getSafeString(fullDoc, "status");
        String finalApprovalStatus = !workflowState.isEmpty() ? workflowState : docStatus;
        String fulfillmentStatus = getSafeString(fullDoc, "status").isEmpty() ? "Not Fulfilled" : getSafeString(fullDoc, "status");

        SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);
        prefs.edit()
                .putString("EDIT_APPROVAL_STATUS", finalApprovalStatus)
                .putString("EDIT_FULFILLMENT_STATUS", fulfillmentStatus)
                .apply();

        populateDataManager(fullDoc);
        Intent intent = new Intent(this, Activity_OrderDetails.class);
        intent.putExtra("Session_Cookie", finalCookie);
        intent.putExtra("IS_EDIT_MODE", true);
        intent.putExtra("EDITING_ORDER_ID", salesOrderId);
        startActivity(intent);
    }

    private void populateDataManager(JSONObject fullDoc) {
        try {
            OrderDataManager data = OrderDataManager.getInstance();
            data.clearData();

            data.editingOrderId = salesOrderId;
            data.customer = getSafeString(fullDoc, "customer");
            data.customerDisplayName = getSafeString(fullDoc, "customer_name");
            if (data.customerDisplayName.isEmpty()) data.customerDisplayName = data.customer;

            data.company = getSafeString(fullDoc, "company");
            data.transactionDate = getSafeString(fullDoc, "transaction_date");
            data.deliveryDate = getSafeString(fullDoc, "delivery_date");
            data.contactPerson = getSafeString(fullDoc, "contact_person");
            data.mobileNumber = getSafeString(fullDoc, "contact_mobile");
            data.paymentTerms = getSafeString(fullDoc, "payment_terms_template");

            data.territory = getSafeString(fullDoc, "territory");
            data.customerAddressName = getSafeString(fullDoc, "customer_address");

            String rawAddr = getSafeString(fullDoc, "address_display").replaceAll("<[^>]*>", "\n").trim();
            data.fullAddress = rawAddr.replaceAll("\n+", "\n");

            data.instructions = getSafeString(fullDoc, "notes");
            if (data.instructions.isEmpty()) data.instructions = getSafeString(fullDoc, "terms");

            JSONArray paymentArray = fullDoc.optJSONArray("payment_schedule");
            if (paymentArray != null) {
                data.paymentSchedule.clear();
                for (int payIdx = 0; payIdx < paymentArray.length(); payIdx++) {
                    JSONObject pObj = paymentArray.getJSONObject(payIdx);
                    data.paymentSchedule.add(new OrderDataManager.PaymentScheduleItem(
                            getSafeString(pObj, "payment_term"),
                            getSafeString(pObj, "description"),
                            getSafeString(pObj, "due_date"),
                            getSafeString(pObj, "invoice_portion"),
                            getSafeString(pObj, "payment_amount")
                    ));
                }
            }

            JSONArray itemsArray = fullDoc.optJSONArray("items");
            if (itemsArray != null) {
                data.items.clear();
                for (int i = 0; i < itemsArray.length(); i++) {
                    JSONObject itemObj = itemsArray.getJSONObject(i);
                    OrderItem orderItem = new OrderItem();
                    String itemCode = getSafeString(itemObj, "item_code");
                    orderItem.setItemCode(itemCode);
                    orderItem.setItemName(itemCode);
                    orderItem.setQuantity(itemObj.optInt("qty", 1));
                    orderItem.setRate(itemObj.optDouble("rate", 0.0));
                    orderItem.setUom(getSafeString(itemObj, "uom"));
                    if (orderItem.getUom().isEmpty()) orderItem.setUom("Box");
                    orderItem.setWarehouse(getSafeString(itemObj, "warehouse"));
                    if (orderItem.getWarehouse().isEmpty()) orderItem.setWarehouse("PIMS MAIN - PE");
                    String delDate = getSafeString(itemObj, "delivery_date");
                    orderItem.setDate(delDate);
                    orderItem.setDeliveryDate(delDate);
                    orderItem.setNotes(getSafeString(itemObj, "description"));
                    data.items.add(orderItem);
                }
            }
            data.captureSnapshot();
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error preparing data", Toast.LENGTH_SHORT).show();
        }
    }

    private void populateItemsTable(JSONArray itemsArray) {
        if (tableItems == null) return;
        int childCount = tableItems.getChildCount();
        if (childCount > 1) tableItems.removeViews(1, childCount - 1);

        if (itemsArray == null) return;

        int totalQty = 0;
        double totalPrice = 0.0;

        for (int i = 0; i < itemsArray.length(); i++) {
            JSONObject item = itemsArray.optJSONObject(i);
            if (item == null) continue;

            String code = getSafeString(item, "item_code");
            if (code.isEmpty()) code = "N/A";
            String name = getSafeString(item, "item_name");
            String desc = getSafeString(item, "description");
            String delDate = getSafeString(item, "delivery_date");
            if (delDate.isEmpty()) delDate = "---";

            int qty = (int) item.optDouble("qty", 0);
            double rate = item.optDouble("rate", 0);
            double amount = item.optDouble("amount", (qty * rate));

            totalQty += qty;
            totalPrice += amount;

            String itemDetail = code;
            if (!name.isEmpty() && !name.equalsIgnoreCase(code)) {
                itemDetail += " : " + name;
            }

            TableRow row = new TableRow(this);
            row.setPadding(0, 16, 0, 16);
            if (i % 2 == 1) row.setBackgroundColor(Color.parseColor("#F9F9F9"));

            addTextToRow(row, String.valueOf(i + 1), Gravity.CENTER);
            addTextToRow(row, itemDetail, Gravity.START);
            addTextToRow(row, delDate, Gravity.CENTER);
            addTextToRow(row, String.valueOf(qty), Gravity.CENTER);
            addTextToRow(row, String.format("%.2f", rate), Gravity.END);
            addTextToRow(row, String.format("%.2f", amount), Gravity.END);

            if (row.getChildCount() > 1) {
                TextView tvCode = (TextView) row.getChildAt(1);
                tvCode.setTextColor(Color.parseColor("#835C9F"));
                tvCode.setOnClickListener(v -> Toast.makeText(Activity_CompleteView_Details.this, "Item: " + name + "\n" + desc, Toast.LENGTH_LONG).show());
            }
            tableItems.addView(row);
        }

        if (tvGrandTotalQty != null) tvGrandTotalQty.setText(String.valueOf(totalQty));
        if (tvGrandTotalAmount != null) tvGrandTotalAmount.setText(String.format("₱ %.2f", totalPrice));
    }

    private void populatePaymentScheduleTable(JSONArray scheduleArray) {
        if (tablePaymentSchedule == null) return;
        int childCount = tablePaymentSchedule.getChildCount();
        if (childCount > 1) tablePaymentSchedule.removeViews(1, childCount - 1);

        if (scheduleArray == null) return;

        for (int i = 0; i < scheduleArray.length(); i++) {
            JSONObject item = scheduleArray.optJSONObject(i);
            if (item == null) continue;

            String term = getSafeString(item, "payment_term");
            if (term.isEmpty()) term = "N/A";
            String description = getSafeString(item, "description");
            String dueDate = getSafeString(item, "due_date");
            if (dueDate.isEmpty()) dueDate = "---";
            double invoicePortion = item.optDouble("invoice_portion", 0.0);
            double amount = item.optDouble("payment_amount", 0.0);

            TableRow row = new TableRow(this);
            row.setPadding(12, 16, 12, 16);
            if (i % 2 == 1) row.setBackgroundColor(Color.parseColor("#F9F9F9"));
            else row.setBackgroundColor(Color.WHITE);

            addTextToRow(row, String.valueOf(i + 1), Gravity.CENTER);
            addTextToRow(row, term, Gravity.START);
            addTextToRow(row, description, Gravity.START);
            addTextToRow(row, dueDate, Gravity.CENTER);
            addTextToRow(row, String.format("%.2f%%", invoicePortion), Gravity.END);
            addTextToRow(row, String.format("₱ %.2f", amount), Gravity.END);

            tablePaymentSchedule.addView(row);
        }
    }

    private void populateSalesTeamTable(JSONArray salesTeamArray) {
        if (tableSalesTeam == null) return;
        int childCount = tableSalesTeam.getChildCount();
        if (childCount > 1) tableSalesTeam.removeViews(1, childCount - 1);

        if (salesTeamArray == null) return;

        for (int i = 0; i < salesTeamArray.length(); i++) {
            JSONObject item = salesTeamArray.optJSONObject(i);
            if (item == null) continue;

            String person = getSafeString(item, "sales_person");
            if (person.isEmpty()) person = "N/A";
            double contributionPct = item.optDouble("allocated_percentage", 0.0);
            double contributionAmount = item.optDouble("allocated_amount", 0.0);
            double incentives = item.optDouble("incentives", 0.0);

            TableRow row = new TableRow(this);
            row.setPadding(12, 16, 12, 16);
            if (i % 2 == 1) row.setBackgroundColor(Color.parseColor("#F9F9F9"));
            else row.setBackgroundColor(Color.WHITE);

            addTextToRow(row, String.valueOf(i + 1), Gravity.CENTER);
            addTextToRow(row, person, Gravity.START);
            addTextToRow(row, String.format("%.2f%%", contributionPct), Gravity.END);
            addTextToRow(row, String.format("₱ %.2f", contributionAmount), Gravity.END);
            addTextToRow(row, String.format("₱ %.2f", incentives), Gravity.END);

            tableSalesTeam.addView(row);
        }
    }

    private void addTextToRow(TableRow row, String text, int gravity) {
        TextView tv = new TextView(this);
        tv.setText(text);
        int pLR = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        int pTB = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        tv.setPadding(pLR, pTB, pLR, pTB);
        tv.setTextColor(Color.BLACK);
        tv.setTextSize(12);
        tv.setGravity(gravity);
        row.addView(tv);
    }

    private void showHamburgerMenu(View v) {
        animateHamburgerMenu((ImageView) v, () -> showUniversalMenu(v));
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

    private String getUserFullName(String ownerId, JSONObject userInfoMap, String currentEmail) {
        if (ownerId == null || ownerId.isEmpty()) return "Unknown";
        if (ownerId.equalsIgnoreCase(currentEmail)) return "You";

        String lowerId = ownerId.toLowerCase();
        String ownerPrefix = lowerId.contains("@") ? lowerId.split("@")[0] : lowerId;

        if (!globalUserMap.isEmpty()) {
            if (globalUserMap.containsKey(lowerId)) return globalUserMap.get(lowerId);
            for (String emailKey : globalUserMap.keySet()) {
                String keyPrefix = emailKey.contains("@") ? emailKey.split("@")[0] : emailKey;
                if (keyPrefix.equals(ownerPrefix)) return globalUserMap.get(emailKey);
            }
        }

        if (userInfoMap != null) {
            Iterator<String> keys = userInfoMap.keys();
            while (keys.hasNext()) {
                String key = keys.next().toLowerCase();
                String keyPrefix = key.contains("@") ? key.split("@")[0] : key;

                if (key.equals(lowerId) || keyPrefix.equals(ownerPrefix)) {
                    JSONObject fuzzyObj = userInfoMap.optJSONObject(key);
                    if (fuzzyObj != null) {
                        String fn = fuzzyObj.optString("full_name", fuzzyObj.optString("fullname", ""));
                        if (!fn.trim().isEmpty()) return fn.trim();
                    }
                }
            }
        }

        return ownerId;
    }

    private void fetchActivityTrail(JSONObject orderData) {
        String creation = getSafeString(orderData, "creation");
        String owner = getSafeString(orderData, "owner");
        fetchTimelineData(owner, creation);
    }

    private void fetchTimelineData(String owner, String creation) {
        if (salesOrderId == null || salesOrderId.isEmpty()) {
            addBaseCreationOnly(owner, creation);
            return;
        }

        String url = Config.BASE_URL + "/api/method/frappe.desk.form.load.getdoc";

        JSONObject postBody = new JSONObject();
        try {
            postBody.put("doctype", "Sales Order");
            postBody.put("name", salesOrderId);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                postBody.toString(), okhttp3.MediaType.parse("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", finalCookie)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("TIMELINE", "Failed to fetch getdoc", e);
                runOnUiThread(() -> addBaseCreationOnly(owner, creation));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String respStr = response.isSuccessful() ? response.body().string() : "";

                if (globalUserMap.isEmpty() && !respStr.isEmpty()) {
                    fetchUsersAndRenderTimeline(respStr, owner, creation);
                } else {
                    renderTimeline(respStr, owner, creation);
                }
            }
        });
    }

    private void fetchUsersAndRenderTimeline(String timelineJson, String owner, String creation) {
        String url = Config.BASE_URL + "/api/resource/User?fields=[\"name\",\"full_name\"]&limit_page_length=1000";
        Request request = new Request.Builder().url(url).addHeader("Cookie", finalCookie).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                renderTimeline(timelineJson, owner, creation);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray data = json.optJSONArray("data");
                        if (data != null) {
                            for (int i = 0; i < data.length(); i++) {
                                JSONObject u = data.getJSONObject(i);
                                String email = u.optString("name", "");
                                String fullName = u.optString("full_name", "");
                                if (!email.isEmpty() && !fullName.isEmpty()) {
                                    globalUserMap.put(email.toLowerCase(), fullName);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e("USER_FETCH", "Error parsing users", e);
                    }
                }
                renderTimeline(timelineJson, owner, creation);
            }
        });
    }

    private void renderTimeline(String respStr, String owner, String creation) {
        runOnUiThread(() -> {
            clearActivityContainers();

            try {
                SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);
                String currentEmail = prefs.getString("User_Email", "");

                JSONObject mergedUserInfoMap = new JSONObject();

                if (!respStr.isEmpty()) {
                    JSONObject json = new JSONObject(respStr);
                    JSONObject dataRoot = json.has("message") ? json.getJSONObject("message") : json;

                    JSONObject rootUsers = dataRoot.optJSONObject("user_info");
                    if (rootUsers != null) {
                        Iterator<String> keys = rootUsers.keys();
                        while (keys.hasNext()) {
                            String k = keys.next();
                            mergedUserInfoMap.put(k, rootUsers.getJSONObject(k));
                        }
                    }

                    JSONObject docinfo = dataRoot.optJSONObject("docinfo");
                    if (docinfo != null && docinfo.has("users")) {
                        JSONObject docUsers = docinfo.optJSONObject("users");
                        if (docUsers != null) {
                            Iterator<String> keys = docUsers.keys();
                            while (keys.hasNext()) {
                                String k = keys.next();
                                mergedUserInfoMap.put(k, docUsers.getJSONObject(k));
                            }
                        }
                    }

                    if (docinfo != null) {
                        List<TimelineEntry> allEntries = new ArrayList<>();

                        JSONArray comments = docinfo.optJSONArray("comments");
                        if (comments != null) {
                            for (int i = 0; i < comments.length(); i++) {
                                JSONObject c = comments.getJSONObject(i);
                                String commentType = c.optString("comment_type", "Comment");
                                String content = c.optString("content");

                                if (commentType.equals("Comment")) {
                                    allEntries.add(new TimelineEntry(c.optString("owner"), c.optString("creation"), content, "comment"));
                                } else {
                                    allEntries.add(new TimelineEntry(c.optString("owner"), c.optString("creation"), content, "system_comment"));
                                }
                            }
                        }

                        JSONArray infoLogs = docinfo.optJSONArray("info_logs");
                        if (infoLogs != null) {
                            for (int i = 0; i < infoLogs.length(); i++) {
                                JSONObject il = infoLogs.getJSONObject(i);
                                allEntries.add(new TimelineEntry(
                                        il.optString("owner"),
                                        il.optString("creation"),
                                        il.optString("content"),
                                        "info_log"
                                ));
                            }
                        }

                        JSONArray communications = docinfo.optJSONArray("communications");
                        if (communications != null) {
                            for (int i = 0; i < communications.length(); i++) {
                                JSONObject comm = communications.getJSONObject(i);
                                allEntries.add(new TimelineEntry(
                                        comm.optString("sender"),
                                        comm.optString("creation"),
                                        comm.optString("content"),
                                        "communication"
                                ));
                            }
                        }

                        JSONArray versions = docinfo.optJSONArray("versions");
                        if (versions != null) {
                            for (int i = 0; i < versions.length(); i++) {
                                JSONObject v = versions.getJSONObject(i);
                                Object dataObj = v.opt("data");
                                String dataStr = "";
                                if (dataObj instanceof JSONObject) {
                                    dataStr = ((JSONObject) dataObj).toString();
                                } else if (dataObj instanceof JSONArray) {
                                    dataStr = ((JSONArray) dataObj).toString();
                                } else if (dataObj != null) {
                                    dataStr = dataObj.toString();
                                }

                                List<String> versionMessages = getVersionMessages(dataStr);
                                if (versionMessages.isEmpty()) {
                                    allEntries.add(new TimelineEntry(v.optString("owner"), v.optString("creation"), "last edited this", "version_generic"));
                                } else {
                                    for (String msg : versionMessages) {
                                        allEntries.add(new TimelineEntry(v.optString("owner"), v.optString("creation"), msg, "version"));
                                    }
                                }
                            }
                        }

                        JSONArray assignments = docinfo.optJSONArray("assignments");
                        if (assignments != null) {
                            for (int i = 0; i < assignments.length(); i++) {
                                JSONObject a = assignments.getJSONObject(i);
                                allEntries.add(new TimelineEntry(
                                        a.optString("owner"),
                                        a.optString("creation"),
                                        "assigned this document to " + a.optString("assign_to"),
                                        "assignment"
                                ));
                            }
                        }

                        Collections.sort(allEntries, (e1, e2) -> e2.creation.compareTo(e1.creation));

                        for (int i = 0; i < allEntries.size(); i++) {
                            TimelineEntry entry = allEntries.get(i);
                            int iconRes = 0;

                            String rawUser = getUserFullName(entry.owner, mergedUserInfoMap, currentEmail);
                            String prefix = rawUser.equalsIgnoreCase("You") ? "You " : rawUser + " ";

                            switch (entry.type.toLowerCase()) {
                                case "comment":
                                    String commentText = entry.content.replaceAll("<[^>]*>", "");
                                    String displayComment = rawUser + " commented\n\n" + commentText;
                                    addActivityItem(displayComment, entry.creation, iconRes, (i == 0), false, true);
                                    break;

                                case "version":
                                case "version_generic":
                                    addActivityItem(prefix + entry.content, entry.creation, iconRes, (i == 0), false, false);
                                    break;

                                case "communication":
                                    String commMsg = prefix + "sent: " + entry.content.replaceAll("<[^>]*>", "");
                                    addActivityItem(commMsg, entry.creation, iconRes, (i == 0), false, false);
                                    break;

                                default:
                                    String msg = entry.content.replaceAll("<[^>]*>", "");
                                    if (msg.contains(rawUser) || rawUser.equalsIgnoreCase("You")) {
                                        addActivityItem(msg, entry.creation, iconRes, (i == 0), false, false);
                                    } else {
                                        addActivityItem(prefix + msg, entry.creation, iconRes, (i == 0), false, false);
                                    }
                                    break;
                            }
                        }
                    }
                }

                String creatorLabel = getUserFullName(owner, mergedUserInfoMap, currentEmail);
                addActivityItem(creatorLabel + " created this", creation, 0, false, true, false);

            } catch (Exception e) {
                Log.e("TIMELINE", "Parse error", e);
                addBaseCreationOnly(owner, creation);
            }
        });
    }

    private static class TimelineEntry {
        String owner, creation, content, type;
        TimelineEntry(String owner, String creation, String content, String type) {
            this.owner = owner; this.creation = creation; this.content = content; this.type = type;
        }
    }

    private List<String> getVersionMessages(String data) {
        List<String> messages = new ArrayList<>();
        if (data == null || data.trim().isEmpty() || data.equals("{}") || data.equals("[]")) {
            return messages;
        }

        try {
            String parsedData = data.trim();
            // Frappe v15 sometimes doubly escapes strings inside the json wrapper
            if (parsedData.startsWith("\"") && parsedData.endsWith("\"")) {
                parsedData = parsedData.substring(1, parsedData.length() - 1)
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\n", "\n");
            }
            parsedData = parsedData.trim();

            if (parsedData.startsWith("[")) {
                String parsed = parseVersionArray(new JSONArray(parsedData));
                if (parsed != null && !parsed.isEmpty()) messages.add(parsed);
            } else if (parsedData.startsWith("{")) {
                messages.addAll(parseVersionObject(new JSONObject(parsedData)));
            }
        } catch (Exception e) {
            Log.e("TIMELINE_PARSE", "Failed to parse version data: " + data, e);
        }
        return messages;
    }

    private String parseVersionArray(JSONArray changed) {
        try {
            List<String> changeParts = new ArrayList<>();
            for (int i = 0; i < changed.length(); i++) {
                JSONArray c = changed.optJSONArray(i);
                if (c == null || c.length() < 1) continue;
                String fieldName = c.getString(0);
                if (isRedundantField(fieldName)) continue;

                String field = prettifyFieldName(fieldName);
                String oldVal = formatFieldValueStrict(fieldName, c.isNull(1) ? "null" : c.optString(1, ""));
                String newVal = formatFieldValueStrict(fieldName, c.isNull(2) ? "null" : c.optString(2, ""));

                changeParts.add(field + " from " + oldVal + " to " + newVal);
            }
            if (!changeParts.isEmpty()) {
                return "changed the value of " + String.join(", ", changeParts);
            }
        } catch (Exception e) {
            Log.e("TIMELINE_PARSE", "Error parsing version array", e);
        }
        return null;
    }

    // =========================================================================
    // BUG FIX: parseVersionObject — row_changed is a JSONArray, NOT a JSONObject.
    //
    // Frappe's actual wire format for row_changed:
    //   "row_changed": [
    //     ["items", <row_index_int>, "<row_name_str>", [
    //       ["grant_commission", <old>, <new>],
    //       ["projected_qty",    <old>, <new>]
    //     ]]
    //   ]
    //
    // The old code called obj.optJSONObject("row_changed") which always returned
    // null for this structure, silently dropping all child-table edits (stock
    // projections, grant commission, projected qty, reserve stock, etc.).
    // =========================================================================
    private List<String> parseVersionObject(JSONObject obj) {
        List<String> parts = new ArrayList<>();

        try {
            // ── top-level field changes ──────────────────────────────────────
            if (obj.has("changed")) {
                JSONArray changed = obj.optJSONArray("changed");
                if (changed != null) {
                    List<String> changeParts = new ArrayList<>();
                    for (int i = 0; i < changed.length(); i++) {
                        JSONArray c = changed.optJSONArray(i);
                        if (c == null || c.length() < 1) continue;
                        String fieldName = c.getString(0);

                        if (fieldName.equals("docstatus")) {
                            String docStatus = c.optString(2, "");
                            if (docStatus.equals("1")) parts.add("submitted this document");
                            else if (docStatus.equals("2")) parts.add("cancelled this document");
                            continue;
                        }

                        if (isRedundantField(fieldName)) continue;

                        String field = prettifyFieldName(fieldName);
                        String oldVal = formatFieldValueStrict(fieldName, c.isNull(1) ? "null" : c.optString(1, ""));
                        String newVal = formatFieldValueStrict(fieldName, c.isNull(2) ? "null" : c.optString(2, ""));
                        changeParts.add(field + " from " + oldVal + " to " + newVal);
                    }
                    if (!changeParts.isEmpty()) {
                        parts.add("changed the value of " + String.join(", ", changeParts));
                    }
                }
            }

            // ── child-table row edits (THE BUG WAS HERE) ────────────────────
            // row_changed is a JSONArray of 4-element tuples:
            //   [tableName, rowIndex, rowName, [[field, old, new], ...]]
            if (obj.has("row_changed")) {
                JSONArray rowChangedArray = obj.optJSONArray("row_changed");
                if (rowChangedArray != null && rowChangedArray.length() > 0) {
                    List<String> rowChangeParts = new ArrayList<>();

                    for (int i = 0; i < rowChangedArray.length(); i++) {
                        // Each element is: [tableName, rowIndex, rowName, [[field,old,new],...]]
                        JSONArray tuple = rowChangedArray.optJSONArray(i);
                        if (tuple == null || tuple.length() < 4) continue;

                        // tuple[1] = zero-based row index (integer)
                        // tuple[2] = internal row name/docname (string) — less readable
                        // We use rowIndex for the user-facing "#N" label (1-based).
                        int rowIndex = tuple.optInt(1, 0);
                        String rowLabel = "#" + rowIndex;

                        // tuple[3] = array of [fieldName, oldValue, newValue]
                        JSONArray fieldChanges = tuple.optJSONArray(3);
                        if (fieldChanges == null) continue;

                        for (int j = 0; j < fieldChanges.length(); j++) {
                            JSONArray fc = fieldChanges.optJSONArray(j);
                            if (fc == null || fc.length() < 3) continue;

                            String fieldName = fc.optString(0, "");
                            if (fieldName.isEmpty() || isRedundantField(fieldName)) continue;

                            String fieldPrettified = prettifyFieldName(fieldName);
                            String oldVal = formatFieldValueStrict(fieldName, fc.isNull(1) ? "null" : fc.optString(1, ""));
                            String newVal = formatFieldValueStrict(fieldName, fc.isNull(2) ? "null" : fc.optString(2, ""));

                            rowChangeParts.add(fieldPrettified + " from " + oldVal + " to " + newVal + " in row " + rowLabel);
                        }
                    }

                    if (!rowChangeParts.isEmpty()) {
                        parts.add("changed the values for " + String.join(", ", rowChangeParts));
                    }
                }
            }

            // ── rows added ───────────────────────────────────────────────────
            if (obj.has("added")) {
                JSONArray added = obj.optJSONArray("added");
                if (added != null) {
                    List<String> addedParts = new ArrayList<>();
                    for (int i = 0; i < added.length(); i++) {
                        JSONArray a = added.optJSONArray(i);
                        if (a != null && a.length() > 0) {
                            addedParts.add(prettifyFieldName(a.getString(0)));
                        }
                    }
                    if (!addedParts.isEmpty()) {
                        parts.add("added rows for " + String.join(", ", addedParts));
                    }
                }
            }

            // ── rows removed ─────────────────────────────────────────────────
            if (obj.has("removed")) {
                JSONArray removed = obj.optJSONArray("removed");
                if (removed != null) {
                    List<String> removedParts = new ArrayList<>();
                    for (int i = 0; i < removed.length(); i++) {
                        JSONArray r = removed.optJSONArray(i);
                        if (r != null && r.length() > 0) {
                            removedParts.add(prettifyFieldName(r.getString(0)));
                        }
                    }
                    if (!removedParts.isEmpty()) {
                        parts.add("removed rows for " + String.join(", ", removedParts));
                    }
                }
            }

        } catch (Exception e) {
            Log.e("TIMELINE_PARSE", "Error parsing version object", e);
        }

        return parts;
    }

    private boolean isRedundantField(String field) {
        if (field == null) return true;
        String f = field.toLowerCase();
        List<String> redundant = Arrays.asList(
                "total", "net_total", "base_net_total", "base_in_words", "in_words",
                "amount_eligible_for_commission", "rounded_total", "base_rounded_total",
                "taxes_and_charges_added", "taxes_and_charges_deducted",
                "base_taxes_and_charges_added", "base_taxes_and_charges_deducted",
                "modified", "modified_by"
        );
        return redundant.contains(f);
    }

    private String formatFieldValueStrict(String field, String value) {
        if (value == null || value.equals("null") || value.equals("None")) {
            value = "";
        }

        String f = field.toLowerCase();

        // Strict Currency Field Matching
        if (f.contains("rate") || f.contains("amount") || f.contains("price") || f.contains("total") || f.contains("value") || f.contains("incentives")) {
            try {
                double val = value.isEmpty() ? 0.0 : Double.parseDouble(value);
                if (val == 0) return "0";
                return String.format("₱ %,.2f", val);
            } catch (Exception e) { return value.isEmpty() ? "0" : value; }
        }

        // Strict Quantity/Integer/Checkbox Field Matching
        if (f.contains("qty") || f.contains("quantity") || f.contains("stock") || f.contains("commission") || f.contains("portion")) {
            try {
                double val = value.isEmpty() ? 0.0 : Double.parseDouble(value);
                if (val == (long) val) {
                    return String.format("%,d", (long) val);
                } else {
                    return String.format("%,.2f", val);
                }
            } catch (Exception e) { return value.isEmpty() ? "0" : value; }
        }

        if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            String[] p = value.split("-");
            return p[1] + "-" + p[2] + "-" + p[0];
        }

        if (value.isEmpty()) return "\"\"";
        return value;
    }

    private String prettifyFieldName(String field) {
        if (field == null || field.isEmpty()) return field;

        if (field.equalsIgnoreCase("transaction_date")) return "Date";
        if (field.equalsIgnoreCase("total_qty")) return "Total Quantity";
        if (field.equalsIgnoreCase("base_grand_total")) return "Grand Total (PHP)";
        if (field.equalsIgnoreCase("grand_total")) return "Grand Total";
        if (field.equalsIgnoreCase("base_total")) return "Total (PHP)";
        if (field.equalsIgnoreCase("delivery_date")) return "Delivery Date";
        if (field.equalsIgnoreCase("status")) return "Status";
        if (field.equalsIgnoreCase("fulfillment_status") || field.equalsIgnoreCase("order_fulfillment_status")) return "Order Fulfillment Status";
        if (field.equalsIgnoreCase("workflow_state") || field.equalsIgnoreCase("approval_status")) return "Approval Status";
        if (field.equalsIgnoreCase("items")) return "Items";
        if (field.equalsIgnoreCase("payment_schedule")) return "Payment Schedule";
        if (field.equalsIgnoreCase("sales_team")) return "Sales Team";
        if (field.equalsIgnoreCase("po_no") || field.equalsIgnoreCase("customer_purchase_order")) return "Customer's Purchase Order";
        if (field.equalsIgnoreCase("cost_center")) return "Cost Center";
        if (field.equalsIgnoreCase("qty_warehouse")) return "Qty (Warehouse)";
        if (field.equalsIgnoreCase("qty_company")) return "Qty (Company)";
        if (field.equalsIgnoreCase("contact_person")) return "Contact Person";
        if (field.equalsIgnoreCase("contact_display") || field.equalsIgnoreCase("contact")) return "Contact";
        if (field.equalsIgnoreCase("contact_mobile") || field.equalsIgnoreCase("phone")) return "Phone";
        if (field.equalsIgnoreCase("customer_address")) return "Customer Address";
        if (field.equalsIgnoreCase("address_display") || field.equalsIgnoreCase("address")) return "Address";
        if (field.equalsIgnoreCase("shipping_address_name")) return "Shipping Address Name";
        if (field.equalsIgnoreCase("taxes_and_charges") || field.equalsIgnoreCase("sales_taxes_and_charges_template")) return "Sales Taxes and Charges Template";
        if (field.equalsIgnoreCase("stock_uom_rate") || field.equalsIgnoreCase("rate_of_stock_uom")) return "Rate of Stock UOM";
        if (field.equalsIgnoreCase("grant_commission")) return "Grant Commission";
        if (field.equalsIgnoreCase("projected_qty")) return "Projected Qty";
        if (field.equalsIgnoreCase("reserve_stock")) return "Reserve Stock";

        String[] words = field.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private final Handler liveTimeHandler = new Handler(Looper.getMainLooper());
    private final Runnable liveTimeRunnable = new Runnable() {
        @Override
        public void run() {
            updateLiveTimestamps();
            liveTimeHandler.postDelayed(this, 30000);
        }
    };

    private void updateLiveTimestamps() {
        View[] tabs = {viewDetails, viewAddress, viewTerms, viewMoreInfo};
        for (View tab : tabs) {
            if (tab == null) continue;
            android.widget.LinearLayout container = tab.findViewById(R.id.activityItemsContainer);
            if (container == null) continue;

            for (int i = 0; i < container.getChildCount(); i++) {
                View item = container.getChildAt(i);
                Object timestampObj = item.getTag(R.id.tvActivityTime);
                Object contentObj = item.getTag(R.id.tvActivityContent);

                if (timestampObj instanceof String && contentObj instanceof String) {
                    String timestamp = (String) timestampObj;
                    String baseContent = (String) contentObj;
                    TextView tvContent = item.findViewById(R.id.tvActivityContent);

                    if (tvContent != null) {
                        String timeAgo = getTimeAgo(timestamp);
                        String timeSpan = " <font color='#888888'>· " + timeAgo + "</font>";
                        String updatedText;

                        if (baseContent.contains("commented\n\n")) {
                            String[] parts = baseContent.split("commented\n\n", 2);
                            updatedText = parts[0] + "commented" + timeSpan + "<br><br>" + parts[1];
                        } else {
                            updatedText = baseContent + timeSpan;
                        }
                        tvContent.setText(Html.fromHtml(updatedText, Html.FROM_HTML_MODE_LEGACY));
                    }
                }
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        liveTimeHandler.post(liveTimeRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        liveTimeHandler.removeCallbacks(liveTimeRunnable);
    }

    private void addBaseCreationOnly(String owner, String creation) {
        SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);
        String currentEmail = prefs.getString("User_Email", "");
        String creatorLabel = ((owner != null && owner.equalsIgnoreCase(currentEmail)) ? "You" : owner);
        addActivityItem(creatorLabel + " created this", creation, 0, true, true, false);
    }

    private void clearActivityContainers() {
        View[] tabs = {viewDetails, viewAddress, viewTerms, viewMoreInfo};
        for (View tab : tabs) {
            if (tab != null) {
                android.widget.LinearLayout container = tab.findViewById(R.id.activityItemsContainer);
                if (container != null) container.removeAllViews();
            }
        }
    }

    private void addActivityItem(String content, String timestamp, int iconRes, boolean isFirst, boolean isLast, boolean isComment) {
        View[] tabs = {viewDetails, viewAddress, viewTerms, viewMoreInfo};
        String timeAgo = (timestamp != null && !timestamp.isEmpty()) ? getTimeAgo(timestamp) : "";

        for (View tab : tabs) {
            if (tab != null) {
                android.widget.LinearLayout container = tab.findViewById(R.id.activityItemsContainer);
                if (container != null) {
                    View itemView = getLayoutInflater().inflate(R.layout.item_activity_trail, container, false);
                    itemView.setTag(R.id.tvActivityTime, timestamp);
                    itemView.setTag(R.id.tvActivityContent, content);

                    TextView tvContent = itemView.findViewById(R.id.tvActivityContent);
                    View lineTop = itemView.findViewById(R.id.viewLineTop);
                    View lineBottom = itemView.findViewById(R.id.viewLineBottom);
                    View dot = itemView.findViewById(R.id.viewActivityDot);
                    ImageView icon = itemView.findViewById(R.id.imgActivityIcon);

                    String timeSpan = " <font color='#888888'>· " + timeAgo + "</font>";
                    String displayHtml;

                    if (isComment) {
                        if (content.contains("commented\n\n")) {
                            String[] parts = content.split("commented\n\n", 2);
                            displayHtml = parts[0] + "commented" + timeSpan + "<br><br>" + parts[1];
                        } else {
                            displayHtml = content + timeSpan;
                        }

                        tvContent.setText(Html.fromHtml(displayHtml, Html.FROM_HTML_MODE_LEGACY));
                        tvContent.setTextColor(Color.parseColor("#333333"));
                        tvContent.setPadding(30, 30, 30, 30);
                        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                        gd.setColor(Color.WHITE);
                        gd.setCornerRadius(20f);
                        gd.setStroke(1, Color.parseColor("#E0E0E0"));
                        tvContent.setBackground(gd);

                        if (icon != null) {
                            icon.setImageResource(R.drawable.ic_profile);
                            icon.setVisibility(View.VISIBLE);
                            if (dot != null) dot.setVisibility(View.GONE);
                        }
                    } else {
                        displayHtml = content + timeSpan;
                        tvContent.setText(Html.fromHtml(displayHtml, Html.FROM_HTML_MODE_LEGACY));
                        tvContent.setTextColor(Color.parseColor("#444444"));

                        if (iconRes != 0 && icon != null) {
                            icon.setImageResource(iconRes);
                            icon.setVisibility(View.VISIBLE);
                            if (dot != null) dot.setVisibility(View.GONE);
                        }
                    }

                    if (isFirst) lineTop.setVisibility(View.INVISIBLE);
                    if (isLast) lineBottom.setVisibility(View.INVISIBLE);

                    container.addView(itemView);
                }
            }
        }
    }

    private String getTimeAgo(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "";

        try {
            String cleanDate = dateStr.contains(".") ? dateStr.split("\\.")[0] : dateStr;
            java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(cleanDate,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            Instant past = ldt.atZone(ZoneId.of("Asia/Manila")).toInstant();
            Instant now = Instant.now();
            long seconds = Duration.between(past, now).getSeconds();

            if (seconds < 0) seconds = 0;

            if (seconds < 60) {
                return "Just now";
            }

            long minutes = seconds / 60;
            if (minutes < 60) {
                return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
            }

            long hours = minutes / 60;
            if (hours < 24) {
                return hours + (hours == 1 ? " hour ago" : " hours ago");
            }

            long days = hours / 24;
            if (days == 1) {
                return "yesterday";
            }
            if (days < 7) {
                return days + " days ago";
            }

            long weeks = days / 7;
            if (days < 30) {
                return weeks + (weeks == 1 ? " week ago" : " weeks ago");
            }

            long months = days / 30;
            if (months < 12) {
                return months + (months == 1 ? " month ago" : " months ago");
            }

            long years = months / 12;
            return years + (years == 1 ? " year ago" : " years ago");
        } catch (Exception e) {
            return dateStr;
        }
    }

    @Override
    protected void setupHoverEffect(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.92f).scaleY(0.92f).alpha(0.7f).setDuration(100).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(100).start();
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        v.performClick();
                    }
                    break;
            }
            return true;
        });
    }

    private boolean isMedRep() {
        return loggedInUserRole.equalsIgnoreCase("MedRep");
    }

    private boolean isDSM() {
        String role = loggedInUserRole.toUpperCase();
        return role.equals("DSM") || role.equals("DSM_I") || role.equals("DSM_IA");
    }

    private boolean isGM() {
        return loggedInUserRole.equalsIgnoreCase("GM");
    }

    private boolean isNSM1() {
        String role = loggedInUserRole.toUpperCase();
        return role.equals("NSM-1") || role.equals("NSM1");
    }

    private boolean isNSM2() {
        String role = loggedInUserRole.toUpperCase();
        return role.equals("NSM-2") || role.equals("NSM2");
    }

    private boolean isAdmin() {
        return loggedInUserRole.equalsIgnoreCase("Admin");
    }

    private boolean isOrderActionableByRole(JSONObject data) {
        String workflowState = getSafeString(data, "workflow_state");
        String docStatus = getSafeString(data, "status").isEmpty() ? "Draft" : getSafeString(data, "status");
        String status = !workflowState.isEmpty() ? workflowState : docStatus;

        if (isMedRep()) {
            return isRejectedOrder(status);
        }
        if (isDSM()) {
            return status.equalsIgnoreCase(STATE_FOR_DSM);
        }
        if (isGM()) {
            return status.equalsIgnoreCase(STATE_FOR_GM);
        }
        if (isNSM1()) {
            return status.equalsIgnoreCase(STATE_FOR_NSM1);
        }
        if (isNSM2()) {
            return status.equalsIgnoreCase(STATE_FOR_NSM2);
        }
        if (isAdmin()) {
            return isOrderActionable(data);
        }
        return false;
    }

    private boolean isRejectedOrder(String state) {
        return state.equalsIgnoreCase(STATE_REJECTED_DSM)
                || state.equalsIgnoreCase(STATE_REJECTED_GM)
                || state.equalsIgnoreCase(STATE_REJECTED_NSM1)
                || state.equalsIgnoreCase(STATE_REJECTED_NSM2);
    }

    private boolean isOrderActionable(JSONObject data) {
        String workflowState = getSafeString(data, "workflow_state");
        String docStatus = getSafeString(data, "status").isEmpty() ? "Draft" : getSafeString(data, "status");
        String approvalStatus = !workflowState.isEmpty() ? workflowState : docStatus;

        if (approvalStatus.equalsIgnoreCase(STATE_SO_APPROVED)) {
            return false;
        }

        String fulfillmentStatus = getSafeString(data, "fulfillment_status").isEmpty() ? "Not Fulfilled" : getSafeString(data, "fulfillment_status");
        boolean isTerminalFulfillment = fulfillmentStatus.equalsIgnoreCase(FULFILLMENT_DELIVERED)
                || fulfillmentStatus.equalsIgnoreCase(FULFILLMENT_CANCELLED);

        return !isTerminalFulfillment;
    }

    private void setupWorkflowActions(JSONObject data) {
        if (btnApproveActions == null) return;

        if (isOrderActionableByRole(data)) {
            btnApproveActions.setVisibility(View.VISIBLE);
            setupHoverEffect(btnApproveActions);
            btnApproveActions.setOnClickListener(v -> {
                if (isMedRep()) {
                    triggerEditMode(data);
                } else {
                    showRoleBasedActionDialog(data);
                }
            });
        } else {
            btnApproveActions.setVisibility(View.GONE);
        }
    }

    private void showRoleBasedActionDialog(JSONObject data) {
        if (isMedRep()) return;

        String dialogTitle;
        String[] options;

        switch (loggedInUserRole.toUpperCase()) {
            case "DSM":
                dialogTitle = "DSM Action";
                options = new String[]{ ACTION_APPROVE_PASS_TO_INVOICER, ACTION_APPROVE_PASS_TO_GM, ACTION_REJECT };
                break;
            case "DSM_I":
                dialogTitle = "DSM Action";
                options = new String[]{ ACTION_APPROVE_PASS_TO_INVOICER, ACTION_APPROVE_PASS_TO_NSM1, ACTION_REJECT };
                break;
            case "DSM_IA":
                dialogTitle = "DSM Action";
                options = new String[]{ ACTION_APPROVE_PASS_TO_INVOICER, ACTION_APPROVE_PASS_TO_NSM2, ACTION_REJECT };
                break;
            case "NSM1":
                dialogTitle = "NSM-1 Action";
                options = new String[]{ ACTION_APPROVE_PASS_TO_INVOICER, ACTION_APPROVE_PASS_TO_NSM2, ACTION_REJECT };
                break;
            case "NSM2":
                dialogTitle = "NSM-2 Action";
                options = new String[]{ ACTION_APPROVE, ACTION_REJECT };
                break;
            case "GM":
                dialogTitle = "GM Action";
                options = new String[]{ ACTION_APPROVE, ACTION_REJECT };
                break;
            case "ADMIN":
                String adminPhase = determineAdminPhase(data);
                if (adminPhase.equals(ADMIN_PHASE_MEDREP)) {
                    dialogTitle = "Phase 1 (Submit)";
                    options = new String[]{ACTION_SUBMIT_FOR_APPROVAL};
                } else if (adminPhase.equals(ADMIN_PHASE_DSM)) {
                    dialogTitle = "Phase 2 (DSM)";
                    options = new String[]{ ACTION_APPROVE_PASS_TO_INVOICER, ACTION_APPROVE_PASS_TO_GM, ACTION_REJECT };
                } else {
                    dialogTitle = "Phase 3 (GM)";
                    options = new String[]{ACTION_APPROVE, ACTION_REJECT};
                }
                break;
            default:
                Toast.makeText(this, "Your role does not have permission to perform actions.", Toast.LENGTH_LONG).show();
                return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final AlertDialog[] dialogHolder = new AlertDialog[1];
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        float density = getResources().getDisplayMetrics().density;
        int rootPad = (int)(16 * density);
        root.setPadding(rootPad, rootPad, rootPad, rootPad);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int layoutVPad = (int)(40 * density);
        int layoutHPad = (int)(24 * density);
        layout.setPadding(layoutHPad, layoutVPad, layoutHPad, layoutVPad);
        layout.setGravity(Gravity.CENTER);

        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(Color.parseColor("#CCFFFFFF"));
        gd.setCornerRadius(30 * density);
        gd.setStroke(4, Color.parseColor("#AAFFFFFF"));
        layout.setBackground(gd);
        root.addView(layout);

        TextView titleView = new TextView(this);
        titleView.setText(dialogTitle);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextColor(Color.parseColor("#333333"));
        titleView.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        titleView.setPadding(0, 0, 0, (int)(32 * density));
        layout.addView(titleView);

        for (String option : options) {
            TextView btn = createPopupButton(option);
            btn.setOnClickListener(v2 -> {
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                performWorkflowAction(option);
            });
            layout.addView(btn);
        }

        TextView btnCancel = new TextView(this);
        btnCancel.setText("CANCEL");
        btnCancel.setPadding(0, (int)(24 * density), 0, 0);
        btnCancel.setTextColor(Color.GRAY);
        btnCancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        btnCancel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        btnCancel.setGravity(Gravity.CENTER);
        layout.addView(btnCancel);

        builder.setView(root);
        dialogHolder[0] = builder.create();
        if (dialogHolder[0].getWindow() != null) {
            dialogHolder[0].getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }
        btnCancel.setOnClickListener(v2 -> dialogHolder[0].dismiss());
        dialogHolder[0].show();
    }

    private TextView createPopupButton(String text) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setGravity(Gravity.CENTER);

        float density = getResources().getDisplayMetrics().density;
        int hPad = (int)(24 * density);
        int vPad = (int)(12 * density);
        btn.setPadding(hPad, vPad, hPad, vPad);

        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        btn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
        btnBg.setColor(Color.parseColor("#835C9F"));
        btnBg.setCornerRadius(30 * density);
        btn.setBackground(btnBg);

        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        int margin = (int)(8 * density);
        lp.setMargins(0, margin, 0, margin);
        btn.setLayoutParams(lp);

        setupHoverEffect(btn);
        return btn;
    }

    private String determineAdminPhase(JSONObject data) {
        String workflowState = getSafeString(data, "workflow_state");
        String docStatus = getSafeString(data, "status").isEmpty() ? "Draft" : getSafeString(data, "status");
        String state = !workflowState.isEmpty() ? workflowState : docStatus;

        if (state.equalsIgnoreCase(STATE_DRAFT)
                || state.equalsIgnoreCase(STATE_REJECTED_DSM)
                || state.equalsIgnoreCase(STATE_REJECTED_GM)
                || state.equalsIgnoreCase(STATE_REJECTED_NSM1)
                || state.equalsIgnoreCase(STATE_REJECTED_NSM2)) {
            return ADMIN_PHASE_MEDREP;
        } else if (state.equalsIgnoreCase(STATE_FOR_DSM)) {
            return ADMIN_PHASE_DSM;
        } else {
            return ADMIN_PHASE_GM;
        }
    }

    private void performWorkflowAction(String action) {
        if (btnApproveActions != null) {
            btnApproveActions.setEnabled(false);
            btnApproveActions.setText("Processing...");
        }

        applyWorkflowSingle(action, (isSuccess, errorMsg) -> {
            runOnUiThread(() -> {
                if (isSuccess) {
                    if (btnApproveActions != null) btnApproveActions.setVisibility(View.GONE);
                    AppNotification.show(this, "Order processed successfully", AppNotification.Type.SUCCESS);
                    fetchOrderData();
                } else {
                    if (btnApproveActions != null) {
                        btnApproveActions.setEnabled(true);
                        btnApproveActions.setText("Actions");
                    }
                    AppNotification.show(this, "Action Failed: " + errorMsg, AppNotification.Type.ERROR);
                }
            });
        });
    }

    private void applyWorkflowSingle(String action, WorkflowCallback callback) {
        try {
            JSONObject payload = new JSONObject();
            JSONObject doc = new JSONObject();
            doc.put("doctype", "Sales Order");
            doc.put("name", salesOrderId);
            payload.put("doc", doc);
            payload.put("action", action);

            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    payload.toString(), okhttp3.MediaType.parse("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(WORKFLOW_API)
                    .post(body)
                    .addHeader("Cookie", finalCookie)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { callback.onResult(false, "Network Failure"); }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String respBody = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        callback.onResult(true, null);
                    } else {
                        String erpError = ErrorUtils.parseErpNextError(respBody);
                        callback.onResult(false, erpError);
                    }
                }
            });
        } catch (Exception e) { callback.onResult(false, e.getMessage()); }
    }

    private interface WorkflowCallback {
        void onResult(boolean isSuccess, String errorMsg);
    }

    private void updateHeaderAlignment(boolean isActionable) {
        if (tvHeaderCustomerName == null) return;

        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) tvHeaderCustomerName.getLayoutParams();

        // Requirement: Always align to the start (left) regardless of actionability
        tvHeaderCustomerName.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        lp.horizontalBias = 0.0f;

        int marginStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        lp.setMarginStart(marginStart);

        if (btnApproveActions != null && btnApproveActions.getVisibility() == View.VISIBLE) {
            // Optimization: Constrain text end to the start of the "Actions" button to prevent overlap.
            // This ensures the name is truncated (ellipsized) if it gets too long, instead of overriding the button.
            lp.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
            lp.endToStart = R.id.btnApproveActions;
            int marginEnd = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
            lp.setMarginEnd(marginEnd);
        } else {
            // If no button is present at the top, allow the text to extend towards the right edge.
            lp.endToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
            lp.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            int marginEnd = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
            lp.setMarginEnd(marginEnd);
        }

        tvHeaderCustomerName.setLayoutParams(lp);
    }

    private void fetchCustomerLicenseInfo(String customerId) {
        if (customerId == null || customerId.isEmpty()) return;

        String url = Config.BASE_URL + "/api/resource/Customer/" + android.net.Uri.encode(customerId);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", finalCookie)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("CUSTOMER_FETCH", "Failed to fetch customer license info", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONObject custData = json.optJSONObject("data");
                        if (custData != null) {
                            final String lto = getSafeString(custData, "lto_no").isEmpty() ? "N/A" : getSafeString(custData, "lto_no");
                            final String permit = getSafeString(custData, "business_permit").isEmpty() ? "N/A" : getSafeString(custData, "business_permit");

                            runOnUiThread(() -> {
                                if (tvLtoNoVal != null) tvLtoNoVal.setText(lto);
                                if (tvBusinessPermitVal != null) tvBusinessPermitVal.setText(permit);
                            });
                        }
                    } catch (Exception e) {
                        Log.e("CUSTOMER_FETCH", "Error parsing customer data", e);
                    }
                }
            }
        });
    }
}