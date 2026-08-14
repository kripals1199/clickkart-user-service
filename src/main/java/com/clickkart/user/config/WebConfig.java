// src/main/java/com/clickkart/user/config/WebConfig.java
package com.clickkart.user.config;

import com.clickkart.user.filter.AccessLogFilter;
import com.clickkart.user.filter.MdcCleanupFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Note there is no {@code CorrelationIdFilter} here, unlike Audit Log and Notification Service.
 * Those two are reached only service-to-service and take the correlation id from the {@code
 * X-Correlation-Id} header. This service is browser-facing, so its correlation id comes from the
 * {@code correlationId} claim inside the signature-verified JWT ({@code JwtAuthenticationFilter})
 * - a header would be client-supplied and could be used to stitch one customer's actions into
 * another's trace.
 */
@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<MdcCleanupFilter> mdcCleanupFilter() {
        FilterRegistrationBean<MdcCleanupFilter> registration = new FilterRegistrationBean<>(new MdcCleanupFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AccessLogFilter> accessLogFilter() {
        FilterRegistrationBean<AccessLogFilter> registration = new FilterRegistrationBean<>(new AccessLogFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
