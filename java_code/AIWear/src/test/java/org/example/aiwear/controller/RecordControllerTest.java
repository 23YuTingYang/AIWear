package org.example.aiwear.controller;

import org.example.aiwear.entity.Record;
import org.example.aiwear.service.RecordService;
import org.example.aiwear.util.JWTUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecordController.class)
@AutoConfigureMockMvc(addFilters = false)
class RecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JWTUtil jwtUtil;

    @MockBean
    private RecordService recordService;

// 测试说明：验证不传 action 条件时，历史记录接口返回成功结果。
    //这里不是“空 action 自动得到 edit”，而是“当 service 返回 edit 时，controller 能正确把它返回出去”。
    @Test
    void myRecords_shouldReturnSuccessWithoutAction() throws Exception {
        when(jwtUtil.parseToken("Bearer t")).thenReturn("token");
        when(jwtUtil.getUserId("token")).thenReturn(7L);

        Record record = new Record();
        record.setId(1L);
        record.setAction("edit");
        record.setUserId(7L);
        when(recordService.myRecords(7L, null)).thenReturn(List.of(record));

        mockMvc.perform(get("/api/record/my").header("Authorization", "Bearer t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].action").value("edit"));
    }

// 测试说明：验证传入 action 条件时，接口会把筛选条件传给 service。
    @Test
    void myRecords_shouldPassActionFilterToService() throws Exception {
        when(jwtUtil.parseToken("Bearer t")).thenReturn("token");
        when(jwtUtil.getUserId("token")).thenReturn(7L);

        Record record = new Record();
        record.setId(2L);
        record.setAction("merge");
        record.setUserId(7L);
        when(recordService.myRecords(7L, "merge")).thenReturn(List.of(record));

        mockMvc.perform(get("/api/record/my")
                        .header("Authorization", "Bearer t")
                        .param("action", "merge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].action").value("merge"));
    }

// 测试说明：验证缺少 Authorization 请求头时，接口返回 400 Bad Request。
    @Test
    void myRecords_shouldReturnBadRequestWhenAuthorizationMissing() throws Exception {
        mockMvc.perform(get("/api/record/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
