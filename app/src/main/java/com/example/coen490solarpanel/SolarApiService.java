package com.example.coen490solarpanel;

import com.example.coen490solarpanel.LocationPayload;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface SolarApiService {
    // Corresponds to the ESP32's "handleStatus"
    @GET("/status")
    Call<SolarStatus> getStatus();

    // Corresponds to "handleModeControl"
    @GET("/mode")
    Call<String> toggleMode(@Query("manual") int manualState);

    // Corresponds to "handleGPS"
    @POST("/update")
    Call<SyncResponse> updateLocation(@Body LocationPayload payload);
}