package com.pims.medretailers;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;

public class ProductSpinnerAdapter extends ArrayAdapter<ProductItem> {

    public ProductSpinnerAdapter(Context context, List<ProductItem> products) {
        // We use the default simple_spinner_item to bypass your old black/red custom XML
        super(context, android.R.layout.simple_spinner_item, products);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        TextView tv = (TextView) super.getView(position, convertView, parent);
        ProductItem item = getItem(position);

        if (item != null) {
            if (position == 0) {
                tv.setText(item.name);
                tv.setTextColor(Color.parseColor("#835C9F"));
            } else {
                tv.setText(item.code + " : " + item.name);
                tv.setTextColor(Color.parseColor("#835C9F"));
            }
        }
        tv.setTextSize(14f);
        return tv;
    }

    // THIS METHOD CONTROLS THE OPEN DROPDOWN LIST
    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        // We generate a fresh TextView programmatically to guarantee it is pure white
        TextView tv = new TextView(getContext());
        ProductItem item = getItem(position);

        if (item != null) {
            if (position == 0) {
                tv.setText(item.name);
                tv.setTextColor(Color.parseColor("#835C9F"));
            } else {
                tv.setText(item.code + " : " + item.name);
                tv.setTextColor(Color.BLACK); // Force Crisp Black Text
            }
        }

        // CRITICAL FIX: Set text background to transparent so the rounded popup shows through
        tv.setBackgroundColor(Color.TRANSPARENT);
        tv.setPadding(40, 30, 40, 30); // Generous padding for a clean look
        tv.setTextSize(15f);

        return tv;
    }
}