
package uk.me.mart.androidnextbus;

import android.util.Log;
import java.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.net.URLEncoder;
import com.google.android.maps.GeoPoint;
import android.os.Handler;

public class BusInfo {

    private static final String LOG_TAG = "BusInfo";
    private static final String MAIN_PAGE_URL = "http://www.nextmuni.com/googleMap/googleMap.jsp?a=sf-muni";

    private static Timer timer = new Timer();

    private Map<String,Route> routes = new HashMap<String,Route>();
    private boolean sessionStarted = false;

    // This is all stops we know for all routes
    private Collection<Stop> stops = new HashSet<Stop>();
    private GeoMap<Stop> geoStops = new GeoMap<Stop>();
    private GeoMap<Vehicle> geoVehicles = new GeoMap<Vehicle>();

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

    public Collection<Vehicle> getVehiclesInRect(GeoPoint p1, GeoPoint p2) {
        return this.geoVehicles.getInRect(p1, p2);
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
        private VehicleLocationFetcher vlf = null;

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

                        // Start polling for vehicle locations
                        Log.d(LOG_TAG, "Completed initialization for route "+number);
                        vlf = new VehicleLocationFetcher();
                    }
                }
            });
        }

        private String getConfigUrl() {
            return "http://www.nextmuni.com/s/COM.NextBus.Servlets.XMLFeed?command=routeConfig&a=sf-muni&r="+eurl(number);
        }

        private class VehicleLocationFetcher implements DataFetcher.Callback, Runnable {
            private String lastTime = "1";
            private boolean requestInProgress = false;
            private String url = null;
            private Handler handler = null;
            private TimerTask timerTask = null;

            public VehicleLocationFetcher() {
                Log.d(LOG_TAG, "Starting a location fetcher for route "+number);
                handler = new Handler();

                timerTask = new TimerTask() {
                    public void run() {
                        handler.post(VehicleLocationFetcher.this);
                    }
                };

                timer.scheduleAtFixedRate(timerTask, 2000, 15000);

                this.url = "http://www.nextmuni.com/s/COM.NextBus.Servlets.XMLFeed?command=vehicleLocations&a=sf-muni&r="+eurl(number)+"&t=";
            }

            public void cb(org.w3c.dom.Document doc) {

                if (doc != null) {

                    Log.d(LOG_TAG, "Retrieved bus location information for route "+number);

                    NodeList vnl = doc.getElementsByTagName("vehicle");
                    int vehicleCount = vnl.getLength();
                    for (int i = 0; i < vehicleCount; i++) {
                        try {
                            Element vehicleElem = (Element)vnl.item(i);

                            // If leadingVehicleId is present then we have
                            // the second car of a two-car train, so let's ignore it.
                            if (! vehicleElem.getAttribute("leadingVehicleId").equals("")) {
                                continue;
                            }

                            // Also ignore it if it's not marked as "predictable"
                            if (vehicleElem.getAttribute("leadingVehicleId").equals("false")) {
                                continue;
                            }

                            String latitude = vehicleElem.getAttribute("lat");
                            String longitude = vehicleElem.getAttribute("lon");
                            String id = vehicleElem.getAttribute("id");
                            String routeNumber = vehicleElem.getAttribute("routeTag");
                            String secsSinceLastReport = vehicleElem.getAttribute("secsSinceReport");
                            String heading = vehicleElem.getAttribute("heading");

                            Vehicle vehicle = new Vehicle(
                                Integer.parseInt(id),
                                routeNumber,
                                (int)(Float.parseFloat(latitude)*1E6),
                                (int)(Float.parseFloat(longitude)*1E6),
                                (int)(Integer.parseInt(heading)),
                                (int)(Integer.parseInt(secsSinceLastReport))
                            );

                            geoVehicles.put(vehicle.getGeoPoint(), vehicle);
                            Log.d(LOG_TAG, "Got location update for "+vehicle);
                        }
                        catch (NumberFormatException ex) {
                            // One of the attributes was missing or didn't contain
                            // a number, I guess. Just ignore it.
                        }
                    }

                    NodeList ltnl = doc.getElementsByTagName("lastTime");
                    if (ltnl.getLength() > 0) {
                        String lastTime = ((Element)ltnl.item(0)).getAttribute("time");
                        if (lastTime != null) {
                            this.lastTime = lastTime;
                        }
                    }

                    if (vehicleCount > 0) {
                        fireUpdateNotification();
                    }

                    requestInProgress = false;
                }
                else {
                    Log.d(LOG_TAG, "Failed to retrieve location information for route "+number);
                }
            }

            public void run() {

                // Don't allow two requests to run concurrently
                if (requestInProgress) {
                    Log.d(LOG_TAG, "Not polling for locations on route "+number+" because a request is already in progress");
                    return;
                }

                Log.d(LOG_TAG, "Polling for bus locations on route "+number);
                Log.d(LOG_TAG, "Request URL is "+this.getNextRequestURL());

                requestInProgress = true;
                DataFetcher.fetch(this.getNextRequestURL(), this);

            }

            private String getNextRequestURL() {
                return this.url+lastTime;
            }

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

    public class Vehicle {
        private int id;
        private int latitude;
        private int longitude;
        private int secsSinceReport;
        private int heading;
        private String routeNumber;

        private Vehicle(int id, String routeNumber, int latitude, int longitude, int heading, int secsSinceReport) {
            this.id = id;
            this.latitude = latitude;
            this.longitude = longitude;
            this.heading = heading;
            this.secsSinceReport = secsSinceReport;
            this.routeNumber = routeNumber;
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

        public int getHeading() {
            return this.heading;
        }

        public int getSecsSinceReport() {
            return this.secsSinceReport;
        }

        public Route getRoute() {
            return routes.get(this.routeNumber);
        }

        public int hashCode() {
            return id;
        }

        public GeoPoint getGeoPoint() {
            return new GeoPoint(this.latitude, this.longitude);
        }

        public String toString() {
            return this.routeNumber+"("+this.getGeoPoint().toString()+")";
        }

    }

    private static String eurl(String in) {
        return java.net.URLEncoder.encode(in);
    }

    public interface UpdateListener {
        public void dataUpdated(BusInfo busInfo);
    }

}
