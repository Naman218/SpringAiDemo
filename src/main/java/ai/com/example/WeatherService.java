package ai.com.example;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

import java.util.function.Function;

@Service
public class WeatherService implements Function<WeatherService.WeatherRequest, WeatherService.WeatherResponse> {

    @Value("${weather.api.key}")
    private String apiKey;

    @Override
    public WeatherResponse apply(WeatherRequest request) {
        // Add the aqi=yes parameter to your URL
        String url = "https://api.weatherapi.com/v1/current.json?q=" + request.location +
                "&key=" + apiKey + "&aqi=yes";
        RestTemplate restTemplate = new RestTemplate();

        try {
            String json = restTemplate.getForObject(url, String.class);
            return new WeatherResponse("Success", json);
        } catch (Exception e) {
            return new WeatherResponse("Error", "Failed to fetch weather: " + e.getMessage());
        }
    }

    public record WeatherRequest(String location) {}
    public record WeatherResponse(String status, String data) {}
}

