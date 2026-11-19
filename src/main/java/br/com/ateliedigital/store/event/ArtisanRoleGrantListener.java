package br.com.ateliedigital.store.event;


import br.com.ateliedigital.accounts.AccountManagement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ArtisanRoleGrantListener {
    private static final Logger log = LoggerFactory.getLogger(ArtisanRoleGrantListener.class);

    private final AccountManagement accountManagement;

    ArtisanRoleGrantListener(AccountManagement accountManagement){
        this.accountManagement = accountManagement;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onStoreCreated(StoreCreatedEvent event) {
        try {
            accountManagement.grantArtisanRole(event.keycloakId());
        } catch (RuntimeException exception) {
            // A loja já foi commitada: não derrubamos a requisição. É idempotente, então pode ser reexecutado.
            log.error("Falha ao conceder papel ARTISAN. keycloakId={} storeId={}",
                    event.keycloakId(), event.storeId(), exception);
        }
    }
}
