
package uk.me.mart.androidnextbus;

import android.util.Log;
import java.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.net.URLEncoder;
import com.google.android.maps.GeoPoint;

public class BusInfo {

    private static final String LOG_TAG = "BusInfo";
    private static final String MAIN_PAGE_URL = "http://www.nextmuni.com/googleMap/googleMap.jsp?a=sf-muni";

    private Map<String,Route> routes = new HashMap<String,Route>();
    private boolean sessionStarted = false;

    // This is all stops we know for all routes
    private Collection<Stop> stops = new HashSet<Stop>();
    private GeoMap<Stop> geoStops = new GeoMap<Stop>();

    private Collection<UpdateListener> listeners = new HashSet<UpdateListener>();

    public void startSession() {
        // Kick things off with a request to the main Google Map page
        // so that we have a session cookie for our subsequent requests.
        DataFetcher.fetch(MAIN_PAGE_URL, new DataFetcher.Callback() {
            public void cb(Document doc) {
                Log.d(LOG_TAG, "Started a session with NextBus");
                sessionStarted = true;
                initializeInitialRoutes();
                fireUpdateNotification();
            }
        });
    }

    public Route addRoute(String number) {
        Route route = new Route(number);
        if (sessionStarted) route.initialize();
        routes.put(number, route);
        return route;
    }

    private void initializeInitialRoutes() {
        for (Route route : routes.values()) {
            route.initialize();
        }
    }

    public Collection<Stop> getStops() {
        return this.stops;
    }

    public Collection<Stop> getStopsInRect(GeoPoint p1, GeoPoint p2) {
        return this.geoStops.getInRect(p1, p2);
    }

    public void addUpdateListener(UpdateListener listener) {
        listeners.add(listener);
    }

    private void fireUpdateNotification() {
        for (UpdateListener listener : listeners) {
            listener.dataUpdated(this);
        }
    }

    public class Route {
        private String number;
        private String name;
        private Collection<Stop> stops = new HashSet<Stop>();
        private Map<String,String> directionNames = new HashMap<String,String>();

        public Route(String number) {
            this.number = number;
        }

        public String getNumber() {
            return number;
        }

        public String getName() {
            return name;
        }

        public String getDirectionNameByCode(String code) {
            return directionNames.get(code);
        }

        public void initialize() {
            Log.d(LOG_TAG, "Doing initialization for route "+number);

            DataFetcher.fetch(this.getConfigUrl(), new DataFetcher.Callback() {
                public void cb(org.w3c.dom.Document doc) {
                    Log.d(LOG_TAG, "Retrieved route information for "+getNumber());

                    NodeList rnl = doc.getElementsByTagName("route");
                    if (rnl.getLength() > 0) {
                        Element routeElem = (Element)rnl.item(0);
                        name = routeElem.getAttribute("title");

                        NodeList snl = doc.getElementsByTagName("stop");
                        int stopCount = snl.getLength();
                        for (int i = 0; i < stopCount; i++) {
                            Element stopElem = (Element)snl.item(i);
                            String latitude = stopElem.getAttribute("lat");
                            String longitude = stopElem.getAttribute("lon");
                            String id = stopElem.getAttribute("tag");

                            // NextBus uses the same element name for the list
                            // of stops for a direction, but we don't care about
                            // that here, so stop looping as soon as we hit one
                            // of these.
                            if (latitude.equals("")) break;

                            Stop stop = new Stop(
                                Integer.parseInt(id),
                                (int)(Float.parseFloat(latitude)*1E6),
                                (int)(Float.parseFloat(longitude)*1E6)
                            );
                            stops.add(stop);
                            BusInfo.this.stops.add(stop);
                            BusInfo.this.geoStops.put(stop.getGeoPoint(), stop);
                        }
                        fireUpdateNotification();
                    }
                }
            });
        }

        private String getConfigUrl() {
            return "http://www.nextmuni.com/s/COM.NextBus.Servlets.XMLFeed?command=routeConfig&a=sf-muni&r="+eurl(number);
        }

    }

    public class Stop {
        private int id;
        private int latitude;
        private int longitude;

        private Stop(int id, int latitude, int longitude) {
            this.id = id;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public int getId() {
            return this.id;
        }

        public int getLatitude() {
            return this.latitude;
        }

        public int getLongitude() {
            return this.longitude;
        }

        public int hashCode() {
            return id;
        }

        public GeoPoint getGeoPoint() {
            return new GeoPoint(this.latitude, this.longitude);
        }

        public String toString() {
            return this.getId()+"("+this.getGeoPoint().toString()+")";
        }
    }

    private static String eurl(String in) {
        return java.net.URLEncoder.encode(in);
    }

    public interface UpdateListener {
        public void dataUpdated(BusInfo busInfo);
    }

}
