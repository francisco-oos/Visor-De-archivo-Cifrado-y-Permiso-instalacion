# Roadmap técnico — VSDOC3 + visor PDF propio

Fecha: 2026-09-16  
Estado: Diseño aprobado; implementación después de validar R1 en teléfono.

## Principio

No se copiará AndroidX PDF ni un visor de terceros. Se estudiarán sus patrones públicos y la documentación de Android/PDFium para construir una implementación propia adaptada a manuales HSE cifrados.

Tampoco se implementará un parser PDF completo desde cero. `PdfRenderer`/backend de plataforma seguirá resolviendo el formato PDF; nuestro código controlará sesión, seguridad, viewport, planificación, caché, memoria, búsqueda y UX.

## Arquitectura objetivo

```text
SecureDocumentSession
│
├── DocumentCatalog
├── AuthorizationGate
├── ContentKeyProvider
├── VsdocContainer
│     ├── VSDOC1 reader (compatibilidad)
│     ├── VSDOC2 reader (compatibilidad/stream temporal)
│     └── VSDOC3 seekable streaming
│
└── PdfViewerEngine
      ├── PageLayoutEngine
      ├── ViewportController
      ├── RenderScheduler
      ├── TileRenderer / PageRenderer
      ├── WeightedBitmapCache
      ├── MemoryGovernor
      ├── PrefetchPlanner
      ├── SearchEngine
      ├── GestureController
      └── SecurityOverlay
             │
             ├── LegacyPdfRendererBackend (API 26+)
             └── ModernPdfBackend (capacidad del dispositivo)
```

La UI hablará con `PdfViewerEngine`, no directamente con `PdfRenderer`.

---

## R2 — Reducir memoria sin cambiar formato

### 1. VSDOC2: ciphertext ya no debe cargarse completo

Situación actual:

```text
.bin completo -> ByteArray ciphertext
             -> ByteArray PDF plaintext
             -> archivo temporal
             -> PdfRenderer
```

Primer paso compatible:

```text
Asset/InputStream
      ↓
parse header + metadata
      ↓
CipherInputStream / flujo autenticado controlado
      ↓
archivo temporal privado
      ↓
PdfRenderer
```

Objetivo: eliminar la coexistencia de dos buffers del tamaño total del manual. El PDF temporal seguirá existiendo sólo en esta etapa y se limpiará defensivamente al inicio/cierre.

Nota: AES-GCM monolítico no ofrece acceso aleatorio autenticado por segmentos; por eso VSDOC2 no es el formato final para lectura seekable.

### 2. `SecureDocumentSession`

Una sesión poseerá explícitamente:

- documento seleccionado;
- autorización ya validada;
- descriptor/renderer;
- trabajo de render activo;
- trabajo de búsqueda activo;
- caché;
- recursos temporales.

Cerrar la sesión debe cancelar trabajos, cerrar páginas/descriptores y limpiar plaintext temporal.

### 3. Trabajo fuera del hilo UI

Mover a coroutines/dispatchers:

- descifrado;
- hash/integridad;
- apertura pesada;
- búsqueda;
- render cuando el backend lo permita.

La UI sólo recibe estados/resultados.

---

## R2 — Motor visual propio

### Viewport virtualizado

El modelo no pregunta "¿cuántas páginas tiene el PDF?" para decidir memoria, sino "¿qué está visible ahora?".

```text
scroll/gesto
   ↓
ViewportController
   ↓
páginas intersectadas
   ↓
regiones/tiles necesarios
```

Sólo las páginas visibles y un margen pequeño entran a la cola de render.

### RenderScheduler con prioridades

Prioridad propuesta:

1. región actualmente visible;
2. resto de página visible;
3. siguiente página según dirección de lectura;
4. página anterior inmediata;
5. todo lo demás: no renderizar.

Si el usuario cambia rápido de página/zoom, trabajos que ya no sirven deben cancelarse o descartarse antes de publicar el bitmap.

Cada solicitud tendrá una `generationId`/token de sesión. Un resultado de una generación vieja nunca reemplazará el contenido nuevo.

### Prefetch direccional

No precargar siempre ambos lados por igual.

Se conservará velocidad/dirección reciente:

```text
83 -> 84 -> 85
          prioridad: 86
          secundaria: 84
```

Si el usuario cambia dirección se ajusta el prefetch. Se usará histéresis para evitar alternar por pequeños movimientos.

### Resolución por niveles

No usar siempre un bitmap de página completa preparado para zoom máximo.

Propuesta:

- nivel FIT: suficiente para página completa;
- nivel READ: resolución media al comenzar zoom;
- nivel DETAIL: tiles de alta resolución sólo para viewport ampliado.

Mientras llega DETAIL puede mantenerse temporalmente el nivel anterior escalado para que el gesto no se congele.

### Tiles

El tamaño exacto se medirá; punto inicial de laboratorio: 256–512 px lógicos/renderizados por tile, ajustado a densidad, backend y coste de decodificación.

No se congela 256 px como regla: se benchmarkearán al menos 256, 384 y 512 porque el óptimo depende de dispositivo/documento.

Clave de caché aproximada:

```text
(documentId, page, zoomBucket, tileX, tileY, renderGeneration)
```

### WeightedBitmapCache

No limitar por "N imágenes". Limitar por bytes reales.

Peso:

```text
bitmap.allocationByteCount
```

Política base: LRU ponderado por bytes, con protección temporal para tiles visibles. Lo visible no se expulsa para conservar prefetch lejano.

### MemoryGovernor

Presupuesto dinámico, no constante global.

Entradas:

- `ActivityManager.memoryClass`;
- memoria disponible/pressure callbacks;
- tamaño de viewport;
- densidad;
- tamaño real de bitmaps;
- estado de zoom.

