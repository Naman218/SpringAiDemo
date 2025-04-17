package ai.com.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RestController
public class EmailController {

    private final ChatClient chatClient;

    @Value("classpath:/prompts/email-prompt-template.st")
    private Resource emailPromptTemplate;

    public EmailController(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("You are a helpful AI Assistant that generates a proper email subject and body.")
                .defaultTools("sendEmailFunction")
                .build();
    }

    @GetMapping("/send-email")
    public String sendEmail(@RequestParam String prompt) {
        log.info("Received email request with prompt: {}", prompt);

        // Extract email address using regex
        String email = extractEmail(prompt);
        if (email == null) {
            log.error("Failed to extract a valid email from the prompt: {}", prompt);
            return "Error: No valid email found in the prompt!";
        }
        log.info("Extracted recipient email: {}", email);

        // Ask AI to generate subject & body
        log.info("Requesting AI to generate email subject and body...");
        String emailContent = chatClient.prompt()
                .user("Generate an email subject and body for this request: " + prompt)
                .call()
                .content();
        log.info("AI Response for email generation: \n{}", emailContent);

        // Extract subject & body from AI response
        String[] parts = emailContent.split("\n", 2);
        String subject = parts.length > 0 ? parts[0].replace("Subject:", "").trim() : "No Subject";
        String body = parts.length > 1 ? parts[1].replace("Body:", "").trim() : "No Body";

        log.info("Generated Email Subject: {}", subject);
        log.info("Generated Email Body: {}", body);

        // Send email using AI function
        log.info("Sending email to {} with subject '{}'...", email, subject);
        String sendResponse = chatClient.prompt()
                .user("Send an email to " + email + " with subject '" + subject + "' and body '" + body + "'.")
                .call()
                .content();

        log.info("Email sending response: {}", sendResponse);
        return sendResponse;
    }

    // Helper function to extract a valid email from the prompt
    private String extractEmail(String text) {
        Pattern pattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }
}

