package com.jelly.cinema;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class JellyCinemaApplication {

    public static void main(String[] args) {
        System.setProperty("nacos.logging.default.config.enabled", "false");
        SpringApplication.run(JellyCinemaApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  Jelly Cinema Server Started Successfully   ლ(´ڡ`ლ)ﾞ  ");
    }
}
