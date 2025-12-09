package sample.hhplus_w2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class HhplusW2Application {

    public static void main(String[] args) {
        SpringApplication.run(HhplusW2Application.class, args);
    }

}
