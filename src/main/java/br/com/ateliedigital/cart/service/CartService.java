package br.com.ateliedigital.cart.service;

import br.com.ateliedigital.cart.config.CartProperties;
import br.com.ateliedigital.cart.dto.CartRequests.AddCartItemRequest;
import br.com.ateliedigital.cart.dto.CartRequests.UpdateCartItemRequest;
import br.com.ateliedigital.cart.dto.CartResponses.CartItemResponse;
import br.com.ateliedigital.cart.dto.CartResponses.CartResponse;
import br.com.ateliedigital.cart.dto.CartResponses.RemovalReason;
import br.com.ateliedigital.cart.dto.CartResponses.RemovedCartItem;
import br.com.ateliedigital.cart.integration.catalog.ProductCatalog;
import br.com.ateliedigital.cart.integration.catalog.ProductSnapshot;
import br.com.ateliedigital.cart.integration.catalog.VariantLookup;
import br.com.ateliedigital.cart.mapper.CartMapper;
import br.com.ateliedigital.cart.model.CartItem;
import br.com.ateliedigital.cart.repository.CartRepository;
import br.com.ateliedigital.exceptions.ResourceNotFoundException;
import br.com.ateliedigital.exceptions.VioletedRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras do carrinho. Toda alteracao e validada contra o catalogo (produto existente/ativo, dados integros,
 * estoque) e contra os limites de {@link CartProperties}; a leitura reconcilia o carrinho com o catalogo.
 */
@Service
public class CartService {
    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private final CartRepository cartRepository;
    private final ProductCatalog productCatalog;
    private final CartMapper mapper;
    private final CartProperties properties;
    private final Clock clock;

    @Autowired
    public CartService(CartRepository cartRepository, ProductCatalog productCatalog, CartMapper mapper,
                       CartProperties properties) {
        this(cartRepository, productCatalog, mapper, properties, Clock.systemUTC());
    }

    CartService(CartRepository cartRepository, ProductCatalog productCatalog, CartMapper mapper,
                CartProperties properties, Clock clock) {
        this.cartRepository = cartRepository;
        this.productCatalog = productCatalog;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Carrinho conferido com o catalogo: precos e estoque atuais, status por item e totais dos itens compraveis.
     * Itens cujo produto deixou de existir ou foi desativado sao removidos do Redis e listados em
     * {@code removed_items}. Se o catalogo estiver fora, os itens aparecem como {@code UNVERIFIED} e nada e removido.
     */
    public CartResponse getCart(UUID userId) {
        List<CartItem> items = cartRepository.findAll(userId);
        if (items.isEmpty()) {
            return mapper.toCart(List.of(), List.of());
        }
        Map<String, VariantLookup> lookups = productCatalog.lookupAll(
                items.stream().map(CartItem::productVariantId).toList());

        List<CartItemResponse> responses = new ArrayList<>();
        List<RemovedCartItem> removed = new ArrayList<>();
        for (CartItem item : items) {
            VariantLookup lookup = lookups.getOrDefault(item.productVariantId(), VariantLookup.failed("not consulted"));
            switch (lookup) {
                case VariantLookup.Found found -> responses.add(mapper.toResponse(item, found.product()));
                case VariantLookup.Missing ignored -> removed.add(
                        new RemovedCartItem(item.productVariantId(), RemovalReason.PRODUCT_UNAVAILABLE));
                case VariantLookup.Failed ignored -> responses.add(mapper.toUnverifiedResponse(item));
            }
        }
        if (!removed.isEmpty()) {
            List<String> removedIds = removed.stream().map(RemovedCartItem::productVariantId).toList();
            cartRepository.removeAll(userId, removedIds);
            log.info("Itens indisponiveis removidos do carrinho. userId={} variants={}", userId, removedIds);
        }
        return mapper.toCart(responses, removed);
    }

    /** Adiciona a variante ao carrinho ou soma a quantidade, se ela ja estiver nele. */
    public CartItemResponse addItem(UUID userId, AddCartItemRequest request) {
        ProductSnapshot product = requireProduct(request.productVariantId());
        Instant now = clock.instant();

        CartItem saved = cartRepository.save(userId, product.productVariantId(), (current, itemCount) -> {
            if (current == null && itemCount >= properties.maxDistinctItems()) {
                throw new VioletedRule("Cart cannot have more than %d different items"
                        .formatted(properties.maxDistinctItems()));
            }
            int quantity = (current == null ? 0 : current.quantity()) + request.quantity();
            checkQuantity(quantity, product);
            return current == null ? CartItem.create(product, quantity, now) : current.refresh(product, quantity, now);
        });
        return mapper.toResponse(saved, product);
    }

    /**
     * Define a quantidade final do item. {@code 0} remove o item.
     *
     * @return o item atualizado, ou vazio quando o item foi removido
     */
    public Optional<CartItemResponse> updateItem(UUID userId, String productVariantId, UpdateCartItemRequest request) {
        if (request.quantity() == 0) {
            removeItem(userId, productVariantId);
            return Optional.empty();
        }
        if (!cartRepository.exists(userId, productVariantId)) {
            throw notInCart();
        }
        ProductSnapshot product = switch (productCatalog.lookup(productVariantId)) {
            case VariantLookup.Found found -> found.product();
            case VariantLookup.Missing ignored -> {
                cartRepository.remove(userId, productVariantId);
                throw new ResourceNotFoundException("Product is no longer available and was removed from the cart");
            }
            case VariantLookup.Failed ignored -> throw catalogUnavailable();
        };
        Instant now = clock.instant();

        CartItem saved = cartRepository.save(userId, productVariantId, (current, itemCount) -> {
            if (current == null) {
                throw notInCart(); // removido por outra requisicao enquanto o catalogo era consultado
            }
            checkQuantity(request.quantity(), product);
            return current.refresh(product, request.quantity(), now);
        });
        return Optional.of(mapper.toResponse(saved, product));
    }

    public void removeItem(UUID userId, String productVariantId) {
        if (!cartRepository.remove(userId, productVariantId)) {
            throw notInCart();
        }
    }

    public void clear(UUID userId) {
        cartRepository.clear(userId);
    }

    private ProductSnapshot requireProduct(String productVariantId) {
        return switch (productCatalog.lookup(productVariantId)) {
            case VariantLookup.Found found -> found.product();
            case VariantLookup.Missing ignored -> throw new ResourceNotFoundException("Product not found");
            case VariantLookup.Failed ignored -> throw catalogUnavailable();
        };
    }

    private void checkQuantity(int quantity, ProductSnapshot product) {
        if (quantity > properties.maxQuantityPerItem()) {
            throw new VioletedRule("Quantity per item cannot exceed %d".formatted(properties.maxQuantityPerItem()));
        }
        if (quantity > product.stock()) {
            throw new VioletedRule("Insufficient stock: only %d units available".formatted(product.stock()));
        }
    }

    private static ResourceNotFoundException notInCart() {
        return new ResourceNotFoundException("Product is not in the cart");
    }

    private static ResponseStatusException catalogUnavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog service unavailable");
    }
}
