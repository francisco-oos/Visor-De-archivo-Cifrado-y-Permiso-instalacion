# Formato de licencia para frontend/API — VISOR_LICENSE_V2

Desde Hardening R1, el formato productivo recomendado es una licencia firmada de forma asimétrica. La herramienta/servidor de administración conserva la **clave privada**; la APK sólo contiene la **clave pública**.

## Envelope

```json
{
  "schema": "VISOR_LICENSE_V2",
  "payload_b64": "BASE64_DEL_JSON_PAYLOAD_UTF8",
  "signature": "BASE64_DE_ECDSA_P256_SHA256_SOBRE_PAYLOAD_B64"
}
```

La firma se calcula sobre los bytes UTF-8 exactos de `payload_b64` usando ECDSA P-256 (`secp256r1`) con SHA-256.

La APK recibe la clave pública X.509/DER en Base64 mediante:

```text
LICENSE_VERIFY_PUBLIC_KEY_B64
```

La clave pública puede distribuirse con la APK; no permite fabricar licencias.

## Payload antes de Base64

```json
{
  "employee_name": "Juan Pérez",
  "employee_id": "739",
  "position": "Observador",
  "area": "TOPOGRAFIA",
  "project": "ALACTE",
  "device_hash": "...",
  "install_id": "...",
  "expires_at": "2026-12-31",
  "issued_at": "2026-09-16T19:00:00",
  "access_mode": "AREA",
  "allowed_areas": ["TOPOGRAFIA", "OPERACIONES"]
}
```

Para acceso total:

```json
{
  "access_mode": "ALL",
  "allowed_areas": ["ALL"]
}
```

## Reglas de validación R1

- firma V2 válida;
- datos mínimos de empleado;
- `device_hash` de la instalación actual;
- `install_id` de la instalación actual;
- fecha de caducidad de licencia;
- vigencia propia de la versión de la app;
- autorización por `allowed_areas`/`access_mode`.

Un área vacía sin `allowed_areas` **no** concede acceso global.

## Compatibilidad V1

`VISOR_LICENSE_V1`/HMAC y el formato histórico `{payload, signature}` sólo pueden verificarse en un build `debug` y requieren `LEGACY_LICENSE_HMAC_SECRET` local. Una APK `release` los rechaza por diseño.

## Relación con la clave de documentos

La licencia no contiene directamente la clave maestra VSDOC1/VSDOC2.

En R1 esa clave se mantiene fuera de Git en `visor-secrets.properties` y se inyecta al compilar para conservar compatibilidad. Sigue siendo una solución transitoria porque el valor termina dentro de la APK.

VSDOC3 sustituirá esta arquitectura por content keys/envelope protegidas por instalación/Android Keystore, evitando una clave universal fija en el paquete.
