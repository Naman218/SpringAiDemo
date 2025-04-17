package ai.com.example;


import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.ai.chat.prompt.Prompt;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@RestController
public class MailController {

    private final ChatClient chatClient;
    private final PromptTemplate emailPromptTemplate;

    public MailController(
            ChatClient.Builder builder,
            @Value("classpath:/prompts/email-prompt-template.st") Resource emailPromptTemplateResource
    ) {
        this.chatClient = builder
                .defaultSystem("You are a helpful AI Assistant that generates professional email communications.")
                .defaultTools("sendEmailFunction")
                .build();

        // Load prompt template
        try {
            String templateContent = new String(Files.readAllBytes(emailPromptTemplateResource.getFile().toPath()));
            this.emailPromptTemplate = new PromptTemplate(templateContent);
        } catch (IOException e) {
            log.error("Failed to load email prompt template", e);
            throw new RuntimeException("Could not load email prompt template", e);
        }
    }

    @GetMapping("/send-mail")
    public ResponseEntity<String> sendEmail(@RequestParam String prompt) {
        try {
            log.info("Received email request with prompt: {}", prompt);

            // Extract email address using regex
            String email = extractEmail(prompt);
            if (email == null) {
                log.error("Failed to extract a valid email from the prompt: {}", prompt);
                return ResponseEntity
                        .badRequest()
                        .body("Error: No valid email found in the prompt!");
            }
            log.info("Extracted recipient email: {}", email);

            // Generate subject using LLM
            log.info("Requesting AI to generate email subject...");
            String subject = generateEmailSubject(prompt);
            log.info("Generated Email Subject: {}", subject);

            // Prepare prompt template parameters
            Map<String, Object> templateParams = new HashMap<>();
            templateParams.put("recipient_name", extractNameFromEmail(email));
            templateParams.put("sender_name", "AI Assistant");
            templateParams.put("subject", subject);
            templateParams.put("email_body", prompt);

            // Generate email body using prompt template
            log.info("Requesting AI to generate email body...");
            Prompt generatedPrompt = emailPromptTemplate.create(templateParams);
            String emailContent = chatClient.prompt(generatedPrompt)
                    .user(prompt)
                    .call()
                    .content();
            log.info("AI Response for email generation: \n{}", emailContent);

            // Extract body from AI response
            String[] parts = emailContent.split("\n", 2);
            String body = parts.length > 1 ? parts[1].replace("Body:", "").trim() : "No Body";
            log.info("Generated Email Body: {}", body);

            // Send email using AI function
            log.info("Sending email to {} with subject '{}'...", email, subject);
            String sendResponse = chatClient.prompt()
                    .user("Send an email to " + email + " with subject '" + subject + "' and body '" + body + "'.")
                    .call()
                    .content();

            log.info("Email sending response: {}", sendResponse);

            // Check for successful sending
            if (sendResponse.toLowerCase().contains("success") ||
                    sendResponse.toLowerCase().contains("sent") ||
                    sendResponse.toLowerCase().contains("email delivered") ||
                    sendResponse.toLowerCase().contains("delivered")) {
                return ResponseEntity
                        .ok()
                        .body(String.format("Email successfully sent to %s with subject '%s'", email, subject));
            } else {
                log.error("Failed to send email: {}", sendResponse);
                return ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to send email: " + sendResponse);
            }
        } catch (Exception e) {
            log.error("Unexpected error during email sending", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }

    // Method to generate email subject using LLM
    private String generateEmailSubject(String prompt) {
        String subjectGenerationPrompt = "Generate a concise, professional email subject line that captures the essence of this request: " + prompt;

        return chatClient.prompt()
                .user(subjectGenerationPrompt)
                .call()
                .content()
                .replace("Subject:", "")
                .trim();
    }

    // Helper function to extract a valid email from the prompt
    private String extractEmail(String text) {
        Pattern pattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    // Helper function to extract name from email
    private String extractNameFromEmail(String email) {
        if (email == null) return "Valued Recipient";

        // Split email at @ and take the first part
        String[] parts = email.split("@");
        if (parts.length > 0) {
            // Replace dots and convert to title case
            String name = parts[0].replace(".", " ");
            return Arrays.stream(name.split("\\s+"))
                    .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                    .collect(Collectors.joining(" "));
        }
        return "Valued Recipient";
    }
}