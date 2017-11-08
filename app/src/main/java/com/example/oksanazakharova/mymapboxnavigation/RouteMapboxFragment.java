package com.example.oksanazakharova.mymapboxnavigation;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.Polyline;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.constants.MapboxConstants;
import com.mapbox.mapboxsdk.constants.Style;
import com.mapbox.mapboxsdk.exceptions.InvalidLatLngBoundsException;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerMode;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin;
import com.mapbox.services.Constants;
import com.mapbox.services.android.location.LostLocationEngine;
import com.mapbox.services.android.location.MockLocationEngine;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.ui.v5.voice.NavigationInstructionPlayer;
import com.mapbox.services.android.navigation.v5.milestone.MilestoneEventListener;
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation;
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigationOptions;
import com.mapbox.services.android.navigation.v5.navigation.NavigationConstants;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;
import com.mapbox.services.android.navigation.v5.offroute.OffRouteListener;
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress;
import com.mapbox.services.android.telemetry.location.LocationEngine;
import com.mapbox.services.android.telemetry.location.LocationEngineListener;
import com.mapbox.services.android.telemetry.location.LocationEnginePriority;
import com.mapbox.services.api.directions.v5.DirectionsCriteria;
import com.mapbox.services.api.directions.v5.models.DirectionsResponse;
import com.mapbox.services.api.directions.v5.models.DirectionsRoute;
import com.mapbox.services.commons.geojson.LineString;
import com.mapbox.services.commons.models.Position;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by Oksana Zakharova on 08.11.2017.
 */

