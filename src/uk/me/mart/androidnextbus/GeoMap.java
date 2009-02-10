
package uk.me.mart.androidnextbus;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import com.google.android.maps.GeoPoint;
import android.util.Log;

public class GeoMap<T> {

    private static final int ZONE_SIZE = 500;
    private static final String LOG_TAG = "GeoMap";

    Map<GeoPoint,List<T>> coordMap = new HashMap<GeoPoint,List<T>>();
    Map<T,GeoPoint> objMap = new HashMap<T,GeoPoint>();

    public GeoMap() {}

    public void put(GeoPoint pt, T obj) {
        GeoPoint topLeft = new GeoPoint(bucketize(pt.getLatitudeE6()), bucketize(pt.getLongitudeE6()));

        if (objMap.containsKey(obj)) {
            GeoPoint currentPoint = objMap.get(obj);

            if (currentPoint.equals(topLeft)) {
                Log.d(LOG_TAG, "The item is already in the right bucket. Nothing to do.");
                // Already in the right bucket, so return.
                return;
            }
            else {
                Log.d(LOG_TAG, "Moving item from "+currentPoint+" to "+topLeft);
                // Need to remove it from the old bucket.
                List<T> list = coordMap.get(topLeft);
                if (list != null) {
                    list.remove(obj);
                    if (list.size() == 0) {
                        // Remove the list to shrink the map
                        coordMap.remove(topLeft);
                    }
                }
                else {
                    // This should never happen, but if it does then we don't really care.
                }
            }
        }

        objMap.put(obj, topLeft);
        List<T> list = coordMap.get(topLeft);

        if (list == null) {
            list = new ArrayList<T>();
            coordMap.put(topLeft, list);
        }

        list.add(obj);
    }

    public void put(int lat, int lng, T obj) {
        this.put(new GeoPoint(lat, lng), obj);
    }

    public List<T> getInRect(GeoPoint p1, GeoPoint p2) {
        List<T> ret = new ArrayList<T>();

        int lat1 = bucketize(p1.getLatitudeE6());
        int lon1 = bucketize(p1.getLongitudeE6());
        int lat2 = bucketize(p2.getLatitudeE6());
        int lon2 = bucketize(p2.getLongitudeE6());

        int count = 0;

        for (int lat = lat2; lat <= lat1; lat += ZONE_SIZE) {
            for (int lon = lon1; lon <= lon2; lon += ZONE_SIZE) {
                GeoPoint pt = new GeoPoint(lat, lon);
                List<T> zoneItems = coordMap.get(pt);
                if (zoneItems != null) {
                    ret.addAll(zoneItems);
                }
            }
        }

        return ret;
    }

    public void remove(T obj) {
        GeoPoint topLeft = objMap.get(obj);
        List<T> list = coordMap.get(topLeft);
        if (list != null) {
            list.remove(obj);
            objMap.remove(obj);
            if (list.size() == 0) {
                coordMap.remove(topLeft);
            }
        }
        else {
            // This should never happen, but if it does then we don't really care.
        }
    }

    private int bucketize(int coord) {
        return (coord / ZONE_SIZE) * ZONE_SIZE;
    }

}
