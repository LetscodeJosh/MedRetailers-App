package com.pims.medretailers;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ErrorUtils {

    /**
     * Parses ERPNext v15 error response body into a clean, human-readable string.
     * Extracts multiple messages from _server_messages, exception, and message fields.
     */
    public static String parseErpNextError(String respBody) {
        if (respBody == null || respBody.trim().isEmpty()) return "Unknown server error.";

        StringBuilder errorBuilder = new StringBuilder();
        try {
            // Handle if the response is a simple string instead of JSON
            String trimmedBody = respBody.trim();
            if (!trimmedBody.startsWith("{") && !trimmedBody.startsWith("[")) {
                return fromHtml(respBody).trim();
            }

            JSONObject errJson = new JSONObject(respBody);

            // 1. Check for _server_messages (Standard Frappe/ERPNext Multi-error list)
            if (errJson.has("_server_messages")) {
                try {
                    String serverMsgsStr = errJson.getString("_server_messages");
                    JSONArray messagesArray = new JSONArray(serverMsgsStr);
                    
                    for (int i = 0; i < messagesArray.length(); i++) {
                        Object entry = messagesArray.get(i);
                        String msgEntry = entry.toString();
                        
                        try {
                            // Some entries are JSON objects (or stringified JSON objects) with a "message" key
                            JSONObject msgObj;
                            if (entry instanceof JSONObject) {
                                msgObj = (JSONObject) entry;
                            } else {
                                msgObj = new JSONObject(msgEntry);
                            }

                            if (msgObj.has("message")) {
                                String cleanMsg = fromHtml(msgObj.getString("message")).trim();
                                if (!cleanMsg.isEmpty()) {
                                    if (errorBuilder.indexOf(cleanMsg) == -1) {
                                        if (errorBuilder.length() > 0) errorBuilder.append("\n");
                                        errorBuilder.append(cleanMsg);
                                    }
                                }
                            }
                        } catch (JSONException e) {
                            // Others are just plain strings (sometimes with HTML)
                            String cleanMsg = fromHtml(msgEntry).trim();
                            if (!cleanMsg.isEmpty()) {
                                if (errorBuilder.indexOf(cleanMsg) == -1) {
                                    if (errorBuilder.length() > 0) errorBuilder.append("\n");
                                    errorBuilder.append(cleanMsg);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e("ErrorUtils", "Failed to parse _server_messages: " + e.getMessage());
                }
            }

            // 2. Check for "exception" field (Detailed stack trace or specific error)
            if (errJson.has("exception")) {
                String exc = fromHtml(errJson.getString("exception")).trim();
                
                // Frequently it's "frappe.exceptions.PermissionError: Not permitted"
                // Clean up common exception prefixes to match user's requested style
                String cleanedExc = exc;
                if (exc.contains(":")) {
                    cleanedExc = exc.substring(exc.indexOf(":") + 1).trim();
                }

                if (errorBuilder.indexOf(cleanedExc) == -1 && errorBuilder.indexOf(exc) == -1) {
                    if (errorBuilder.length() > 0) errorBuilder.append("\n");
                    errorBuilder.append(cleanedExc);
                }
            }

            // 3. Check for "exc_type" if it's a PermissionError or similar
            if (errJson.has("exc_type") && errorBuilder.length() == 0) {
                String type = errJson.getString("exc_type");
                if (type.contains("PermissionError")) {
                    errorBuilder.append("Not permitted: You do not have enough permissions to perform this action.");
                }
            }

            // 4. Check for top-level "message" (Common in some REST responses)
            if (errJson.has("message") && errorBuilder.length() == 0) {
                String msg = fromHtml(errJson.getString("message")).trim();
                errorBuilder.append(msg);
            }

        } catch (Exception e) {
            Log.e("ErrorUtils", "Raw Error Body parsing failed: " + respBody);
            try {
                return fromHtml(respBody).trim();
            } catch (Exception e2) {
                return respBody.length() > 500 ? respBody.substring(0, 500) + "..." : respBody;
            }
        }

        String finalError = errorBuilder.toString().trim();

        // QOL FIX: Replace technical Frappe exceptions with readable interpretations
        if (finalError.contains("frappe.exceptions.PermissionError")) {
            finalError = finalError.replace("frappe.exceptions.PermissionError", "Insufficient Permission");
        }
        
        // Clean up common ERPNext internal formatting if it persists
        finalError = finalError.replace("Not permitted:", "Insufficient Permission:")
                             .replace("Not permitted", "Insufficient Permission");

        return finalError.isEmpty() ? "Unknown ERP Error Occurred." : finalError;
    }

    private static String fromHtml(String html) {
        if (html == null) return "";
        try {
            return android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString();
        } catch (Exception e) {
            return html.replaceAll("<[^>]*>", "");
        }
    }
}
