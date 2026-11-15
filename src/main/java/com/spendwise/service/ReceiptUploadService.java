package com.spendwise.service;

import com.spendwise.exception.BadRequestException;
import com.spendwise.model.ReceiptUpload;
import com.spendwise.repository.ReceiptUploadRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ReceiptUploadService {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");
    private final ReceiptUploadRepository uploadRepository;
    private final ExpenseGroupService groupService;
    private final UserService userService;
    private final Path storageDirectory;

    public ReceiptUploadService(ReceiptUploadRepository uploadRepository, ExpenseGroupService groupService,
                                UserService userService,
                                @Value("${app.receipts.directory:data/receipts}") String storageDirectory) {
        this.uploadRepository = uploadRepository;
        this.groupService = groupService;
        this.userService = userService;
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public List<ReceiptUpload> list(String guestIdentifier, Long groupId) {
        groupService.getGroupById(guestIdentifier, groupId);
        return uploadRepository.findByGroupId(groupId);
    }

    public ReceiptUpload upload(String guestIdentifier, Long groupId, MultipartFile file) {
        groupService.getGroupById(guestIdentifier, groupId);
        if (file == null || file.isEmpty()) throw new BadRequestException("Choose a receipt file to upload");
        if (file.getSize() > MAX_FILE_SIZE) throw new BadRequestException("Receipt must be 10 MB or smaller");
        String detectedContentType = file.getContentType();
        String contentType = detectedContentType == null ? "" : detectedContentType.toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(contentType)) throw new BadRequestException("Upload a JPG, PNG, WEBP, or PDF receipt");
        try (var input = file.getInputStream()) {
            if (!matchesFileSignature(contentType, input.readNBytes(12))) {
                throw new BadRequestException("The selected file does not match its declared receipt type");
            }
        } catch (IOException ex) {
            throw new BadRequestException("Receipt file could not be read");
        }

        String submittedName = file.getOriginalFilename();
        if (submittedName == null) submittedName = "receipt";
        String originalName = submittedName.replace('\\', '/');
        originalName = originalName.substring(originalName.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (originalName.isBlank()) originalName = "receipt";
        if (originalName.length() > 255) originalName = originalName.substring(0, 255);
        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".pdf";
        };
        String storageKey = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(storageDirectory);
            try (var input = file.getInputStream()) {
                Files.copy(input, storageDirectory.resolve(storageKey));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Receipt could not be stored");
        }
        var user = userService.getUserByGuestIdentifier(guestIdentifier);
        try {
            Long id = uploadRepository.save(groupId, user.getId(), originalName, storageKey, contentType, file.getSize());
            ReceiptUpload upload = new ReceiptUpload();
            upload.setId(id);
            upload.setGroupId(groupId);
            upload.setOriginalFilename(originalName);
            upload.setContentType(contentType);
            upload.setFileSize(file.getSize());
            upload.setUploadedAt(LocalDateTime.now());
            return upload;
        } catch (RuntimeException ex) {
            try { Files.deleteIfExists(storageDirectory.resolve(storageKey)); } catch (IOException ignored) { }
            throw ex;
        }
    }

    private boolean matchesFileSignature(String contentType, byte[] header) {
        return switch (contentType) {
            case "application/pdf" -> startsWith(header, "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            case "image/png" -> startsWith(header, new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
            case "image/jpeg" -> header.length >= 3 && header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF;
            case "image/webp" -> header.length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
    }

    private boolean startsWith(byte[] input, byte[] signature) {
        if (input.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) if (input[i] != signature[i]) return false;
        return true;
    }
}
