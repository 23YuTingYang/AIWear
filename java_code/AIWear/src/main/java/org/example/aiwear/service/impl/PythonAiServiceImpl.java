package org.example.aiwear.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.aiwear.dto.request.EditImageRequest;
import org.example.aiwear.dto.request.MergeImageRequest;
import org.example.aiwear.dto.request.PythonUploadImageRequest;
import org.example.aiwear.dto.request.SearchImageRequest;
import org.example.aiwear.dto.response.*;
import org.example.aiwear.service.PythonAiService;
import org.example.aiwear.util.AliyunOssService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * Python AI 服务 HTTP 客户端
 */
@Slf4j
@Service
public class PythonAiServiceImpl implements PythonAiService {

    private final RestClient.Builder restClientBuilder;
    private final AliyunOssService aliyunOssService;

    @Value("${python.ai.base-url:http://127.0.0.1:6789}")
    private String pythonAiBaseUrl;

    private RestClient restClient;

    public PythonAiServiceImpl(RestClient.Builder restClientBuilder, AliyunOssService aliyunOssService) {
        this.restClientBuilder = restClientBuilder;
        this.aliyunOssService = aliyunOssService;
    }

    @PostConstruct
    public void init() {
        // 配置注入完成后再创建客户端，保证 base-url 已经可用。
        restClient = restClientBuilder.baseUrl(normalizeBaseUrl(pythonAiBaseUrl)).build();
    }

