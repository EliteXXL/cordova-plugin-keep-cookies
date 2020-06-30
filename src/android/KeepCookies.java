package org.apache.cordova;

import android.content.Context;

import android.util.Log;

import android.webkit.WebView;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.engine.SystemWebViewClient;
import org.apache.cordova.engine.SystemWebViewEngine;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.File;

import java.lang.System;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.CookieManager;
import java.net.HttpCookie;

public class KeepCookies extends CordovaPlugin {
    public static final String PLUGIN_NAME = "KeepCookies";
    protected static final String TAG = "KeepCookies";

    private static final String cookieDataFilename = "keepcookies.dat";

    private static KeepCookies instance = null;
    public static KeepCookies getInstance() {
        return instance;
    }
    
    public KeepCookies() {
        super();
        instance = this;
    }

    @Override
    public void pluginInitialize() {
        super.pluginInitialize();
        Log.d(TAG, "initialized");

        ((WebView) webView.getView()).setWebViewClient(new SystemWebViewClient((SystemWebViewEngine) webView.getEngine()) {
            
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request != null && requestData != null && request.getUrl() != null && request.getMethod().equalsIgnoreCase("get")) {
                    String url = request.getUrl().toString();
                    if (url.equals(requestData.get("url"))){
                        String scheme = request.getUrl().getScheme().trim();
                        if (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) {
                            return executeRequest(requestData.get("method"), url, request.getRequestHeaders(), requestData.get("input"));
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if (url != null && requestData != null) {
                    if (url.equals(requestData.get("url"))) {
                        return executeRequest(requestData.get("method"), url, requestData.get("input"));
                    }
                }
                return super.shouldInterceptRequest(view, url);
            }

        });

    }

    private Map<String, String> requestData = null;
    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if (action.equals("storeRequestData")) {
            requestData = new HashMap<String, String>();
            requestData.put("method", args.getString(0));
            requestData.put("url"   , args.getString(1));
            requestData.put("input" , args.getString(2));
            // Log.d(TAG, "stored data " + requestData.get("method") + " " + requestData.get("url") + " " + requestData.get("input"));
            return true;
        }
        return false;
    }

    private WebResourceResponse executeRequest(String method, String url) {
        return executeRequest(method, url, new HashMap<String, String>(), "");
    }
    private WebResourceResponse executeRequest(String method, String url, String input) {
        return executeRequest(method, url, new HashMap<String, String>(), input);
    }
    private WebResourceResponse executeRequest(String method, String url, Map<String, String> headers) {
        return executeRequest(method, url, headers, "");
    }
    private WebResourceResponse executeRequest(String method, String url, Map<String, String> headers, String input) {
        if (method.equalsIgnoreCase("GET")) {
            return null;
        }
        // Log.d(TAG, "executeRequest");
        HttpURLConnection connection = null;
        android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
        try {
            connection = (HttpURLConnection) (new URL(url)).openConnection();
            connection.setRequestMethod(method);
            connection.setDoInput(true);
            boolean doOut = false;
            if (method.equals("POST")) {
                connection.setDoOutput(true);
                doOut = true;
            }
            
            Log.d(TAG, "Connection headers:");
            for (Map.Entry<String,String> entry : headers.entrySet()) {
                Log.d(TAG, "\t" + entry.getKey() + " : " + entry.getValue());
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            
            cookieManager.setAcceptCookie(true);
            
            String cookie = cookieManager.getCookie(url);
            Log.d(TAG, "Cookie: " + cookie);
            connection.setRequestProperty("Cookie", cookie);

            connection.setAllowUserInteraction(true);

            // Log.d(TAG, "connection.connect");
            connection.connect();

            if (doOut) {
                DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
                wr.writeBytes(input == null ? "" : input);
                wr.flush();
            }

        } catch (Exception e) {
            Log.d(TAG, "Exception", e);
            e.printStackTrace();
            return null;
        }

        int responseCode;
        String responseMessage;
        try {
            responseCode = connection.getResponseCode();
            responseMessage = connection.getResponseMessage();
        } catch (Exception e) {
            return null;
        }

        InputStream stream;
        try {
            stream = connection.getInputStream();
        } catch (IOException e) {
            stream = connection.getErrorStream();
        }
        
        String mimeType = connection.getContentType();
        String encoding = connection.getContentEncoding();

        // Log.d(TAG, "Response of " + method + " " + url);
        // Log.d(TAG, "mimeType: " + mimeType);
        // Log.d(TAG, "encoding: " + encoding);

        Log.d(TAG, "Getting response headers");
        Map<String, String> responseHeaders = new HashMap<String, String>();
        for (Map.Entry<String, List<String>> entry: connection.getHeaderFields().entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            List<String> value = entry.getValue();
            for (int i = 0; i < value.size(); i++) {
                String actualValue = value.get(i);
                if (key.equalsIgnoreCase("content-type")) {
                    mimeType = actualValue;
                } else if (key.equalsIgnoreCase("content-encoding")) {
                    encoding = actualValue;
                } else if (key.equalsIgnoreCase("set-cookie")) {
                    cookieManager.setCookie(url, actualValue);
                    saveCookie(url, /* key + ": " + */ actualValue);
                } else if (key.equalsIgnoreCase("set-cookie2")) {
                    cookieManager.setCookie(url, actualValue);
                    saveCookie(url, /* key + ": " + */ actualValue);
                }
                Log.d(TAG, "HEADER " + i + " " + key + " : " + actualValue);
                responseHeaders.put(key, actualValue);
            }
        }
        Integer charsetI = mimeType.indexOf("charset=");
        if (charsetI > -1) {
            encoding = mimeType.substring(charsetI + 8);
        }
        mimeType = mimeType.split(";")[0];
        // Log.d(TAG, "mimeType: " + mimeType);
        // Log.d(TAG, "encoding: " + encoding);

        return new WebResourceResponse(
            mimeType, encoding, 
            responseCode, responseMessage,
            responseHeaders, stream
        );
    }

    private String readCookiesData() {
        String ret = "";

        try {
            File file = cordova.getActivity().getFileStreamPath(cookieDataFilename);
            if (file == null || !file.exists()) {
                return ret;
            }
            InputStream inputStream = cordova.getActivity().openFileInput(cookieDataFilename);

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                List<String> strings = new ArrayList<String>();

                while ( (receiveString = bufferedReader.readLine()) != null ) {
                    strings.add(receiveString);
                }

                inputStream.close();
                try {
                    String.class.getMethod("join", new Class[] { CharSequence.class, Iterable.class });
                    ret = String.join(System.lineSeparator(), strings);
                } catch (NoSuchMethodException | SecurityException e) {
                    ret = "";
                    boolean first = true;
                    for (int i = strings.size() - 1; i >= 0; i--) {
                        ret = strings.get(i) + (!first ? System.lineSeparator() + ret : "");
                        first = false;
                    }
                }
            }
        }
        catch (FileNotFoundException e) {
            Log.e(TAG, "File not found: ", e);
        } catch (IOException e) {
            Log.e(TAG, "Can not read file: ", e);
        }

        return ret;
    }

