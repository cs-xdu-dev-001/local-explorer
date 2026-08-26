package com.localexplorer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
@EnableAsync
@Slf4j
public class LocalExplorerApplication {
    public static void main(String[] args) {
        // Druid 1.2.1 reflective ping leaves MySQL's receive timestamp stale.
        System.setProperty("druid.mysql.usePingMethod", "false");
        SpringApplication.run(LocalExplorerApplication.class, args);
        log.info("local explorer server started");
    }
}
