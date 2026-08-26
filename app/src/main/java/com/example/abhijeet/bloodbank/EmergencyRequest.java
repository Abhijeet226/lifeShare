package com.example.abhijeet.bloodbank;

public class EmergencyRequest {
    private String id;
    private String _id;
    private String patientName;
    private String hospital;
    private String hospitalId;
    private String hospitalName;
    private String hospitalAddress;
    private boolean isAuthoritativeHospital;
    private String city;
    private String cityId;
    private String bloodGroup;
    private int unitsNeeded;
    private int unitsRequired;
    private int acceptedCount;
    private int unitsFulfilled;
    private String contactNumber;
    private String postedBy;
    private long timestamp;
    private boolean isFulfilled;
    private String urgency = "URGENT"; // NORMAL, URGENT, CRITICAL
    private String status = "SEARCHING"; // REQUESTED, SEARCHING, DONORS_NOTIFIED, PARTIALLY_ACCEPTED, DONOR_ACCEPTED, FULFILLED, etc.
    private double latitude = 0.0;
    private double longitude = 0.0;
    private double hospitalLatitude = 0.0;
    private double hospitalLongitude = 0.0;
    private double distanceKm = 0.0;

    public EmergencyRequest() {
        this.timestamp = System.currentTimeMillis();
        this.isFulfilled = false;
        this.unitsNeeded = 1;
        this.unitsRequired = 1;
        this.acceptedCount = 0;
        this.unitsFulfilled = 0;
        this.postedBy = "";
    }

    public EmergencyRequest(String id, String patientName, String hospital, String city, String bloodGroup, int unitsNeeded, String contactNumber) {
        this(id, patientName, hospital, city, bloodGroup, unitsNeeded, contactNumber, "");
    }

    public EmergencyRequest(String id, String patientName, String hospital, String city, String bloodGroup, int unitsNeeded, String contactNumber, String postedBy) {
        this.id = id;
        this._id = id;
        this.patientName = patientName;
        this.hospital = hospital;
        this.city = city;
        this.bloodGroup = bloodGroup;
        this.unitsNeeded = unitsNeeded;
        this.unitsRequired = unitsNeeded;
        this.acceptedCount = 0;
        this.unitsFulfilled = 0;
        this.contactNumber = contactNumber;
        this.postedBy = postedBy != null ? postedBy : "";
        this.timestamp = System.currentTimeMillis();
        this.isFulfilled = false;
    }

    public boolean hasLocation() {
        return (latitude != 0.0 || longitude != 0.0 || hospitalLatitude != 0.0 || hospitalLongitude != 0.0);
    }

    public String getId() {
        if (id != null && !id.isEmpty()) return id;
        if (_id != null && !_id.isEmpty()) return _id;
        return "";
    }

    public void setId(String id) {
        this.id = id;
        this._id = id;
    }

    public String getPatientName() {
        return patientName != null ? patientName : "";
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getHospital() {
        if (hospitalName != null && !hospitalName.isEmpty()) return hospitalName;
        return hospital != null ? hospital : "";
    }

    public void setHospital(String hospital) {
        this.hospital = hospital;
    }

    public String getHospitalId() {
        return hospitalId;
    }

    public void setHospitalId(String hospitalId) {
        this.hospitalId = hospitalId;
    }

    public String getHospitalName() {
        return hospitalName != null ? hospitalName : hospital;
    }

    public void setHospitalName(String hospitalName) {
        this.hospitalName = hospitalName;
    }

    public String getHospitalAddress() {
        return hospitalAddress != null ? hospitalAddress : "";
    }

    public void setHospitalAddress(String hospitalAddress) {
        this.hospitalAddress = hospitalAddress;
    }

    public boolean isAuthoritativeHospital() {
        return isAuthoritativeHospital;
    }

    public void setAuthoritativeHospital(boolean authoritativeHospital) {
        isAuthoritativeHospital = authoritativeHospital;
    }

    public String getCity() {
        return city != null ? city : "Bhubaneswar";
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCityId() {
        return cityId;
    }

    public void setCityId(String cityId) {
        this.cityId = cityId;
    }

    public String getBloodGroup() {
        return bloodGroup != null ? bloodGroup : "";
    }

    public void setBloodGroup(String bloodGroup) {
        this.bloodGroup = bloodGroup;
    }

    public int getUnitsNeeded() {
        return unitsRequired > 0 ? unitsRequired : (unitsNeeded > 0 ? unitsNeeded : 1);
    }

    public void setUnitsNeeded(int unitsNeeded) {
        this.unitsNeeded = unitsNeeded;
        this.unitsRequired = unitsNeeded;
    }

    public int getUnitsRequired() {
        return unitsRequired > 0 ? unitsRequired : (unitsNeeded > 0 ? unitsNeeded : 1);
    }

    public void setUnitsRequired(int unitsRequired) {
        this.unitsRequired = unitsRequired;
        this.unitsNeeded = unitsRequired;
    }

    public int getAcceptedCount() {
        return acceptedCount;
    }

    public void setAcceptedCount(int acceptedCount) {
        this.acceptedCount = acceptedCount;
    }

    public int getUnitsFulfilled() {
        return unitsFulfilled;
    }

    public void setUnitsFulfilled(int unitsFulfilled) {
        this.unitsFulfilled = unitsFulfilled;
    }

    public int getRemainingUnits() {
        return Math.max(0, getUnitsRequired() - acceptedCount);
    }

    public String getContactNumber() {
        return contactNumber != null ? contactNumber : "";
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getPostedBy() {
        return postedBy != null ? postedBy : "";
    }

    public void setPostedBy(String postedBy) {
        this.postedBy = postedBy;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isFulfilled() {
        return isFulfilled;
    }

    public void setFulfilled(boolean fulfilled) {
        isFulfilled = fulfilled;
    }

    public String getUrgency() {
        return urgency != null ? urgency : "URGENT";
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    private String statusDisplay;

    public String getStatus() {
        return status != null ? status : "SEARCHING";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusDisplay() {
        if (statusDisplay != null && !statusDisplay.isEmpty()) {
            return statusDisplay;
        }
        if ("FULFILLED".equalsIgnoreCase(status) || (unitsRequired > 0 && unitsFulfilled >= unitsRequired)) {
            return "Fulfilled";
        }
        if ("PARTIAL".equalsIgnoreCase(status) || unitsFulfilled > 0) {
            return "Partially Fulfilled";
        }
        if ("EXPIRED".equalsIgnoreCase(status)) return "Expired";
        if ("CANCELLED".equalsIgnoreCase(status)) return "Closed";
        return "Seeking Donors";
    }

    public void setStatusDisplay(String statusDisplay) {
        this.statusDisplay = statusDisplay;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getHospitalLatitude() {
        return hospitalLatitude;
    }

    public void setHospitalLatitude(double hospitalLatitude) {
        this.hospitalLatitude = hospitalLatitude;
    }

    public double getHospitalLongitude() {
        return hospitalLongitude;
    }

    public void setHospitalLongitude(double hospitalLongitude) {
        this.hospitalLongitude = hospitalLongitude;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(double distanceKm) {
        this.distanceKm = distanceKm;
    }
}
