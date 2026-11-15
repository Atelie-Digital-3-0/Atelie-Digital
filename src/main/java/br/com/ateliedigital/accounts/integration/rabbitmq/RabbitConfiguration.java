package br.com.ateliedigital.accounts.integration.rabbitmq;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
    @Bean
    TopicExchange auditExchange(@Value("${integrations.rabbitmq.exchange:atelie.audit}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }
}
