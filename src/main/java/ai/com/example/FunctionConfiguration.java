package ai.com.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class FunctionConfiguration {

    private final EmailService emailService;

    private final WeatherService weatherService;

    public FunctionConfiguration(EmailService emailService,WeatherService weatherService) {
        this.emailService = emailService;
        this.weatherService=weatherService;
    }

    @Bean
    @Description("Send an email using the email service.")
    public Function<EmailService.EmailRequest, EmailService.EmailResponse> sendEmailFunction() {
        return emailService;
    }

    @Bean
    @Description("Fetch the current weather for a given location.")
    public Function<WeatherService.WeatherRequest, WeatherService.WeatherResponse> getWeatherFunction() {
        return weatherService; // or inject it via constructor if using @Service
    }

}

