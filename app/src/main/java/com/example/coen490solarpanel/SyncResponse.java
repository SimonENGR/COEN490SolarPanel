package com.example.coen490solarpanel;

import com.google.gson.annotations.SerializedName;

public class SyncResponse {
    // These names must match the JSON keys from the ESP32 code above
    @SerializedName("message")
    public String message;

    @SerializedName("received_lat")
    public double receivedLat;

    @SerializedName("received_lon")
    public double receivedLon;
}