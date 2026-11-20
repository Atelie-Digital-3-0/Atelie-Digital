package br.com.ateliedigital.support;

/** Corpos JSON (snake_case, como a API recebe) usados pelos testes de integracao HTTP. */
public final class Payloads {
    private Payloads() { }

    public static String createAccount(TestUser user) {
        return """
                {"first_name": "Maria", "last_name": "Silva", "cellphone": "11999999999",
                 "date_of_birth": "1990-05-20", "cpf": "%s", "email": "%s"}""".formatted(user.cpf(), user.email());
    }

    public static String completeAccount(TestUser user, String addressJson) {
        return """
                {"first_name": "Maria", "last_name": "Silva", "cellphone": "11999999999",
                 "date_of_birth": "1990-05-20", "cpf": "%s", "bio": "bio", "address": %s}""".formatted(user.cpf(), addressJson);
    }

    public static String address(String zipCode, String street, String number, boolean main) {
        return """
                {"zip_code": "%s", "street": "%s", "number": "%s", "neighborhood": "Centro",
                 "city": "Sao Paulo", "state": "SP", "main": %s}""".formatted(zipCode, street, number, main);
    }
}
