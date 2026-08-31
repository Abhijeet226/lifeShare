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

    public static final XYTileSource HUMANITARIAN_HOT = new XYTileSource(
            "HumanitarianHOT",
            0, 19, 256, ".png",
            new String[]{
                    "https://a.tile.openstreetmap.fr/hot/",
                    "https://b.tile.openstreetmap.fr/hot/",
                    "https://c.tile.openstreetmap.fr/hot/"
            },
            "© OpenStreetMap contributors, Humanitarian OpenStreetMap Team"
    );

    private String emergencyId = "";
    private MapView mapView;
    private View btnBack, btnRecenter;
    private TextView tvTrackingStatusHeader, tvPatientName, tvHospitalName, tvBloodBadge;
    private TextView tvEtaHeadline, tvDistanceSub;
    private ImageView btnExternalMaps;
    private MaterialButton btnOpenChat, btnCall;

    private final List<ApiClient.DonorTrackInfo> activeDonors = new ArrayList<>();

    private Marker hospitalMarker;
    private final List<Marker> donorMarkers = new ArrayList<>();
    private final List<Polyline> routePolylines = new ArrayList<>();

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
        mapView.setTileSource(HUMANITARIAN_HOT);
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

        ApiClient.getInstance().getEmergencyTracking(emergencyId, new ApiClient.ApiCallback<ApiClient.EmergencyTrackingResponse>() {
            @Override
            public void onSuccess(ApiClient.EmergencyTrackingResponse tracking) {
                if (isFinishing() || isDestroyed() || tracking == null) {
                    plotMockTrackingRoute();
                    return;
                }

                if (tracking.patientName != null) patientName = tracking.patientName;
                if (tracking.hospital != null) hospitalName = tracking.hospital;
                if (tracking.hospitalAddress != null) hospitalAddress = tracking.hospitalAddress;
                if (tracking.hospitalLat != 0.0 && tracking.hospitalLng != 0.0) {
                    hospLat = tracking.hospitalLat;
                    hospLng = tracking.hospitalLng;
                }

                activeDonors.clear();
                if (tracking.donors != null && !tracking.donors.isEmpty()) {
                    activeDonors.addAll(tracking.donors);
                    if (!activeDonors.isEmpty()) {
                        ApiClient.DonorTrackInfo first = activeDonors.get(0);
                        donorLat = first.latitude;
                        donorLng = first.longitude;
                        if (first.bloodGroup != null) bloodGroup = first.bloodGroup;
                        if (first.phone != null && !first.phone.isEmpty()) contactPhone = first.phone;
                    }
                } else {
                    // Fallback to local user location if donor
                    double[] userLoc = DataManager.getInstance(LiveDonorTrackingActivity.this).getLastKnownLocation();
                    if (userLoc != null && userLoc[0] != 0.0) {
                        donorLat = userLoc[0];
                        donorLng = userLoc[1];
                    }
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
        if (emergencyId != null && !emergencyId.isEmpty()) {
            ApiClient.getInstance().getEmergencyTracking(emergencyId, new ApiClient.ApiCallback<ApiClient.EmergencyTrackingResponse>() {
                @Override
                public void onSuccess(ApiClient.EmergencyTrackingResponse tracking) {
                    if (isFinishing() || isDestroyed() || tracking == null) return;
                    if (tracking.donors != null && !tracking.donors.isEmpty()) {
                        activeDonors.clear();
                        activeDonors.addAll(tracking.donors);
                        drawMapOverlays();
                    }
                }

                @Override
                public void onError(String errorMessage) {}
            });
        }
    }

    private void renderTrackingUI() {
        tvPatientName.setText(patientName);
        tvHospitalName.setText(hospitalName);
        tvBloodBadge.setText(bloodGroup);

        if (activeDonors.size() > 1) {
            int minEta = 999;
            for (ApiClient.DonorTrackInfo d : activeDonors) {
                if (d.etaMinutes < minEta && d.etaMinutes > 0) minEta = d.etaMinutes;
            }
            if (minEta == 999) minEta = 15;
            if (tvTrackingStatusHeader != null) {
                tvTrackingStatusHeader.setText("Fleet Radar (" + activeDonors.size() + " Donors Active)");
            }
            tvEtaHeadline.setText(activeDonors.size() + " Donors En Route • Next ETA ~" + minEta + " mins");
            tvDistanceSub.setText("All responding voluntary donors converging on " + hospitalName);
        } else {
            double distKm = LocationHelper.calculateDistanceKm(donorLat, donorLng, hospLat, hospLng);
            int estMinutes = Math.max(4, (int) Math.round((distKm / 28.0) * 60)); // assume 28 km/h city speed
            if (tvTrackingStatusHeader != null) {
                tvTrackingStatusHeader.setText("Live Transit Tracker");
            }
            tvEtaHeadline.setText("Estimated Arrival: ~" + estMinutes + " mins");
            tvDistanceSub.setText(String.format("%.1f km remaining along driving route", distKm));
        }

        drawMapOverlays();
    }

    private void drawMapOverlays() {
        mapView.getOverlays().clear();
        donorMarkers.clear();
        routePolylines.clear();

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

        // 2. Render Donors (Fleet or Single)
        if (!activeDonors.isEmpty()) {
            for (int i = 0; i < activeDonors.size(); i++) {
                ApiClient.DonorTrackInfo d = activeDonors.get(i);
                GeoPoint dPoint = new GeoPoint(d.latitude, d.longitude);

                Marker dMarker = new Marker(mapView);
                dMarker.setPosition(dPoint);
                dMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                dMarker.setTitle(d.name + " (" + d.bloodGroup + ")");
                dMarker.setSnippet("Status: " + d.journeyStatusDisplay + " • ETA: ~" + d.etaMinutes + " mins");
                Drawable donorIcon = ContextCompat.getDrawable(this, R.drawable.ic_donor_transit);
                if (donorIcon != null) dMarker.setIcon(donorIcon);
                donorMarkers.add(dMarker);
                mapView.getOverlays().add(dMarker);

                // Polyline path
                Polyline line = new Polyline();
                line.setColor(i == 0 ? Color.parseColor("#E11D48") : (i == 1 ? Color.parseColor("#0284C7") : Color.parseColor("#16A34A")));
                line.setWidth(10.0f);
                line.setPoints(generateSmoothRoutePoints(dPoint, hospPoint));
                routePolylines.add(line);
                mapView.getOverlays().add(line);
            }
        } else {
            GeoPoint donorPoint = new GeoPoint(donorLat, donorLng);
            Marker singleMarker = new Marker(mapView);
            singleMarker.setPosition(donorPoint);
            singleMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            singleMarker.setTitle("Donor in Transit");
            singleMarker.setSnippet("Active Volunteer");
            Drawable donorIcon = ContextCompat.getDrawable(this, R.drawable.ic_donor_transit);
            if (donorIcon != null) singleMarker.setIcon(donorIcon);
            mapView.getOverlays().add(singleMarker);

            Polyline routePolyline = new Polyline();
            routePolyline.setColor(Color.parseColor("#E11D48"));
            routePolyline.setWidth(10.0f);
            routePolyline.setPoints(generateSmoothRoutePoints(donorPoint, hospPoint));
            mapView.getOverlays().add(routePolyline);
        }

        mapView.invalidate();
        fitRouteBounds();
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
            double minLat = hospLat;
            double maxLat = hospLat;
            double minLng = hospLng;
            double maxLng = hospLng;

            if (!activeDonors.isEmpty()) {
                for (ApiClient.DonorTrackInfo d : activeDonors) {
                    minLat = Math.min(minLat, d.latitude);
                    maxLat = Math.max(maxLat, d.latitude);
                    minLng = Math.min(minLng, d.longitude);
                    maxLng = Math.max(maxLng, d.longitude);
                }
            } else {
                minLat = Math.min(minLat, donorLat);
                maxLat = Math.max(maxLat, donorLat);
                minLng = Math.min(minLng, donorLng);
                maxLng = Math.max(maxLng, donorLng);
            }

            minLat -= 0.008;
            maxLat += 0.008;
            minLng -= 0.008;
            maxLng += 0.008;

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
