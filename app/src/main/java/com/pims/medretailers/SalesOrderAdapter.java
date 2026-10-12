package com.pims.medretailers;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class SalesOrderAdapter extends RecyclerView.Adapter<SalesOrderAdapter.ViewHolder> implements Filterable {

    private List<SalesOrder> salesOrderList;
    private List<SalesOrder> salesOrderListFull;

    // Listeners
    private OnItemClickListener listener;
    private OnItemCheckedChangeListener checkedChangeListener;

    public interface OnItemClickListener {
        void onItemClick(SalesOrder item);
    }

    public interface OnItemCheckedChangeListener {
        void onItemCheckedChange(int checkedCount, boolean allChecked);
    }

    // ==========================================
    // UNIFIED CONSTRUCTOR (Fixes the variable conflict)
    // ==========================================
    public SalesOrderAdapter(List<SalesOrder> salesOrderList, OnItemClickListener listener, OnItemCheckedChangeListener checkedChangeListener) {
        this.salesOrderList = salesOrderList;
        this.salesOrderListFull = new ArrayList<>(salesOrderList);
        this.listener = listener;
        this.checkedChangeListener = checkedChangeListener;
    }

    public void updateData(List<SalesOrder> newList) {
        this.salesOrderList = newList;
        this.salesOrderListFull = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    // Select All Logic
    public void selectAll(boolean isSelected) {
        for (SalesOrder order : salesOrderList) {
            order.setSelected(isSelected);
        }
        notifyDataSetChanged();

        if (checkedChangeListener != null) {
            int count = isSelected ? salesOrderList.size() : 0;
            checkedChangeListener.onItemCheckedChange(count, isSelected);
        }
    }

    // ==========================================
    // NEW METHOD: GET ALL CHECKED ITEMS FOR THE ACTION BUTTON
    // ==========================================
    public List<SalesOrder> getCheckedItems() {
        List<SalesOrder> checked = new ArrayList<>();
        for (SalesOrder order : salesOrderList) {
            if (order.isSelected()) {
                checked.add(order);
            }
        }
        return checked;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.itemsales_order_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SalesOrder order = salesOrderList.get(position);

        holder.tvCustomer.setText(order.getCustomerName());
        holder.tvId.setText(order.getId());
        holder.tvOfs.setText(order.getOfsStatus());
        holder.tvApproval.setText(order.getApprovalStatus());
        holder.tvDate.setText(order.getDate());
        holder.tvTerritory.setText(order.getTerritory());
        holder.tvTotal.setText(String.format("₱ %.2f", order.getGrandTotal()));

        // CLICK LISTENER: Ensure it triggers for both Customer Name and ID
        View.OnClickListener clickAction = v -> {
            if (listener != null) listener.onItemClick(order);
        };
        holder.tvCustomer.setOnClickListener(clickAction);
        holder.tvId.setOnClickListener(clickAction);

        // ==========================================
        // THE MAGIC: CHECKBOX LISTENER THAT COUNTS
        // ==========================================
        holder.cbSelect.setOnCheckedChangeListener(null); // Prevent recycling bugs
        holder.cbSelect.setChecked(order.isSelected());

        holder.cbSelect.setOnCheckedChangeListener((v, isChecked) -> {
            order.setSelected(isChecked);

            int count = 0;
            for (SalesOrder o : salesOrderList) {
                if (o.isSelected()) count++;
            }

            if (checkedChangeListener != null) {
                boolean allChecked = (count == salesOrderList.size() && count > 0);
                checkedChangeListener.onItemCheckedChange(count, allChecked);
            }
        });
    }

    @Override
    public int getItemCount() {
        return salesOrderList.size();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                List<SalesOrder> filteredList = new ArrayList<>();

                if (constraint == null || constraint.length() == 0) {
                    filteredList.addAll(salesOrderListFull);
                } else {
                    String filterPattern = constraint.toString().toLowerCase().trim();

                    for (SalesOrder item : salesOrderListFull) {
                        String customer = item.getCustomerName() != null ? item.getCustomerName().toLowerCase() : "";
                        String id = item.getId() != null ? item.getId().toLowerCase() : "";
                        String ofs = item.getOfsStatus() != null ? item.getOfsStatus().toLowerCase() : "";
                        String approval = item.getApprovalStatus() != null ? item.getApprovalStatus().toLowerCase() : "";
                        String date = item.getDate() != null ? item.getDate().toLowerCase() : "";
                        String territory = item.getTerritory() != null ? item.getTerritory().toLowerCase() : "";
                        String total = String.format("₱ %.2f", item.getGrandTotal()).toLowerCase();

                        if (customer.contains(filterPattern) ||
                                id.contains(filterPattern) ||
                                ofs.contains(filterPattern) ||
                                approval.contains(filterPattern) ||
                                date.contains(filterPattern) ||
                                territory.contains(filterPattern) ||
                                total.contains(filterPattern)) {

                            filteredList.add(item);
                        }
                    }
                }

                FilterResults results = new FilterResults();
                results.values = filteredList;
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                salesOrderList.clear();
                if (results.values != null) {
                    salesOrderList.addAll((List) results.values);
                }
                notifyDataSetChanged();
            }
        };
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbSelect;
        TextView tvCustomer, tvId, tvOfs, tvApproval, tvDate, tvTerritory, tvTotal;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cbSelect = itemView.findViewById(R.id.cbSelect);
            tvCustomer = itemView.findViewById(R.id.tvCustomerName);
            tvId = itemView.findViewById(R.id.tvId);
            tvOfs = itemView.findViewById(R.id.tvOfs);
            tvApproval = itemView.findViewById(R.id.tvApproval);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTerritory = itemView.findViewById(R.id.tvTerritory);
            tvTotal = itemView.findViewById(R.id.tvTotal);
        }
    }
}

