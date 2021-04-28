package com.jasonette.seed.Action;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import com.jasonette.seed.Helper.JasonHelper;
import com.jasonette.seed.Launcher.Launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Iterator;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import timber.log.Timber;



public class JasonNetworkAction {
    private void _request(final JSONObject callback, final JSONObject action, final JSONObject data, final JSONObject event, final Context context){
        try{
            final JSONObject options = action.getJSONObject("options");
            if(options.has("url")){
                String url = options.getString("url");
                // method
                String method = "GET";
                if(options.has("method")) {
                    method = options.getString("method").toUpperCase();
                }

                // Attach session if it exists
                SharedPreferences pref = context.getSharedPreferences("session", 0);
                JSONObject session = null;

                URI uri_for_session = new URI(url.toLowerCase());
                String session_domain = uri_for_session.getHost();

                if(pref.contains(session_domain)){
                    String str = pref.getString(session_domain, null);
                    session = new JSONObject(str);
                }


                Request request;
                Request.Builder builder = new Request.Builder();
                // Attach Header from Session
                if(session != null && session.has("header")) {
                    Iterator<?> keys = session.getJSONObject("header").keys();
                    while (keys.hasNext()) {
                        String key = (String) keys.next();
                        String val = session.getJSONObject("header").getString(key);
                        builder.addHeader(key, val);
                    }
                }
                JSONArray errorVal = new JSONArray();
                JSONArray errorFullList = new JSONArray();
                // Attach header passed in as options
                if (options.has("header")) {
                    JSONObject header = options.getJSONObject("header");
                    Iterator<String> keys = header.keys();
                    try {
                        while (keys.hasNext()) {
                            String key = (String) keys.next();
                            String val = header.getString(key);
                            builder.addHeader(key, val);
                        }
                    } catch (Exception e) {

                    }
                }

                if(method.equalsIgnoreCase("get")) {
                    Uri.Builder b = Uri.parse(url).buildUpon();

                    // Attach Params from Session
                    if(session != null && session.has("body")) {
                        Iterator<?> keys = session.getJSONObject("body").keys();
                        while (keys.hasNext()) {
                            String key = (String) keys.next();
                            String val = session.getJSONObject("body").getString(key);

                            b.appendQueryParameter(key, val);

                        }
                    }

                    // params
                    if (options.has("data")) {
                        JSONObject d = options.getJSONObject("data");
                        Iterator<String> keysIterator = d.keys();
                        try {
                            while (keysIterator.hasNext()) {
                                String key = (String) keysIterator.next();
                                String val = d.getString(key);
                                b.appendQueryParameter(key, val);
                            }
                        } catch (Exception e) {
                            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                        }
                    }

                    Uri uri = b.build();
                    url = uri.toString();

                    request = builder
                            .url(url)
                            .build();
                } else {

                    if(options.has("header") && options.getJSONObject("header").has("content_type")) {
                        String content_type = options.getJSONObject("header").getString("content_type");
                        MediaType mediaType;
                        byte[] d;
                        if (content_type.equalsIgnoreCase("json")) {
                            mediaType = MediaType.parse("application/json; charset=utf-8");
                           d = options.getString("data").getBytes();
                        } else {
                            mediaType = MediaType.parse(content_type);
                            d = Base64.decode(options.getString("data"), Base64.DEFAULT);
                        }

                        request = builder
                                .url(url)
                                .method(method, RequestBody.create(mediaType, d))
                                .build();
                    } else {
                        // Params
                        FormBody.Builder bodyBuilder = new FormBody.Builder();
                        if (options.has("data")) {
                            // default json
                            JSONObject d = options.getJSONObject("data");
                            Iterator<String> keysIterator = d.keys();
                            try {
                                while (keysIterator.hasNext()) {
                                    String key = (String) keysIterator.next();
                                    try {
                                        JSONArray valTest = d.getJSONArray(key);
                                        JSONArray newVal = new JSONArray();

                                        for (int i = 0; i < valTest.length(); i++) {
                                            JSONObject row = valTest.getJSONObject(i);
                                            JSONObject newRow = new JSONObject();
                                            Boolean isFaulty = false;
                                            Iterator<String> rowKeys = row.keys();
                                            while(rowKeys.hasNext()) {

                                                String rowKey = rowKeys.next();
                                                String rowValue = row.getString(rowKey);
                                                if (rowValue.startsWith("content://") || rowValue.startsWith("file://")) {
                                                    Uri newFileUri = Uri.parse(rowValue);
                                                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                                                    //Log.d("test", uri_for_session.toString() + session_domain.toString());
                                                        if (Build.VERSION.SDK_INT < 29) {
                                                            try {
                                                                Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), newFileUri);
                                                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                                                                byte[] byteArray = outputStream.toByteArray();
                                                                String encodedString = Base64.encodeToString(byteArray, Base64.DEFAULT);
                                                                newRow.put(rowKey, encodedString);
                                                            } catch (SecurityException e) {
                                                                newRow.put(rowKey, rowValue);
                                                                JSONObject errorItem = new JSONObject();
                                                                errorItem.put("_parent_id", row.getString("_id"));
                                                                errorItem.put("_parent", key);
                                                                errorItem.put("_id", errorVal.length());
                                                                errorFullList = valTest;
                                                                errorVal.put(errorItem);
                                                                isFaulty = true;
                                                            }
                                                        } else {
                                                            Log.d("ok then", "before parsing");
                                                            try {
                                                                context.grantUriPermission("com.google.android.apps.photos.contentprovider", newFileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                                                InputStream stream =  context.getContentResolver().openInputStream(newFileUri);
                                                                byte[] byteArray = JasonHelper.readBytes(stream);
                                                                String encodedString = Base64.encodeToString(byteArray, Base64.DEFAULT);
                                                                stream.close();
                                                                newRow.put(rowKey, encodedString);
                                                            } catch (SecurityException e) {
                                                                StackTraceElement[] stackTrace = e.getStackTrace();
                                                                JSONObject errorItem = new JSONObject();
                                                                errorItem.put(rowKey, rowValue);
                                                                errorItem.put("_parent_id", row.getString("_id"));
                                                                errorItem.put("_parent", key);
                                                                errorItem.put("_id", errorVal.length());
                                                                errorFullList = valTest;
                                                                errorVal.put(errorItem);
                                                                isFaulty = true;
                                                                newRow.put(rowKey, "Security Error: no access granted for the target file");
                                                                for (int ii = 0; ii < e.getStackTrace().length; ii++) {
                                                                    Log.d("warning", stackTrace[ii].getMethodName() + " : " + stackTrace[ii].toString());
                                                                }                                                            }
                                                        }
                                                } else {
                                                    newRow.put(rowKey, rowValue);
                                                }
                                            }
                                            if (isFaulty) {
                                                newVal.put(newRow);
                                            } else {
                                                newVal.put(newRow);
                                            }
                                        }
                                        Log.d("value builder", "JSONArray : " + valTest.toString());
                                        bodyBuilder.add(key, newVal.toString());
                                    } catch (Exception e) {
                                        String val = d.getString(key);
                                        Log.d("value builder", "String : " + val);
                                        bodyBuilder.add(key, val);
                                    }
                                }
                            } catch (Exception e) {
                                Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                            }
                        }
                        // Attach Params from Session
                        if(session != null && session.has("body")) {
                            Iterator<?> keys = session.getJSONObject("body").keys();
                            while (keys.hasNext()) {
                                String key = (String) keys.next();
                                String val = session.getJSONObject("body").getString(key);
                                bodyBuilder.add(key, val);
                            }
                        }
                        RequestBody requestBody = bodyBuilder.build();

                        request = builder
                                .method(method, requestBody)
                                .url(url)
                                .build();
                    }
                }


                OkHttpClient client;
                if(options.has("timeout")) {
                    Object timeout = options.get("timeout");
                    if(timeout instanceof Long) {
                        client = ((Launcher)context.getApplicationContext()).getHttpClient((long)timeout);
                    } else if (timeout instanceof String){
                        Long timeout_int = Long.parseLong((String)timeout);
                        client = ((Launcher)context.getApplicationContext()).getHttpClient(timeout_int);
                    } else {
                        client = ((Launcher)context.getApplicationContext()).getHttpClient(0);
                    }
                } else{
                    client = ((Launcher)context.getApplicationContext()).getHttpClient(0);
                }
                if(errorVal.length() > 0 && action.has("content_error")) {
                    JSONObject errorObject = new JSONObject();
                    errorObject.put("content_error", errorVal);
                    errorObject.put("full_list", errorFullList);
                    JasonHelper.next("content_error", action, errorObject, event, context);
                } else {
                    client.newCall(request).enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            Timber.e(e);
                            try {
                                if (action.has("error")) {

                                    JSONObject error = new JSONObject();
                                    error.put("data", e.toString());

                                    if(callback != null) {
                                        JasonHelper.callback(callback, null, context);
                                    } else {
                                        JasonHelper.next("error", action, error, event, context);
                                    }
                                }
                            } catch (Exception e2){
                                Log.d("Warning", e2.getStackTrace()[0].getMethodName() + " : " + e2.toString());
                            }
                        }

                        @Override
                        public void onResponse(Call call, final Response response) throws IOException {
                            if (!response.isSuccessful()) {
                                try {
                                    if (action.has("error")) {
                                        JSONObject error = new JSONObject();
                                        error.put("data", response.toString());
                                        if(callback != null){
                                            JasonHelper.callback(callback, null, context);
                                        } else {
                                            JasonHelper.next("error", action, error, event, context);
                                        }
                                    }
                                } catch (Exception e){
                                    Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                                }
                            } else {
                                try {
                                    String jsonData = response.body().string();
                                    if(callback != null){
                                        JasonHelper.callback(callback, jsonData, context);
                                    } else {
                                        JasonHelper.next("success", action, jsonData, event, context);
                                    }

                                } catch (Exception e) {
                                    Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                                }
                            }
                        }
                    });
                }
            }
        } catch (Exception e){
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }
    public void request(final JSONObject action, final JSONObject data, final JSONObject event, final Context context){
        _request(null, action, data, event, context);
    }

    public void upload(JSONObject action, final JSONObject data, final JSONObject event, final Context context){
        try {
            final JSONObject options = action.getJSONObject("options");
            if (options.has("data")) {
                JSONObject stack = new JSONObject();
                stack.put("class", "JasonNetworkAction");
                stack.put("method", "process");
                stack = JasonHelper.preserve(stack, action, data, event, context);


                JSONObject params = new JSONObject();
                params.put("bucket", options.getString("bucket"));
                String uniqueId = UUID.randomUUID().toString();
                if(options.has("path")) {
                    params.put("path", options.getString("path") + "/" + uniqueId);
                } else {
                    params.put("path", "/" + uniqueId);
                }
                stack.put("filename", params.getString("path"));
                params.put("content-type", options.getString("content_type"));

                JSONObject new_options = new JSONObject();
                new_options.put("url", options.getString("sign_url"));
                new_options.put("data", params);

                JSONObject upload_action = new JSONObject();
                upload_action.put("options", new_options);

                _request(stack, upload_action, data, event, context);
            }
        } catch (Exception e){
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    // util
    public void process(JSONObject stack, String result) {
        try {
            if(result != null) {
                JSONObject o = stack.getJSONObject("options");
                final JSONObject action = o.getJSONObject("action");
                final JSONObject data = o.getJSONObject("data");
                final JSONObject event = o.getJSONObject("event");
                final Context context = (Context) o.get("context");

                JSONObject options = action.getJSONObject("options");

                JSONObject signed_url_object = new JSONObject(result);
                String signed_url = signed_url_object.getString("$jason"); // must return a signed_url wrapped with $jason

                stack.put("class", "JasonNetworkAction");
                stack.put("method", "uploadfinished");

                JSONObject header = new JSONObject();
                header.put("content_type", options.getString("content_type"));

                JSONObject new_options = new JSONObject();
                new_options.put("url", signed_url);
                new_options.put("data", options.get("data"));
                new_options.put("method", "put");
                new_options.put("header", header);
                action.put("options", new_options);

                _request(stack, action, data, event, context);

            }
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }
    public void uploadfinished(JSONObject stack, String result) {
        try {
            JSONObject ret = new JSONObject();
            ret.put("filename", stack.getString("filename"));
            ret.put("file_name", stack.getString("filename"));

            JSONObject o = stack.getJSONObject("options");
            final JSONObject action = o.getJSONObject("action");
            final JSONObject event = o.getJSONObject("event");
            final Context context = (Context) o.get("context");
            JasonHelper.next("success", action, ret, event, context);
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }
}
