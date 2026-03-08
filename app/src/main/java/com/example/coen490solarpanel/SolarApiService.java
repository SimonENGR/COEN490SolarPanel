package com.example.coen490solarpanel;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface SolarApiService {

    // ----------------------------------------------------------------
    // Existing endpoints — UNCHANGED
    // ----------------------------------------------------------------

    /** Full system status (now also includes wiper_percent, wiper_calibrated, wiper_moving) */
    @GET("/status")
    Call<SolarStatus> getStatus();

    /** Toggle auto / manual mode */
    @GET("/mode")
    Call<SyncResponse> toggleMode(@Query("manual") int manualState);

    /** Send GPS location to ESP32 */
    @POST("/update")
    Call<SyncResponse> updateLocation(@Body LocationPayload payload);

    /** Raw IBT-2 motor control (used by hold-to-operate buttons) */
    @GET("/motor")
    Call<SyncResponse> controlMotor(@Query("type") String motorType,
                                    @Query("dir")  int    direction);

    /** Nudge tilt angle by a relative delta (degrees) */
    @GET("/angle")
    Call<SyncResponse> nudgeAngle(@Query("delta") float delta);

    /** Move tilt to an absolute angle (degrees) */
    @GET("/angle")
    Call<SyncResponse> setAngle(@Query("target") float target);

    // ----------------------------------------------------------------
    // Wiper / Cleaning motor endpoints (new)
    // ----------------------------------------------------------------

    /** Move wiper to an absolute position (0–100%) */
    @GET("/wiper")
    Call<SyncResponse> setWiperTarget(@Query("target") float targetPercent);

    /** Nudge wiper by a relative percentage (positive = up, negative = down) */
    @GET("/wiper")
    Call<SyncResponse> nudgeWiper(@Query("delta") float deltaPercent);

    /** Trigger a full autonomous clean cycle (up → down → up to rest) */
    @GET("/wiper")
    Call<SyncResponse> triggerFullClean(@Query("clean") int start);

    /** Query current wiper status without commanding anything */
    @GET("/wiper")
    Call<SyncResponse> getWiperStatus();
}