    public void saveCookie(String url, String cookieHeader) {
        try {
            String data = readCookiesData();
            String newData = "";

            if (!data.equals("")) {
                URL thisUrl = new URL(url);
                HttpCookie thisCookie = HttpCookie.parse(cookieHeader).get(0);
                String[] split = data.split(System.lineSeparator() + System.lineSeparator());
                for (int i = 0; i < split.length; i++) {
                    String[] cookieData = split[i].split(System.lineSeparator());
                    
                    URL cookieUrl = new URL(cookieData[0]);
                    HttpCookie actualCookie = HttpCookie.parse(cookieData[1]).get(0);

                    // check if cookie was overwritten
                    if (
                        cookieUrl.getHost().equals(thisUrl.getHost()) &&
                        actualCookie.getName().equals(thisCookie.getName()) &&
                        (
                            (actualCookie.getPath() == null && thisCookie.getPath() == null)?
                            cookieUrl.getPath().equals(thisCookie.getPath()):
                            (actualCookie.getPath() != null && actualCookie.getPath().equals(thisCookie.getPath()))
                        ) &&
                        (
                            (actualCookie.getDomain() == null && thisCookie.getDomain() == null)?
                            true:
                            (actualCookie.getDomain() != null && actualCookie.getDomain().equals(thisCookie.getDomain()))
                        )
                    ) {
                        // Log.d(TAG, "found same cookie: " + actualCookie.getName());
                        continue;
                    }
                    // add not-overwritten cookie to file
                    newData += split[i];
                    if (i < split.length - 1) {
                        // add separation line
                        newData += System.lineSeparator() + System.lineSeparator();
                    }
                }
                if (!newData.equals("")) {
                    // add separation line
                    newData += System.lineSeparator() + System.lineSeparator();
                }
            }
            newData += url + System.lineSeparator() + cookieHeader;

            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(cordova.getActivity().openFileOutput(cookieDataFilename, Context.MODE_PRIVATE));
            outputStreamWriter.write(newData, 0, newData.length());
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e(TAG, "File write failed: ", e);
        } 
    }

    @Override
    public void onStart() {
        android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
        cookieManager.removeAllCookies(null);
        String data = readCookiesData();
        
        // Log.d(TAG, "###");
        // Log.d(TAG, "got cookies");
        // Log.d(TAG, data.replace(System.lineSeparator(), "◘"));
        // Log.d(TAG, "###");
        if (!data.equals("")) {
            String[] split = data.split(System.lineSeparator() + System.lineSeparator());
            for (int i = 0; i < split.length; i++) {
                String[] cookieData = split[i].split(System.lineSeparator());
                if (cookieData.length < 2) {
                    continue;
                }
                cookieManager.setCookie(cookieData[0], cookieData[1]);
            }
        }
    }

}
