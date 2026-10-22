package lv.lms.ratelimit;

import lv.lms.config.AppProperties;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RateLimitConfig {

	/** Just before Spring Security's filter chain (order -100). */
	private static final int ORDER = -101;

	@Bean
	FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilter(RateLimiter rateLimiter,
			AppProperties properties) {
		FilterRegistrationBean<AuthRateLimitFilter> registration = new FilterRegistrationBean<>(
				new AuthRateLimitFilter(rateLimiter, properties));
		registration.addUrlPatterns("/api/v1/auth/*");
		registration.setOrder(ORDER);
		return registration;
	}

}