public class RouteMapboxFragment extends Fragment implements
        LocationEngineListener,
        MilestoneEventListener,
        ProgressChangeListener,
        OffRouteListener {

    public static final String TAG = "RouteMapboxFragment";
    private static final int CAMERA_ANIMATION_DURATION = 1000;
    public static final float MAPBOX_NAVIGATION_ZOOM = 16.0f;

    private SharedPreferences preferences;

    private Activity mActivity;
    private Context mContext;
    private View rootView;

    private MapView mapView;
    private MapboxMap mapboxMap;

    private TextView adviceTextView;
    private Button goButton;

    private MapboxNavigation navigation;
    private LocationEngine locationEngine;
    private DirectionsRoute currentRoute;

    private ArrayList<LatLng> wayPoints = new ArrayList<LatLng>();

    private ArrayList<LatLng> coordinatesFromRoute = new ArrayList<LatLng>();
    private ArrayList<PolylineOptions> segmentsPolylines = null;
    private List<Polyline> polylines;

    private boolean navigationInProgress = false;

    private Position currentPosition;
    private boolean navType = false; //true - real; false - simulation

    private LocationLayerPlugin locationLayer;
    private NavigationMapRoute mapRoute;

    private NavigationInstructionPlayer instructionPlayer;

    private Marker originMarker;
    private Marker destinationMarker;

    private Boolean isShowWayPoints = true;

    public void setCoordinatesFromRoute(ArrayList<LatLng> coordinatesFromRoute) {
        this.coordinatesFromRoute = coordinatesFromRoute;
    }
    public ArrayList<PolylineOptions> getSegmentsPolylines() {
        return this.segmentsPolylines;
    }

    public void setSegmentsPolylines(ArrayList<PolylineOptions> segmentsPolylines) {
        this.segmentsPolylines = segmentsPolylines;
    }

    public void addSegmentsPolylines(List<PolylineOptions> segmentsPolylines) {
        polylines = mapboxMap.addPolylines(segmentsPolylines);
    }

    public void removeSegmentsPolylines() {
        if (polylines != null) {
            for (Polyline polyline : polylines) {
                polyline.remove();
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = getActivity();
        mContext = mActivity.getApplicationContext();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        rootView = inflater.inflate(R.layout.mapbox_fragment_layout, container, false);

        preferences = mActivity.getSharedPreferences("com.example.oksanazakharova.mymapboxnavigation.SHARED_PREFERENCES", Context.MODE_PRIVATE);

        initUI();
        setWayPoints();

        mapView = (MapView) rootView.findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull MapboxMap mMapboxMap) {
                Log.v(TAG, "onCreateView onMapReady");
                mapboxMap = mMapboxMap;
                mapboxMap.setStyleUrl(Style.MAPBOX_STREETS);

                initLocationEngine();
                initMapRoute();
                initLocationLayer();
                getMapboxRoute();
            }
        });

        //init navigation
        MapboxNavigationOptions options = MapboxNavigationOptions.builder()
                .defaultMilestonesEnabled(true)
                .snapToRoute(true)
                .build();

        navigation = new MapboxNavigation(mContext, Mapbox.getAccessToken(), options);

        instructionPlayer = new NavigationInstructionPlayer(mContext,
                preferences.getString(NavigationConstants.NAVIGATION_VIEW_AWS_POOL_ID, null));

        return rootView;
    }

    private void setWayPoints() {
        wayPoints.add(new LatLng(49.246438435985596, 28.49311541765928));
        wayPoints.add(new LatLng(49.2446296000755, 28.49031113595));
        wayPoints.add(new LatLng(49.2446296000755, 28.49031113595));
        wayPoints.add(new LatLng(49.2449143567711, 28.4942643921636));
        wayPoints.add(new LatLng(49.2471725971224, 28.4904940929168));
        wayPoints.add(new LatLng(49.2470910397683, 28.4927818401806));
        wayPoints.add(new LatLng(49.2419387953855, 28.4955759085423));
        wayPoints.add(new LatLng(49.243875997133, 28.4943227074081));
        wayPoints.add(new LatLng(49.2444253346786, 28.4954037224178));
        wayPoints.add(new LatLng(49.2439445216759, 28.4966493953068));
        wayPoints.add(new LatLng(49.2434489609554, 28.4978935619753));
        wayPoints.add(new LatLng(49.244032489995, 28.4990389139626));
        wayPoints.add(new LatLng(49.244032489995, 28.4990389139626));
        wayPoints.add(new LatLng(49.2450720458962, 28.4989669988525));
        wayPoints.add(new LatLng(49.2450720458962, 28.4989669988525));
        wayPoints.add(new LatLng(49.2449902382194, 28.4965955338299));
        wayPoints.add(new LatLng(49.245624930372, 28.4977746959137));
        wayPoints.add(new LatLng(49.2474632472329, 28.4976291822617));
    }

    private void getMapboxRoute(){
        Position origin = Position.fromCoordinates(wayPoints.get(0).getLongitude(),
                wayPoints.get(0).getLatitude());
        Position destination = Position.fromCoordinates(wayPoints.get(wayPoints.size()-1).getLongitude(),
                wayPoints.get(wayPoints.size()-1).getLatitude());

        NavigationRoute.Builder builder = NavigationRoute.builder()
                .accessToken(Mapbox.getAccessToken())
                .origin(origin)
                .destination(destination)
                .profile(DirectionsCriteria.PROFILE_DRIVING);

        for (LatLng wayPoint : wayPoints) {
            builder.addWaypoint(Position.fromLngLat(wayPoint.getLongitude(), wayPoint.getLatitude()));
        }

        NavigationRoute navigationRoute = builder.build();
        navigationRoute.getRoute(new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                currentRoute = response.body().getRoutes().get(0);
                Log.v("OZakharova", "currentRoute = "+currentRoute);
                mapRoute.addRoute(currentRoute);
                Log.v("OZakharova", "mapRoute = "+mapRoute);

                boundCameraToRoute();
                onRouteMapboxSuccess(currentRoute);
            }

            @Override
            public void onFailure(Call<DirectionsResponse> call, Throwable t) {
                goButton.setEnabled(false);
                Toast.makeText(getActivity(),getString(R.string.mapbox_not_able_create_route),Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void boundCameraToRoute() {
        if (currentRoute != null) {
            List<Position> routeCoords = LineString.fromPolyline(currentRoute.getGeometry(),
                    Constants.PRECISION_6).getCoordinates();
            List<LatLng> bboxPoints = new ArrayList<>();
            for (Position position : routeCoords) {
                bboxPoints.add(new LatLng(position.getLatitude(), position.getLongitude()));
            }
            if (bboxPoints.size() > 1) {
                try {
                    LatLngBounds bounds = new LatLngBounds.Builder().includes(bboxPoints).build();
                    animateCameraBbox(bounds, CAMERA_ANIMATION_DURATION, new int[]{50, 200, 50, 100});
                } catch (InvalidLatLngBoundsException exception) {
                    Toast.makeText(mContext, "Valid route not found.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void animateCameraBbox(LatLngBounds bounds, int animationTime, int[] padding) {
        mapboxMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds,
                padding[0], padding[1], padding[2], padding[3]), animationTime);
    }

    private void animateCamera(Location location) {
        if (navigationInProgress) {
            CameraPosition position = new CameraPosition.Builder()
                    .target(new LatLng(location.getLatitude(), location.getLongitude()))
                    .zoom(MAPBOX_NAVIGATION_ZOOM)
                    .tilt(MapboxConstants.MAXIMUM_TILT)
                    .bearing(location.getBearing())
                    .build();
            mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(position), CAMERA_ANIMATION_DURATION);
        }
    }

    public void initUI() {
        adviceTextView = (TextView) rootView.findViewById(R.id.adviceTextView);

        goButton = (Button) rootView.findViewById(R.id.btn_go);
        goButton.setVisibility(View.VISIBLE);
        goButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launchNavigation();
            }
        });
    }

    public void onRouteMapboxSuccess(DirectionsRoute currentRoute) {

        LineString lineString = LineString.fromPolyline(currentRoute.getGeometry(), Constants.OSRM_PRECISION_V4);
        List<Position> positions = lineString.getCoordinates();

        if(positions != null) {
            ArrayList<LatLng> coordinates = new ArrayList<LatLng>();
            for (Position position : positions) {
                coordinatesFromRoute.add(new LatLng(position.getLatitude(), position.getLongitude()));
            }
            setCoordinatesFromRoute(coordinates);
        }
        showPreview();
    }

    public void showPreview() {
        goButton.setVisibility(View.VISIBLE);
        goButton.setEnabled(true);

        setAnnotationsOnMap(null);
        drawSegmentsPolylines();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        Log.v(TAG, "onResume");
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();

        if (locationLayer != null) {
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            locationLayer.onStart();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        mapView.onStop();

        if (locationLayer != null) {
            locationLayer.onStop();
        }

        if (locationEngine != null) {
            locationEngine.removeLocationEngineListener(this);
            locationEngine.removeLocationUpdates();
            locationEngine.deactivate();
        }

        stopNavigation();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");

        locationEngine.removeLocationUpdates();
        locationEngine.deactivate();

        if (navigation != null) {
            stopNavigation();
        }
        mapView.onDestroy();
    }

    private void initLocationEngine() {
        if(navType)
            locationEngine = LostLocationEngine.getLocationEngine(mContext);
        else
            locationEngine = new MockLocationEngine();

        mapboxMap.setLocationSource(locationEngine);

        locationEngine.addLocationEngineListener(this);

        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (locationEngine.getLastLocation() != null) {

            Location lastLocation = locationEngine.getLastLocation();
            currentPosition = Position.fromCoordinates(lastLocation.getLongitude(), lastLocation.getLatitude());
        }
    }

    private void initLocationLayer() {
        locationLayer = new LocationLayerPlugin(mapView, mapboxMap, locationEngine);
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationLayer.setLocationLayerEnabled(LocationLayerMode.NAVIGATION);
    }

    private void initMapRoute() {
        mapRoute = new NavigationMapRoute(navigation, mapView, mapboxMap);//, R.style.NavigationMapRoute);
    }

    private void launchNavigation() {
        Log.v(TAG, "launchNavigation currentRoute = " + currentRoute);

        if (navigation !=null && currentRoute != null) {
            navigation.addProgressChangeListener(this);
            navigation.addOffRouteListener(this);
            navigation.addMilestoneEventListener(this);

            // Adjust location engine to force a gps reading every second. This isn't required but gives an overall
            // better navigation experience for users. The updating only occurs if the user moves 3 meters or further
            // from the last update.
            locationEngine.setInterval(0);
            locationEngine.setPriority(LocationEnginePriority.HIGH_ACCURACY);
            locationEngine.setFastestInterval(1000);
            locationEngine.activate();

            if(!navType)
                ((MockLocationEngine) locationEngine).setRoute(currentRoute);

            navigation.setLocationEngine(locationEngine);

            navigation.startNavigation(currentRoute);

            navigationInProgress = true;
        }
    }

    public void stopNavigation() {

        navigationInProgress = false;
        // Remove all navigation listeners
        navigation.removeProgressChangeListener(this);
        navigation.removeOffRouteListener(this);
        navigation.removeMilestoneEventListener(this);

        // End the navigation session
        navigation.endNavigation();
        navigation.onDestroy();
    }

    public void setAnnotationsOnMap(LatLng originCoordinate) {
        if ( wayPoints != null && wayPoints.size() > 0 ) {
            if(originCoordinate == null){
                originCoordinate =  new LatLng(wayPoints.get(0).getLatitude(), wayPoints.get(0).getLongitude());
            }

            //set start marker
            originMarker  =  mapboxMap.addMarker(new MarkerOptions()
                    .position(originCoordinate)
                    .icon(IconFactory.getInstance(mContext).fromResource(R.mipmap.car))
                    .title("Start point"));

            //set finish marker
            destinationMarker = mapboxMap.addMarker(new MarkerOptions()
                    .position(new LatLng(wayPoints.get(wayPoints.size()-1).getLatitude(), wayPoints.get(wayPoints.size()-1).getLongitude()))
                    .title("Destination")
                    .icon(IconFactory.getInstance(mContext).fromResource(R.mipmap.flag_red)));
        }

        if (isShowWayPoints ) {
            int i = 1;
            for (LatLng wayPoint : wayPoints) {
                mapboxMap.addMarker(new MarkerOptions()
                        .position(wayPoint)
                        .title("Waypoint id =" + i + " \n" + wayPoint.getLatitude() + ", " + wayPoint.getLongitude())
                        .icon(IconFactory.getInstance(mContext).fromResource(R.mipmap.marker))
                );
                i++;
            }
        }
    }

    @Override
    public void onMilestoneEvent(RouteProgress routeProgress, String instruction, int identifier) {
        instructionPlayer.play(instruction);
        adviceTextView.setText(instruction);
        Log.v(TAG, "onMilestoneEvent instruction = " + instruction);
    }

    @Override
    public void onConnected() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationEngine.requestLocationUpdates();

        if (locationEngine.getLastLocation() != null) {
            Location lastLocation = locationEngine.getLastLocation();
            Log.v(TAG, "onConnected lastLocation = "+lastLocation);
            currentPosition = Position.fromCoordinates(lastLocation.getLongitude(), lastLocation.getLatitude());
            Log.v(TAG, "onConnected currentPosition = "+currentPosition);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
    }

    @Override
    public void onProgressChange(Location currentLocation, RouteProgress routeProgress) {
        Log.v(TAG, "onProgressChange currentLocation = " + currentLocation.toString());

        currentPosition = Position.fromCoordinates(currentLocation.getLongitude(), currentLocation.getLatitude());

        animateCamera(currentLocation);
        locationLayer.forceLocationUpdate(currentLocation);
    }

    @Override
    public void userOffRoute(final Location location) {
        Log.v(TAG, "userOffRoute");
        Toast.makeText(getActivity(), getString(R.string.rerouting), Toast.LENGTH_LONG).show();

        Position newOrigin = Position.fromCoordinates(location.getLatitude(),
                location.getLongitude());
        Position newDestination = Position.fromCoordinates(wayPoints.get(wayPoints.size()-1).getLatitude(),
                wayPoints.get(wayPoints.size()-1).getLongitude());

        NavigationRoute.Builder builder = NavigationRoute.builder()
                .accessToken(Mapbox.getAccessToken())
                .origin(newOrigin)
                .destination(newDestination)
                .profile(DirectionsCriteria.PROFILE_DRIVING);

        for (LatLng wayPoint : wayPoints) {
            builder.addWaypoint(Position.fromLngLat(wayPoint.getLongitude(), wayPoint.getLatitude()));
        }

        NavigationRoute navigationRoute = builder.build();
        navigationRoute.getRoute(new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                removeSegmentsPolylines();

                currentRoute = response.body().getRoutes().get(0);
                mapRoute.addRoute(currentRoute);

                drawSegmentsPolylines();
                Log.v(TAG, "userOffRoute REROUTE currentRoute = " + currentRoute);
            }

            @Override
            public void onFailure(Call<DirectionsResponse> call, Throwable t) {
                Toast.makeText(getActivity(),getString(R.string.mapbox_not_able_create_route),Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void drawSegmentsPolylines() {
        List<PolylineOptions> polylines = getSegmentsPolylines();
        if (polylines != null && !polylines.isEmpty()) {
            addSegmentsPolylines(polylines);
        }
    }

}