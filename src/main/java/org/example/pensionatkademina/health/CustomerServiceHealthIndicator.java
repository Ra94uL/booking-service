package org.example.pensionatkademina.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
@Slf4j
@Component("customerService")
public class CustomerServiceHealthIndicator implements HealthIndicator {

    private final RestClient restClient;

    public CustomerServiceHealthIndicator(@Value("${customer-service.url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public Health health() {
        try {
            restClient.get().uri("/actuator/health").retrieve().toBodilessEntity();
            log.info("Health check: customer-service is UP");
            return Health.up().withDetail("service", "customer-service").build();
        } catch (RestClientException e) {
            log.warn("Health check: customer-service is DOWN ({})", e.getClass().getSimpleName());
            return Health.down()
                    .withDetail("service", "customer-service")
                    .withDetail("error", e.getClass().getSimpleName())
                    .build();
        }
    }
}