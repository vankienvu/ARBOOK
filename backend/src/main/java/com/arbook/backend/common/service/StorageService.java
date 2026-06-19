package com.arbook.backend.common.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.arbook.backend.common.exception.BusinessException;

@Service
public class StorageService {
    private final Path uploadLocation;
    private final String provider;
    
    // GCS Configs
    private final boolean gcpEnabled;
    private final String projectId;
    private final String bucketName;
    private final String credentialsJson;
    private com.google.cloud.storage.Storage gcsClient;

    // AWS S3 Configs
    private final String s3BucketName;
    private final String s3Region;
    private final String s3AccessKey;
    private final String s3SecretKey;
    private final String s3EndpointUrl;
    private software.amazon.awssdk.services.s3.S3Client s3Client;

    // Cloudinary Configs
    private final String cloudinaryCloudName;
    private final String cloudinaryApiKey;
    private final String cloudinaryApiSecret;
    private com.cloudinary.Cloudinary cloudinaryClient;

    public StorageService(
            @Value("${app.upload-dir:uploads}") String uploadDir,
            @Value("${app.storage.provider:local}") String provider,
            @Value("${app.gcp.storage.enabled:false}") boolean gcpEnabled,
            @Value("${app.gcp.storage.project-id:}") String projectId,
            @Value("${app.gcp.storage.bucket-name:}") String bucketName,
            @Value("${app.gcp.storage.credentials-json:}") String credentialsJson,
            @Value("${app.s3.bucket-name:}") String s3BucketName,
            @Value("${app.s3.region:us-east-1}") String s3Region,
            @Value("${app.s3.access-key:}") String s3AccessKey,
            @Value("${app.s3.secret-key:}") String s3SecretKey,
            @Value("${app.s3.endpoint-url:}") String s3EndpointUrl,
            @Value("${app.cloudinary.cloud-name:}") String cloudinaryCloudName,
            @Value("${app.cloudinary.api-key:}") String cloudinaryApiKey,
            @Value("${app.cloudinary.api-secret:}") String cloudinaryApiSecret) {
        
        this.uploadLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadLocation);
            Files.createDirectories(this.uploadLocation.resolve("models"));
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage location", e);
        }

        this.provider = provider;

        // Google Cloud Storage / Firebase Initialization
        this.gcpEnabled = gcpEnabled;
        this.projectId = projectId;
        this.bucketName = bucketName;
        this.credentialsJson = credentialsJson;

        String activeProvider = provider;
        if ("local".equalsIgnoreCase(provider) && gcpEnabled) {
            activeProvider = "gcs";
        }

        if ("gcs".equalsIgnoreCase(activeProvider) || "firebase".equalsIgnoreCase(activeProvider)) {
            try {
                com.google.cloud.storage.StorageOptions.Builder builder = 
                    com.google.cloud.storage.StorageOptions.newBuilder();
                if (StringUtils.hasText(projectId)) {
                    builder.setProjectId(projectId);
                }
                if (StringUtils.hasText(credentialsJson)) {
                    try (java.io.ByteArrayInputStream stream = new java.io.ByteArrayInputStream(
                            credentialsJson.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                        builder.setCredentials(com.google.auth.oauth2.GoogleCredentials.fromStream(stream));
                    }
                }
                this.gcsClient = builder.build().getService();
            } catch (Exception e) {
                System.err.println("GCS Initialization failed: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // AWS S3 Initialization
        this.s3BucketName = s3BucketName;
        this.s3Region = s3Region;
        this.s3AccessKey = s3AccessKey;
        this.s3SecretKey = s3SecretKey;
        this.s3EndpointUrl = s3EndpointUrl;

        if ("s3".equalsIgnoreCase(activeProvider)) {
            try {
                software.amazon.awssdk.auth.credentials.AwsBasicCredentials credentials = 
                    software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(s3AccessKey, s3SecretKey);
                software.amazon.awssdk.services.s3.S3ClientBuilder builder = software.amazon.awssdk.services.s3.S3Client.builder()
                    .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(credentials))
                    .region(software.amazon.awssdk.regions.Region.of(s3Region));
                
                if (StringUtils.hasText(s3EndpointUrl)) {
                    builder.endpointOverride(java.net.URI.create(s3EndpointUrl));
                }
                this.s3Client = builder.build();
            } catch (Exception e) {
                System.err.println("S3 Initialization failed: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Cloudinary Initialization
        this.cloudinaryCloudName = cloudinaryCloudName;
        this.cloudinaryApiKey = cloudinaryApiKey;
        this.cloudinaryApiSecret = cloudinaryApiSecret;

        if ("cloudinary".equalsIgnoreCase(activeProvider)) {
            try {
                this.cloudinaryClient = new com.cloudinary.Cloudinary(com.cloudinary.utils.ObjectUtils.asMap(
                    "cloud_name", cloudinaryCloudName,
                    "api_key", cloudinaryApiKey,
                    "api_secret", cloudinaryApiSecret
                ));
            } catch (Exception e) {
                System.err.println("Cloudinary Initialization failed: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public String storeFile(MultipartFile file, String subDir) {
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        if (originalFileName.contains("..")) {
            throw new BusinessException("INVALID_PATH", "Tên file chứa đường dẫn không hợp lệ.", HttpStatus.BAD_REQUEST);
        }
        
        String extension = "";
        int idx = originalFileName.lastIndexOf('.');
        if (idx > 0) {
            extension = originalFileName.substring(idx);
        }
        String cleanName = originalFileName.substring(0, idx > 0 ? idx : originalFileName.length())
                .replaceAll("[^a-zA-Z0-9-_]", "_");
        String finalFileName = cleanName + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;
        
        String activeProvider = provider;
        if ("local".equalsIgnoreCase(provider) && gcpEnabled) {
            activeProvider = "gcs";
        }

        // 1. Google Cloud Storage / Firebase Storage Upload
        if (("gcs".equalsIgnoreCase(activeProvider) || "firebase".equalsIgnoreCase(activeProvider)) && gcsClient != null) {
            try {
                String gcsPath = (subDir != null ? subDir + "/" : "") + finalFileName;
                com.google.cloud.storage.BlobId blobId = com.google.cloud.storage.BlobId.of(bucketName, gcsPath);
                com.google.cloud.storage.BlobInfo blobInfo = com.google.cloud.storage.BlobInfo.newBuilder(blobId)
                        .setContentType(file.getContentType())
                        .build();
                
                try {
                    blobInfo = blobInfo.toBuilder()
                            .setAcl(java.util.List.of(com.google.cloud.storage.Acl.of(
                                    com.google.cloud.storage.Acl.User.ofAllUsers(), 
                                    com.google.cloud.storage.Acl.Role.READER)))
                            .build();
                } catch (Exception aclEx) {
                    // Silent catch: fails if Uniform access control is enabled
                }
                
                gcsClient.create(blobInfo, file.getBytes());
                return String.format("https://storage.googleapis.com/%s/%s", bucketName, gcsPath);
            } catch (IOException e) {
                throw new BusinessException("UPLOAD_FAILED", "Không thể lưu tệp lên Cloud Storage: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        // 2. AWS S3 Upload
        if ("s3".equalsIgnoreCase(activeProvider) && s3Client != null) {
            try {
                String s3Key = (subDir != null ? subDir + "/" : "") + finalFileName;
                
                try {
                    software.amazon.awssdk.services.s3.model.PutObjectRequest putRequest = software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                            .bucket(s3BucketName)
                            .key(s3Key)
                            .contentType(file.getContentType())
                            .acl(software.amazon.awssdk.services.s3.model.ObjectCannedACL.PUBLIC_READ)
                            .build();
                    s3Client.putObject(putRequest, software.amazon.awssdk.core.sync.RequestBody.fromBytes(file.getBytes()));
                } catch (Exception aclEx) {
                    // Retry without ACL (for buckets enforcing Owner Enforced / blocking public ACLs)
                    software.amazon.awssdk.services.s3.model.PutObjectRequest putRequest = software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                            .bucket(s3BucketName)
                            .key(s3Key)
                            .contentType(file.getContentType())
                            .build();
                    s3Client.putObject(putRequest, software.amazon.awssdk.core.sync.RequestBody.fromBytes(file.getBytes()));
                }

                if (StringUtils.hasText(s3EndpointUrl)) {
                    return String.format("%s/%s/%s", s3EndpointUrl.replaceAll("/$", ""), s3BucketName, s3Key);
                }
                return String.format("https://%s.s3.%s.amazonaws.com/%s", s3BucketName, s3Region, s3Key);
            } catch (IOException e) {
                throw new BusinessException("UPLOAD_FAILED", "Không thể lưu tệp lên S3 Storage: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        // 3. Cloudinary Upload
        if ("cloudinary".equalsIgnoreCase(activeProvider) && cloudinaryClient != null) {
            try {
                String folder = subDir != null ? subDir : "";
                java.util.Map params = com.cloudinary.utils.ObjectUtils.asMap(
                    "resource_type", "raw",
                    "folder", folder,
                    "public_id", finalFileName
                );
                java.util.Map uploadResult = cloudinaryClient.uploader().upload(file.getBytes(), params);
                return (String) uploadResult.get("secure_url");
            } catch (Exception e) {
                throw new BusinessException("UPLOAD_FAILED", "Không thể lưu tệp lên Cloudinary: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        // 3. Local Fallback
        try {
            Path targetDir = subDir != null ? this.uploadLocation.resolve(subDir) : this.uploadLocation;
            Path targetLocation = targetDir.resolve(finalFileName);
            Files.createDirectories(targetLocation.getParent());
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            
            return (subDir != null ? "/uploads/" + subDir + "/" : "/uploads/") + finalFileName;
        } catch (IOException e) {
            throw new BusinessException("UPLOAD_FAILED", "Không thể lưu tệp cục bộ: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
