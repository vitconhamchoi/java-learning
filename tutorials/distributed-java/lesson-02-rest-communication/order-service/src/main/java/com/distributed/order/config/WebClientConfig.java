package com.distributed.order.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Cấu hình WebClient và RestTemplate với timeouts và logging interceptors.
 */
@Configuration
public class WebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);

    @Value("${inventory.service.url:http://localhost:8081}")
    private String inventoryServiceUrl;

    /**
     * WebClient bean - non-blocking reactive HTTP client (khuyến nghị dùng trong Spring Boot 3.x).
     *
     * Cấu hình:
     * - Base URL: inventory service
     * - Connect timeout: 10 giây (thời gian tạo TCP connection)
     * - Read timeout: 30 giây (thời gian nhận response sau khi connected)
     * - Logging filter: log mọi request và response
     */
    @Bean
    public WebClient inventoryWebClient() {
        // Cấu hình Netty HTTP client với timeouts
        HttpClient httpClient = HttpClient.create()
                // Connect timeout: bao lâu để establish TCP connection
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                // Response timeout: tổng thời gian từ request đến full response
                .responseTimeout(Duration.ofSeconds(30))
                .doOnConnected(conn -> conn
                        // Read timeout: bao lâu để đọc data sau khi connected
                        .addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                        // Write timeout: bao lâu để ghi request
                        .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(inventoryServiceUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // Thêm logging filter để log request/response
                .filter(logRequest())
                .filter(logResponse())
                // Default headers cho mọi request
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /**
     * ExchangeFilterFunction để log outgoing requests.
     * Ghi lại method và URL của mỗi request.
     */
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.info("→ HTTP Request: {} {}", clientRequest.method(), clientRequest.url());
            clientRequest.headers().forEach((name, values) -> {
                // Không log Authorization header để bảo mật
                if (!name.equalsIgnoreCase("Authorization")) {
                    log.debug("  Header: {}={}", name, values);
                }
            });
            return Mono.just(clientRequest);
        });
    }

    /**
     * ExchangeFilterFunction để log incoming responses.
     * Ghi lại status code của response.
     */
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            log.info("← HTTP Response: status={}", clientResponse.statusCode());
            return Mono.just(clientResponse);
        });
    }

    /**
     * RestTemplate bean - blocking HTTP client (deprecated nhưng vẫn được dùng nhiều).
     *
     * Dùng Apache HttpComponents để cấu hình timeout chi tiết hơn.
     * Lưu ý: RestTemplate sẽ bị remove trong Spring Framework 7.
     *
     * @deprecated Prefer WebClient for new code
     */
    @Bean
    @Deprecated(since = "Spring Boot 3.x", forRemoval = false)
    public RestTemplate restTemplate() {
        RequestConfig requestConfig = RequestConfig.custom()
                // Thời gian tạo connection đến server
                .setConnectionRequestTimeout(10_000, TimeUnit.MILLISECONDS)
                // Thời gian chờ server response
                .setResponseTimeout(30_000, TimeUnit.MILLISECONDS)
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        RestTemplate restTemplate = new RestTemplate(factory);

        // Thêm interceptor để log requests
        restTemplate.getInterceptors().add((request, body, execution) -> {
            log.info("→ RestTemplate Request: {} {}", request.getMethod(), request.getURI());
            var response = execution.execute(request, body);
            log.info("← RestTemplate Response: {}", response.getStatusCode());
            return response;
        });

        return restTemplate;
    }
}
