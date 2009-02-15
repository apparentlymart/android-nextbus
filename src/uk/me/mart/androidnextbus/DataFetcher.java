
package uk.me.mart.androidnextbus;

import android.os.Handler;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.HttpResponse;
import android.util.Log;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.client.CookieStore;
import org.apache.http.impl.client.BasicCookieStore;
import org.json.*;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;

public abstract class DataFetcher {

    private static final String LOG_TAG = "BusInfo";

    private static CookieStore cookies = new BasicCookieStore();

    public static void fetch(String url, Callback cb) {
        final Handler handler = new Handler();

        Log.d(LOG_TAG, "Starting a runthread to fetch "+url);

        new RunThread(handler, url, cb, cookies).start();
    }

    public interface Callback {
        public void cb(JSONObject obj);
    }

    private static class RunThread extends Thread {
        private Handler handler;
        private String url;
        private Callback cb;
        private CookieStore cookies;

        public RunThread(Handler handler, String url, Callback cb, CookieStore cookies) {
            this.handler = handler;
            this.url = url;
            this.cb = cb;
            this.cookies = cookies;
        }

        public void run() {
            Log.d(LOG_TAG, "Starting request to "+url);
            HttpUriRequest req = new HttpGet(url);
            HttpResponse res;
            try {
                AbstractHttpClient client = new DefaultHttpClient();
                client.setCookieStore(this.cookies);
                org.apache.http.client.params.HttpClientParams.setCookiePolicy(
                    client.getParams(),
                    CookiePolicy.RFC_2109
                );

                res = client.execute(req);
            }
            catch (java.io.IOException ex) {
                Log.d(LOG_TAG, "HTTP request failed: "+ex.getMessage());
                res = null;
            }

            JSONObject obj = null;

            if (res != null) {
                try {
                    String jsonData = readStream(res.getEntity().getContent());
                    if (jsonData != null) {
                        try {
                            if (jsonData.charAt(0) == '[') {
                                // It's an array
                                // Since our callback can only deal in objects, we make an
                                // object with a single element called 'list' containing
                                // the list.

                                JSONArray arr = new JSONArray(jsonData);
                                obj = new JSONObject();
                                obj.put("list", arr);
                            }
                            else {
                                // It's an object
                                obj = new JSONObject(jsonData);
                            }
                        }
                        catch (JSONException ex) {
                            Log.d(LOG_TAG, "JSON parsing failed: "+ex.getMessage());
                            // Don't care. obj is null.
                        }
                    }
                    else {
                        Log.d(LOG_TAG, "Failure to read response body");
                    }
                }
                catch (java.io.IOException ex) {
                    Log.d(LOG_TAG, "Failed to read response body: "+ex.getMessage());
                    // Don't care.
                }
            }

            handler.post(new RunCallback(obj, cb));
        }
    }

    private static String readStream(InputStream is) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        }
        catch (IOException e) {
            return null;
        }
        finally {
            try {
                is.close();
            }
            catch (IOException e) {
                // Don't care
            }
        }
        return sb.toString();
    }

    private static class RunCallback implements Runnable {
        private JSONObject obj;
        private Callback cb;

        public RunCallback(JSONObject obj, Callback cb) {
            this.obj = obj;
            this.cb = cb;
        }

        public void run() {
            Log.d(LOG_TAG, "Completed request");
            this.cb.cb(this.obj);
        }
    }

}
