package org.example.aiwear.dto.response;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 上传图片响应实体类
 */
@Data
@Builder
public class UploadImageResponse implements Serializable {

    // 图片的URL
    private String url;

    // 图片的文件名
    private String fileName;

    // 图片的文件大小
    private Long fileSize;
}
