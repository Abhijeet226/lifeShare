package com.example.abhijeet.bloodbank;

public class UserProfile {

    private String id;
    private String Name;
    private String firstName;
    private String lastName;
    private String dob;
    private String gender = "Male";
    private String Email;
    private String Mobile;
    private String BloodGroup;
    private String City = "Bhubaneswar";
    private String cityId = null;
    private boolean isAvailable = true;
    private String lastDonationDate;
    private String donorId;

    // Geospatial Distance info
    private double distanceKm = 0.0;
    private int distanceMeters = 0;
    private double latitude = 0.0;
    private double longitude = 0.0;

    // Privacy & Security Preferences
    private boolean hideMobileNumber = false;
    private boolean biometricEnabled = true;
    private boolean hospitalOnlyVisibility = false;

    // Trust & Verification
    private String verificationStatus = "UNVERIFIED";
    private String accountStatus = "ACTIVE";
    private boolean phoneVerified = false;
    private boolean emailVerified = false;
    private int donationsCount = 0;
    private int karmaPoints = 0;
    private java.util.List<ApiClient.DonorBadge> badges = new java.util.ArrayList<>();

    public UserProfile() {
        this.isAvailable = true;
        this.gender = "Male";
        this.City = "Bhubaneswar";
        this.verificationStatus = "UNVERIFIED";
        this.accountStatus = "ACTIVE";
    }

    public UserProfile(String userName, String userEmail, String userMobile, String userBloodGroup) {
        this.Name = userName;
        parseFirstAndLastName(userName);
        this.Email = userEmail;
        this.Mobile = userMobile;
        this.BloodGroup = userBloodGroup;
        this.City = "Bhubaneswar";
        this.isAvailable = true;
        this.lastDonationDate = "";
    }

    public UserProfile(String userName, String userEmail, String userMobile, String userBloodGroup, String city, boolean isAvailable) {
        this.Name = userName;
        parseFirstAndLastName(userName);
        this.Email = userEmail;
        this.Mobile = userMobile;
        this.BloodGroup = userBloodGroup;
        this.City = city != null && !city.isEmpty() ? city : "Bhubaneswar";
        this.isAvailable = isAvailable;
        this.lastDonationDate = "";
    }

    public UserProfile(String firstName, String lastName, String dob, String gender, String email, String mobile, String bloodGroup, String city, boolean isAvailable) {
        this.firstName = firstName != null ? firstName.trim() : "";
        this.lastName = lastName != null ? lastName.trim() : "";
        this.Name = (this.firstName + " " + this.lastName).trim();
        this.dob = dob != null ? dob.trim() : "";
        this.gender = gender != null ? gender.trim() : "Male";
        this.Email = email != null ? email.trim() : "";
        this.Mobile = mobile != null ? mobile.trim() : "+91 ";
        this.BloodGroup = bloodGroup != null ? bloodGroup.trim() : "O+";
        this.City = city != null && !city.trim().isEmpty() ? city.trim() : "Bhubaneswar";
        this.isAvailable = isAvailable;
        this.lastDonationDate = "";
    }

