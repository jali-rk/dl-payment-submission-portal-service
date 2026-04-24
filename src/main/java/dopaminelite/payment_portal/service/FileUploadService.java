package dopaminelite.payment_portal.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.util.UUID;

/**
 * Service for handling file uploads to S3.
 * Used for uploading study pack thumbnails and other files.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket.output}")
    private String bucketName;

    /**
     * Uploads a file to S3 and returns the S3 key.
     *
     * @param file the file to upload
     * @param folder the S3 folder prefix (e.g., "study-packs")
     * @return the S3 key of the uploaded file
     */
    public String uploadFile(MultipartFile file, String folder) {
        String key = "thumbnails/" + folder + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
        log.info("Uploading file to S3. Bucket: {}, Key: {}", bucketName, key);
        
        try {
            PutObjectResponse response = s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
            
            log.info("Successfully uploaded file. ETag: {}", response.eTag());
            return key;
        } catch (IOException e) {
            log.error("Failed to upload file to S3. Bucket: {}, Key: {}", bucketName, key, e);
            throw new RuntimeException("Failed to upload file", e);
        }
    }

    /**
     * Deletes a file from S3.
     *
     * @param key the S3 key of the file to delete
     */
    public void deleteFile(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        
        log.info("Deleting file from S3. Bucket: {}, Key: {}", bucketName, key);
        
        try {
            s3Client.deleteObject(
                DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build()
            );
            
            log.info("Successfully deleted file from S3.");
        } catch (Exception e) {
            log.error("Failed to delete file from S3. Bucket: {}, Key: {}", bucketName, key, e);
        }
    }
}
