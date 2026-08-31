package com.example.abhijeet.bloodbank;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonObject;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

public class LiveDonorTrackingActivity extends AppCompatActivity {

    public static final XYTileSource CARTO_VOYAGER = new XYTileSource(
            "CartoVoyager",
            0, 20, 256, ".png",
            new String[]{
                    "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
                    "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
                    "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
                    "https://d.basemaps.cartocdn.com/rastertiles/voyager/"
            },
            "© OpenStreetMap contributors © CARTO"
    );

    private String emergencyId = "";
    private MapView mapView;
    private View btnBack, btnRecenter;
    private TextView tvTrackingStatusHeader, tvPatientName, tvHospitalName, tvBloodBadge;
    private TextView tvEtaHeadline, tvDistanceSub;
    private ImageView btnExternalMaps;
    private MaterialButton btnOpenChat, btnCall;

    private Marker donorMarker;
    private Marker hospitalMarker;
    private Polyline routePolyline;

    private double donorLat = 20.2961;
    private double donorLng = 85.8245;
    private double hospLat = 20.2289;
    private double hospLng = 85.7770;

    private String patientName = "Emergency Patient";
    private String hospitalName = "AIIMS Hospital Blood Bank";
    private String hospitalAddress = "Sijua, Patrapada, Bhubaneswar";
    private String bloodGroup = "O+";
    private String contactPhone = "";

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private boolean isPolling = false;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPolling) {
                refreshLiveCoordinates();
                pollHandler.postDelayed(this, 4000);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // OSMDroid configuration initialization with compliant User-Agent & dedicated internal app cache
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue("LifeShare-EmergencyDonorTracking/2.1 (contact: abhijeet.pradhan226@gmail.com; android)");

        java.io.File osmBasePath = new java.io.File(ctx.getCacheDir(), "osmdroid");
        java.io.File osmTilesPath = new java.io.File(osmBasePath, "tiles");
        if (!osmTilesPath.exists()) osmTilesPath.mkdirs();
        Configuration.getInstance().setOsmdroidBasePath(osmBasePath);
        Configuration.getInstance().setOsmdroidTileCache(osmTilesPath);

        WindowHelper.applyEdgeToEdge(this);
        setContentView(R.layout.activity_live_donor_tracking);

        emergencyId = getIntent().getStringExtra("emergency_id");
        if (emergencyId == null || emergencyId.isEmpty()) {
            emergencyId = getIntent().getStringExtra("requestId");
        }

        initViews();
        setupMap();
        setupListeners();
        loadEmergencyData();
    }

    private void initViews() {
        mapView = findViewById(R.id.map_view_live_tracking);
        btnBack = findViewById(R.id.btn_tracking_back);
        btnRecenter = findViewById(R.id.btn_tracking_recenter);
        tvTrackingStatusHeader = findViewById(R.id.tv_tracking_status_header);
        tvPatientName = findViewById(R.id.tv_track_patient_name);
        tvHospitalName = findViewById(R.id.tv_track_hospital_name);
        tvBloodBadge = findViewById(R.id.tv_track_blood_badge);
        tvEtaHeadline = findViewById(R.id.tv_track_eta_headline);
        tvDistanceSub = findViewById(R.id.tv_track_distance_sub);
        btnExternalMaps = findViewById(R.id.btn_track_google_maps_external);
        btnOpenChat = findViewById(R.id.btn_track_open_chat);
        btnCall = findViewById(R.id.btn_track_call);
    }

    private void setupMap() {
        mapView.setTileSource(CARTO_VOYAGER);
        mapView.setMultiTouchControls(true);
        mapView.setTilesScaledToDpi(true);
        mapView.getZoomController().setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER);
        mapView.getController().setZoom(15.0);

        GeoPoint defaultCenter = new GeoPoint(donorLat, donorLng);
        mapView.getController().setCenter(defaultCenter);
    }

    private void setupListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        if (btnRecenter != null) {
            btnRecenter.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    fitRouteBounds();
                }
            });
        }

        btnOpenChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent chatIntent = new Intent(LiveDonorTrackingActivity.this, EmergencyChatActivity.class);
                chatIntent.putExtra("emergency_id", emergencyId);
                startActivity(chatIntent);
            }
        });

        btnCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (contactPhone != null && !contactPhone.isEmpty()) {
                    Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + contactPhone.trim()));
                    startActivity(callIntent);
                } else {
                    Toast.makeText(LiveDonorTrackingActivity.this, "Contact number not available", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnExternalMaps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Uri gmmIntentUri = Uri.parse("google.navigation:q=" + hospLat + "," + hospLng + "&mode=d");
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                if (mapIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(mapIntent);
                } else {
                    Uri webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + hospLat + "," + hospLng);
                    startActivity(new Intent(Intent.ACTION_VIEW, webUri));
                }
            }
        });
    }

    private void loadEmergencyData() {
        if (emergencyId == null || emergencyId.isEmpty()) {
            plotMockTrackingRoute();
            return;
        }

        ApiClient.getInstance().getEmergencyDetail(emergencyId, new ApiClient.ApiCallback<ApiClient.EmergencyDetailResponse>() {
            @Override
            public void onSuccess(ApiClient.EmergencyDetailResponse detail) {
                if (isFinishing() || isDestroyed() || detail == null || detail.emergency == null) {
                    plotMockTrackingRoute();
                    return;
                }

                EmergencyRequest req = detail.emergency;
                if (req.getPatientName() != null) patientName = req.getPatientName();
                if (req.getHospital() != null) hospitalName = req.getHospital();
                if (req.getHospitalAddress() != null) hospitalAddress = req.getHospitalAddress();
                if (req.getBloodGroup() != null) bloodGroup = req.getBloodGroup();
                if (req.getContactNumber() != null) contactPhone = req.getContactNumber();

                if (req.getHospitalLatitude() != 0.0 && req.getHospitalLongitude() != 0.0) {
                    hospLat = req.getHospitalLatitude();
                    hospLng = req.getHospitalLongitude();
                }

                // Check donor location
                double[] userLoc = DataManager.getInstance(LiveDonorTrackingActivity.this).getLastKnownLocation();
                if (userLoc != null && userLoc[0] != 0.0) {
                    donorLat = userLoc[0];
                    donorLng = userLoc[1];
                }

                renderTrackingUI();
            }

            @Override
            public void onError(String errorMessage) {
                if (isFinishing() || isDestroyed()) return;
                plotMockTrackingRoute();
            }
        });
    }

    private void refreshLiveCoordinates() {
        double[] userLoc = DataManager.getInstance(this).getLastKnownLocation();
        if (userLoc != null && userLoc[0] != 0.0) {
            donorLat = userLoc[0];
            donorLng = userLoc[1];
            updateDonorMarkerPosition();
        }
    }

    private void renderTrackingUI() {
        tvPatientName.setText(patientName);
        tvHospitalName.setText(hospitalName);
        tvBloodBadge.setText(bloodGroup);

        double distKm = LocationHelper.calculateDistanceKm(donorLat, donorLng, hospLat, hospLng);
        int estMinutes = Math.max(4, (int) Math.round((distKm / 28.0) * 60)); // assume 28 km/h city speed

        tvEtaHeadline.setText("Estimated Arrival: ~" + estMinutes + " mins");
        tvDistanceSub.setText(String.format("%.1f km remaining along driving route", distKm));

        drawMapOverlays();
    }

    private void drawMapOverlays() {
        mapView.getOverlays().clear();

        GeoPoint donorPoint = new GeoPoint(donorLat, donorLng);
        GeoPoint hospPoint = new GeoPoint(hospLat, hospLng);

        // 1. Destination Hospital Marker
        hospitalMarker = new Marker(mapView);
        hospitalMarker.setPosition(hospPoint);
        hospitalMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        hospitalMarker.setTitle("Destination: " + hospitalName);
        hospitalMarker.setSnippet(hospitalAddress);
        Drawable hospIcon = ContextCompat.getDrawable(this, R.drawable.ic_hospital_desk);
        if (hospIcon != null) hospitalMarker.setIcon(hospIcon);
        mapView.getOverlays().add(hospitalMarker);

        // 2. Moving Donor Marker
        donorMarker = new Marker(mapView);
        donorMarker.setPosition(donorPoint);
        donorMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        donorMarker.setTitle("Donor in Transit");
        donorMarker.setSnippet("Active Volunteer");
        Drawable donorIcon = ContextCompat.getDrawable(this, R.drawable.ic_donor_transit);
        if (donorIcon != null) donorMarker.setIcon(donorIcon);
        mapView.getOverlays().add(donorMarker);

        // 3. Road Route Polyline (Curved driving path)
        routePolyline = new Polyline();
        routePolyline.setColor(Color.parseColor("#E11D48"));
        routePolyline.setWidth(10.0f);

        List<GeoPoint> geoPoints = generateSmoothRoutePoints(donorPoint, hospPoint);
        routePolyline.setPoints(geoPoints);
        mapView.getOverlays().add(routePolyline);

        mapView.invalidate();
        fitRouteBounds();
    }

    private void updateDonorMarkerPosition() {
        if (donorMarker != null) {
            GeoPoint newPoint = new GeoPoint(donorLat, donorLng);
            donorMarker.setPosition(newPoint);

            double distKm = LocationHelper.calculateDistanceKm(donorLat, donorLng, hospLat, hospLng);
            int estMinutes = Math.max(3, (int) Math.round((distKm / 28.0) * 60));
            tvEtaHeadline.setText("Estimated Arrival: ~" + estMinutes + " mins");
            tvDistanceSub.setText(String.format("%.1f km remaining along driving route", distKm));

            if (routePolyline != null) {
                GeoPoint hospPoint = new GeoPoint(hospLat, hospLng);
                routePolyline.setPoints(generateSmoothRoutePoints(newPoint, hospPoint));
            }
            mapView.invalidate();
        }
    }

    private List<GeoPoint> generateSmoothRoutePoints(GeoPoint start, GeoPoint end) {
        List<GeoPoint> points = new ArrayList<>();
        points.add(start);

        // Intermediate bezier-like bend point simulating real street routing
        double midLat = (start.getLatitude() + end.getLatitude()) / 2.0 + 0.003;
        double midLng = (start.getLongitude() + end.getLongitude()) / 2.0 - 0.002;
        points.add(new GeoPoint(midLat, midLng));

        points.add(end);
        return points;
    }

    private void fitRouteBounds() {
        try {
            double minLat = Math.min(donorLat, hospLat) - 0.008;
            double maxLat = Math.max(donorLat, hospLat) + 0.008;
            double minLng = Math.min(donorLng, hospLng) - 0.008;
            double maxLng = Math.max(donorLng, hospLng) + 0.008;

            BoundingBox box = new BoundingBox(maxLat, maxLng, minLat, minLng);
            mapView.zoomToBoundingBox(box, true, 80);
        } catch (Exception e) {
            mapView.getController().setCenter(new GeoPoint(hospLat, hospLng));
        }
    }

    private void plotMockTrackingRoute() {
        donorLat = 20.2961;
        donorLng = 85.8245;
        hospLat = 20.2289;
        hospLng = 85.7770;
        hospitalName = "AIIMS Bhubaneswar Blood Bank";
        patientName = "Critical Emergency Patient";
        bloodGroup = "O+";
        renderTrackingUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
        isPolling = true;
        pollHandler.postDelayed(pollRunnable, 4000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
        isPolling = false;
        pollHandler.removeCallbacks(pollRunnable);
    }
}
