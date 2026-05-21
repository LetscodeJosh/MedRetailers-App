package com.pims.medretailers;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Fragment_AddressContact extends Fragment {

    private static final String TAG = "AddressTab";
    private EditText etCustomerAddressName, etContactPerson, etMobileNumber, etFullAddress, etTerritory;
    private ImageView btnSubmit;
    private String finalCookie = "";
    private final OkHttpClient client = new OkHttpClient();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_address_contact, container, false);

        if (getActivity() != null) {
            SharedPreferences prefs = getActivity().getSharedPreferences("MedRetailerSession", Context.MODE_PRIVATE);
            finalCookie = prefs.getString("Session_Cookie", "");
        }

        etCustomerAddressName = view.findViewById(R.id.etCustomerAddressName);
        etContactPerson       = view.findViewById(R.id.etContactPerson);
        etMobileNumber        = view.findViewById(R.id.etMobileNumber);
        etFullAddress         = view.findViewById(R.id.etFullAddress);
        etTerritory           = view.findViewById(R.id.etTerritory);
        btnSubmit             = view.findViewById(R.id.btnSubmitOrder);

        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                updateDataManager();
                OrderSubmitter.submitFullOrder(getActivity(), finalCookie);
            });
        }

        return view;
    }

    private void updateDataManager() {
        OrderDataManager data = OrderDataManager.getInstance();
        if (etContactPerson != null)       data.contactPerson       = etContactPerson.getText().toString().trim();
        if (etMobileNumber != null)        data.mobileNumber        = etMobileNumber.getText().toString().trim();
        if (etCustomerAddressName != null) data.customerAddressName = etCustomerAddressName.getText().toString().trim();
        if (etFullAddress != null)         data.fullAddress         = etFullAddress.getText().toString().trim();
        if (etTerritory != null)           data.territory           = etTerritory.getText().toString().trim();
    }

    @Override
    public void onResume() {
        super.onResume();
        OrderDataManager data = OrderDataManager.getInstance();
        String selectedCustomer = data.customer;

        boolean shouldFetch = (data.fullAddress == null || data.fullAddress.isEmpty())
                && selectedCustomer != null
                && !selectedCustomer.isEmpty();

        if (shouldFetch) {
            fetchCustomerDetails(selectedCustomer);
        } else {
            restoreFieldsFromMemory(data);
        }
    }

    private void restoreFieldsFromMemory(OrderDataManager data) {
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
    }

    private String getSafeString(JSONObject json, String key) {
        String val = json.optString(key, "");
        if (val == null || val.equalsIgnoreCase("null") || val.isEmpty()) {
            return "";
        }
        return val.trim();
    }

    private void fetchCustomerDetails(String customerId) {
        try {
            String encodedCustomer = java.net.URLEncoder.encode(customerId, "UTF-8");
            String url = Config.BASE_URL + "/api/resource/Customer"
                    + "?fields=[%22name%22,%22customer_name%22,%22customer_primary_address%22,%22primary_address%22,%22customer_primary_contact%22,%22territory%22,%22mobile_no%22]"
                    + "&filters=[[%22name%22,%22=%22,%22" + encodedCustomer + "%22]]";

            client.newCall(new Request.Builder()
                    .url(url).addHeader("Cookie", finalCookie).get().build()
            ).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String respData = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) return;
                    try {
                        JSONObject jsonObject = new JSONObject(respData);
                        JSONArray dataArray = jsonObject.optJSONArray("data");
                        if (dataArray == null || dataArray.length() == 0) return;

                        JSONObject cust = dataArray.getJSONObject(0);
                        String customerName      = getSafeString(cust, "customer_name");
                        String territory         = getSafeString(cust, "territory");
                        String mobileNo          = getSafeString(cust, "mobile_no");
                        String primaryAddressHtml = getSafeString(cust, "primary_address");
                        String addressName       = getSafeString(cust, "customer_primary_address");
                        String contactDocName    = getSafeString(cust, "customer_primary_contact");

                        if (primaryAddressHtml.isEmpty() && !addressName.isEmpty()) {
                            fetchDetailedAddress(addressName, customerName, territory, mobileNo, contactDocName);
                            return;
                        }

                        // Clean up address HTML
                        String fullAddress = primaryAddressHtml
                                .replace("<br>", "\n")
                                .replace("<br/>", "\n")
                                .replace("<br />", "\n")
                                .trim();

                        OrderDataManager data = OrderDataManager.getInstance();
                        data.customerAddressName = customerName;
                        data.mobileNumber        = mobileNo;
                        data.fullAddress         = fullAddress;
                        data.territory           = territory;

                        if (!contactDocName.isEmpty()) {
                            fetchContactName(contactDocName, customerName, territory, mobileNo, fullAddress);
                        } else {
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> updateUI("", customerName, territory, mobileNo, fullAddress));
                            }
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void fetchContactName(String contactDocName, String customerName, String territory, String mobileNo, String fullAddress) {
        try {
            String encodedContact = java.net.URLEncoder.encode(contactDocName, "UTF-8");
            String url = Config.BASE_URL + "/api/resource/Contact/" + encodedContact
                    + "?fields=[%22first_name%22,%22last_name%22,%22full_name%22,%22mobile_no%22]";

            client.newCall(new Request.Builder().url(url).addHeader("Cookie", finalCookie).get().build()
            ).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String respData = response.body() != null ? response.body().string() : "";
                    String contactName = "";
                    try {
                        JSONObject json = new JSONObject(respData);
                        JSONObject contactData = json.optJSONObject("data");
                        if (contactData != null) {
                            contactName = getSafeString(contactData, "full_name");
                            if (contactName.isEmpty()) {
                                String first = getSafeString(contactData, "first_name");
                                String last  = getSafeString(contactData, "last_name");
                                contactName  = (first + " " + last).trim();
                            }
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                    OrderDataManager.getInstance().contactPerson = contactName;
                    final String finalContactName = contactName.isEmpty() ? "" : contactName + " - Billing";
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> updateUI(finalContactName, customerName, territory, mobileNo, fullAddress));
                    }
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void fetchDetailedAddress(String addressName, String customerName, String territory, String mobileNo, String contactDocName) {
        try {
            String encodedAddress = java.net.URLEncoder.encode(addressName, "UTF-8");
            String url = Config.BASE_URL + "/api/resource/Address/" + encodedAddress;

            client.newCall(new Request.Builder()
                    .url(url).addHeader("Cookie", finalCookie).get().build()
            ).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    handleFetchError(customerName, territory, mobileNo, contactDocName);
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String respData = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        handleFetchError(customerName, territory, mobileNo, contactDocName);
                        return;
                    }
                    try {
                        JSONObject jsonObject = new JSONObject(respData);
                        JSONObject dataObj = jsonObject.optJSONObject("data");
                        if (dataObj == null) {
                            handleFetchError(customerName, territory, mobileNo, contactDocName);
                            return;
                        }

                        StringBuilder sb = new StringBuilder();
                        String line1 = getSafeString(dataObj, "address_line1");
                        String line2 = getSafeString(dataObj, "address_line2");
                        String city = getSafeString(dataObj, "city");
                        String state = getSafeString(dataObj, "state");
                        String country = getSafeString(dataObj, "country");
                        String pincode = getSafeString(dataObj, "pincode");

                        if (!line1.isEmpty()) sb.append(line1).append("\n");
                        if (!line2.isEmpty()) sb.append(line2).append("\n");
                        if (!city.isEmpty()) sb.append(city).append("\n");
                        if (!state.isEmpty()) sb.append(state).append("\n");
                        if (!country.isEmpty()) sb.append(country).append("\n");
                        if (!pincode.isEmpty()) sb.append(pincode);

                        String reconstructedAddress = sb.toString().trim();

                        OrderDataManager data = OrderDataManager.getInstance();
                        data.customerAddressName = customerName;
                        data.mobileNumber        = mobileNo;
                        data.fullAddress         = reconstructedAddress;
                        data.territory           = territory;

                        if (!contactDocName.isEmpty()) {
                            fetchContactName(contactDocName, customerName, territory, mobileNo, reconstructedAddress);
                        } else {
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> updateUI("", customerName, territory, mobileNo, reconstructedAddress));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        handleFetchError(customerName, territory, mobileNo, contactDocName);
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            handleFetchError(customerName, territory, mobileNo, contactDocName);
        }
    }

    private void handleFetchError(String customerName, String territory, String mobileNo, String contactDocName) {
        if (!contactDocName.isEmpty()) {
            fetchContactName(contactDocName, customerName, territory, mobileNo, "");
        } else {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> updateUI("", customerName, territory, mobileNo, ""));
            }
        }
    }

    private void updateUI(String contactName, String customerName, String territory, String mobileNo, String fullAddress) {
        if (!isAdded()) return;
        if (etCustomerAddressName != null) etCustomerAddressName.setText(customerName + " - Billing");
        if (etTerritory != null)           etTerritory.setText(territory);
        if (etContactPerson != null)       etContactPerson.setText(contactName);
        if (etMobileNumber != null)        etMobileNumber.setText(mobileNo);
        if (etFullAddress != null)         etFullAddress.setText(fullAddress);
    }

    @Override
    public void onPause() {
        super.onPause();
        updateDataManager();
    }
}
