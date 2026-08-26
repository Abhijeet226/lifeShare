package com.example.abhijeet.bloodbank;

public class BloodBankCenter {
    private String id;
    private String name;
    private String address;
    private String city;
    private String phone;
    private String timings;
    private double latitude;
    private double longitude;
    private String type; // "Hospital", "Blood Bank", "Donation Camp"

    public BloodBankCenter(String id, String name, String address, String city, String phone, String timings, double latitude, double longitude, String type) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.city = city;
        this.phone = phone;
        this.timings = timings;
        this.latitude = latitude;
        this.longitude = longitude;
        this.type = type;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public String getPhone() { return phone; }
    public String getTimings() { return timings; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getType() { return type; }
}
