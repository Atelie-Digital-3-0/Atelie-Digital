package br.com.ateliedigital.cart.mapper;

import br.com.ateliedigital.cart.dto.CartResponses.CartItemResponse;
import br.com.ateliedigital.cart.dto.CartResponses.CartItemStatus;
import br.com.ateliedigital.cart.dto.CartResponses.CartResponse;
import br.com.ateliedigital.cart.dto.CartResponses.RemovedCartItem;
import br.com.ateliedigital.cart.integration.catalog.ProductSnapshot;
import br.com.ateliedigital.cart.model.CartItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Monta as respostas do carrinho cruzando o snapshot gravado ({@link CartItem}) com os dados atuais do catalogo.
 * Escrito a mao (e nao com MapStruct) porque quase todos os campos sao derivados: status, subtotal e totais.
 */
@Component
public class CartMapper {
    private static final int MONEY_SCALE = 2;

    /** Item conferido no catalogo: preco, estoque e dados descritivos vem do catalogo (estado atual). */
    public CartItemResponse toResponse(CartItem item, ProductSnapshot current) {
        boolean priceChanged = item.unitPrice() == null || item.unitPrice().compareTo(current.unitPrice()) != 0;
        CartItemStatus status;
        if (current.stock() < item.quantity()) {
            status = CartItemStatus.INSUFFICIENT_STOCK;
        } else if (priceChanged) {
            status = CartItemStatus.PRICE_CHANGED;
        } else {
            status = CartItemStatus.AVAILABLE;
        }
        return new CartItemResponse(item.productVariantId(), current.productId(), current.storeId(), current.sku(),
                current.color(), current.size(), item.quantity(), money(current.unitPrice()),
                priceChanged ? money(item.unitPrice()) : null, subtotal(current.unitPrice(), item.quantity()),
                current.stock(), status, item.addedAt(), item.updatedAt());
    }

    /** Item que nao pode ser conferido (catalogo indisponivel): exibido com os dados do snapshot. */
    public CartItemResponse toUnverifiedResponse(CartItem item) {
        return new CartItemResponse(item.productVariantId(), item.productId(), item.storeId(), item.sku(),
                item.color(), item.size(), item.quantity(), money(item.unitPrice()), null,
                subtotal(item.unitPrice(), item.quantity()), null, CartItemStatus.UNVERIFIED,
                item.addedAt(), item.updatedAt());
    }

    public CartResponse toCart(List<CartItemResponse> items, List<RemovedCartItem> removedItems) {
        List<CartItemResponse> purchasable = items.stream().filter(item -> item.status().purchasable()).toList();
        int totalQuantity = purchasable.stream().mapToInt(CartItemResponse::quantity).sum();
        BigDecimal totalPrice = purchasable.stream()
                .map(CartItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean verified = items.stream().noneMatch(item -> item.status() == CartItemStatus.UNVERIFIED);
        return new CartResponse(items, items.size(), totalQuantity, money(totalPrice), verified, removedItems);
    }

    private static BigDecimal subtotal(BigDecimal unitPrice, int quantity) {
        return unitPrice == null ? money(BigDecimal.ZERO) : money(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
    }
}
