package com.pims.medretailers;

import java.util.HashMap;

public class ProductItem {
    public String code;
    public String name;
    public String description; // We will use this to hold the Promo text!
    public String uom;

    public static HashMap<String, String> liveCatalog = new HashMap<>();

    public ProductItem(String code, String name, String description, String uom) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.uom = uom;
    }

    // THE FIX: Returning Code + Name allows the Search bar to filter by either one!
    @Override
    public String toString() {
        if (name == null || name.isEmpty() || name.contains("Select")) return code;
        return code + " : " + name;
    }
}