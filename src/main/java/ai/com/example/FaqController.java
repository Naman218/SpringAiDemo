package ai.com.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
public class FaqController {
    private final ChatClient chatClient;

    @Value("classpath:/prompts/rag-prompt-template.st")
    private Resource ragPromptTemplate;

    private final VectorStore vectorStore;

    public FaqController(ChatClient.Builder builder, VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.chatClient = builder
                .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore,SearchRequest.defaults()))
                .build();
    }

    @GetMapping("/faq")
    public String faq(@RequestParam(value = "message", defaultValue = "How many athletes compete in the Olympic Games Paris 2024") String message) {
        List<Document> similarDocuments = vectorStore.similaritySearch(SearchRequest.query(message).withTopK(2));
        List<String> contentList = similarDocuments.stream().map(Document::getContent).toList();
        //create a prompt template so that LLM/bot answer accordingly
        PromptTemplate promptTemplate = new PromptTemplate (ragPromptTemplate);
        Map<String,Object> promptParameters= new HashMap();
        promptParameters.put("input", message);
        promptParameters.put("documents",  String.join("\n", contentList));
        Prompt prompt= promptTemplate.create(promptParameters);
        return chatClient.prompt(prompt)
                .user(message)
                .call()
                .content();
    }


    @GetMapping("/ask")
    public String ask(@RequestParam(value = "message", defaultValue = "How many athletes compete in the Olympic Games Paris 2024") String message) {
        // Perform similarity search with a metadata filter for "uploaded" documents
        //String filterExpression = String.format("source == '%s'", source);
        SearchRequest searchRequest = SearchRequest.query(message)
                .withTopK(2)
                .withFilterExpression("source == 'uploaded'");// Filter to include only user-uploaded files

        List<Document> similarDocuments = vectorStore.similaritySearch(searchRequest);
        List<String> contentList = similarDocuments.stream().map(Document::getContent).toList();

        // Prepare the prompt
        PromptTemplate promptTemplate = new PromptTemplate(ragPromptTemplate);
        Map<String, Object> promptParameters = new HashMap<>();
        promptParameters.put("input", message);
        promptParameters.put("documents", String.join("\n", contentList));

        Prompt prompt = promptTemplate.create(promptParameters);

        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
