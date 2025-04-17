package ai.com.example;


import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Configuration
public class RagConfiguration {

    @Value("classpath:/docs/olympic-faq.txt")
    private Resource faq;

    @Value("vectorstore.json")
    private String vectorStoreName;


  /*
    SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel) throws IOException{
        var simpleVectorStore = new SimpleVectorStore(embeddingModel);
        var vectorStoreFile = getVectorStoreFile();
        if(vectorStoreFile.exists()){
            log.info("Vector Store File Exists,");
            simpleVectorStore.load(vectorStoreFile);
        }
        else {
            log.info("Vector Store File Does Not Exist, loading documents");
            TextReader textReader = new TextReader(faq);
            textReader.getCustomMetadata().put("filename","olympic-faq.txt");
            List<Document> documents = textReader.get();
            TextSplitter textSplitter = new TokenTextSplitter();
            List<Document> splitDocuments = textSplitter.apply(documents);
            simpleVectorStore.add(splitDocuments);
            simpleVectorStore.save(vectorStoreFile);
        }

        return simpleVectorStore;
    } */


    @Bean
    ChromaVectorStore chromaVectorStore(EmbeddingModel embeddingModel) throws IOException {
        ChromaApi chromaApi = new ChromaApi("http://localhost:8000");

        // Initialize ChromaVectorStore with builder pattern and proper configuration
      return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .collectionName("documents_collection")              // Ensure collection name matches existing collection
                .initializeSchema(true)                // Creates collection if it doesn't exist
                .initializeImmediately(true)          // Immediately initializes and sets collectionId
                .build();

        // Check if the database already has embeddings
      /*  if (chromaApi.countEmbeddings(chromaVectorStore.getCollectionId())>0) {
            log.info("Chroma DB already has embeddings.");
        } else {
            log.info("Chroma DB is empty, loading documents.");

            // Read the FAQ document and set metadata
            TextReader textReader = new TextReader(faq);
            textReader.getCustomMetadata().put("filename", "olympic-faq.txt");
            List<Document> documents = textReader.get();

            // Split the documents into manageable chunks
            TextSplitter textSplitter = new TokenTextSplitter();
            List<Document> splitDocuments = textSplitter.apply(documents);

            // Ensure embeddings are generated and upsert into Chroma
            chromaVectorStore.add(splitDocuments);
            log.info("Successfully added {} documents to the Chroma collection.", splitDocuments.size());
        }*/

       // return chromaVectorStore;
    }


    private File getVectorStoreFile() {
        Path path = Paths.get("src", "main", "resources", "data");
        String absolutePath = path.toFile().getAbsolutePath() + "/" + vectorStoreName;
        return new File(absolutePath);
    }


}
