"""Stub da atelie-catalog-api para testar o modulo de carrinho localmente.

Responde apenas GET /api/v1/catalog/products/variations/{id} na porta 8008
(o endereco padrao de CATALOG_API_BASE_URL). Uso: python3 catalog_stub.py

Para simular mudanca de preco, falta de estoque ou produto removido, edite VARIANTS e reinicie.
"""
import json
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

VARIANTS = {
    "101": {"id": 101, "product_id": 10, "store_id": 1, "price": 49.9, "stock": 10,
            "sku": "CAN-AZUL-M", "color": "azul", "size": "M"},
    "102": {"id": 102, "product_id": 10, "store_id": 1, "price": 15.0, "stock": 5,
            "sku": "CAN-VERDE-P", "color": "verde", "size": "P"},
}
ROUTE = re.compile(r"^/api/v1/catalog/products/variations/([^/]+)$")


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        match = ROUTE.match(self.path)
        variant = VARIANTS.get(match.group(1)) if match else None
        body = json.dumps(variant or {"detail": "Variation or Product not found"}).encode()
        self.send_response(200 if variant else 404)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    print("Catalog stub em http://127.0.0.1:8008/api/v1/catalog")
    ThreadingHTTPServer(("127.0.0.1", 8008), Handler).serve_forever()
