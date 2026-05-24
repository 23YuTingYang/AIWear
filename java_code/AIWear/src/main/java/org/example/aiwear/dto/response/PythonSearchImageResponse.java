package org.example.aiwear.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Python 图片检索接口响应。
 */
@Data
public class PythonSearchImageResponse implements Serializable {

    /**
     * Python 服务业务状态码，200 表示查询成功。
     */
    private Integer code;

    /**
     * Python 服务返回的提示信息。
     */
    private String message;

    /**
     * 图片检索结果列表，已按相似度倒序排列。
     */
    private List<SearchImageResponse> data;
}
