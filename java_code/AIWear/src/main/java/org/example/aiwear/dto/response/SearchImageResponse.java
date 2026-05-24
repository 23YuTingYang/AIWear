package org.example.aiwear.dto.response;


import lombok.Data;

@Data
public class SearchImageResponse {
    /**
     * 图片访问地址，对应 Python 返回的 filePath。
     */
    private String filePath;

    /**
     * 图片原始文件名，由 Java 根据 OSS 地址从数据库记录中补充。
     */
    private String fileName;

    /**
     * 搜索相似度，文搜图和图搜图都会返回该字段。
     */
    private Double similarity;
}
