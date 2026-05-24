package org.example.aiwear.service.impl;

import org.example.aiwear.dto.request.EditImageRequest;
import org.example.aiwear.dto.request.MergeImageRequest;
import org.example.aiwear.dto.request.SearchImageRequest;
import org.example.aiwear.dto.response.EditImageResponse;
import org.example.aiwear.dto.response.MergeImageResponse;
import org.example.aiwear.dto.response.SearchImageResponse;
import org.example.aiwear.dto.response.UploadImageResponse;
import org.example.aiwear.entity.ImageFile;
import org.example.aiwear.mapper.FileMapper;
import org.example.aiwear.service.PythonAiService;
import org.example.aiwear.service.RecordService;
import org.example.aiwear.util.AliyunOssService;
import org.example.aiwear.util.JWTUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = FileServiceImpl.class)
class FileServiceImplTest {

    @MockBean
    private FileMapper fileMapper;
    @MockBean
    private AliyunOssService aliyunOssService;
    @MockBean
    private PythonAiService pythonAiService;
    @MockBean
    private JWTUtil jwtUtil;
    @MockBean
    private RecordService recordService;

    @Autowired
    private FileServiceImpl fileService;

    private MockMultipartFile imageFile;

    @BeforeEach
    void setUp() {
        imageFile = new MockMultipartFile("file", "photo.png", "image/png", "img".getBytes());
    }

