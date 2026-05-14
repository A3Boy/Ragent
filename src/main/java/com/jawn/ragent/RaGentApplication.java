package com.jawn.ragent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.CrossOrigin;

/**
 * RAGent 启动类
 */
@SpringBootApplication
@CrossOrigin("*")
public class RaGentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RaGentApplication.class, args);
    }

}
