package com.pims.medretailers;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.datepicker.MaterialDatePicker;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Activity_SO_Landscape extends BaseActivity {

    private RecyclerView recyclerView;
    private SalesOrderAdapter adapter;
    private List<SalesOrder> salesOrderList;
    private String finalCookie = "";
    private String loggedInUserRole = "";

    private String currentSearchQuery = "";

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private TextView tvPageNumber;
    private TextView tvTotalOrderCount;
    private android.widget.CheckBox headerCheckbox;
    private AppCompatButton btnPreviousPage, btnNextPage;
    private AppCompatButton btnCreateNewOrder;
    private AppCompatButton btnApproveActions;

    private TextView tvEmptyState;
    private SwipeRefreshLayout swipeRefreshLayout;

    private int currentLimitStart = 0;
    private int totalOrderCount = 0;
    private final int PAGE_LENGTH = 20;
    private boolean isLoading = false;
    private boolean isLastPage = false;

    private final OkHttpClient client = NetworkClient.getInstance();

    public static final MediaType JSON_MEDIA =
            MediaType.get("application/json; charset=utf-8");

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

    private final String[] APPROVAL_STATUSES = {
            "For Approval by DSM", "For Approval by GM", "For Approval by NSM-1", "For Approval by NSM-2",
            "SO Approved", "Rejected by DSM", "Rejected by GM", "Rejected by NSM-1", "Rejected by NSM-2", "Draft"
    };

    private final String[] FULFILLMENT_STATUSES = {
            "-", "For Processing", "Dispatched", "Delivered", "For Payment Validation", "Paid", "Returned", "For re-deliver", "Cancelled"
    };

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

    private boolean isOrderActionableByRole(SalesOrder order) {
        String status = order.getApprovalStatus();

        if (isMedRep()) {
            return isRejectedOrder(order);
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
            return isOrderActionable(order);
        }
        return false;
    }

    private boolean isRejectedOrder(SalesOrder order) {
        String state = order.getApprovalStatus();
        return state.equalsIgnoreCase(STATE_REJECTED_DSM)
                || state.equalsIgnoreCase(STATE_REJECTED_GM)
                || state.equalsIgnoreCase(STATE_REJECTED_NSM1)
                || state.equalsIgnoreCase(STATE_REJECTED_NSM2);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_so_landscape);

        SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("Session_Cookie")) {
            finalCookie = intent.getStringExtra("Session_Cookie");
            prefs.edit().putString("Session_Cookie", finalCookie).apply();
        } else {
            finalCookie = prefs.getString("Session_Cookie", "");
        }

        loggedInUserRole = prefs.getString("User_Role", "MedRep");
        Log.d("SO_LANDSCAPE", "Session started as role: " + loggedInUserRole);

        tvPageNumber = findViewById(R.id.tvPageNumber);
        tvTotalOrderCount = findViewById(R.id.tvTotalOrderCount);
        headerCheckbox = findViewById(R.id.header_checkbox);
        btnPreviousPage = findViewById(R.id.btnPreviousPage);
        btnNextPage = findViewById(R.id.btnNextPage);
        btnCreateNewOrder = findViewById(R.id.btnCreateNewOrder);
        btnApproveActions = findViewById(R.id.btnApproveActions);

        ImageView imgTopDecor = findViewById(R.id.imgTopDecor);
        if (imgTopDecor != null) {
            imgTopDecor.setOnClickListener(this::showHamburgerMenu);
        }

        tvEmptyState = findViewById(R.id.tvEmptyState);
        if (tvEmptyState != null) {
            tvEmptyState.setVisibility(View.GONE);
        }

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(android.graphics.Color.parseColor("#835C9F"));
            swipeRefreshLayout.setOnRefreshListener(() -> {
                currentLimitStart = 0;
                fetchTotalOrderCount();
                fetchSalesOrdersFromApi();
            });
        }

        if (btnCreateNewOrder != null) {
            setupHoverEffect(btnCreateNewOrder);
            btnCreateNewOrder.setOnClickListener(v -> {
                OrderDataManager.getInstance().clearData();
                Intent newOrderIntent = new Intent(this, Activity_OrderDetails.class);
                newOrderIntent.putExtra("Session_Cookie", finalCookie);
                startActivity(newOrderIntent);
            });
        }

        if (btnApproveActions != null) {
            setupHoverEffect(btnApproveActions);
            btnApproveActions.setOnClickListener(v -> {
                List<SalesOrder> checkedItems = adapter.getCheckedItems();
                if (checkedItems.isEmpty()) return;

                if (isMedRep()) {
                    List<SalesOrder> rejectedItems = new ArrayList<>();
                    for (SalesOrder o : checkedItems) {
                        if (isRejectedOrder(o)) rejectedItems.add(o);
                    }

                    if (rejectedItems.size() == 1) {
                        openRejectedOrderForEditing(rejectedItems.get(0));
                    } else if (rejectedItems.size() > 1) {
                        Toast.makeText(this, "Please select only ONE rejected order to edit at a time.", Toast.LENGTH_LONG).show();
                    }
                    return;
                }

                showRoleBasedActionDialog(checkedItems);
            });
        }

        if (isMedRep() && btnApproveActions != null) {
            btnApproveActions.setVisibility(View.GONE);
        }

        androidx.appcompat.widget.SearchView searchView = findViewById(R.id.imgsearchSO);
        if (searchView != null) {
            EditText searchEditText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
            if (searchEditText != null) {
                searchEditText.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        if (searchView.getQuery().toString().trim().isEmpty()) {
                            v.performClick();
                            showSearchOptionsPopup(searchView);
                            return true;
                        }
                    }
                    return false;
                });
            }

            searchView.setOnQueryTextListener(
                    new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
                        @Override
                        public boolean onQueryTextSubmit(String query) {
                            if (searchRunnable != null) {
                                searchHandler.removeCallbacks(searchRunnable);
                            }

                            currentSearchQuery = query;
                            currentLimitStart = 0;
                            isLoading = false;
                            fetchTotalOrderCount();
                            fetchSalesOrdersFromApi();
                            return false;
                        }

                        @Override
                        public boolean onQueryTextChange(String newText) {
                            if (searchRunnable != null) {
                                searchHandler.removeCallbacks(searchRunnable);
                            }

                            searchRunnable = () -> {
                                currentSearchQuery = newText;
                                currentLimitStart = 0;
                                isLoading = false;
                                fetchTotalOrderCount();
                                fetchSalesOrdersFromApi();
                            };

                            searchHandler.postDelayed(searchRunnable, 400);
                            return true;
                        }
                    });
        }

        recyclerView = findViewById(R.id.recyclerSalesOrders);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        salesOrderList = new ArrayList<>();

        adapter = new SalesOrderAdapter(salesOrderList,
                item -> {
                    Intent detailsIntent = new Intent(this,
                            Activity_CompleteView_Details.class);
                    detailsIntent.putExtra("SO_ID", item.getId());
                    detailsIntent.putExtra("Session_Cookie", finalCookie);
                    startActivity(detailsIntent);
                },
                (checkedCount, isAllChecked) -> {
                    if (headerCheckbox != null) {
                        headerCheckbox.setOnCheckedChangeListener(null);
                        headerCheckbox.setChecked(isAllChecked);
                        headerCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                            if (adapter != null) adapter.selectAll(isChecked);
                        });
                    }

                    if (checkedCount > 0) {
                        List<SalesOrder> selectedItems = adapter.getCheckedItems();
                        boolean allActionable = true;
                        for (SalesOrder o : selectedItems) {
                            if (!isOrderActionableByRole(o)) {
                                allActionable = false;
                                break;
                            }
                        }

                        if (allActionable) {
                            btnCreateNewOrder.setVisibility(View.GONE);
                            btnApproveActions.setVisibility(View.VISIBLE);
                            btnApproveActions.setText("Actions");
                        } else {
                            btnCreateNewOrder.setVisibility(View.GONE);
                            btnApproveActions.setVisibility(View.GONE);
                        }
                    } else {
                        btnCreateNewOrder.setVisibility(View.VISIBLE);
                        btnApproveActions.setVisibility(View.GONE);
                    }
                }
        );
        recyclerView.setAdapter(adapter);

        btnNextPage.setOnClickListener(v -> {
            if (!isLastPage && !isLoading) {
                currentLimitStart += PAGE_LENGTH;
                fetchSalesOrdersFromApi();
            }
        });

        btnPreviousPage.setOnClickListener(v -> {
            if (currentLimitStart >= PAGE_LENGTH && !isLoading) {
                currentLimitStart -= PAGE_LENGTH;
                fetchSalesOrdersFromApi();
            }
        });

        fetchTotalOrderCount();
        fetchSalesOrdersFromApi();
    }

    private void showHamburgerMenu(View v) {
        animateHamburgerMenu((ImageView) v, () -> showUniversalMenu(v));
    }

    private void showSearchOptionsPopup(androidx.appcompat.widget.SearchView searchView) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);

        float density = getResources().getDisplayMetrics().density;
        int rootPad = (int)(24 * density);
        root.setPadding(rootPad, rootPad, rootPad, rootPad);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int layoutVPad = (int)(32 * density);
        int layoutHPad = (int)(24 * density);
        layout.setPadding(layoutHPad, layoutVPad, layoutHPad, layoutVPad);
        layout.setGravity(android.view.Gravity.CENTER);

        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(android.graphics.Color.parseColor("#F8F9FA"));
        gd.setCornerRadius(30 * density);
        layout.setBackground(gd);
        root.addView(layout);

        TextView title = new TextView(this);
        title.setText("Choose Search Type: ");
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f);
        title.setPadding(0, 0, 0, (int)(24 * density));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(android.graphics.Color.parseColor("#835C9F"));
        layout.addView(title);

        TextView btnSearchText = createPopupButton("Search by Text (Name, ID)");
        TextView btnSearchDate = createPopupButton("Search by Date / Range");
        TextView btnSearchApproval = createPopupButton("Search by Approval Status");
        TextView btnSearchFulfillment = createPopupButton("Search by Order Fulfillment Status");

        TextView btnCancel = new TextView(this);
        btnCancel.setText("CANCEL");
        btnCancel.setPadding(0, (int)(20 * density), 0, 0);
        btnCancel.setTextColor(android.graphics.Color.GRAY);
        btnCancel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f);
        btnCancel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        btnCancel.setGravity(android.view.Gravity.CENTER);

        layout.addView(btnSearchText);
        layout.addView(btnSearchDate);
        layout.addView(btnSearchApproval);
        layout.addView(btnSearchFulfillment);
        layout.addView(btnCancel);

        builder.setView(root);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnSearchText.setOnClickListener(v2 -> {
            dialog.dismiss();
            EditText searchEditText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
            if (searchEditText != null) {
                searchEditText.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        btnSearchDate.setOnClickListener(v2 -> {
            dialog.dismiss();
            showDateSearchOptions(searchView);
        });

        btnSearchApproval.setOnClickListener(v2 -> {
            dialog.dismiss();
            showFilterDialog("Choose Approval Status", APPROVAL_STATUSES, status -> {
                currentSearchQuery = "workflow_state:" + status;
                searchView.setQuery(currentSearchQuery, true);
            });
        });

        btnSearchFulfillment.setOnClickListener(v2 -> {
            dialog.dismiss();
            showFilterDialog("Choose Fulfillment Status", FULFILLMENT_STATUSES, status -> {
                currentSearchQuery = "" + status;
                searchView.setQuery(currentSearchQuery, true);
            });
        });

        btnCancel.setOnClickListener(v2 -> dialog.dismiss());
        dialog.show();
    }

    private TextView createPopupButton(String text) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setGravity(android.view.Gravity.CENTER);

        float density = getResources().getDisplayMetrics().density;
        int hPad = (int)(24 * density);
        int vPad = (int)(12 * density);
        btn.setPadding(hPad, vPad, hPad, vPad);

        btn.setTextColor(android.graphics.Color.WHITE);
        btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f);
        btn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
        btnBg.setColor(android.graphics.Color.parseColor("#835C9F"));
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

    private void openDateRangePicker(androidx.appcompat.widget.SearchView searchView) {
        MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Select Date Range")
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection != null && selection.first != null && selection.second != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                String start = sdf.format(new Date(selection.first));
                String end = sdf.format(new Date(selection.second));
                currentSearchQuery = "range:" + start + "," + end;
                searchView.setQuery(currentSearchQuery, true);
            }
        });
        picker.show(getSupportFragmentManager(), "DATE_RANGE_PICKER");
    }

    private void showDateSearchOptions(androidx.appcompat.widget.SearchView searchView) {
        String[] options = {"Pick Single Date", "Pick Date Range", "Clear Date Filter"};
        new AlertDialog.Builder(this)
                .setTitle("Select Date Search Type")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Calendar c = Calendar.getInstance();
                        new android.app.DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                            String picked = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                            currentSearchQuery = "date:" + picked;
                            searchView.setQuery(currentSearchQuery, true);
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
                    } else if (which == 1) {
                        openDateRangePicker(searchView);
                    } else {
                        currentSearchQuery = "";
                        searchView.setQuery("", true);
                    }
                }).show();
    }

    private void showFilterDialog(String title, String[] items, FilterSelectListener listener) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(items, (dialog, which) -> listener.onSelected(items[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private interface FilterSelectListener {
        void onSelected(String status);
    }

    private void fetchTotalOrderCount() {
        // Switching to frappe.desk.reportview.get_count which is used by the web UI list views
        // and respects User Permissions more accurately.
        String url = Config.BASE_URL + "/api/method/frappe.desk.reportview.get_count";

        try {
            JSONArray finalFilters = new JSONArray();
            applyPermissionFilters(finalFilters);

            StringBuilder params = new StringBuilder();
            params.append("?doctype=Sales Order");

            if (!currentSearchQuery.isEmpty()) {
                if (currentSearchQuery.startsWith("date:") ||
                    currentSearchQuery.startsWith("range:") ||
                    currentSearchQuery.startsWith("workflow_state:") ||
                    currentSearchQuery.startsWith("fulfillment_status:") ||
                    currentSearchQuery.startsWith("status:")) {

                    JSONArray searchFilters = buildFilters(currentSearchQuery);
                    for (int i = 0; i < searchFilters.length(); i++) finalFilters.put(searchFilters.get(i));

                    params.append("&filters=").append(java.net.URLEncoder.encode(finalFilters.toString(), "UTF-8"));
                } else {
                    // Global search: Apply permissions to filters, search query to or_filters
                    if (finalFilters.length() > 0) {
                        params.append("&filters=").append(java.net.URLEncoder.encode(finalFilters.toString(), "UTF-8"));
                    }

                    JSONArray orFilters = new JSONArray();
                    orFilters.put(new JSONArray().put("Sales Order").put("customer_name").put("like").put("%" + currentSearchQuery + "%"));
                    orFilters.put(new JSONArray().put("Sales Order").put("name").put("like").put("%" + currentSearchQuery + "%"));
                    orFilters.put(new JSONArray().put("Sales Order").put("territory").put("like").put("%" + currentSearchQuery + "%"));
                    orFilters.put(new JSONArray().put("Sales Order").put("workflow_state").put("like").put("%" + currentSearchQuery + "%"));
                    orFilters.put(new JSONArray().put("Sales Order").put("fulfillment_status").put("like").put("%" + currentSearchQuery + "%"));
                    orFilters.put(new JSONArray().put("Sales Order").put("status").put("like").put("%" + currentSearchQuery + "%"));

                    params.append("&or_filters=").append(java.net.URLEncoder.encode(orFilters.toString(), "UTF-8"));
                }
            } else if (finalFilters.length() > 0) {
                params.append("&filters=").append(java.net.URLEncoder.encode(finalFilters.toString(), "UTF-8"));
            }

            url += params.toString();

            // Cancel pending count requests
            for (Call call : client.dispatcher().queuedCalls()) {
                if ("SO_COUNT".equals(call.request().tag())) call.cancel();
            }
            for (Call call : client.dispatcher().runningCalls()) {
                if ("SO_COUNT".equals(call.request().tag())) call.cancel();
            }

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Cookie", finalCookie)
                    .tag("SO_COUNT")
                    .get()
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    final String respBody = response.body() != null ? response.body().string() : "";
                    if (swipeRefreshLayout != null) {
                    runOnUiThread(() -> {
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }

                if (response.isSuccessful()) {
                        try {
                            JSONObject json = new JSONObject(respBody);
                            // reportview.get_count returns count in "message"
                            final int total = json.optInt("message", 0);
                            totalOrderCount = total;
                            runOnUiThread(() -> {
                                if (tvTotalOrderCount != null) tvTotalOrderCount.setText("(Total: " + total + ")");
                                updatePaginationUI();
                                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                            });
                        } catch (Exception ignored) {}
                    } else {
                        runOnUiThread(() -> {
                            String erpError = ErrorUtils.parseErpNextError(respBody);
                            Log.e("SO_COUNT", "Error fetching total count: " + erpError);
                        });
                    }
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void fetchSalesOrdersFromApi() {
        // Requirement: Ensure list changes when searching even if no results are found.
        // Also: Cancel any pending search calls to avoid race conditions.
        for (Call call : client.dispatcher().queuedCalls()) {
            if ("SO_FETCH".equals(call.request().tag())) call.cancel();
        }
        for (Call call : client.dispatcher().runningCalls()) {
            if ("SO_FETCH".equals(call.request().tag())) call.cancel();
        }

        if (isLoading) isLoading = false; // Force reset if we are starting a new search
        isLoading = true;

        String url = Config.BASE_URL + "/api/method/frappe.desk.reportview.get"
                + "?doctype=Sales Order"
                + "&fields=[\"name\",\"customer_name\",\"status\",\"workflow_state\",\"delivery_date\",\"territory\",\"grand_total\",\"fulfillment_status\"]"
                + "&order_by=modified desc"
                + "&start=" + currentLimitStart
                + "&page_length=" + PAGE_LENGTH;

        try {
            JSONArray finalFilters = new JSONArray();
            applyPermissionFilters(finalFilters);

            if (!currentSearchQuery.isEmpty()) {
                if (currentSearchQuery.startsWith("date:") ||
                    currentSearchQuery.startsWith("range:") ||
                    currentSearchQuery.startsWith("workflow_state:") ||
                    currentSearchQuery.startsWith("fulfillment_status:") ||
                    currentSearchQuery.startsWith("status:")) {

                    JSONArray searchFilters = buildFilters(currentSearchQuery);
                    for (int i = 0; i < searchFilters.length(); i++) finalFilters.put(searchFilters.get(i));

                    url += "&filters=" + java.net.URLEncoder.encode(finalFilters.toString(), "UTF-8");
                } else {
                    if (finalFilters.length() > 0) {
                        url += "&filters=" + java.net.URLEncoder.encode(finalFilters.toString(), "UTF-8");
                    }

                    JSONArray orFilters = new JSONArray();
                    orFilters.put(new JSONArray().put("Sales Order").put("customer_name").put("like").put("%" + currentSearchQuery + "%"));
                    orFilters.put(new JSONArray().put("Sales Order").put("name").put("like").put("%" + currentSearchQuery + "%"));
                    orFilters.put(new JSONArray().put("Sales Order").put("territory").put("like").put("%" + currentSearchQuery + "%"));
                    orFilters.put(new JSONArray().put("Sales Order").put("workflow_state").put("like").put("%" + currentSearchQuery + "%"));
                    orFilters.put(new JSONArray().put("Sales Order").put("fulfillment_status").put("like").put("%" + currentSearchQuery + "%"));
                    orFilters.put(new JSONArray().put("Sales Order").put("status").put("like").put("%" + currentSearchQuery + "%"));

                    url += "&or_filters=" + java.net.URLEncoder.encode(orFilters.toString(), "UTF-8");
                }
            } else if (finalFilters.length() > 0) {
                url += "&filters=" + java.net.URLEncoder.encode(finalFilters.toString(), "UTF-8");
            }
        } catch (Exception e) {
            e.printStackTrace();
            isLoading = false;
        }

        Log.d("API_URL", "URL: " + url);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", finalCookie)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                isLoading = false;
                runOnUiThread(() -> {
                    if (!currentSearchQuery.isEmpty()) {
                        salesOrderList.clear();
                        adapter.notifyDataSetChanged();
                        if (tvEmptyState != null) {
                            tvEmptyState.setVisibility(View.VISIBLE);
                            tvEmptyState.setText("No matching records found.");
                        }
                        View tableView = findViewById(R.id.horizontalScrollTable);
                        if (tableView != null) tableView.setVisibility(View.GONE);
                        if (recyclerView != null) recyclerView.setVisibility(View.GONE);
                    }
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(Activity_SO_Landscape.this, "Network Failure", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                isLoading = false;
                final String respBody = response.body() != null ? response.body().string() : "";

                if (swipeRefreshLayout != null) {
                    runOnUiThread(() -> {
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }

                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonObject = new JSONObject(respBody);
                        JSONObject messageObj = jsonObject.getJSONObject("message");
                        JSONArray keys = messageObj.getJSONArray("keys");
                        JSONArray values = messageObj.getJSONArray("values");

                        // Map keys to indices for robust parsing
                        int idxName = -1, idxCust = -1, idxStat = -1, idxWorkflow = -1, idxDate = -1, idxTerr = -1, idxTotal = -1, idxFulfill = -1;
                        for (int i = 0; i < keys.length(); i++) {
                            String k = keys.getString(i);
                            if (k.equals("name")) idxName = i;
                            else if (k.equals("customer_name")) idxCust = i;
                            else if (k.equals("status")) idxStat = i;
                            else if (k.equals("workflow_state")) idxWorkflow = i;
                            else if (k.equals("delivery_date")) idxDate = i;
                            else if (k.equals("territory")) idxTerr = i;
                            else if (k.equals("grand_total")) idxTotal = i;
                            else if (k.equals("fulfillment_status")) idxFulfill = i;
                        }

                        List<SalesOrder> newList = new ArrayList<>();
                        for (int i = 0; i < values.length(); i++) {
                            JSONArray row = values.getJSONArray(i);
                            
                            String name = idxName != -1 ? row.optString(idxName, "N/A") : "N/A";
                            String customer = idxCust != -1 ? row.optString(idxCust, "N/A") : "N/A";
                            String status = idxStat != -1 ? row.optString(idxStat, "Draft") : "Draft";
                            String workflow = idxWorkflow != -1 ? row.optString(idxWorkflow, status) : status;
                            String date = idxDate != -1 ? row.optString(idxDate, "N/A") : "N/A";
                            String territory = idxTerr != -1 ? row.optString(idxTerr, "N/A") : "N/A";
                            double total = idxTotal != -1 ? row.optDouble(idxTotal, 0.0) : 0.0;
                            String fulfillment = idxFulfill != -1 ? row.optString(idxFulfill, "-") : "-";

                            if (fulfillment == null || fulfillment.isEmpty() || fulfillment.equalsIgnoreCase("null") || fulfillment.equalsIgnoreCase("None") || fulfillment.equals("false")) {
                                fulfillment = "-";
                            }

                            newList.add(new SalesOrder(name, customer, fulfillment, workflow, date, territory, total));
                        }

                        runOnUiThread(() -> {
                            salesOrderList.clear();
                            salesOrderList.addAll(newList);
                            adapter.notifyDataSetChanged();

                            isLastPage = newList.size() < PAGE_LENGTH;
                            updatePaginationUI();

                            // Requirement: Display empty state message when search returns 0 results
                            View tableView = findViewById(R.id.horizontalScrollTable);
                            if (newList.isEmpty()) {
                                if (tvEmptyState != null) {
                                    tvEmptyState.setVisibility(View.VISIBLE);
                                    tvEmptyState.setText("No matching records found.");
                                }
                                if (tableView != null) tableView.setVisibility(View.GONE);
                                if (recyclerView != null) recyclerView.setVisibility(View.GONE);

                                // Hide pagination — no point showing page controls with nothing to page through
                                if (btnPreviousPage != null) btnPreviousPage.setVisibility(View.GONE);
                                if (btnNextPage != null) btnNextPage.setVisibility(View.GONE);
                                if (tvPageNumber != null) tvPageNumber.setVisibility(View.GONE);
                                if (tvTotalOrderCount != null) tvTotalOrderCount.setVisibility(View.GONE);
                            } else {
                                if (tvEmptyState != null) tvEmptyState.setVisibility(View.GONE);
                                if (tableView != null) tableView.setVisibility(View.VISIBLE);
                                if (recyclerView != null) recyclerView.setVisibility(View.VISIBLE);

                                // Show pagination UI when results exist
                                if (btnPreviousPage != null) btnPreviousPage.setVisibility(View.VISIBLE);
                                if (btnNextPage != null) btnNextPage.setVisibility(View.VISIBLE);
                                if (tvPageNumber != null) tvPageNumber.setVisibility(View.VISIBLE);
                                if (tvTotalOrderCount != null) tvTotalOrderCount.setVisibility(View.VISIBLE);
                            }
                        });

                    } catch (JSONException e) {
                        e.printStackTrace();
                        runOnUiThread(() -> {
                            if (!currentSearchQuery.isEmpty()) {
                                salesOrderList.clear();
                                adapter.notifyDataSetChanged();
                                if (tvEmptyState != null) {
                                    tvEmptyState.setVisibility(View.VISIBLE);
                                    tvEmptyState.setText("No matching records found.");
                                }
                                View tableView = findViewById(R.id.horizontalScrollTable);
                                if (tableView != null) tableView.setVisibility(View.GONE);
                                if (recyclerView != null) recyclerView.setVisibility(View.GONE);
                            }
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        if (!currentSearchQuery.isEmpty()) {
                            salesOrderList.clear();
                            adapter.notifyDataSetChanged();
                            if (tvEmptyState != null) {
                                tvEmptyState.setVisibility(View.VISIBLE);
                                tvEmptyState.setText("No matching records found.");
                            }
                            View tableView = findViewById(R.id.horizontalScrollTable);
                            if (tableView != null) tableView.setVisibility(View.GONE);
                            if (recyclerView != null) recyclerView.setVisibility(View.GONE);
                        }
                        String erpError = ErrorUtils.parseErpNextError(respBody);
                        AppNotification.show(Activity_SO_Landscape.this, erpError, AppNotification.Type.ERROR);
                    });
                }
            }
        });
    }

    private JSONArray buildFilters(String query) throws JSONException {
        JSONArray filters = new JSONArray();
        if (query.startsWith("date:")) {
            filters.put(new JSONArray().put("Sales Order").put("delivery_date").put("=").put(query.substring(5)));
        } else if (query.startsWith("range:")) {
            String[] parts = query.substring(6).split(",");
            filters.put(new JSONArray().put("Sales Order").put("delivery_date").put("between").put(new JSONArray().put(parts[0]).put(parts[1])));
        } else if (query.startsWith("workflow_state:")) {
            filters.put(new JSONArray().put("Sales Order").put("workflow_state").put("=").put(query.substring(15)));
        } else if (query.startsWith("fulfillment_status:")) {
            String statusVal = query.substring(19);
            if (statusVal.equals("-")) {
                filters.put(new JSONArray().put("Sales Order").put("fulfillment_status").put("is").put("not set"));
            } else if (!statusVal.isEmpty()) {
                filters.put(new JSONArray().put("Sales Order").put("fulfillment_status").put("=").put(statusVal));
            }
        } else if (query.startsWith("status:")) {
            filters.put(new JSONArray().put("Sales Order").put("status").put("=").put(query.substring(7)));
        } else {
            filters.put(new JSONArray().put("Sales Order").put("customer_name").put("like").put("%" + query + "%"));
        }
        return filters;
    }

    private void updatePaginationUI() {
        int pageNum = (currentLimitStart / PAGE_LENGTH) + 1;

        tvPageNumber.setText("Page " + pageNum);
        btnPreviousPage.setEnabled(currentLimitStart > 0);
        btnNextPage.setEnabled(!isLastPage);

        btnPreviousPage.setAlpha(currentLimitStart > 0 ? 1.0f : 0.5f);
        btnNextPage.setAlpha(!isLastPage ? 1.0f : 0.5f);
    }

    private void openRejectedOrderForEditing(SalesOrder order) {
        runOnUiThread(() -> {
            AppNotification.show(this, "Opening rejected order: " + order.getId(), AppNotification.Type.INFO);
            OrderDataManager.getInstance().clearData();
            Intent intent = new Intent(this, Activity_CompleteView_Details.class);
            intent.putExtra("SO_ID", order.getId());
            intent.putExtra("Session_Cookie", finalCookie);
            intent.putExtra("TRIGGER_EDIT_MODE", true);
            startActivity(intent);
        });
    }

    private void showRoleBasedActionDialog(List<SalesOrder> selectedOrders) {
        if (isMedRep()) return;

        List<SalesOrder> actionableOrders = new ArrayList<>();
        for (SalesOrder order : selectedOrders) {
            if (isOrderActionable(order)) {
                actionableOrders.add(order);
            }
        }

        if (actionableOrders.isEmpty()) {
            Toast.makeText(this, "All selected orders are already completed.", Toast.LENGTH_LONG).show();
            return;
        }

        String dialogTitle;
        String[] options;

        switch (loggedInUserRole.toUpperCase()) {
            case "DSM":
                dialogTitle = "DSM Action — " + actionableOrders.size() + " Order(s)";
                options = new String[]{ ACTION_APPROVE_PASS_TO_INVOICER, ACTION_APPROVE_PASS_TO_GM, ACTION_REJECT };
                break;
            case "DSM_I":
                dialogTitle = "DSM Action — " + actionableOrders.size() + " Order(s)";
                options = new String[]{ ACTION_APPROVE_PASS_TO_INVOICER, ACTION_APPROVE_PASS_TO_NSM1, ACTION_REJECT };
                break;
            case "DSM_IA":
                dialogTitle = "DSM Action — " + actionableOrders.size() + " Order(s)";
                options = new String[]{ ACTION_APPROVE_PASS_TO_INVOICER, ACTION_APPROVE_PASS_TO_NSM2, ACTION_REJECT };
                break;
            case "NSM1":
                dialogTitle = "NSM-1 Action — " + actionableOrders.size() + " Order(s)";
                options = new String[]{ ACTION_APPROVE_PASS_TO_INVOICER, ACTION_APPROVE_PASS_TO_NSM2, ACTION_REJECT };
                break;
            case "NSM2":
                dialogTitle = "NSM-2 Action — " + actionableOrders.size() + " Order(s)";
                options = new String[]{ ACTION_APPROVE, ACTION_REJECT };
                break;
            case "GM":
                dialogTitle = "GM Action — " + actionableOrders.size() + " Order(s)";
                options = new String[]{ ACTION_APPROVE, ACTION_REJECT };
                break;
            case "ADMIN":
                String adminPhase = determineAdminPhase(actionableOrders);
                if (adminPhase.equals(ADMIN_PHASE_MIXED)) {
                    showMixedStateErrorDialog();
                    return;
                }
                if (adminPhase.equals(ADMIN_PHASE_MEDREP)) {
                    dialogTitle = "Phase 1 (Submit) — " + actionableOrders.size() + " Order(s)";
                    options = new String[]{ACTION_SUBMIT_FOR_APPROVAL};
                } else if (adminPhase.equals(ADMIN_PHASE_DSM)) {
                    dialogTitle = "Phase 2 (DSM) — " + actionableOrders.size() + " Order(s)";
                    options = new String[]{ ACTION_APPROVE_PASS_TO_INVOICER, ACTION_APPROVE_PASS_TO_GM, ACTION_REJECT };
                } else {
                    dialogTitle = "Phase 3 (GM) — " + actionableOrders.size() + " Order(s)";
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
        layout.setGravity(android.view.Gravity.CENTER);

        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(android.graphics.Color.parseColor("#CCFFFFFF"));
        gd.setCornerRadius(30 * density);
        gd.setStroke(4, android.graphics.Color.parseColor("#AAFFFFFF"));
        layout.setBackground(gd);
        root.addView(layout);

        TextView titleView = new TextView(this);
        titleView.setText(dialogTitle);
        titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f);
        titleView.setGravity(android.view.Gravity.CENTER);
        titleView.setTextColor(android.graphics.Color.parseColor("#333333"));
        titleView.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        titleView.setPadding(0, 0, 0, (int)(32 * density));
        layout.addView(titleView);

        for (String option : options) {
            TextView btn = createPopupButton(option);
            btn.setOnClickListener(v2 -> {
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                performBulkWorkflowAction(actionableOrders, option);
            });
            layout.addView(btn);
        }

        TextView btnCancel = new TextView(this);
        btnCancel.setText("CANCEL");
        btnCancel.setPadding(0, (int)(24 * density), 0, 0);
        btnCancel.setTextColor(android.graphics.Color.GRAY);
        btnCancel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f);
        btnCancel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        btnCancel.setGravity(android.view.Gravity.CENTER);
        layout.addView(btnCancel);

        builder.setView(root);
        dialogHolder[0] = builder.create();
        if (dialogHolder[0].getWindow() != null) {
            dialogHolder[0].getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        btnCancel.setOnClickListener(v2 -> dialogHolder[0].dismiss());
        dialogHolder[0].show();
    }

    private void performBulkWorkflowAction(List<SalesOrder> orders, String action) {
        if (btnApproveActions != null) {
            btnApproveActions.setEnabled(false);
            btnApproveActions.setText("Processing...");
        }

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        StringBuilder lastError = new StringBuilder();
        int totalToProcess = orders.size();

        for (SalesOrder order : orders) {
            applyWorkflowSingle(order.getId(), action, (isSuccess, errorMsg) -> {
                if (isSuccess) {
                    successCount.incrementAndGet();
                } else {
                    failureCount.incrementAndGet();
                    if (errorMsg != null) {
                        synchronized (lastError) {
                            if (lastError.length() == 0) lastError.append(errorMsg);
                        }
                    }
                }

                if (successCount.get() + failureCount.get() == totalToProcess) {
                    runOnUiThread(() -> {
                        if (btnApproveActions != null) {
                            btnApproveActions.setEnabled(true);
                            btnApproveActions.setVisibility(View.GONE);
                            btnCreateNewOrder.setVisibility(View.VISIBLE);
                        }

                        // Requirement: Clear selections automatically after bulk action
                        if (adapter != null) {
                            adapter.selectAll(false);
                            if (headerCheckbox != null) {
                                headerCheckbox.setOnCheckedChangeListener(null);
                                headerCheckbox.setChecked(false);
                                headerCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                                    if (adapter != null) adapter.selectAll(isChecked);
                                });
                            }
                        }

                        if (failureCount.get() > 0) {
                            String finalMsg = "Action Completed: " + successCount.get() + " Success, " + failureCount.get() + " Failed.\nError: " + lastError.toString();
                            AppNotification.show(this, finalMsg, AppNotification.Type.ERROR);
                        } else {
                            AppNotification.show(this, "Action Completed: " + successCount.get() + " Orders Processed", AppNotification.Type.SUCCESS);
                        }

                        currentLimitStart = 0;
                        fetchSalesOrdersFromApi();
                    });
                }
            });
        }
    }

    private void applyWorkflowSingle(String docName, String action, WorkflowCallback callback) {
        try {
            JSONObject payload = new JSONObject();
            JSONObject doc = new JSONObject();
            doc.put("doctype", "Sales Order");
            doc.put("name", docName);
            payload.put("doc", doc);
            payload.put("action", action);

            RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA);
            Request request = new Request.Builder()
                    .url(WORKFLOW_API)
                    .post(body)
                    .addHeader("Cookie", finalCookie)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { callback.onResult(false, "Network Failure"); }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String respBody = response.body() != null ? response.body().string() : "";
                    if (swipeRefreshLayout != null) {
                        runOnUiThread(() -> {
                            swipeRefreshLayout.setRefreshing(false);
                        });
                    }

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

    private void showMixedStateErrorDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Invalid Selection")
                .setMessage("You have selected orders in different workflow states. Please select orders that share the same status to perform a bulk action.")
                .setPositiveButton("OK", null)
                .show();
    }

    private boolean isOrderActionable(SalesOrder order) {
        String workflowState = order.getApprovalStatus();
        String fulfillmentStatus = order.getOfsStatus();

        boolean isFullyApproved = workflowState.equalsIgnoreCase(STATE_SO_APPROVED);
        boolean isTerminalFulfillment = fulfillmentStatus.equalsIgnoreCase(FULFILLMENT_DELIVERED)
                || fulfillmentStatus.equalsIgnoreCase(FULFILLMENT_CANCELLED);

        return !(isFullyApproved && isTerminalFulfillment);
    }

    private String determineAdminPhase(List<SalesOrder> orders) {
        boolean hasMedRepPhase = false;
        boolean hasDSMPhase = false;
        boolean hasGMPhase = false;

        for (SalesOrder order : orders) {
            if (!isOrderActionable(order)) continue;
            String state = order.getApprovalStatus();

            if (state.equalsIgnoreCase(STATE_DRAFT)
                    || state.equalsIgnoreCase(STATE_REJECTED_DSM)
                    || state.equalsIgnoreCase(STATE_REJECTED_GM)
                    || state.equalsIgnoreCase(STATE_REJECTED_NSM1)
                    || state.equalsIgnoreCase(STATE_REJECTED_NSM2)) {
                hasMedRepPhase = true;
            } else if (state.equalsIgnoreCase(STATE_FOR_DSM)) {
                hasDSMPhase = true;
            } else {
                hasGMPhase = true;
            }
        }

        int activePhases = 0;
        if (hasMedRepPhase) activePhases++;
        if (hasDSMPhase) activePhases++;
        if (hasGMPhase) activePhases++;

        if (activePhases > 1) return ADMIN_PHASE_MIXED;
        if (hasMedRepPhase) return ADMIN_PHASE_MEDREP;
        if (hasDSMPhase) return ADMIN_PHASE_DSM;
        return ADMIN_PHASE_GM;
    }

    private String getAdminPhaseLabel(String phase) {
        switch (phase) {
            case ADMIN_PHASE_MEDREP: return "Submit Actions";
            case ADMIN_PHASE_DSM: return "DSM Actions";
            default: return "Final Actions";
        }
    }

    private void applyPermissionFilters(JSONArray targetFilters) {
        if (isAdmin()) return;

        try {
            SharedPreferences prefs = getSharedPreferences("MedRetailerSession", MODE_PRIVATE);
            String permsString = prefs.getString("User_Permissions_Map", "{}");
            JSONObject perms = new JSONObject(permsString);

            if (perms.has("Territory")) {
                JSONArray allowedTerritories = perms.getJSONArray("Territory");
                if (allowedTerritories.length() > 0) {
                    targetFilters.put(new JSONArray()
                            .put("Sales Order")
                            .put("territory")
                            .put("in")
                            .put(allowedTerritories));
                }
            }

            if (perms.has("Company")) {
                JSONArray allowedCompanies = perms.getJSONArray("Company");
                if (allowedCompanies.length() > 0) {
                    targetFilters.put(new JSONArray()
                            .put("Sales Order")
                            .put("company")
                            .put("in")
                            .put(allowedCompanies));
                }
            }
        } catch (Exception e) {
            Log.e("SO_PERMS", "Error applying permission filters", e);
        }
    }

    @Override
    public void onBackPressed() {
        // Prevent accidental exit. Guide user to use the formal Logout button in the menu.
        AppNotification.show(this, "Use the Logout Account button in the menu to exit.", AppNotification.Type.INFO);
    }
}
