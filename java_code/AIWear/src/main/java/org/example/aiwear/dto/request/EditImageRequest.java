package org.example.aiwear.dto.request;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EditImageRequest {

    // 当前图⽚上传了之后产⽣的oss地址
    @NotBlank(message = "图⽚不能为空")
    private String image;

    // 编辑指令
    @NotBlank(message = "编辑图⽚的指令不能为空")
    private String instruction;
}