    // 图片验证
    @Override
    public boolean validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("图片文件不能为空");
        }

        // Python 的 /api/validate-image 接口要求 multipart/form-data，字段名固定为 file。
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        //意思是添加一个文件字段：字段名 = file  字段内容 = 当前上传图片
        bodyBuilder.part("file", file.getResource())
                .filename(file.getOriginalFilename() == null ? "image" : file.getOriginalFilename());

        // 构建一个 multipart/form-data 请求体
        //一个file 可以对应多个图片
        MultiValueMap<String, HttpEntity<?>> multipartBody = bodyBuilder.build();

        try {
            // 返回示例：{"code":200,"allow":true}。
            PythonValidateImageResponse response = restClient.post()
                    .uri("/api/validate-image")
                    .contentType(MediaType.MULTIPART_FORM_DATA)  // Spring 中定义的 multipart/form-data 常量
                    .body(multipartBody)
                    .retrieve()
                    .body(PythonValidateImageResponse.class);

            if (response == null) {
                throw new RuntimeException("Python图片审核服务响应为空");
            }
            if (!Objects.equals(response.getCode(), 200)) {
                throw new RuntimeException("Python图片审核服务处理失败");
            }
            // allow=false 表示图片不属于衣服/服装/穿搭或人物人像，上传流程会在 Java 侧终止。
            return Boolean.TRUE.equals(response.getAllow());
        } catch (RestClientException e) {
            log.error("调用Python图片审核服务失败，fileName: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("调用Python图片审核服务失败", e);
        }
    }

    // 图片上传向量
    @Override
    public void uploadImageVector(Long userId, String ossUrl) {
        if (userId == null) {
            throw new RuntimeException("userId不能为空");
        }
        if (ossUrl == null || ossUrl.isBlank()) {
            throw new RuntimeException("ossUrl不能为空");
        }

        PythonUploadImageRequest request = PythonUploadImageRequest.builder()
                .userId(userId)
                .ossUrl(ossUrl)
                .build();

        try {
            // Python 服务接收 JSON 参数：{"ossUrl":"...","userId":123}。
            PythonUploadImageResponse response = restClient.post()
                    .uri("/api/upload-image")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(PythonUploadImageResponse.class);

            if (response == null) {
                throw new RuntimeException("Python图片向量服务响应为空");
            }
            if (!Objects.equals(response.getCode(), 200)) {
                throw new RuntimeException("Python图片向量服务处理失败: " + response.getMessage());
            }
        } catch (RestClientException e) {
            log.error("调用Python图片向量服务失败，userId: {}, ossUrl: {}", userId, ossUrl, e);
            throw new RuntimeException("调用Python图片向量服务失败", e);
        }
    }

    /**
     * 调用 Python 图片检索接口。
     *
     * 参数:
     * userId 当前登录用户 ID，只检索当前用户上传过的图片。
     * searchImageRequest 前端传入的搜索条件，query 用于文搜图，file 用于图搜图。
     *
     * 返回:
     * Python 服务按相似度倒序返回的图片列表。
     */
    @Override
    public List<SearchImageResponse> search(Long userId, SearchImageRequest searchImageRequest) {
        if (userId == null) {
            throw new RuntimeException("userId不能为空");
        }
        String query = searchImageRequest == null ? null : searchImageRequest.getQuery();
        MultipartFile file = searchImageRequest == null ? null : searchImageRequest.getFile();
        if ((query == null || query.isBlank()) && (file == null || file.isEmpty())) {
            return List.of();
        }

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("userId", userId.toString());
        if (query != null && !query.isBlank()) {
            bodyBuilder.part("query", query.trim());
        }
        if (file != null && !file.isEmpty()) {
            bodyBuilder.part("file", file.getResource())
                    .filename(file.getOriginalFilename() == null ? "image" : file.getOriginalFilename());
        }
        MultiValueMap<String, HttpEntity<?>> multipartBody = bodyBuilder.build();

        try {
            PythonSearchImageResponse response = restClient.post()
                    .uri("/api/search-image")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipartBody)
                    .retrieve()
                    .body(PythonSearchImageResponse.class);

            if (response == null) {
                throw new RuntimeException("Python图片检索服务响应为空");
            }
            if (!Objects.equals(response.getCode(), 200)) {
                throw new RuntimeException("Python图片检索服务处理失败: " + response.getMessage());
            }
            return response.getData() == null ? List.of() : response.getData();
        } catch (RestClientException e) {
            log.error("调用Python图片检索服务失败，userId: {}", userId, e);
            throw new RuntimeException("调用Python图片检索服务失败", e);
        }
    }

    // 图片编辑
    //1.接收前端传来的图片编辑请求。
    //2.从请求里拿到原图 OSS 地址和编辑指令。
    //3.下载原图。
    //4.把原图和编辑指令以 multipart/form-data 形式发给 Python 服务。
    //5.Python 返回编辑后的图片 URL。
    //6.Java 再把这个结果图下载下来，上传到自己的阿里云 OSS。
    //7.最后返回两个地址：Python 临时地址 url，自己 OSS 的持久地址 saveUrl
    @Override
    public EditImageResponse edit(EditImageRequest editImageRequest) {
        if (editImageRequest == null) {
            throw new IllegalArgumentException("编辑图片请求不能为空");
        }
        String imageUrl = editImageRequest.getImage();
        String instruction = editImageRequest.getInstruction();
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("图片地址不能为空");
        }
        if (instruction == null || instruction.isBlank()) {
            throw new IllegalArgumentException("编辑指令不能为空");
        }

        // 1. Python 的编辑接口接收 multipart 文件，所以先把 OSS URL 转成文件资源。
        //OSS地址的图片伪装成一个 multipart 文件传给 Python。multipart 文件通常需要一个文件名
        String sourceFileName = buildFilename(imageUrl, "edit-source.png");
        ByteArrayResource imageResource = new ByteArrayResource(downloadImageBytes(imageUrl)) {
            @Override
            public String getFilename() {
                return sourceFileName;
            }
        };

        // 2. 构造调用 Python /api/skill/image 的请求参数：编辑指令 + 原图文件。
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("instruction", instruction.trim());
        bodyBuilder.part("file", imageResource).filename(sourceFileName);
        MultiValueMap<String, HttpEntity<?>> multipartBody = bodyBuilder.build();

        try {
            // 3. 调用 Python 服务进行图片编辑，返回值里只关心生成后的图片 URL。
            PythonEditImageResponse response = restClient.post()
                    .uri("/api/skill/image")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipartBody)
                    .retrieve()
                    .body(PythonEditImageResponse.class);
            //获取编辑结果图片的 URL
            String resultUrl = getEditResultUrl(response);

            // 4. Python 返回的 DashScope 地址可能会过期，需要下载后重新保存到自己的 OSS。
            String saveUrl = aliyunOssService.uploadImageBytes(
                    downloadImageBytes(resultUrl),
                    buildFilename(resultUrl, "python_edit_result.png"),
                    guessImageContentType(resultUrl)
            );

            // 5. 封装对外接口需要的两个地址：临时结果地址 + 自己 OSS 的持久化地址。
            EditImageResponse editImageResponse = new EditImageResponse();
            editImageResponse.setUrl(resultUrl);
            editImageResponse.setSaveUrl(saveUrl);
            return editImageResponse;
        } catch (RestClientException e) {
            log.error("调用Python图片编辑服务失败，imageUrl: {}", imageUrl, e);
            throw new RuntimeException("调用Python图片编辑服务失败", e);
        }
    }

    //合并图片
    @Override
    public MergeImageResponse merge(MergeImageRequest mergeImageRequest) {
        if (mergeImageRequest == null) {
            throw new IllegalArgumentException("合并图片请求不能为空");
        }
        String imageUrl1 = mergeImageRequest.getImage1();
        String imageUrl2 = mergeImageRequest.getImage2();
        String instruction = mergeImageRequest.getInstruction();
        if (imageUrl1 == null || imageUrl1.isBlank()) {
            throw new IllegalArgumentException("图片1地址不能为空");
        }
        if (imageUrl2 == null || imageUrl2.isBlank()) {
            throw new IllegalArgumentException("图片2地址不能为空");
        }
        if (instruction == null || instruction.isBlank()) {
            throw new IllegalArgumentException("合并指令不能为空");
        }

        String sourceFileName1 = buildFilename(imageUrl1, "merge-source-1.png");
        String sourceFileName2 = buildFilename(imageUrl2, "merge-source-2.png");
        ByteArrayResource imageResource1 = new ByteArrayResource(downloadImageBytes(imageUrl1)) {
            @Override
            public String getFilename() {
                return sourceFileName1;
            }
        };
        ByteArrayResource imageResource2 = new ByteArrayResource(downloadImageBytes(imageUrl2)) {
            @Override
            public String getFilename() {
                return sourceFileName2;
            }
        };

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("instruction", instruction.trim());
        bodyBuilder.part("file1", imageResource1).filename(sourceFileName1);
        bodyBuilder.part("file2", imageResource2).filename(sourceFileName2);
        MultiValueMap<String, HttpEntity<?>> multipartBody = bodyBuilder.build();

        try {
            PythonEditImageResponse response = restClient.post()
                    .uri("/api/skill/image")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipartBody)
                    .retrieve()
                    .body(PythonEditImageResponse.class);
            String resultUrl = getEditResultUrl(response);

            String saveUrl = aliyunOssService.uploadImageBytes(
                    downloadImageBytes(resultUrl),
                    buildFilename(resultUrl, "python_merge_result.png"),
                    guessImageContentType(resultUrl)
            );

            MergeImageResponse mergeImageResponse = new MergeImageResponse();
            mergeImageResponse.setUrl(resultUrl);
            mergeImageResponse.setSaveUrl(saveUrl);
            return mergeImageResponse;
        } catch (RestClientException e) {
            log.error("调用Python图片合并服务失败，imageUrl1: {}, imageUrl2: {}", imageUrl1, imageUrl2, e);
            throw new RuntimeException("调用Python图片合并服务失败", e);
        }
    }

    // 下载图片
    private byte[] downloadImageBytes(String imageUrl) {
        try {
            byte[] imageBytes = restClient.get()
                    .uri(URI.create(imageUrl.trim()))
                    .retrieve()
                    .body(byte[].class);
            if (imageBytes == null || imageBytes.length == 0) {
                throw new RuntimeException("下载图片内容为空");
            }
            return imageBytes;
        } catch (RestClientException | IllegalArgumentException e) {
            throw new RuntimeException("下载图片失败: " + imageUrl, e);
        }
    }

    //返回图片编辑结果 URL
    private String getEditResultUrl(PythonEditImageResponse response) {
        if (response == null) {
            throw new RuntimeException("Python图片编辑服务响应为空");
        }
        if (!Boolean.TRUE.equals(response.getSuccess()) || response.getUrl() == null || response.getUrl().isBlank()) {
            throw new RuntimeException("Python图片编辑服务处理失败: " + response.getMessage());
        }
        return response.getUrl();
    }

    //构建文件名
    private String buildFilename(String imageUrl, String defaultFilename) {
        try {
            String path = URI.create(imageUrl.trim()).getPath();
            if (path != null) {
                int index = path.lastIndexOf('/');
                String filename = index >= 0 ? path.substring(index + 1) : path;
                if (!filename.isBlank() && filename.contains(".")) {
                    return filename;
                }
            }
        } catch (IllegalArgumentException ignored) {
        }
        return defaultFilename;
    }

    // 获取图片类型
    private String guessImageContentType(String imageUrl) {
        String lowerUrl = imageUrl == null ? "" : imageUrl.toLowerCase();
        if (lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg")) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        if (lowerUrl.contains(".gif")) {
            return "image/gif";
        }
        return MediaType.IMAGE_PNG_VALUE;
    }



    // 规范 baseUrl
    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://127.0.0.1:6789";
        }
        // 去掉末尾斜杠，避免 baseUrl + uri 拼出双斜杠。
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
