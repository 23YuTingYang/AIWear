package org.example.aiwear.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * Python 图片审核接口响应
 */
@Data
public class PythonValidateImageResponse implements Serializable {

    /**
     * Python 服务业务状态码，200 表示审核请求处理成功。
     */
    private Integer code;

    /**
     * 图片是否允许上传。
     */
    private Boolean allow;
}
