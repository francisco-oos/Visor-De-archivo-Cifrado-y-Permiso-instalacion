# Arquitectura Document Vault

## Estado R1

Actualmente el repositorio conserva VSDOC1/VSDOC2 dentro de `assets/manuales` sólo como mecanismo de empaquetado compatible. Los PDFs fuente, archivos cifrados de trabajo y secretos no se versionan.

```text
Repositorio
├── app/                  AndroidApp
├── tools/                DocumentEncryptor
└── docs/

Fuera de Git
├── tools/MANUALES_PARA_ENCRIPTAR/
├── app/src/main/assets/manuales/
├── visor-secrets.properties
└── backups/DocumentVault (si se usa)
```

El encriptador exporta temporalmente VSDOC2 + `index.json` a assets antes de compilar.

## Dirección VSDOC3

La arquitectura final moverá los documentos a un vault privado actualizable independiente del APK:

```text
filesDir/vault/
├── catalog firmado
├── documento_A.vsdoc3
└── documento_B.vsdoc3
```

Así una actualización de manual no obligará a recompilar toda la aplicación. El diseño completo está en `docs/ROADMAP_VSDOC3_VIEWER.md`.
