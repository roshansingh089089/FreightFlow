package com.freightflow.mail;

import com.freightflow.auth.entity.AppUser;
import com.freightflow.common.exception.ApiException;
import com.freightflow.common.security.SecurityConfig;
import com.freightflow.mail.entity.OutboundAttachment;
import com.freightflow.mail.repository.OutboundAttachmentRepo;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

interface MailAttachmentStorage {
  void put(String key, byte[] bytes) throws IOException;

  byte[] get(String key) throws IOException;

  void delete(String key) throws IOException;
}

@Component
@Profile("local")
class LocalMailAttachmentStorage implements MailAttachmentStorage {
  private final Path root;

  LocalMailAttachmentStorage(
      @Value("${mail.attachments.local-dir:/tmp/freightflow-mail-dev}") String directory) {
    root = Path.of(directory).toAbsolutePath().normalize();
  }

  public void put(String key, byte[] bytes) throws IOException {
    Files.createDirectories(root);
    Files.write(root.resolve(key), bytes, StandardOpenOption.CREATE_NEW);
  }

  public byte[] get(String key) throws IOException {
    return Files.readAllBytes(root.resolve(key));
  }

  public void delete(String key) throws IOException {
    Files.deleteIfExists(root.resolve(key));
  }
}

@Component
@Profile("!local")
@ConditionalOnProperty("mail.attachments.private-dir")
class PrivateMailAttachmentStorage implements MailAttachmentStorage {
  private final Path root;

  PrivateMailAttachmentStorage(@Value("${mail.attachments.private-dir}") String directory) {
    if (directory == null || directory.isBlank())
      throw new IllegalArgumentException("Private mail attachment directory is required");
    root = Path.of(directory).toAbsolutePath().normalize();
  }

  private Path file(String key) {
    UUID.fromString(key);
    return root.resolve(key);
  }

  public void put(String key, byte[] bytes) throws IOException {
    Files.createDirectories(root);
    try {
      Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
    } catch (UnsupportedOperationException ignored) {
    }
    Path path = file(key);
    Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    try {
      Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    } catch (UnsupportedOperationException ignored) {
    }
  }

  public byte[] get(String key) throws IOException {
    Path path = file(key);
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      throw new IOException("Missing file");
    return Files.readAllBytes(path);
  }

  public void delete(String key) throws IOException {
    Files.deleteIfExists(file(key));
  }
}

@Service
class MailAttachmentService {
  private final OutboundAttachmentRepo attachments;
  private final MailAttachmentStorage storage;

  MailAttachmentService(
      OutboundAttachmentRepo attachments, ObjectProvider<MailAttachmentStorage> storage) {
    this.attachments = attachments;
    this.storage = storage.getIfAvailable();
  }

  OutboundAttachment upload(MultipartFile file) {
    try {
      return store(file.getOriginalFilename(), file.getBytes());
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(500, "MAIL_ATTACHMENT_FAILED", "Could not store attachment");
    }
  }

  OutboundAttachment storeGeneratedPdf(String filename, byte[] bytes) {
    if (bytes.length < 4
        || bytes[0] != '%'
        || bytes[1] != 'P'
        || bytes[2] != 'D'
        || bytes[3] != 'F')
      throw new ApiException(400, "MAIL_ATTACHMENT_TYPE_INVALID", "Quotation PDF is invalid");
    return store(filename, bytes);
  }

