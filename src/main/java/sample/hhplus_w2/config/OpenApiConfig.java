package sample.hhplus_w2.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("E-Commerce Core API")
                        .version("1.0.0")
                        .description("항해플러스 2주차 과제 - E-Commerce API"))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local")
                ));
    }
}