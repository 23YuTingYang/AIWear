package org.example.aiwear.service.impl;

import org.example.aiwear.dto.request.EditImageRequest;
import org.example.aiwear.dto.request.MergeImageRequest;
import org.example.aiwear.dto.request.SearchImageRequest;
import org.example.aiwear.dto.response.SearchImageResponse;
import org.example.aiwear.util.AliyunOssService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest(
        classes = {PythonAiServiceImpl.class, PythonAiServiceImplTest.TestConfig.class},
        properties = "python.ai.base-url=http://127.0.0.1:6789"
)
class PythonAiServiceImplTest {

    @Configuration
    static class TestConfig {
        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }
    }

    @MockBean
    private AliyunOssService aliyunOssService;

    @Autowired
    private PythonAiServiceImpl pythonAiService;

    @Autowired
    private RestClient.Builder restClientBuilder;

    private MockRestServiceServer server;


    @BeforeEach
    void setUp() {
        //创建一个 模拟 HTTP 服务端 用来拦截 RestClient 发出去的 HTTP 请求。
        server = MockRestServiceServer.bindTo(restClientBuilder).ignoreExpectOrder(true).build();
        //把 pythonAiService 里真正用于发请求的 restClient 换成测试版
        //让后续测试发请求时不会访问真实 Python 服务
        //而是进入 MockRestServiceServer
        ReflectionTestUtils.setField(
                pythonAiService,
                "restClient",
                restClientBuilder.baseUrl("http://127.0.0.1:6789").build()
        );
    }

    // 测试说明：验证空文件审核时会抛出异常。
    @Test
    void validateImage_shouldThrowWhenFileIsEmpty() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> pythonAiService.validateImage(empty));
        assertThat(ex.getMessage()).isNotBlank();
    }

    // 测试说明：验证审核接口返回成功时结果为 true。
    @Test
    void validateImage_shouldReturnTrueWhenCode200AndAllowTrue() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "img".getBytes());

        server.expect(requestTo("http://127.0.0.1:6789/api/validate-image"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"allow\":true}", MediaType.APPLICATION_JSON));

        boolean allow = pythonAiService.validateImage(file);

        assertThat(allow).isTrue();
        server.verify();
    }

    // 测试说明：验证审核接口返回失败业务码时会抛出异常。
    @Test
    void validateImage_shouldThrowWhenCodeNot200() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "img".getBytes());

        server.expect(requestTo("http://127.0.0.1:6789/api/validate-image"))
                .andRespond(withSuccess("{\"code\":500,\"allow\":false}", MediaType.APPLICATION_JSON));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> pythonAiService.validateImage(file));
        assertThat(ex.getMessage()).isNotBlank();
    }

    // 测试说明：验证 userId 为空时上传向量会抛出异常。
    @Test
    void uploadImageVector_shouldThrowWhenUserIdIsNull() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> pythonAiService.uploadImageVector(null, "http://oss/a.png"));
        assertThat(ex.getMessage()).contains("userId");
    }

    // 测试说明：验证 ossUrl 为空时上传向量会抛出异常。
    @Test
    void uploadImageVector_shouldThrowWhenOssUrlIsBlank() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> pythonAiService.uploadImageVector(1L, " "));
        assertThat(ex.getMessage()).contains("ossUrl");
    }

    // 测试说明：验证向量上传接口返回成功业务码时不会抛异常。
    @Test
    void uploadImageVector_shouldPassWhenCode200() {
        server.expect(requestTo("http://127.0.0.1:6789/api/upload-image"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"code\":200,\"message\":\"ok\"}", MediaType.APPLICATION_JSON));

        pythonAiService.uploadImageVector(3L, "http://oss/a.png");

        server.verify();
    }

    // 测试说明：验证向量上传接口返回失败业务码时会抛出异常。
    @Test
    void uploadImageVector_shouldThrowWhenCodeNot200() {
        server.expect(requestTo("http://127.0.0.1:6789/api/upload-image"))
                .andRespond(withSuccess("{\"code\":400,\"message\":\"bad\"}", MediaType.APPLICATION_JSON));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> pythonAiService.uploadImageVector(3L, "http://oss/a.png"));
        assertThat(ex.getMessage()).isNotBlank();
    }

    // 测试说明：验证搜索条件为空时直接返回空列表。
    @Test
    void search_shouldReturnEmptyWhenQueryAndFileBothMissing() {
        SearchImageRequest request = new SearchImageRequest();
        List<SearchImageResponse> data = pythonAiService.search(1L, request);
        assertThat(data).isEmpty();
    }

    // 测试说明：验证搜索接口返回成功时能够正确解析结果列表。
    @Test
    void search_shouldParseResultsWhenCode200() {
        SearchImageRequest request = new SearchImageRequest();
        request.setQuery("red shirt");

        server.expect(requestTo("http://127.0.0.1:6789/api/search-image"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(req -> assertThat(req.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).contains("multipart/form-data"))
                .andRespond(withSuccess(
                        "{\"code\":200,\"message\":\"ok\",\"data\":[{\"filePath\":\"http://oss/a.png\",\"similarity\":0.9}]}",
                        MediaType.APPLICATION_JSON
                ));

        List<SearchImageResponse> data = pythonAiService.search(1L, request);

        assertThat(data).hasSize(1);
        assertThat(data.get(0).getFilePath()).isEqualTo("http://oss/a.png");
        server.verify();
    }

    // 测试说明：验证 Python 搜索服务不可达时会抛出调用失败异常。
    @Test
    void search_shouldThrowWhenPythonServiceUnavailable() {
        SearchImageRequest request = new SearchImageRequest();
        request.setQuery("red shirt");

        server.expect(requestTo("http://127.0.0.1:6789/api/search-image"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withException(new IOException("python down")));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> pythonAiService.search(1L, request));

        assertThat(ex.getMessage()).contains("调用Python图片检索服务失败");
        server.verify();
    }

    // 测试说明：验证 Python 编辑服务不可达时会抛出调用失败异常。
    @Test
    void edit_shouldThrowWhenPythonServiceUnavailable() {
        EditImageRequest request = new EditImageRequest();
        request.setImage("http://oss/source.png");
        request.setInstruction("change color");

        server.expect(requestTo("http://oss/source.png"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("img", MediaType.IMAGE_PNG));
        server.expect(requestTo("http://127.0.0.1:6789/api/skill/image"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withException(new IOException("python down")));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> pythonAiService.edit(request));

        assertThat(ex.getMessage()).contains("调用Python图片编辑服务失败");
        server.verify();
    }

    // 测试说明：验证 Python 合并服务不可达时会抛出调用失败异常。
    @Test
    void merge_shouldThrowWhenPythonServiceUnavailable() {
        MergeImageRequest request = new MergeImageRequest();
        request.setImage1("http://oss/source-1.png");
        request.setImage2("http://oss/source-2.png");
        request.setInstruction("merge");

        server.expect(requestTo("http://oss/source-1.png"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("img1", MediaType.IMAGE_PNG));
        server.expect(requestTo("http://oss/source-2.png"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("img2", MediaType.IMAGE_PNG));
        server.expect(requestTo("http://127.0.0.1:6789/api/skill/image"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withException(new IOException("python down")));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> pythonAiService.merge(request));

        assertThat(ex.getMessage()).contains("调用Python图片合并服务失败");
        server.verify();
    }
}
