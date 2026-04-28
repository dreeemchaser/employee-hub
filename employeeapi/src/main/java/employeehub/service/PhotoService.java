package employeehub.service;

import employeehub.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class PhotoService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".pdf");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    @Value("${app.upload.directory:${user.home}/employeehub/uploads/}")
    private String uploadDirectory;

    public String save(MultipartFile file) {
        validateFile(file);
        try {
            Path uploadDir = Paths.get(uploadDirectory).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);

            String filename = UUID.randomUUID() + getExtension(file.getOriginalFilename());
            Files.copy(file.getInputStream(), uploadDir.resolve(filename));
            return filename;
        } catch (IOException e) {
            log.error("Failed to save file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Could not save file: " + e.getMessage());
        }
    }

    public byte[] load(String filename) {
        try {
            Path uploadDir = Paths.get(uploadDirectory).toAbsolutePath().normalize();
            Path filePath = uploadDir.resolve(filename).normalize();
            // Path traversal guard
            if (!filePath.startsWith(uploadDir)) {
                throw new ResourceNotFoundException("File not found: " + filename);
            }
            if (!Files.exists(filePath)) {
                throw new ResourceNotFoundException("File not found: " + filename);
            }
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Could not read file: " + e.getMessage());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds the 10 MB limit");
        }
        String ext = getExtension(file.getOriginalFilename()).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("File type not allowed. Permitted types: jpg, jpeg, png, gif, webp, pdf");
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }
}
