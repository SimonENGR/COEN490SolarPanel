package com.example.coen490solarpanel;

import com.google.gson.annotations.SerializedName;

public class SolarStatus {
    public boolean override;
    public double azimuth;
    public double elevation;
    public double batteryVoltage;
    public int wifiSignal;
    public String sensorStatus; // "OK" or "FAIL"

    @SerializedName("tilt_angle")
    public double tiltAngle;

    @SerializedName("encoder_pos")
    public long encoderPos;
}