    // 测试说明：验证空文件上传时会抛出异常。
    @Test
    void uploadImage_shouldThrowWhenFileIsNull() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> fileService.uploadImage(null, "Bearer t"));
        assertThat(ex.getMessage()).isNotBlank();
    }

    // 测试说明：验证图片审核失败时，service 抛出异常，且不会继续上传 OSS 或写入数据库。
    @Test
    void uploadImage_shouldThrowWhenValidationFails() {
        when(pythonAiService.validateImage(imageFile)).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> fileService.uploadImage(imageFile, "Bearer t"));

        assertThat(ex.getMessage()).isNotBlank();
        //不管它本来会传什么参数 这两个方法都不允许被调用
        verify(aliyunOssService, never()).uploadImage(any());
        verify(fileMapper, never()).insert(any());
    }

    // 测试说明：验证上传成功后会写入图片记录并通知 Python 服务生成向量。
    @Test
    void uploadImage_shouldPersistAndUploadVectorOnSuccess() {
        when(pythonAiService.validateImage(imageFile)).thenReturn(true);
        when(jwtUtil.parseToken("Bearer t")).thenReturn("token");
        when(jwtUtil.getUserId("token")).thenReturn(7L);
        when(aliyunOssService.uploadImage(imageFile)).thenReturn("http://oss/p.png");

        UploadImageResponse response = fileService.uploadImage(imageFile, "Bearer t");

        verify(fileMapper).insert(any(ImageFile.class));
        verify(pythonAiService).uploadImageVector(7L, "http://oss/p.png");
        assertThat(response.getUrl()).isEqualTo("http://oss/p.png");
        assertThat(response.getFileName()).isEqualTo("photo.png");
        assertThat(response.getFileSize()).isEqualTo((long) "img".getBytes().length);
    }

    // 测试说明：验证搜索结果会补全当前用户图片文件名。
    @Test
    void search_shouldMapFileNameFromMyImages() {
        when(jwtUtil.parseToken("Bearer t")).thenReturn("token");
        when(jwtUtil.getUserId("token")).thenReturn(9L);

        ImageFile file = new ImageFile();
        file.setOssUrl("http://oss/a.png");
        file.setFileName("a.png");
        when(fileMapper.selectList(any())).thenReturn(List.of(file));

        SearchImageResponse result = new SearchImageResponse();
        result.setFilePath("http://oss/a.png");
        result.setSimilarity(0.9);
        when(pythonAiService.search(eq(9L), any(SearchImageRequest.class))).thenReturn(List.of(result));

        SearchImageRequest request = new SearchImageRequest();
        request.setQuery("shirt");

        List<SearchImageResponse> responses = fileService.search("Bearer t", request);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getFileName()).isEqualTo("a.png");
    }

    // 测试说明：验证 Python 搜索服务抛异常时，业务层会继续向上抛异常。
    @Test
    void search_shouldThrowWhenPythonAiServiceThrows() {
        when(jwtUtil.parseToken("Bearer t")).thenReturn("token");
        when(jwtUtil.getUserId("token")).thenReturn(9L);
        when(fileMapper.selectList(any())).thenReturn(List.of());

        SearchImageRequest request = new SearchImageRequest();
        request.setQuery("shirt");
        when(pythonAiService.search(eq(9L), any(SearchImageRequest.class)))
                .thenThrow(new RuntimeException("python down"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> fileService.search("Bearer t", request));

        assertThat(ex.getMessage()).contains("python down");
    }

    // 测试说明：验证用户编辑非本人图片时会抛出异常。
    @Test
    void edit_shouldThrowWhenImageNotOwnedByUser() {
        when(jwtUtil.parseToken("Bearer t")).thenReturn("token");
        when(jwtUtil.getUserId("token")).thenReturn(1L);
        when(fileMapper.selectList(any())).thenReturn(List.of());

        EditImageRequest request = new EditImageRequest();
        request.setImage("http://oss/notmine.png");
        request.setInstruction("change color");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> fileService.edit("Bearer t", request));
        assertThat(ex.getMessage()).isNotBlank();
    }

    // 测试说明：验证编辑成功后会调用 Python 服务并保存记录。
    @Test
    void edit_shouldCallPythonAndSaveRecord() {
        when(jwtUtil.parseToken("Bearer t")).thenReturn("token");
        when(jwtUtil.getUserId("token")).thenReturn(1L);

        ImageFile owned = new ImageFile();
        owned.setOssUrl("http://oss/mine.png");
        when(fileMapper.selectList(any())).thenReturn(List.of(owned));

        EditImageRequest request = new EditImageRequest();
        request.setImage("http://oss/mine.png");
        request.setInstruction("change");

        EditImageResponse editResp = new EditImageResponse();
        editResp.setUrl("http://tmp/result.png");
        editResp.setSaveUrl("http://oss/result.png");
        when(pythonAiService.edit(request)).thenReturn(editResp);

        EditImageResponse response = fileService.edit("Bearer t", request);

        assertThat(response.getSaveUrl()).isEqualTo("http://oss/result.png");
        verify(recordService).editSave(1L, request, editResp);
    }

    // 测试说明：验证 Python 编辑服务抛异常时，业务层不会保存编辑记录。
    @Test
    void edit_shouldThrowWhenPythonAiServiceThrows() {
        when(jwtUtil.parseToken("Bearer t")).thenReturn("token");
        when(jwtUtil.getUserId("token")).thenReturn(1L);

        ImageFile owned = new ImageFile();
        owned.setOssUrl("http://oss/mine.png");
        when(fileMapper.selectList(any())).thenReturn(List.of(owned));

        EditImageRequest request = new EditImageRequest();
        request.setImage("http://oss/mine.png");
        request.setInstruction("change");
        when(pythonAiService.edit(request)).thenThrow(new RuntimeException("python down"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> fileService.edit("Bearer t", request));

        assertThat(ex.getMessage()).contains("python down");
        verify(recordService, never()).editSave(any(), any(), any());
    }

    // 测试说明：验证合并时任一图片不属于当前用户会抛出异常。
    @Test
    void merge_shouldThrowWhenAnyImageNotOwnedByUser() {
        when(jwtUtil.parseToken("Bearer t")).thenReturn("token");
        when(jwtUtil.getUserId("token")).thenReturn(1L);

        ImageFile owned = new ImageFile();
        owned.setOssUrl("http://oss/mine-1.png");
        when(fileMapper.selectList(any())).thenReturn(List.of(owned));

        MergeImageRequest request = new MergeImageRequest();
        request.setImage1("http://oss/mine-1.png");
        request.setImage2("http://oss/not-mine.png");
        request.setInstruction("merge");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> fileService.merge("Bearer t", request));
        assertThat(ex.getMessage()).isNotBlank();
    }

    // 测试说明：验证合并成功后会调用 Python 服务并保存记录。
    @Test
    void merge_shouldCallPythonAndSaveRecord() {
        when(jwtUtil.parseToken("Bearer t")).thenReturn("token");
        when(jwtUtil.getUserId("token")).thenReturn(1L);

        ImageFile owned1 = new ImageFile();
        owned1.setOssUrl("http://oss/1.png");
        ImageFile owned2 = new ImageFile();
        owned2.setOssUrl("http://oss/2.png");
        when(fileMapper.selectList(any())).thenReturn(List.of(owned1, owned2));

        MergeImageRequest request = new MergeImageRequest();
        request.setImage1("http://oss/1.png");
        request.setImage2("http://oss/2.png");
        request.setInstruction("merge");

        MergeImageResponse mergeResp = new MergeImageResponse();
        mergeResp.setUrl("http://tmp/merge.png");
        mergeResp.setSaveUrl("http://oss/merge.png");
        when(pythonAiService.merge(request)).thenReturn(mergeResp);

        MergeImageResponse response = fileService.merge("Bearer t", request);

        assertThat(response.getSaveUrl()).isEqualTo("http://oss/merge.png");
        verify(recordService).mergeSave(1L, request, mergeResp);
    }

    // 测试说明：验证 Python 合并服务抛异常时，业务层不会保存合并记录。
    @Test
    void merge_shouldThrowWhenPythonAiServiceThrows() {
        when(jwtUtil.parseToken("Bearer t")).thenReturn("token");
        when(jwtUtil.getUserId("token")).thenReturn(1L);

        ImageFile owned1 = new ImageFile();
        owned1.setOssUrl("http://oss/1.png");
        ImageFile owned2 = new ImageFile();
        owned2.setOssUrl("http://oss/2.png");
        when(fileMapper.selectList(any())).thenReturn(List.of(owned1, owned2));

        MergeImageRequest request = new MergeImageRequest();
        request.setImage1("http://oss/1.png");
        request.setImage2("http://oss/2.png");
        request.setInstruction("merge");
        when(pythonAiService.merge(request)).thenThrow(new RuntimeException("python down"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> fileService.merge("Bearer t", request));

        assertThat(ex.getMessage()).contains("python down");
        verify(recordService, never()).mergeSave(any(), any(), any());
    }
}
