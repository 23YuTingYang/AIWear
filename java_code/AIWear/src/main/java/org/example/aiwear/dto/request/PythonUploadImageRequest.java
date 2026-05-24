package org.example.aiwear.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Python图片向量上传接口请求参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PythonUploadImageRequest implements Serializable {

    /**
     * OSS 图片公网访问地址，Python 服务会根据这个地址下载图片。
     */
    private String ossUrl;

    /**
     * 当前上传用户 ID，用于生成 Redis 用户维度索引。
     */
    private Long userId;
}
