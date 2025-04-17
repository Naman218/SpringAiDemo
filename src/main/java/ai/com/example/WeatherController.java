package ai.com.example;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/weather")
public class WeatherController {

    private final ChatClient chatClient;

    public WeatherController(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("You are a helpful weather assistant that provides clear, friendly explanations about weather conditions. When asked about weather, always call the getWeatherFunction tool to retrieve accurate data. Then explain the weather information in simple, conversational language that anyone can understand. Include practical advice based on the conditions (like clothing recommendations or activity suggestions). For air quality information, explain what the numbers mean for people's health and daily activities. Always translate technical weather terms into everyday language and give context for temperature, humidity, and other measurements.")
                .defaultTools("getWeatherFunction") // Enable the function
                .build();
    }

    @GetMapping("/ask")
    public String getWeather(@RequestParam String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}