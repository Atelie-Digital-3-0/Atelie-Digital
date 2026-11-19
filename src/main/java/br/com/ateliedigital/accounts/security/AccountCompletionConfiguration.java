package br.com.ateliedigital.accounts.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(AccountCompletionProperties.class)
public class AccountCompletionConfiguration implements WebMvcConfigurer {
    private final AccountCompletionInterceptor interceptor;

    public AccountCompletionConfiguration(AccountCompletionInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**");
    }
}
