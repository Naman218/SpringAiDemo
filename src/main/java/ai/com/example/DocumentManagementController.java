package ai.com.example;


import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.ChromaVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.ai.vectorstore.SearchRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;

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
        List<Map<String, Object>> fileList = Files.list(uploadDir)
                .filter(Files::isRegularFile)
                .map(path -> {
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("filename", path.getFileName().toString());
                    try {
                        fileInfo.put("size", Files.size(path));
                        fileInfo.put("uploadTime", Files.getLastModifiedTime(path).toMillis());
                        fileInfo.put("inChromaDb", isInChromaDb(path.getFileName().toString()));
                    } catch (IOException e) {
                        fileInfo.put("error", "Could not read file metadata");
                    }
                    return fileInfo;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(fileList);
    }

    /**
     * Process selected files and store them in ChromaDB
     */




    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processFiles(@RequestBody List<String> filenames) {
        Map<String, Object> response = new HashMap<>();
        List<String> processedFiles = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();
        int totalDocuments = 0;

        for (String filename : filenames) {
            Path filePath = uploadDir.resolve(filename);

            if (!Files.exists(filePath)) {
                failedFiles.add(filename + " (file not found)");
                continue;
            }

            try {
                List<Document> documents = new ArrayList<>();
                Resource fileResource = new FileSystemResource(filePath);

                if (filename.toLowerCase().endsWith(".pdf")) {
                    PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(fileResource);
                    documents = pdfReader.get();
                } else {
                    TextReader textReader = new TextReader(fileResource);
                    documents = textReader.get();
                }

                // Add metadata for filtering
                for (Document doc : documents) {
                    doc.getMetadata().put("source", "uploaded");
                    doc.getMetadata().put("filename", filename);
                }

                // Split documents for better vector indexing
                TextSplitter textSplitter = new TokenTextSplitter();
                List<Document> splitDocuments = textSplitter.apply(documents);

                // Add to ChromaDB
                chromaVectorStore.add(splitDocuments);

                processedFiles.add(filename);
                totalDocuments += splitDocuments.size();
            } catch (Exception e) {
                failedFiles.add(filename + " (" + e.getMessage() + ")");
            }
        }

        response.put("processedFiles", processedFiles);
        response.put("failedFiles", failedFiles);
        response.put("totalDocumentsAdded", totalDocuments);

        return failedFiles.isEmpty()
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(response);
    }




    /**
     * Check if a specific document is in ChromaDB by checking its metadata
     */
    private boolean isInChromaDb(String filename) {
        try {
            // Create a filter expression for the metadata
            String filterExpression = "filename = '" + filename + "'";

            // Use the correct SearchRequest syntax
            return !chromaVectorStore.similaritySearch(
                            SearchRequest.defaults()
                                    .withFilterExpression(filterExpression)
                                    .withTopK(1))
                    .isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Delete a document from ChromaDB (by metadata filter)
     */
    /*
    @DeleteMapping("/{filename}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable String filename) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Delete from ChromaDB by metadata
            Map<String, String> metadataFilter = Map.of("filename", filename);
            chromaVectorStore.delete(metadataFilter);

            // Optionally delete the file from local storage
            Path filePath = uploadDir.resolve(filename);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                response.put("message", "Document deleted from ChromaDB and local storage");
            } else {
                response.put("message", "Document deleted from ChromaDB only (file not found locally)");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "Failed to delete document: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }*/
}