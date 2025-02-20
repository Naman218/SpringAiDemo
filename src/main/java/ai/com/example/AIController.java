package ai.com.example;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/ai")
public class AIController {

    private OllamaChatModel ollamaChatModel;

    private final ChatClient chatClient;
    public AIController(OllamaChatModel ollamaChatModel, ChatClient.Builder builder) {
        this.ollamaChatModel = ollamaChatModel;
        this.chatClient = builder
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();
    }
    @GetMapping("/prompt")
    public Flux<String> promptResponse(@RequestParam("prompt") String prompt){
        return ollamaChatModel.stream(prompt);
    }

    @GetMapping("/message")
    public List<String> messageResponse(@RequestParam("message") String message){
        ListOutputConverter outputConverter =new ListOutputConverter(new DefaultConversionService());
       return outputConverter.convert(chatClient.prompt()
                .user(message)
                .call()
                .content());
    }

}
