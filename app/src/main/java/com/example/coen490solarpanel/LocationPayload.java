package com.example.coen490solarpanel;

public class LocationPayload {
    public double lat;
    public double lon;
    public long timestamp;

    public LocationPayload(double lat, double lon, long timestamp) {
        this.lat = lat;
        this.lon = lon;
        this.timestamp = timestamp;
    }
}