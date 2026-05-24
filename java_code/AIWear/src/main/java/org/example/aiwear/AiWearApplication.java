package org.example.aiwear;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class AiWearApplication {
    public static void main(String[] args) {
        log.info("AIwear项目启动成功...");
        SpringApplication.run(AiWearApplication.class, args);
    }

}