  private OutboundAttachment store(String name, byte[] bytes) {
    if (storage == null)
      throw new ApiException(
          503,
          "MAIL_ATTACHMENT_STORAGE_UNAVAILABLE",
          "Private attachment storage is not configured.");
    if (name == null
        || name.isBlank()
        || name.length() > 255
        || name.contains("/")
        || name.contains("\\")
        || name.contains("..")
        || name.chars().anyMatch(Character::isISOControl))
      throw new ApiException(400, "MAIL_ATTACHMENT_NAME_INVALID", "Invalid attachment filename");
    if (bytes.length == 0 || bytes.length > 10_485_760)
      throw new ApiException(
          400, "MAIL_ATTACHMENT_SIZE_INVALID", "Attachment must be between 1 byte and 10 MB");
    String key = UUID.randomUUID().toString();
    try {
      String type = detect(bytes);
      if (type == null)
        throw new ApiException(
            400, "MAIL_ATTACHMENT_TYPE_INVALID", "Only PDF, PNG, JPEG and plain text are accepted");
      storage.put(key, bytes);
      AppUser user = SecurityConfig.current();
      OutboundAttachment attachment = new OutboundAttachment();
      attachment.setTenantId(user.getTenantId());
      attachment.setUploadedBy(user.getId());
      attachment.setFilename(name);
      attachment.setMediaType(type);
      attachment.setSizeBytes(bytes.length);
      attachment.setStorageKey(key);
      attachment.setSha256(
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
      attachment.setCreatedAt(Instant.now());
      return attachments.save(attachment);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      try {
        storage.delete(key);
      } catch (Exception ignored) {
      }
      throw new ApiException(500, "MAIL_ATTACHMENT_FAILED", "Could not store attachment");
    }
  }

  void delete(UUID id) {
    OutboundAttachment attachment = owned(id);
    if (attachment.getMessageId() != null)
      throw new ApiException(
          409, "MAIL_ATTACHMENT_IN_USE", "Remove the attachment from its draft first");
    try {
      if (storage != null) storage.delete(attachment.getStorageKey());
      attachments.delete(attachment);
    } catch (IOException e) {
      throw new ApiException(503, "MAIL_ATTACHMENT_UNAVAILABLE", "Attachment could not be deleted");
    }
  }

  @Scheduled(fixedDelayString = "${mail.attachments.cleanup-ms:3600000}")
  void cleanupAbandoned() {
    if (storage == null) return;
    for (OutboundAttachment attachment :
        attachments.findByMessageIdIsNullAndCreatedAtBefore(
            Instant.now().minus(java.time.Duration.ofDays(1)))) {
      try {
        storage.delete(attachment.getStorageKey());
        attachments.delete(attachment);
      } catch (IOException ignored) {
      }
    }
  }

  static String detect(byte[] b) {
    if (b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F')
      return "application/pdf";
    if (b.length >= 8
        && (b[0] & 255) == 137
        && b[1] == 'P'
        && b[2] == 'N'
        && b[3] == 'G'
        && (b[4] & 255) == 13
        && (b[5] & 255) == 10) return "image/png";
    if (b.length >= 4 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255)
      return "image/jpeg";
    try {
      String decoded =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(b))
              .toString();
      for (int i = 0; i < decoded.length(); i++) {
        char c = decoded.charAt(i);
        if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') return null;
      }
      return "text/plain";
    } catch (Exception e) {
      return null;
    }
  }

  OutboundAttachment owned(UUID id) {
    AppUser user = SecurityConfig.current();
    OutboundAttachment attachment =
        attachments
            .findByIdAndTenantId(id, user.getTenantId())
            .orElseThrow(
                () -> new ApiException(404, "MAIL_ATTACHMENT_NOT_FOUND", "Attachment not found"));
    if (!attachment.getUploadedBy().equals(user.getId()))
      throw new ApiException(403, "MAIL_ATTACHMENT_FORBIDDEN", "Attachment is not available");
    return attachment;
  }

  byte[] bytes(OutboundAttachment attachment) {
    if (storage == null)
      throw new ApiException(
          503,
          "MAIL_ATTACHMENT_STORAGE_UNAVAILABLE",
          "Private attachment storage is not configured.");
    try {
      return storage.get(attachment.getStorageKey());
    } catch (IOException e) {
      throw new ApiException(503, "MAIL_ATTACHMENT_UNAVAILABLE", "Attachment could not be read");
    }
  }
}
