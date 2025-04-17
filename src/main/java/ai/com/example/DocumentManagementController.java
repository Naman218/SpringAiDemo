package ai.com.example;


import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
@Slf4j
@RestController
@RequestMapping("/api/documents")
public class DocumentManagementController {

    private final Path uploadDir = Paths.get("uploads");
    private final ChromaVectorStore chromaVectorStore;

    @Autowired
    public DocumentManagementController(ChromaVectorStore chromaVectorStore) {
        this.chromaVectorStore = chromaVectorStore;

        // Ensure upload directory exists
        try {
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage location", e);
        }
    }

    /**
     * Upload one or more files to be stored locally
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFiles(@RequestParam("files") List<MultipartFile> files) {
        Map<String, Object> response = new HashMap<>();
        List<String> uploadedFiles = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                // Save file locally
                String filename = file.getOriginalFilename();
                Path targetPath = uploadDir.resolve(filename);
                Files.copy(file.getInputStream(), targetPath);
                uploadedFiles.add(filename);
            } catch (IOException e) {
                failedFiles.add(file.getOriginalFilename());
            }
        }

        response.put("uploaded", uploadedFiles);
        response.put("failed", failedFiles);

        if (!failedFiles.isEmpty()) {
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * List all available documents
     */

    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> listDocuments() throws IOException {
        log.info("Fetching document list...");

        List<Map<String, Object>> fileList = Files.list(uploadDir)
                .filter(Files::isRegularFile)
                .map(path -> {
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("filename", path.getFileName().toString());
                    try {
                        fileInfo.put("size", Files.size(path));
                        fileInfo.put("uploadTime", Files.getLastModifiedTime(path).toMillis());

                        boolean inDb = isInChromaDb(path.getFileName().toString());
                        fileInfo.put("inChromaDb", inDb);

                        log.info("File: {} | Size: {} bytes | In ChromaDB: {}",
                                path.getFileName().toString(),
                                Files.size(path),
                                inDb);
                    } catch (IOException e) {
                        fileInfo.put("error", "Could not read file metadata");
                        log.error("Error reading metadata for file: {}", path.getFileName(), e);
                    }
                    return fileInfo;
                })
                .collect(Collectors.toList());

        log.info("Total files listed: {}", fileList.size());
        return ResponseEntity.ok(fileList);
    }


    /**
     * Process selected files and store them in ChromaDB
     */




    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processFiles(@RequestBody List<String> filenames) {
        log.info("Processing files: {}", filenames);
        Map<String, Object> response = new HashMap<>();
        List<String> processedFiles = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();
        int totalDocuments = 0;

        for (String filename : filenames) {
            Path filePath = uploadDir.resolve(filename);

            if (!Files.exists(filePath)) {
                log.warn("File not found: {}", filename);
                failedFiles.add(filename + " (file not found)");
                continue;
            }

            try {
                List<Document> documents;
                Resource fileResource = new FileSystemResource(filePath);

                // Use TikaDocumentReader for all file types
                TikaDocumentReader tikaReader = new TikaDocumentReader(fileResource);
                documents = tikaReader.get();
                log.info("Extracted {} documents from file: {}", documents.size(), filename);

                // Add metadata for filtering
                for (Document doc : documents) {
                    doc.getMetadata().put("source", "uploaded");
                    doc.getMetadata().put("filename", filename);
                }

                // Split documents for better vector indexing
                TextSplitter textSplitter = new TokenTextSplitter();
                List<Document> splitDocuments = textSplitter.apply(documents);
                log.info("Split into {} smaller documents for file: {}", splitDocuments.size(), filename);

                // Add to ChromaDB
                chromaVectorStore.add(splitDocuments);
                log.info("Added {} documents to ChromaDB for file: {}", splitDocuments.size(), filename);

                processedFiles.add(filename);
                totalDocuments += splitDocuments.size();
            } catch (Exception e) {
                log.error("Error processing file {}: {}", filename, e.getMessage(), e);
                failedFiles.add(filename + " (" + e.getMessage() + ")");
            }
        }

        response.put("processedFiles", processedFiles);
        response.put("failedFiles", failedFiles);
        response.put("totalDocumentsAdded", totalDocuments);

        log.info("Processing completed. Total documents added: {}", totalDocuments);

        return failedFiles.isEmpty()
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(response);
    }





    /**
     * Check if a specific document is in ChromaDB by checking its metadata
     */
    public boolean isInChromaDb(String filename) {
        log.info("Checking if file '{}' exists in ChromaDB...", filename);

        SearchRequest searchRequest = SearchRequest.builder()
                .topK(100)
                .filterExpression("filename == '" + filename + "'")
                .build();


        List<Document> result = chromaVectorStore.doSimilaritySearch(searchRequest);

        boolean exists = !result.isEmpty();
        log.info("File '{}' in ChromaDB: {}", filename, exists);
        return exists;
    }



    /**
     * Delete a document from ChromaDB (by metadata filter)
     */
    @DeleteMapping("/{filename}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable String filename) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Step 1: Search for documents with metadata filter
            SearchRequest searchRequest = SearchRequest.builder()
                    .topK(100)
                    .filterExpression("filename == '" + filename + "'")
                    .build();
            // Adjust based on expected document count

            List<Document> documents = chromaVectorStore.similaritySearch(searchRequest);

            if (documents.isEmpty()) {
                response.put("message", "No documents found in ChromaDB for the given filename");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);  // Ensure a response is returned
            } else {
                // Step 2: Extract document IDs
                List<String> documentIds = documents.stream()
                        .map(Document::getId) // Ensure Document has an `id` field
                        .toList();

                // Step 3: Delete documents using IDs
                try {
                    chromaVectorStore.delete(documentIds); // This now returns void, no need to check success
                    // Deletion was successful
                    response.put("message", "Documents deleted from ChromaDB");
                } catch (Exception e) {
                    // Handle deletion failure
                    response.put("error", "Failed to delete documents from ChromaDB");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }

                // Step 4: Optionally delete the file from local storage
                Path filePath = uploadDir.resolve(filename);
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                    response.put("fileDeletion", "Document deleted from local storage");
                } else {
                    response.put("fileDeletion", "File not found in local storage");
                }

                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            response.put("error", "Failed to delete document: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }


}