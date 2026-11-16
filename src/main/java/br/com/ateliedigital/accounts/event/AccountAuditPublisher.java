package br.com.ateliedigital.accounts.event;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AccountAuditPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    public AccountAuditPublisher(RabbitTemplate rabbitTemplate,
                                 @Value("${integrations.rabbitmq.exchange:atelie.audit}") String exchange) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishCreated(AccountCreatedEvent event) {
        rabbitTemplate.convertAndSend(exchange, "account.created", event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishSensitiveUpdated(SensitiveAccountUpdatedEvent event) {
        rabbitTemplate.convertAndSend(exchange, "account.sensitive_updated", event);
    }
}
