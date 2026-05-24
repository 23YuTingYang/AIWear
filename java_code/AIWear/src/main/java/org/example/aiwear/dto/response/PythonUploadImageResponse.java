package org.example.aiwear.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * Python图片向量上传接口响应
 */
@Data
public class PythonUploadImageResponse implements Serializable {

    /**
     * Python 服务业务状态码，200 表示图片描述和向量已经保存成功。
     */
    private Integer code;

    /**
     * Python 服务返回的提示信息。
     */
    private String message;

    /**
     * Python 服务返回的图片向量入库结果。
     */
    private UploadImageData data;

    @Data
    public static class UploadImageData implements Serializable {

        /**
         * qwen-vl-max 生成的图片文字描述和关键词。
         */
        private String description;

        /**
         * 入库图片的 OSS 地址。
         */
        private String ossUrl;

        /**
         * Python 服务写入 Redis 的图片详情 key。
         */
        private String redisKey;

        /**
         * Python 服务返回的用户 ID 字符串。
         */
        private String userId;

        /**
         * CLIP 图片向量维度，当前模型期望值为 512。
         */
        private Integer vectorSize;
    }
}
