package com.swiftdrop.communication.support;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @MockitoBean
    protected JavaMailSender mailSender;

    @ServiceConnection
    protected static final PostgreSQLContainer postgres;

    @ServiceConnection
    protected static final RabbitMQContainer rabbitmq;

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            postgres = new PostgreSQLContainer("postgres:17")
                    .withInitScript("init-test-db.sql");
            postgres.start();

            rabbitmq = new RabbitMQContainer("rabbitmq:4-management");
            rabbitmq.start();
        } else {
            postgres = null;
            rabbitmq = null;
        }
    }

    @BeforeAll
    static void checkDocker() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available; skipping integration test."
        );
    }
}
