package com.samhanair.logis.accounting.config;

import com.samhanair.logis.accounting.service.MonthEndCloseService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 구성 — {@link AccountingPeriodGuard} 등록 + body cache filter 등록.
 * P2-4 매출 마감 가드는 본 설정으로 활성.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final MonthEndCloseService monthEndCloseService;

    public WebMvcConfig(MonthEndCloseService monthEndCloseService) {
        this.monthEndCloseService = monthEndCloseService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AccountingPeriodGuard(monthEndCloseService))
                .addPathPatterns(
                        "/accounting/journals",
                        "/accounting/journals/**",
                        "/accounting/tax-invoices",
                        "/accounting/tax-invoices/**")
                .excludePathPatterns(
                        "/accounting/closings",
                        "/accounting/closings/**");
    }

    /**
     * 분개/세금계산서 입력 endpoint 가 body 1회 읽기 후 controller 가 다시 읽을 수 있도록
     * {@link AccountingPeriodGuard.CachedBodyRequestWrapper} 로 감싸는 filter.
     */
    @Bean
    public FilterRegistrationBean<Filter> cachedBodyFilter() {
        FilterRegistrationBean<Filter> reg = new FilterRegistrationBean<>(new CachedBodyFilter());
        reg.addUrlPatterns(
                "/accounting/journals",
                "/accounting/journals/*",
                "/accounting/tax-invoices",
                "/accounting/tax-invoices/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }

    /** Body cache filter — interceptor 가 body 를 읽기 위해. */
    static class CachedBodyFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            if (request instanceof HttpServletRequest httpReq
                    && !(request instanceof AccountingPeriodGuard.CachedBodyRequestWrapper)) {
                String method = httpReq.getMethod();
                if ("POST".equals(method) || "PUT".equals(method)) {
                    chain.doFilter(new AccountingPeriodGuard.CachedBodyRequestWrapper(httpReq), response);
                    return;
                }
            }
            chain.doFilter(request, response);
        }
    }
}
