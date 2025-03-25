package ai.com.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class FunctionConfiguration {

    private final EmailService emailService;

    public FunctionConfiguration(EmailService emailService) {
        this.emailService = emailService;
    }

    @Bean
    @Description("Send an email using the email service.")
    public Function<EmailService.EmailRequest, EmailService.EmailResponse> sendEmailFunction() {
        return emailService;
    }
}