    private void parseFirstAndLastName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            this.firstName = "";
            this.lastName = "";
            return;
        }
        String[] parts = fullName.trim().split("\\s+", 2);
        this.firstName = parts[0];
        this.lastName = parts.length > 1 ? parts[1] : "";
    }

    public String getId() {
        return id != null ? id : "";
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDonorId() {
        if (donorId != null && !donorId.trim().isEmpty()) {
            return donorId;
        }
        int hash = Math.abs((getEmail() + getBloodGroup()).hashCode() % 900000) + 100000;
        return "OD-LS-" + hash;
    }

    public void setDonorId(String donorId) {
        this.donorId = donorId;
    }

    public String getQrPayload() {
        return "LIFESHARE_DONOR_PASS\n" +
                "ID:" + getDonorId() + "\n" +
                "NAME:" + getName() + "\n" +
                "BG:" + getBloodGroup() + "\n" +
                "CITY:" + getCity() + "\n" +
                "STATUS:" + (isAvailable() ? "ACTIVE" : "INACTIVE") + "\n" +
                "VERIFIED:TRUE";
    }

    public String getName() {
        if (Name != null && !Name.isEmpty()) return Name;
        String full = (getFirstName() + " " + getLastName()).trim();
        return full.isEmpty() ? "Donor" : full;
    }

    public void setName(String name) {
        this.Name = name;
        parseFirstAndLastName(name);
    }

    public String getFirstName() {
        return firstName != null ? firstName : "";
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
        this.Name = (this.firstName + " " + (this.lastName != null ? this.lastName : "")).trim();
    }

    public String getLastName() {
        return lastName != null ? lastName : "";
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
        this.Name = ((this.firstName != null ? this.firstName : "") + " " + this.lastName).trim();
    }

    public String getDob() {
        return dob != null ? dob : "";
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public String getGender() {
        return gender != null ? gender : "Male";
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getEmail() {
        return Email != null ? Email : "";
    }

    public void setEmail(String email) {
        this.Email = email;
    }

    public String getMobile() {
        return Mobile != null ? Mobile : "";
    }

    public void setMobile(String mobile) {
        this.Mobile = mobile;
    }

    public String getBloodGroup() {
        return BloodGroup != null ? BloodGroup : "O+";
    }

    public void setBloodGroup(String bloodGroup) {
        this.BloodGroup = bloodGroup;
    }

    public String getCity() {
        return City != null ? City : "Bhubaneswar";
    }

    public void setCity(String city) {
        this.City = city;
    }

    public String getCityId() {
        return cityId;
    }

    public void setCityId(String cityId) {
        this.cityId = cityId;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available) {
        isAvailable = available;
    }

    public String getLastDonationDate() {
        return lastDonationDate != null ? lastDonationDate : "";
    }

    public void setLastDonationDate(String lastDonationDate) {
        this.lastDonationDate = lastDonationDate;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public int getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(int distanceMeters) {
        this.distanceMeters = distanceMeters;
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

    public boolean isHideMobileNumber() {
        return hideMobileNumber;
    }

    public void setHideMobileNumber(boolean hideMobileNumber) {
        this.hideMobileNumber = hideMobileNumber;
    }

    public boolean isBiometricEnabled() {
        return biometricEnabled;
    }

    public void setBiometricEnabled(boolean biometricEnabled) {
        this.biometricEnabled = biometricEnabled;
    }

    public boolean isHospitalOnlyVisibility() {
        return hospitalOnlyVisibility;
    }

    public void setHospitalOnlyVisibility(boolean hospitalOnlyVisibility) {
        this.hospitalOnlyVisibility = hospitalOnlyVisibility;
    }

    public String getVerificationStatus() {
        return verificationStatus != null ? verificationStatus : "UNVERIFIED";
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public boolean isVerifiedDonor() {
        return "DONOR_VERIFIED".equalsIgnoreCase(verificationStatus) || "PHONE_VERIFIED".equalsIgnoreCase(verificationStatus);
    }

    public String getAccountStatus() {
        return accountStatus != null ? accountStatus : "ACTIVE";
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public boolean isPhoneVerified() {
        return phoneVerified;
    }

    public void setPhoneVerified(boolean phoneVerified) {
        this.phoneVerified = phoneVerified;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public int getDonationsCount() {
        return donationsCount;
    }

    public void setDonationsCount(int donationsCount) {
        this.donationsCount = donationsCount;
    }

    // Role & 90-Day Cooldown Eligibility
    private String role = "DONOR";
    private boolean isEligibleToDonate = true;
    private int daysRemaining = 0;
    private String nextEligibleDate = "";

    public String getRole() {
        return role != null ? role : "DONOR";
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isCoordinator() {
        return "COORDINATOR".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    public boolean isEligibleToDonate() {
        return isEligibleToDonate;
    }

    public void setEligibleToDonate(boolean eligibleToDonate) {
        isEligibleToDonate = eligibleToDonate;
    }

    public int getDaysRemaining() {
        return daysRemaining;
    }

    public void setDaysRemaining(int daysRemaining) {
        this.daysRemaining = daysRemaining;
    }

    public String getNextEligibleDate() {
        return nextEligibleDate != null ? nextEligibleDate : "";
    }

    public void setNextEligibleDate(String nextEligibleDate) {
        this.nextEligibleDate = nextEligibleDate;
    }

    public int getKarmaPoints() {
        return karmaPoints;
    }

    public void setKarmaPoints(int karmaPoints) {
        this.karmaPoints = karmaPoints;
    }

    public java.util.List<ApiClient.DonorBadge> getBadges() {
        return badges != null ? badges : new java.util.ArrayList<ApiClient.DonorBadge>();
    }

    public void setBadges(java.util.List<ApiClient.DonorBadge> badges) {
        this.badges = badges != null ? badges : new java.util.ArrayList<ApiClient.DonorBadge>();
    }

    private String hospitalName = "";
    private String hospitalId = "";

    public String getHospitalName() {
        return hospitalName != null ? hospitalName : "";
    }

    public void setHospitalName(String hospitalName) {
        this.hospitalName = hospitalName;
    }

    public String getHospitalId() {
        return hospitalId != null ? hospitalId : "";
    }

    public void setHospitalId(String hospitalId) {
        this.hospitalId = hospitalId;
    }
}
