package org.example.aiwear.controller;


import lombok.extern.slf4j.Slf4j;
import org.example.aiwear.common.Result;
import org.example.aiwear.entity.Record;
import org.example.aiwear.log.ApiLog;
import org.example.aiwear.service.RecordService;
import org.example.aiwear.util.JWTUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 历史调用记录控制器
 */


@Slf4j
@RestController
@RequestMapping("/api/record")
public class RecordController {

    @Autowired
    private JWTUtil jwtUtil;

    @Autowired
    private RecordService recordService;


    // 查看调⽤记录
    @ApiLog
    @GetMapping("/my")
    public Result<List<Record>> myRecords(
            @RequestHeader(value = "Authorization") String authorization,
            @RequestParam(value = "action", required = false) String action
    ){
        //解析出userId
        String token = jwtUtil.parseToken(authorization);
        Long userId = jwtUtil.getUserId(token);
        return Result.success("查询成功", recordService.myRecords(userId, action));
    }
}
