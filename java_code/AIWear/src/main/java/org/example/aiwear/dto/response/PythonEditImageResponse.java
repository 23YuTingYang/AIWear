package org.example.aiwear.dto.response;


import lombok.Data;

import java.io.Serializable;

@Data
public class PythonEditImageResponse implements Serializable {
    private Boolean success;
    private String url;
    private String message;
}
