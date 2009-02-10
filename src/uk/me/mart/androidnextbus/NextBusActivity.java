package uk.me.mart.androidnextbus;

import android.app.Activity;
import android.os.Bundle;
import com.google.android.maps.*;
import android.util.Log;

public class NextBusActivity extends MapActivity {

    private static final String LOG_TAG = "NextBusActivity";

    private BusInfo busInfo;
    private MapView map;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        map = (MapView)this.findViewById(R.id.map);
        map.setSatellite(false);

        busInfo = new BusInfo();
        busInfo.addRoute("22");
        busInfo.addRoute("J");
        busInfo.addRoute("KT");
        busInfo.addRoute("L");
        busInfo.addRoute("M");
        busInfo.addRoute("N");
        busInfo.addRoute("F");
        busInfo.addRoute("6");
        busInfo.addRoute("7");
        busInfo.addRoute("71");
        busInfo.startSession();

        MyLocationOverlay locationOverlay = new MyLocationOverlay(this, map);
        map.getOverlays().add(locationOverlay);
        map.getOverlays().add(new BusOverlay(busInfo));

        MapController mapctrl = map.getController();
        //mapctrl.setCenter(new GeoPoint(37779300, -122419200));
        mapctrl.setCenter(new GeoPoint(37772479, -122427220));
        mapctrl.setZoom(16);

        map.invalidate();

        busInfo.addUpdateListener(new BusInfo.UpdateListener() {
            public void dataUpdated(BusInfo busInfo) {
                Log.d(LOG_TAG, "BusInfo data updated");
                map.invalidate();
            }
        });

    }

    public boolean isRouteDisplayed() {
        return false;
    }
}
