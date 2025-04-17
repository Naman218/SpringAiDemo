package ai.com.example;


import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.function.Function;

@Slf4j
@Service
public class EmailService implements Function<EmailService.EmailRequest, EmailService.EmailResponse> {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public EmailResponse apply(EmailRequest request) {
        log.info("Sending email to: {}", request.to());

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(request.to());
            message.setSubject(request.subject());
            message.setText(request.body());
            mailSender.send(message);

            log.info("Email sent successfully to: {}", request.to());
            return new EmailResponse("Success", "Email sent successfully to " + request.to());
        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
            return new EmailResponse("Error", "Failed to send email: " + e.getMessage());
        }
    }

    public record EmailRequest(String to, String subject, String body) {}
    public record EmailResponse(String status, String message) {}
}

