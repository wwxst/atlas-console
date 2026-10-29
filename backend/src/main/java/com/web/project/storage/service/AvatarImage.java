package com.web.project.storage.service;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

/** 使用真实解码确认格式；尺寸限制防止小压缩文件产生过大解码内存。 */
public record AvatarImage(String contentType, String extension) {
    public static AvatarImage inspect(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > 2 * 1024 * 1024) {
            throw invalid();
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) { throw invalid(); }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                AvatarImage image = switch (format) {
                    case "jpeg", "jpg" -> new AvatarImage("image/jpeg", "jpg");
                    case "png" -> new AvatarImage("image/png", "png");
                    case "webp" -> new AvatarImage("image/webp", "webp");
                    default -> throw invalid();
                };
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width * height > 16_000_000L) { throw invalid(); }
                if (reader.read(0) == null) { throw invalid(); }
                return image;
            } finally { reader.dispose(); }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "头像必须是有效的 JPEG、PNG 或 WEBP 图片（不超过 2MB）", exception);
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.BAD_REQUEST, "头像必须是有效的 JPEG、PNG 或 WEBP 图片（不超过 2MB，最多1600万像素）");
    }
}
