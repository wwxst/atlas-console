package com.web.project.storage.service;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** 不在启动时要求密钥，本地存储不依赖 OSS 凭据。 */
public final class CredentialCipher {
    private final String configuredKey;

    public CredentialCipher(String configuredKey) { this.configuredKey = configuredKey; }

    public String encrypt(String plaintext) {
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] result = Arrays.copyOf(nonce, nonce.length + ciphertext.length);
            System.arraycopy(ciphertext, 0, result, nonce.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException exception) { throw failure(exception); }
    }

    public String decrypt(String encrypted) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encrypted);
            if (bytes.length < 28) { throw new IllegalArgumentException("Invalid ciphertext"); }
            return new String(cipher(Cipher.DECRYPT_MODE, Arrays.copyOf(bytes, 12))
                    .doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) { throw failure(exception); }
    }

    private Cipher cipher(int mode, byte[] nonce) throws GeneralSecurityException {
        byte[] key;
        try { key = Base64.getDecoder().decode(configuredKey == null ? "" : configuredKey); }
        catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "STORAGE_ENCRYPTION_KEY 必须是 Base64 编码的32字节密钥", exception);
        }
        if (key.length != 32) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "请配置 STORAGE_ENCRYPTION_KEY（Base64 编码的32字节密钥）");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        return cipher;
    }

    private BusinessException failure(Exception exception) {
        return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "存储凭据加解密失败，请检查部署密钥", exception);
    }
}
