package com.pims.medretailers;

import android.app.DatePickerDialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.ViewHolder> {

    private List<OrderItem> itemList;
    private Context context;
    private TotalUpdateListener listener;

    public interface TotalUpdateListener {
        void onTotalChanged();
    }

    public OrderAdapter(List<OrderItem> itemList, Context context, TotalUpdateListener listener) {
        this.itemList = itemList;
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.itemlist_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OrderItem item = itemList.get(position);

        holder.removeTextWatchers();

        // Ensure 1-letter typing instantly triggers the filter
        holder.spItemName.setThreshold(1);

        // FIX: Force the dropdown to be much wider (e.g., 80% of screen width)
        // This prevents the "tiny" dropdown issue shown in the screenshot
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        holder.spItemName.setDropDownWidth((int) (screenWidth * 0.85));
        
        // Offset it to the left so it doesn't get cut off on the right
        holder.spItemName.setDropDownHorizontalOffset(-100);

        // Only set the adapter once per ViewHolder to avoid focus/UI glitches
        if (holder.spItemName.getAdapter() == null) {
            List<String> itemsToSearch = null;
            if (context instanceof Activity_ItemList_MedRepView) {
                itemsToSearch = Activity_ItemList_MedRepView.availableItemNames;
            } else if (context instanceof androidx.fragment.app.FragmentActivity) {
                itemsToSearch = Fragment_ItemList.availableItemNames;
            }
            if (itemsToSearch != null && !itemsToSearch.isEmpty()) {
                ItemSearchAdapter searchAdapter = new ItemSearchAdapter(context, itemsToSearch);
                holder.spItemName.setAdapter(searchAdapter);
            }
        }

        // ==========================================
        // DISPLAY LOGIC: Force Fully Editable Rows
        // ==========================================
        String nameToShow = (item.getItemName() != null && !item.getItemName().isEmpty() && !item.getItemName().contains("Select"))
                ? item.getItemName() : "";

        // Using setTag/getTag to avoid re-triggering watchers when binding
        holder.spItemName.setText(nameToShow, false);

        holder.spItemName.setFocusable(true);
        holder.spItemName.setFocusableInTouchMode(true);
        holder.spItemName.setClickable(true);
        holder.spItemName.setCursorVisible(true);

        // FIX: Automatically show dropdown when clicking or focusing, without needing to delete text
        holder.spItemName.setOnClickListener(v -> holder.spItemName.showDropDown());
        holder.spItemName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                holder.spItemName.showDropDown();
            }
        });

        holder.spItemName.setOnItemClickListener((parent, view, p, id) -> {
            String selected = (String) parent.getItemAtPosition(p);

            if (selected == null || selected.trim().isEmpty()) {
                item.setItemCode("");
                item.setItemName("");
                item.setRate(0.0);
                holder.etRate.setText("0.00");
                holder.tvAmount.setText(String.format(Locale.US, "₱ %.2f", item.getAmount()));
                if (listener != null) listener.onTotalChanged();
                return;
            }

            String code = "";
            Double rate = 0.0;

            if (context instanceof Activity_ItemList_MedRepView) {
                code = Activity_ItemList_MedRepView.itemCodeMap.get(selected);
                rate = Activity_ItemList_MedRepView.itemRateMap.getOrDefault(selected, 0.0);
            } else {
                code = Fragment_ItemList.itemCodeMap.get(selected);
                rate = Fragment_ItemList.itemRateMap.getOrDefault(selected, 0.0);
            }

            item.setItemCode(code);
            item.setItemName(code);
            item.setRate(rate != null ? rate : 0.0);

            holder.etRate.setText(String.format(Locale.US, "%.2f", item.getRate()));
            holder.tvAmount.setText(String.format(Locale.US, "₱ %.2f", item.getAmount()));

            if (listener != null) listener.onTotalChanged();
        });

        holder.itemNameWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (s.toString().trim().isEmpty()) {
                    item.setItemCode("");
                    item.setItemName("");
                    item.setRate(0.0);
                    holder.etRate.setText("0.00");
                    holder.tvAmount.setText(String.format(Locale.US, "₱ %.2f", item.getAmount()));
                    if (listener != null) listener.onTotalChanged();
                } else {
                    item.setItemName(s.toString());
                    // Optional: Try to auto-link code if exact match
                }
            }
        };
        holder.spItemName.addTextChangedListener(holder.itemNameWatcher);

        // =========================================================
        // DATA BINDING FOR QTY, RATE, AMOUNT
        // =========================================================
        holder.etQty.setText(item.getQuantity() > 0 ? String.valueOf(item.getQuantity()) : "0");
        holder.etQty.setFocusable(true);
        holder.etQty.setFocusableInTouchMode(true);
        holder.etQty.setClickable(true);

        holder.etRate.setText(item.getRate() > 0 ? String.format(Locale.US, "%.2f", item.getRate()) : "0.00");
        holder.etRate.setFocusable(true);
        holder.etRate.setFocusableInTouchMode(true);
        holder.etRate.setClickable(true);

        holder.tvAmount.setText(String.format(Locale.US, "₱ %.2f", item.getAmount()));

        TextWatcher mathWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                try {
                    String qStr = holder.etQty.getText().toString();
                    String rStr = holder.etRate.getText().toString();
                    int q = qStr.isEmpty() ? 0 : Integer.parseInt(qStr);
                    double r = rStr.isEmpty() ? 0.0 : Double.parseDouble(rStr);

                    item.setQuantity(q);
                    item.setRate(r);
                    holder.tvAmount.setText(String.format(Locale.US, "₱ %.2f", item.getAmount()));
                    if (listener != null) listener.onTotalChanged();
                } catch (Exception ignored) {}
            }
        };
        holder.addTextWatchers(mathWatcher);

        // ==========================================
        // DATE DISPLAY
        // ==========================================
        if (holder.tvDate != null) {
            if (item.getDate() == null || item.getDate().isEmpty()) {
                String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                item.setDate(today);
            }
            holder.tvDate.setText(item.getDate());

            holder.tvDate.setClickable(true);
            holder.tvDate.setOnClickListener(v -> {
                Calendar cal = Calendar.getInstance();
                new DatePickerDialog(context, (view, year, month, dayOfMonth) -> {
                    String date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                    item.setDate(date);
                    item.setDeliveryDate(date);
                    holder.tvDate.setText(date);
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
            });
        }

        // ==========================================
        // SELECTION LOGIC
        // ==========================================
        holder.cbSelect.setOnCheckedChangeListener(null);
        holder.cbSelect.setChecked(item.isChecked());
        holder.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> item.setChecked(isChecked));
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public android.widget.AutoCompleteTextView spItemName;
        TextView tvDate, tvAmount;
        EditText etQty, etRate;
        CheckBox cbSelect;

        TextWatcher mathWatcher;
        TextWatcher itemNameWatcher;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            spItemName = itemView.findViewById(R.id.etItemCode);
            tvDate = itemView.findViewById(R.id.tvDate);
            etQty = itemView.findViewById(R.id.etQuantity);
            etRate = itemView.findViewById(R.id.etRate);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            cbSelect = itemView.findViewById(R.id.cbSelect);

            spItemName.setSaveEnabled(false);
            etQty.setSaveEnabled(false);
            etRate.setSaveEnabled(false);
        }

        void addTextWatchers(TextWatcher watcher) {
            this.mathWatcher = watcher;
            etQty.addTextChangedListener(watcher);
            etRate.addTextChangedListener(watcher);
        }

        void removeTextWatchers() {
            if (mathWatcher != null) {
                etQty.removeTextChangedListener(mathWatcher);
                etRate.removeTextChangedListener(mathWatcher);
            }
            if (itemNameWatcher != null && spItemName != null) {
                spItemName.removeTextChangedListener(itemNameWatcher);
            }
        }
    }

    // =========================================================================
    // NEW: SMART SORTING ADAPTER
    // Prioritizes A-Z and StartsWith -> Contains
    // =========================================================================
    public class ItemSearchAdapter extends ArrayAdapter<String> {
        private List<String> originalItems;
        private List<String> filteredItems;

        public ItemSearchAdapter(@NonNull Context context, @NonNull List<String> items) {
            super(context, android.R.layout.simple_dropdown_item_1line, items);
            this.originalItems = new ArrayList<>(items);
            this.filteredItems = new ArrayList<>(items);
        }

        @Override
        public int getCount() { return filteredItems.size(); }

        @Override
        public String getItem(int position) { return filteredItems.get(position); }

        @NonNull
        @Override
        public View getView(int position, @androidx.annotation.Nullable View convertView, @NonNull ViewGroup parent) {
            TextView tv = (TextView) super.getView(position, convertView, parent);
            tv.setTextColor(android.graphics.Color.parseColor("#835C9F"));
            tv.setTextSize(15f);
            tv.setPadding(40, 40, 40, 40); 

            // Ensure text wraps so the whole item name is visible
            tv.setSingleLine(false);
            tv.setMaxLines(5);
            tv.setEllipsize(null); 
            
            tv.setMinimumHeight(140);

            return tv;
        }

        @NonNull
        @Override
        public android.widget.Filter getFilter() {
            return new android.widget.Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    List<String> suggestions = new ArrayList<>();

                    if (constraint == null || constraint.length() == 0) {
                        suggestions.addAll(originalItems);
                        Collections.sort(suggestions, String.CASE_INSENSITIVE_ORDER);
                    } else {
                        String filterPattern = constraint.toString().toLowerCase().trim();
                        List<String> startsWithList = new ArrayList<>();
                        List<String> containsList = new ArrayList<>();

                        for (String item : originalItems) {
                            String lowerItem = item.toLowerCase();
                            // Logic: If it's "CODE : NAME", check both
                            if (lowerItem.startsWith(filterPattern)) {
                                startsWithList.add(item);
                            } else if (lowerItem.contains(filterPattern)) {
                                containsList.add(item);
                            }
                        }

                        // Sort both lists internally A-Z
                        Collections.sort(startsWithList, String.CASE_INSENSITIVE_ORDER);
                        Collections.sort(containsList, String.CASE_INSENSITIVE_ORDER);

                        // Attach the pure 'Starts With' at the very top, followed by 'Contains'
                        suggestions.addAll(startsWithList);
                        suggestions.addAll(containsList);
                    }

                    results.values = suggestions;
                    results.count = suggestions.size();
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    filteredItems.clear();
                    if (results.values != null) {
                        filteredItems.addAll((List<String>) results.values);
                    }
                    notifyDataSetChanged();
                }
            };
        }
    }
}