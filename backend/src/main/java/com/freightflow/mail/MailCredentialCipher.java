package com.freightflow.mail;

import com.freightflow.common.exception.ApiException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class MailCredentialCipher {
  private final String encodedKey;
  private final SecureRandom random = new SecureRandom();

  MailCredentialCipher(@Value("${mailbox.encryption-key:}") String encodedKey) {
    this.encodedKey = encodedKey;
  }

  private SecretKeySpec key() {
    if (encodedKey == null || encodedKey.isBlank())
      throw new ApiException(
          503, "MAIL_ENCRYPTION_NOT_CONFIGURED", "Mailbox encryption key is not configured.");
    try {
      byte[] bytes = Base64.getDecoder().decode(encodedKey);
      if (bytes.length != 32) throw new IllegalArgumentException();
      return new SecretKeySpec(bytes, "AES");
    } catch (IllegalArgumentException e) {
      throw new ApiException(
          503,
          "MAIL_ENCRYPTION_NOT_CONFIGURED",
          "Mailbox encryption key must be a Base64 encoded 32-byte key.");
    }
  }

  String encrypt(String secret) {
    try {
      byte[] nonce = new byte[12];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
      byte[] payload = cipher.doFinal(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      byte[] combined = Arrays.copyOf(nonce, nonce.length + payload.length);
      System.arraycopy(payload, 0, combined, nonce.length, payload.length);
      return "v1:" + Base64.getEncoder().encodeToString(combined);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Mailbox credential encryption failed", e);
    }
  }

  String decrypt(String encrypted) {
    if (encrypted == null || !encrypted.startsWith("v1:"))
      throw new ApiException(
          503, "MAIL_CREDENTIAL_UNAVAILABLE", "Mailbox credentials are unavailable.");
    try {
      byte[] combined = Base64.getDecoder().decode(encrypted.substring(3));
      if (combined.length < 29) throw new IllegalArgumentException();
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, combined, 0, 12));
      return new String(
          cipher.doFinal(combined, 12, combined.length - 12),
          java.nio.charset.StandardCharsets.UTF_8);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(
          503, "MAIL_CREDENTIAL_UNAVAILABLE", "Mailbox credentials could not be decrypted.");
    }
  }
}
