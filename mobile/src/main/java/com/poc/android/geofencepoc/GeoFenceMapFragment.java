package com.poc.android.geofencepoc;

import android.annotation.TargetApi;
import android.app.Activity;
import android.database.ContentObserver;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.VisibleRegion;
import com.poc.android.geofencepoc.model.GeoFence;
import com.poc.android.geofencepoc.model.ModelException;

import org.jetbrains.annotations.NotNull;

import static com.poc.android.geofencepoc.contentprovider.GeoFenceContentProvider.GEOFENCE_CONTENT_URI;


/**
 *
 */
public class GeoFenceMapFragment extends Fragment implements
        GoogleMap.OnMapClickListener,
        GoogleMap.OnCameraChangeListener {
    private static final String TAG = "GeoFenceMapFragment";

    private SupportMapFragment mapFragment;
    private GeoFenceContentMapObserver geoFenceContentObserver;
    private Marker currentMarker;

    public GeoFenceMapFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        geoFenceContentObserver = new GeoFenceContentMapObserver(new Handler());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_geo_fence_map, container, false);

        FragmentManager fragmentManager = getChildFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        GoogleMapOptions options = new GoogleMapOptions();
        options.mapType(GoogleMap.MAP_TYPE_NORMAL)
                .compassEnabled(false)
                .rotateGesturesEnabled(false)
                .tiltGesturesEnabled(false);

        mapFragment = SupportMapFragment.newInstance(options);

        fragmentTransaction.replace(R.id.map, mapFragment);
        fragmentTransaction.commit();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        getActivity().getContentResolver().registerContentObserver(GEOFENCE_CONTENT_URI, true, geoFenceContentObserver);

        GoogleMap map = mapFragment.getMap();
        Log.d(TAG, "map:" + map);

        if (map != null) {
            map.setOnMapClickListener(this);
            map.setOnCameraChangeListener(this);

            map.setMyLocationEnabled(true);
        } else {
            Toast.makeText(getActivity().getApplicationContext(), getString(R.string.error_no_google_maps_available), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop()");
        super.onStop();
        getActivity().getContentResolver().unregisterContentObserver(geoFenceContentObserver);
    }

    @Override
    public void onAttach(Activity activity) {
        Log.d(TAG, "onAttach(" + activity + ")");
        super.onAttach(activity);
    }

    @Override
    public void onDetach() {
        Log.d(TAG, "onDetach()");
        super.onDetach();
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume()");
        drawLatestGeoFence();
        super.onResume();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause()");
        super.onPause();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }

    private float calculateMaxDistance(VisibleRegion visibleRegion) {
        android.location.Location farLeft = new android.location.Location("internal");
        android.location.Location nearRight = new android.location.Location("internal");
        farLeft.setLatitude(visibleRegion.farLeft.latitude);
        farLeft.setLongitude(visibleRegion.farLeft.longitude);
        nearRight.setLatitude(visibleRegion.nearRight.latitude);
        nearRight.setLongitude(visibleRegion.nearRight.longitude);

        float distance = farLeft.distanceTo(nearRight);

        Log.d(TAG, "calculateMaxDistance(): screen distance top left to bottom right:" + distance);
        return distance;
    }

    // start GoogleMap Listeners
    @Override
    public void onCameraChange(CameraPosition cameraPosition) {
        Log.d(TAG, "onCameraChange(): zoom:" + cameraPosition.zoom);
        if (mapFragment != null && mapFragment.getMap() != null) {
            float maxDistance = calculateMaxDistance(mapFragment.getMap().getProjection().getVisibleRegion());
            Log.d(TAG, "onCameraChange(): screen distance top left to bottom right:" + maxDistance);
        }
    }


    @Override
    public void onMapClick(LatLng latLng) {
        Log.d(TAG, "onMapClick(" + latLng + ")");
    }
    // end GoogleMap Listeners

    public class GeoFenceContentMapObserver extends ContentObserver {
        private static final String TAG = "GeoFenceContentMapObserver";
        /**
         * Creates a content observer.
         *
         * @param handler The handler to run {@link #onChange} on, or null if none.
         */
        public GeoFenceContentMapObserver(Handler handler) {
            super(handler);
        }

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            Log.d(TAG, "onChange(" + selfChange + ")");

            drawLatestGeoFence();

            super.onChange(selfChange);
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            Log.d(TAG, "onChange(" + selfChange + ", " + uri + ")");
            super.onChange(selfChange, uri);
        }
    }

    private void drawLatestGeoFence() {
        GeoFence latestGeoFence;

        try {
            latestGeoFence = GeoFence.findLatestGeoFence();
        } catch (ModelException e) {
            Log.e(TAG, "unable to update map with latest geofence: " + e.getLocalizedMessage());
            return;
        }

        if (latestGeoFence != null) {
            if (mapFragment != null && mapFragment.getMap() != null) {
                mapFragment.getMap().clear();
                addMarker(latestGeoFence, mapFragment.getMap());
                if (! isCurrentMarkerVisible(mapFragment)) {
                    zoomToCurrentMarker();
                }
            } else {
                Log.e(TAG, "Google map fragment or Google map are null");
            }
        } else {
            Toast.makeText(getActivity(), App.context.getString(R.string.toast_error_latest_geofence_not_found), Toast.LENGTH_LONG).show();
        }

    }

    private void addMarker(GeoFence latestGeoFence, GoogleMap map) {
        LatLng latLng = new LatLng(latestGeoFence.getLatitude(), latestGeoFence.getLongitude());

        currentMarker = map.addMarker(new MarkerOptions()
                .position(latLng)
                .draggable(false)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));


        map.addCircle(new CircleOptions()
                .center(currentMarker.getPosition())
                .radius(latestGeoFence.getRadius())
                .strokeColor(R.color.DimGray)
                .fillColor(R.color.DimGray)
                .strokeWidth(0));
    }

    private void zoomToCurrentMarker() {
        if (currentMarker != null) {
            if (mapFragment != null && mapFragment.getMap() != null) {
                mapFragment.getMap().moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(currentMarker.getPosition().latitude, currentMarker.getPosition().longitude), 13));
//                mapFragment.getMap().animateCamera(CameraUpdateFactory.zoomTo(15), 1000, null);
            }
        }
    }

    private boolean isCurrentMarkerVisible(@NotNull SupportMapFragment mapFrag) {

        GoogleMap map = mapFrag.getMap();
        View fragView = mapFrag.getView();

        if (map == null) {
            Log.d(TAG, "mapFrag.getMap() == null");
            return false;
        }

        if (fragView == null) {
            Log.d(TAG, "mapFrag.getView() == null");
            return false;
        }

        if (currentMarker != null) {
            Projection projection = map.getProjection();
            Point screenPoint = projection.toScreenLocation((currentMarker.getPosition()));
            Log.d(TAG, "screenPoint = " + screenPoint + ", view h/w = " + fragView.getMeasuredHeight() + "/" + fragView.getMeasuredWidth());
            if (screenPoint.x < 0 || screenPoint.y < 0) {
                return false;
            }
            if (fragView.getMeasuredWidth() == 0 || fragView.getMeasuredHeight() == 0) {
                return false;
            }
            if (screenPoint.x > fragView.getMeasuredWidth()  || screenPoint.y > fragView.getMeasuredHeight()) {
                return false;
            }
        } else {
            Log.d(TAG, "currentMarker == null");
            return false;
        }

        return true;
    }
}
