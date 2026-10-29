package com.web.project.storage.service;

import com.web.project.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class StorageBoundaryTest {
    @Test
    void actualPngIsAcceptedAndFakeImageRejected() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        assertEquals("image/png", AvatarImage.inspect(bytes.toByteArray()).contentType());
        bytes.reset();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
        assertEquals("image/jpeg", AvatarImage.inspect(bytes.toByteArray()).contentType());
        byte[] webp = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        assertEquals("image/webp", AvatarImage.inspect(webp).contentType());
        assertThrows(BusinessException.class, () -> AvatarImage.inspect("fake png".getBytes()));
        assertThrows(BusinessException.class, () -> AvatarImage.inspect(new byte[2 * 1024 * 1024 + 1]));
    }

    @Test
    void domainsAreHttpsOriginsAndRegionsCannotBecomeEndpoints() {
        assertEquals("https://cdn.example.com", StoragePolicy.domain("https://cdn.example.com/"));
        for (String value : new String[]{"http://cdn.example.com", "https://user:secret@example.com", "https://cdn.example.com/path", "https://cdn.example.com?q=x"}) {
            assertThrows(BusinessException.class, () -> StoragePolicy.domain(value));
        }
        assertEquals("https://oss-cn-hangzhou.aliyuncs.com", StoragePolicy.endpoint("oss-cn-hangzhou"));
        assertThrows(BusinessException.class, () -> StoragePolicy.endpoint("oss-evil.example.com"));
    }

    @Test
    void encryptionRequiresDeploymentKeyAndAuthenticatesCiphertext() {
        CredentialCipher absent = new CredentialCipher("");
        assertThrows(BusinessException.class, () -> absent.encrypt("secret"));
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        CredentialCipher cipher = new CredentialCipher(key);
        String first = cipher.encrypt("secret");
        assertNotEquals(first, cipher.encrypt("secret"));
        assertFalse(first.contains("secret"));
        assertEquals("secret", cipher.decrypt(first));
        byte[] corrupted = Base64.getDecoder().decode(first);
        corrupted[corrupted.length - 1] ^= 1;
        assertThrows(BusinessException.class, () -> cipher.decrypt(Base64.getEncoder().encodeToString(corrupted)));
    }
}
