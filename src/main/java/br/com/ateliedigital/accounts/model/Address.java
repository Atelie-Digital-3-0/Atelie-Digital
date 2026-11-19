package br.com.ateliedigital.accounts.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "addresses", schema = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "zip_code", nullable = false, length = 8)
    private String zipCode;

    @Column(nullable = false)
    private String street;

    @Column(nullable = false)
    private String number;

    private String complement;

    @Column(nullable = false)
    private String neighborhood;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false, length = 2)
    private String state;

    @Column(name = "is_main", nullable = false)
    private boolean main;

    /** Preenchido pelo banco (default now()); usado so para ordenar por antiguidade. */
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    /** Mesmo local (ignora caixa e espacos nas pontas); {@code main} e identificadores nao entram na comparacao. */
    public boolean sameLocationAs(Address other) {
        return same(zipCode, other.zipCode) && same(street, other.street) && same(number, other.number)
                && same(complement, other.complement) && same(neighborhood, other.neighborhood)
                && same(city, other.city) && same(state, other.state);
    }

    private static boolean same(String a, String b) {
        return normalize(a).equalsIgnoreCase(normalize(b));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
