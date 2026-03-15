package com.jelly.cinema;

import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {OpenAiAutoConfiguration.class})
public class JellyCinemaApplication {
    public static void main(String[] args) {
        SpringApplication.run(JellyCinemaApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  Jelly Cinema Server Started   ლ(´ڡ`ლ)ﾞ");
    }
}
