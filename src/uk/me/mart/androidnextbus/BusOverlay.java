
package uk.me.mart.androidnextbus;

import com.google.android.maps.*;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.Log;

public class BusOverlay extends Overlay {

    private static final String LOG_TAG = "BusOverlay";

    static Paint stopOutlinePaint = new Paint();
    static Paint stopFillPaint = new Paint();

    static {
        stopFillPaint.setARGB(255, 10, 10, 255);
        stopFillPaint.setAntiAlias(true);
        stopFillPaint.setFakeBoldText(true);

        stopOutlinePaint.setARGB(255, 255, 255, 255);
        stopOutlinePaint.setAntiAlias(true);
        stopOutlinePaint.setFakeBoldText(true);
    }

    private BusInfo busInfo;

    public BusOverlay(BusInfo busInfo) {
        this.busInfo = busInfo;
    }

    public void draw(Canvas canvas, MapView map, boolean shadow) {

        Projection proj = map.getProjection();

        if (! shadow) {
            // Draw Church and Duboce marker
            //drawStop(canvas, 37769500, -122429170, proj);

            // Figure out our current bounding box
            GeoPoint gp1 = proj.fromPixels(0, 0);
            GeoPoint gp2 = proj.fromPixels(canvas.getWidth() - 1, canvas.getHeight() - 1);

            // Tell the BusInfo instance what we're looking at so that it
            // can request only things that are in view.
            this.busInfo.setBoundingBox(gp1, gp2);

            // Draw the stops we know about.
            java.util.Collection<BusInfo.Stop> stops = busInfo.getStopsInRect(gp1, gp2);
            //for (BusInfo.Stop stop : stops) {
            //    drawStop(canvas, stop.getGeoPoint(), proj);
            //}

            // Draw the vehicles we know about.
            //java.util.Collection<BusInfo.Vehicle> vehicles = busInfo.getVehiclesInRect(gp1, gp2);
            java.util.Collection<BusInfo.Vehicle> vehicles = busInfo.getVehicles();
            for (BusInfo.Vehicle vehicle : vehicles) {
                drawVehicle(canvas, vehicle, proj);
            }
        }
        else {

        }

        super.draw(canvas, map, shadow);

    }

    private void drawStop(Canvas canvas, int lat, int lon, Projection proj) {
        GeoPoint gp = new GeoPoint(lat, lon);
        drawStop(canvas, gp, proj);
    }

    private void drawStop(Canvas canvas, GeoPoint gp, Projection proj) {
        Point p = new Point();
        proj.toPixels(gp, p);
        canvas.drawCircle(p.x, p.y, 3, stopOutlinePaint);
        canvas.drawCircle(p.x, p.y, 2, stopFillPaint);
    }

    private void drawVehicle(Canvas canvas, BusInfo.Vehicle vehicle, Projection proj) {
        canvas.save();
        Point p = new Point();
        GeoPoint gp = vehicle.getGeoPoint();
        proj.toPixels(gp, p);
        canvas.translate(p.x, p.y);
        canvas.rotate(vehicle.getHeading());
        canvas.drawRect(-3, -5, 3, 5, stopFillPaint);
        canvas.drawRect(-3, -5, 3, -2, stopOutlinePaint);
        canvas.restore();
    }

}


