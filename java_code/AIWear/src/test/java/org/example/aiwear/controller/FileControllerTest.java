package org.example.aiwear.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.aiwear.dto.request.EditImageRequest;
import org.example.aiwear.dto.request.MergeImageRequest;
import org.example.aiwear.dto.response.EditImageResponse;
import org.example.aiwear.dto.response.MergeImageResponse;
import org.example.aiwear.dto.response.SearchImageResponse;
import org.example.aiwear.dto.response.UploadImageResponse;
import org.example.aiwear.entity.ImageFile;
import org.example.aiwear.service.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
@AutoConfigureMockMvc(addFilters = false)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FileService fileService;

// 测试说明：验证上传文件为空时，接口返回客户端错误结果
    @Test
    void uploadImage_shouldReturnClientErrorWhenFileIsEmpty() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "", "application/octet-stream", new byte[0]);

        mockMvc.perform(multipart("/api/file/upload/image")
                        .file(empty)
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

// 测试说明：验证上传成功时，接口返回成功结构和图片地址。
    @Test
    void uploadImage_shouldReturnSuccessWhenUploaded() throws Exception {
        UploadImageResponse uploadResponse = UploadImageResponse.builder()
                .url("http://oss/a.png")
                .fileName("a.png")
                .fileSize(10L)
                .build();
        when(fileService.uploadImage(any(), eq("Bearer t"))).thenReturn(uploadResponse);

        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "img".getBytes());

        mockMvc.perform(multipart("/api/file/upload/image")
                        .file(file)
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.url").value("http://oss/a.png"));
    }

// 测试说明：验证查询“我的图片”时，接口返回成功结果和图片列表。
    @Test
    void myImages_shouldReturnSuccessResult() throws Exception {
        ImageFile file = new ImageFile();
        file.setId(1L);
        file.setFileName("a.png");
        when(fileService.myImages("Bearer t")).thenReturn(List.of(file));

        mockMvc.perform(get("/api/file/my-images").header("Authorization", "Bearer t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].fileName").value("a.png"));
    }

// 测试说明：验证搜索接口能正确绑定 query 参数并返回结果。
    @Test
    void search_shouldBindQueryAndReturnSuccess() throws Exception {
        SearchImageResponse item = new SearchImageResponse();
        item.setFilePath("http://oss/a.png");
        item.setSimilarity(0.9);
        when(fileService.search(eq("Bearer t"), any())).thenReturn(List.of(item));

        mockMvc.perform(post("/api/file/search")
                        .header("Authorization", "Bearer t")
                        .param("query", "red shirt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].filePath").value("http://oss/a.png"));
    }

// 测试说明：验证编辑接口能正确接收 JSON 请求体和 Authorization 请求头。
    @Test
    void edit_shouldBindBodyAndAuthorizationHeader() throws Exception {
        EditImageRequest request = new EditImageRequest();
        request.setImage("http://oss/a.png");
        request.setInstruction("change color");

        EditImageResponse response = new EditImageResponse();
        response.setUrl("http://tmp/a.png");
        response.setSaveUrl("http://oss/a-2.png");
        when(fileService.edit(eq("Bearer t"), any(EditImageRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/file/edit")
                        .header("Authorization", "Bearer t")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.saveUrl").value("http://oss/a-2.png"));
    }

// 测试说明：验证合并接口能正确接收 JSON 请求体和 Authorization 请求头。
    @Test
    void merge_shouldBindBodyAndAuthorizationHeader() throws Exception {
        MergeImageRequest request = new MergeImageRequest();
        request.setImage1("http://oss/1.png");
        request.setImage2("http://oss/2.png");
        request.setInstruction("merge");

        MergeImageResponse response = new MergeImageResponse();
        response.setUrl("http://tmp/m.png");
        response.setSaveUrl("http://oss/m.png");
        when(fileService.merge(eq("Bearer t"), any(MergeImageRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/file/merge")
                        .header("Authorization", "Bearer t")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.saveUrl").value("http://oss/m.png"));
    }
}
