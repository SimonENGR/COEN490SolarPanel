package com.example.coen490solarpanel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeatherFragment extends Fragment {

    private static final String API_KEY = "a00650a3a9459e3365cebef437c717e9";
    private static final String CITY = "Montreal"; // Make this Location based instead of hard-coded
    private static final String COUNTRY_CODE = "CA";

    private TextView tvCurrentTemp;
    private TextView tvWeatherCondition;
    private TextView tvHumidity;
    private TextView tvWindSpeed;
    private TextView tvSunrise;
    private TextView tvSunset;
    private TextView tvSunAngle;
    private TextView tvCleaningRecommendation;
    private RecyclerView rvForecast;

    private ExecutorService executorService;
    private ForecastAdapter forecastAdapter;
    private List<ForecastItem> forecastList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_weather, container, false);

        // Initialize views
        tvCurrentTemp = view.findViewById(R.id.tv_current_temp);
        tvWeatherCondition = view.findViewById(R.id.tv_weather_condition);
        tvHumidity = view.findViewById(R.id.tv_humidity);
        tvWindSpeed = view.findViewById(R.id.tv_wind_speed);
        tvSunrise = view.findViewById(R.id.tv_sunrise);
        tvSunset = view.findViewById(R.id.tv_sunset);
        tvSunAngle = view.findViewById(R.id.tv_sun_angle);
        tvCleaningRecommendation = view.findViewById(R.id.tv_cleaning_recommendation);
        rvForecast = view.findViewById(R.id.rv_forecast);

        // Setup RecyclerView
        forecastList = new ArrayList<>();
        forecastAdapter = new ForecastAdapter(forecastList);
        rvForecast.setLayoutManager(new LinearLayoutManager(getContext()));
        rvForecast.setAdapter(forecastAdapter);

        executorService = Executors.newSingleThreadExecutor();

        // Fetch weather data
        fetchWeatherData();

        return view;
    }

    private void fetchWeatherData() {
        executorService.execute(() -> {
            try {
                // Fetch current weather
                String currentWeatherUrl = "https://api.openweathermap.org/data/2.5/weather?q=" +
                        CITY + "," + COUNTRY_CODE + "&appid=" + API_KEY + "&units=metric";
                String currentData = makeHttpRequest(currentWeatherUrl);

                // Fetch 7-day forecast
                String forecastUrl = "https://api.openweathermap.org/data/2.5/forecast?q=" +
                        CITY + "," + COUNTRY_CODE + "&appid=" + API_KEY + "&units=metric";
                String forecastData = makeHttpRequest(forecastUrl);

                // Parse and update UI
                if (currentData != null && forecastData != null) {
                    parseCurrentWeather(currentData);
                    parseForecast(forecastData);
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Error fetching weather data", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }

    private String makeHttpRequest(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        reader.close();
        connection.disconnect();

        return response.toString();
    }

    private void parseCurrentWeather(String jsonData) throws JSONException {
        JSONObject json = new JSONObject(jsonData);

        // Temperature
        double temp = json.getJSONObject("main").getDouble("temp");

        // Weather condition
        String condition = json.getJSONArray("weather").getJSONObject(0).getString("main");
        String description = json.getJSONArray("weather").getJSONObject(0).getString("description");

        // Humidity
        int humidity = json.getJSONObject("main").getInt("humidity");

        // Wind speed
        double windSpeed = json.getJSONObject("wind").getDouble("speed");

        // Sunrise and sunset
        long sunriseTimestamp = json.getJSONObject("sys").getLong("sunrise");
        long sunsetTimestamp = json.getJSONObject("sys").getLong("sunset");

        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String sunrise = timeFormat.format(new Date(sunriseTimestamp * 1000));
        String sunset = timeFormat.format(new Date(sunsetTimestamp * 1000));

        // Calculate sun angle (simplified calculation)
        double sunAngle = calculateSunAngle(sunriseTimestamp, sunsetTimestamp);

        // Cleaning recommendation
        String recommendation = getCleaningRecommendation(condition, windSpeed);

        // Update UI on main thread
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                tvCurrentTemp.setText(String.format(Locale.getDefault(), "%.1f°C", temp));
                tvWeatherCondition.setText(description.substring(0, 1).toUpperCase() + description.substring(1));
                tvHumidity.setText(String.format(Locale.getDefault(), "Humidity: %d%%", humidity));
                tvWindSpeed.setText(String.format(Locale.getDefault(), "Wind Speed: %.1f km/h", windSpeed * 3.6));
                tvSunrise.setText("Sunrise: " + sunrise);
                tvSunset.setText("Sunset: " + sunset);
                tvSunAngle.setText(String.format(Locale.getDefault(), "Sun Angle: %.1f°", sunAngle));
                tvCleaningRecommendation.setText(recommendation);
            });
        }
    }

    private void parseForecast(String jsonData) throws JSONException {
        JSONObject json = new JSONObject(jsonData);
        JSONArray list = json.getJSONArray("list");

        List<ForecastItem> newForecast = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());

        // Get one forecast per day (at 12:00)
        for (int i = 0; i < list.length() && newForecast.size() < 7; i++) {
            JSONObject item = list.getJSONObject(i);
            String datetime = item.getString("dt_txt");

            if (datetime.contains("12:00:00")) {
                long timestamp = item.getLong("dt");
                String date = dateFormat.format(new Date(timestamp * 1000));

                double temp = item.getJSONObject("main").getDouble("temp");
                String condition = item.getJSONArray("weather").getJSONObject(0).getString("main");

                newForecast.add(new ForecastItem(date, temp, condition));
            }
        }

        // Update RecyclerView on main thread
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                forecastList.clear();
                forecastList.addAll(newForecast);
                forecastAdapter.notifyDataSetChanged();
            });
        }
    }

    private double calculateSunAngle(long sunrise, long sunset) {
        long currentTime = System.currentTimeMillis() / 1000;
        long dayLength = sunset - sunrise;
        long timeSinceSunrise = currentTime - sunrise;

        if (timeSinceSunrise < 0 || timeSinceSunrise > dayLength) {
            return 0; // Sun is not up
        }

        // Simplified calculation: peak at solar noon
        double progress = (double) timeSinceSunrise / dayLength;
        return 90 * Math.sin(Math.PI * progress);
    }

    private String getCleaningRecommendation(String condition, double windSpeed) {
        boolean goodConditions = !condition.equalsIgnoreCase("Rain") &&
                !condition.equalsIgnoreCase("Snow") &&
                windSpeed < 5;

        if (goodConditions) {
            return "✓ Good weather for cleaning. Low wind, no precipitation expected.";
        } else if (condition.equalsIgnoreCase("Rain") || condition.equalsIgnoreCase("Snow")) {
            return "✗ Not recommended. Precipitation expected.";
        } else {
            return "⚠ Caution. High wind conditions.";
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    // Forecast Item class
    static class ForecastItem {
        String date;
        double temp;
        String condition;

        ForecastItem(String date, double temp, String condition) {
            this.date = date;
            this.temp = temp;
            this.condition = condition;
        }
    }

    // Forecast Adapter
    static class ForecastAdapter extends RecyclerView.Adapter<ForecastAdapter.ViewHolder> {
        private List<ForecastItem> items;

        ForecastAdapter(List<ForecastItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_forecast, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ForecastItem item = items.get(position);
            holder.tvDate.setText(item.date);
            holder.tvTemp.setText(String.format(Locale.getDefault(), "%.1f°C", item.temp));
            holder.tvCondition.setText(item.condition);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvDate, tvTemp, tvCondition;

            ViewHolder(View view) {
                super(view);
                tvDate = view.findViewById(R.id.tv_forecast_date);
                tvTemp = view.findViewById(R.id.tv_forecast_temp);
                tvCondition = view.findViewById(R.id.tv_forecast_condition);
            }
        }
    }
}