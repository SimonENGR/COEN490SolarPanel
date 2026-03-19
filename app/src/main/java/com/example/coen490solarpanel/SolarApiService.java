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

    // Set tracking mode: 0=AUTO, 1=SEMI_AUTO, 2=MANUAL
    @GET("/mode")
    Call<SyncResponse> setMode(@Query("mode") int mode);

    // Legacy: toggle manual mode (0=auto, 1=manual)
    @GET("/mode")
    Call<SyncResponse> toggleMode(@Query("manual") int manualState);

    // Corresponds to "handleGPS"
    @POST("/update")
    Call<SyncResponse> updateLocation(@Body LocationPayload payload);

    @GET("/motor")
    Call<SyncResponse> controlMotor(@Query("type") String motorType, @Query("dir") int direction);

    @GET("/angle")
    Call<SyncResponse> nudgeAngle(@Query("delta") float delta);

    @GET("/angle")
    Call<SyncResponse> setAngle(@Query("target") float target);

    @GET("/wiper")
    Call<SyncResponse> runCleaningCycle(@Query("clean") int clean);

    // Weather control endpoints
    @GET("/weather")
    Call<SyncResponse> setWeatherCondition(@Query("condition") String condition);

    @GET("/weather")
    Call<SyncResponse> confirmWeather(@Query("confirm") int confirm);
}