Al recibir presión:

1. cancelar prefetch;
2. expulsar caché no visible;
3. bajar resolución de trabajo futuro;
4. conservar sólo viewport imprescindible.

Nunca usar `OutOfMemoryError` como mecanismo normal de control.

---

## Búsqueda

La búsqueda actual con PDFBox desde `PdfActivity` se sustituirá por un `SearchEngine`.

Patrones:

- debounce de entrada (~250–350 ms a medir);
- cancelar consulta anterior al cambiar texto;
- emitir resultados progresivamente por página;
- no bloquear navegación mientras busca;
- distinguir PDF con texto de documento escaneado sin texto;
- backend moderno puede aprovechar capacidades nativas cuando existan;
- fallback compatible puede usar PDFBox de manera aislada y asíncrona mientras sea necesario.

El OCR no será obligatorio para VSDOC3 inicial; será una capacidad opcional posterior.

---

## VSDOC3

### Objetivos

- No cargar el archivo completo en RAM.
- No escribir el PDF completo descifrado a disco.
- Autenticar contenido por segmentos.
- Permitir lectura aleatoria/seek que necesita un renderer PDF.
- Mantener metadata/política ligada criptográficamente al documento.
- Soportar evolución de formato.

### Contenedor conceptual

```text
VSDOC3
├── magic/version
├── header length
├── header canónico autenticado
│    ├── document_id
│    ├── document_version
│    ├── area_id
│    ├── original_sha256 opcional
│    ├── content_type
│    ├── crypto_suite
│    └── segment profile
├── streaming header/key metadata
└── encrypted segments...
```

Los offsets/formato exactos se congelarán sólo después de round-trip tests y fuzz/truncation tests.

### Streaming AEAD

Candidato principal: Tink Streaming AEAD con segmentos grandes (perfil inicial de laboratorio ~1 MiB). El formato final no debe depender de nombres de clases Tink en el contrato externo; debe almacenar un identificador propio de suite/perfil para permitir migraciones.

### Lectura seekable

Ruta objetivo en API compatible:

```text
PdfRenderer
    ↓ reads/seeks
ProxyFileDescriptor
    ↓
seekable decrypting channel
    ↓
VSDOC3 segments autenticados
```

La capa proxy debe:

- mapear offset plaintext -> segmento cifrado;
- cachear muy pocos segmentos descifrados por presupuesto;
- limpiar buffers;
- devolver error si un segmento no autentica;
- nunca entregar bytes de otro documento/sesión.

### Content keys

No habrá una única clave universal en fuente.

Diseño objetivo:

```text
content key del documento/vault
        ↓ wrapped/envelope
clave de instalación o material protegido
        ↓
Android Keystore
```

La política exacta se decidirá con el backend de licencias para permitir operación offline y rotación sin recifrar innecesariamente todos los PDFs.

---

## Backend PDF adaptativo

### Compatibilidad

`LegacyPdfRendererBackend` mantiene API 26+.

### Moderno

Cuando el dispositivo tenga capacidades nuevas, `ModernPdfBackend` podrá usar APIs modernas de Android/PdfRendererPreV/AndroidX PDF donde aporten valor estable.

AndroidX PDF se utiliza como referencia de capacidades/patrones, no como código a copiar ni como dependencia obligatoria del dominio.

La selección será por capability detection, no por lógica dispersa en Activities.

---

## Seguridad de visualización

Mantener:

- `FLAG_SECURE`;
- sin Intent de compartir para documento de referencia;
- watermark de sesión configurable;
- autorización revalidada antes de abrir;
- cierre/cancelación al terminar sesión.

Agregar progresivamente:

- identificador de sesión en watermark;
- controles de integridad del paquete/app como señal adicional;
- auditoría local mínima de aperturas si el producto la requiere.

No se prometerá DRM perfecto: un usuario con control físico/privilegiado del equipo puede usar vías externas. El objetivo es defensa en profundidad y evitar fugas accidentales/triviales.

---

## Vault y actualizaciones

Después de VSDOC3:

```text
app pequeña
│
└── filesDir/vault/
     ├── catalog firmado
     ├── manual_A.vsdoc3
     └── manual_B.vsdoc3
```

El catálogo indicará versión/hash/política. Server Oficina podrá actualizar sólo el documento cambiado. Assets seguirá existiendo como `AssetDocumentSource` de compatibilidad/migración.

---

## Métricas de aceptación

Con PDFs de referencia pequeños, 50 MB, 100 MB y 250 MB:

- tiempo a primera página;
- p50/p95 de cambio de página;
- jank durante swipe/zoom;
- pico PSS/heap;
- bytes máximos de bitmap cache;
- tiempo de búsqueda;
- residuos plaintext después de cierre/crash simulado;
- comportamiento tras background/rotación/process recreation;
- corrupción/truncación/tampering VSDOC.

Comparar siempre PRE vs POST. Una optimización no se acepta sólo por "sentirse más rápida".

## Orden de implementación

1. Validar R1 en teléfono.
2. R2A: `SecureDocumentSession` + VSDOC2 stream a archivo privado temporal.
3. R2B: búsqueda y render fuera de UI + caché ponderada.
4. R2C: viewport/render scheduler/resolution ladder.
5. R3A: especificación VSDOC3 + golden vectors + tests de corrupción.
6. R3B: Streaming AEAD + seekable channel.
7. R3C: ProxyFileDescriptor y eliminación de plaintext completo.
8. R3D: content keys + Android Keystore/backend.
9. R4: Vault externo + catálogo firmado + actualización incremental.
10. Sólo entonces integrar Biblioteca en Formatos HSE.
