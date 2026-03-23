package com.example.coen490solarpanel;

import com.google.gson.annotations.SerializedName;

public class SolarStatus {
    public boolean override;
    public int mode;                      // 0=AUTO, 1=SEMI_AUTO, 2=MANUAL
    public double azimuth;
    public double elevation;
    public double batteryVoltage;
    public int wifiSignal;
    public String sensorStatus;           // "OK" or "FAIL"

    @SerializedName("tilt_angle")
    public double tiltAngle;

    @SerializedName("encoder_pos")
    public long encoderPos;

    @SerializedName("weather_condition")
    public String weatherCondition;       // "Clear", "Clouds", "Rain", "Snow"

    @SerializedName("weather_override")
    public int weatherOverride;           // -1 = no override, else target angle

    @SerializedName("weather_pending")
    public boolean weatherPending;        // Semi-auto: waiting for user confirmation
    @SerializedName("wiper_moving")
    public boolean wiperMoving;           // True if auto-clean cycle is currently running
}