
package uk.me.mart.androidnextbus;

import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
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

public abstract class DataFetcher {

    private static final String LOG_TAG = "BusInfo";

    private static CookieStore cookies = new BasicCookieStore();

    public static void fetch(String url, Callback cb) {
        final Handler handler = new Handler();

        new RunThread(handler, url, cb, cookies).start();
    }

    public interface Callback {
        public void cb(Document doc);
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
                res = null;
            }

            Document doc = null;

            if (res != null) {
                // I hate Java.
                try  {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder parser = factory.newDocumentBuilder();
                    doc = parser.parse(res.getEntity().getContent());
                }
                catch (javax.xml.parsers.ParserConfigurationException ex) {
                    throw new java.lang.RuntimeException("Failed to create an XML parser");
                }
                catch (java.io.IOException ex) {
                    doc = null;
                }
                catch (org.xml.sax.SAXException ex) {
                    doc = null;
                }
            }

            handler.post(new RunCallback(doc, cb));
        }
    }

    private static class RunCallback implements Runnable {
        private Document doc;
        private Callback cb;

        public RunCallback(Document doc, Callback cb) {
            this.doc = doc;
            this.cb = cb;
        }

        public void run() {
            Log.d(LOG_TAG, "Completed request");
            this.cb.cb(this.doc);
        }
    }

}
