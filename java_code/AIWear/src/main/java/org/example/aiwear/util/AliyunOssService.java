package org.example.aiwear.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

/**
 * 阿里云 OSS 文件上传服务类
 */
@Slf4j
@Service
public class AliyunOssService {

    private static final String OSS_UPLOAD_ERROR_MESSAGE = "上传图片到OSS失败，请稍后重试";

    @Value("${aliyun.oss.endpoint}")
    private String endpoint;

    @Value("${aliyun.oss.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret}")
    private String accessKeySecret;

    @Value("${aliyun.oss.bucket-name}")
    private String bucketName;

    @Value("${aliyun.oss.max-size}")
    private Long maxSize;
    /**
     * 上传前端传入的 MultipartFile 图片，并返回 OSS 访问地址。
     */
    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("图片文件不能为空");
        }
        if (file.getSize() > maxSize) {
            throw new RuntimeException("图片大小超过限制");
        }

        try (InputStream inputStream = file.getInputStream()) {
            return uploadImageToOss(
                    inputStream,
                    file.getSize(),
                    file.getOriginalFilename(),
                    file.getContentType()
            );
        } catch (Exception e) {
            log.error("上传图片到OSS失败，文件名: {}", file.getOriginalFilename(), e);
            throw new RuntimeException(OSS_UPLOAD_ERROR_MESSAGE);
        }
    }

    /**
     * 上传内存中的图片字节，主要用于保存 AI 编辑后的结果图。
     */
    public String uploadImageBytes(byte[] imageBytes, String originalFilename, String contentType) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new RuntimeException("图片内容不能为空");
        }
        if (imageBytes.length > maxSize) {
            throw new RuntimeException("图片大小超过限制");
        }

        try (InputStream inputStream = new ByteArrayInputStream(imageBytes)) {
            return uploadImageToOss(
                    inputStream,
                    imageBytes.length,
                    originalFilename,
                    contentType
            );
        } catch (Exception e) {
            log.error("上传图片字节到OSS失败，文件名: {}", originalFilename, e);
            throw new RuntimeException(OSS_UPLOAD_ERROR_MESSAGE);
        }
    }

    /**
     * OSS 上传的公共核心逻辑：只关心输入流、大小、文件名和 Content-Type。
     */
    private String uploadImageToOss(InputStream inputStream, long size, String originalFilename, String contentType) {
        String objectName = buildObjectName(originalFilename);
        OSS ossClient = new OSSClientBuilder().build(buildClientEndpoint(), trim(accessKeyId), trim(accessKeySecret));

        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size);
            if (contentType != null && !contentType.isBlank()) {
                metadata.setContentType(contentType);
            }
            ossClient.putObject(bucketName, objectName, inputStream, metadata);
            return buildOssUrl(objectName);
        } catch (OSSException e) {
            log.error("上传图片到OSS失败，文件名: {}, 错误码: {}, 请求ID: {}, 错误信息: {}",
                    originalFilename, e.getErrorCode(), e.getRequestId(), e.getErrorMessage(), e);
            throw new RuntimeException(OSS_UPLOAD_ERROR_MESSAGE);
        } catch (ClientException e) {
            log.error("上传图片到OSS失败，文件名: {}, 客户端错误: {}", originalFilename, e.getMessage(), e);
            throw new RuntimeException(OSS_UPLOAD_ERROR_MESSAGE);
        } finally {
            ossClient.shutdown();
        }
    }
    /**
     * 构造 OSS 访问路径：uuid.ext
     */
    private String buildObjectName(String originalFilename) {
        String ext = "";
        if (originalFilename != null) {
            int index = originalFilename.lastIndexOf(".");
            if (index >= 0) {
                ext = originalFilename.substring(index);
            }
        }
        return UUID.randomUUID() + ext;
    }

    /**
     * 根据 bucket、endpoint 和 objectName 拼接 OSS 访问地址
     */
    private String buildOssUrl(String objectName) {
        String cleanEndpoint = trim(endpoint)
                .replaceFirst("^https?://", "")
                .replaceFirst("/+$", "");
        return "https://" + trim(bucketName) + "." + cleanEndpoint + "/" + objectName;
    }

    /**
     * OSS SDK 客户端使用的 endpoint，配置中未写协议时默认补 https
     */
    private String buildClientEndpoint() {
        String cleanEndpoint = trim(endpoint);
        if (cleanEndpoint.startsWith("http://") || cleanEndpoint.startsWith("https://")) {
            return cleanEndpoint;
        }
        return "https://" + cleanEndpoint;
    }

    /**
     * 如果字符串是 null，返回空字符串
     * 否则去掉字符串前后的空格
     * 这样可以减少空指针异常，也避免配置值前后带空格导致 OSS 访问失败。
     */
    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
