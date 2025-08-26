package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = DemoApplication.class,
    exclude = DataSourceAutoConfiguration.class
)
class DemoApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the application context loads without a datasource
    }
}
