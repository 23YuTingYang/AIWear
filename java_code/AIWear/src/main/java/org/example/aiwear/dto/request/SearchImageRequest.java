package org.example.aiwear.dto.request;


import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class SearchImageRequest {
    // 搜索内容
    private String query;

    // 搜索图片
    private MultipartFile file;
}
