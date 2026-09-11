# Directrices del proyecto

Las decisiones de Lain que gobiernan cómo se trabaja en este repositorio. `CLAUDE.md` tiene las
prohibiciones absolutas; esto tiene el resto, y se lee al empezar.

**Qué va aquí**: una directriz vigente, con su mecanismo, redactada como lo que es cierto ahora. **Qué no
va aquí**: la crónica de cómo se llegó a ella, incidentes, fechas de lo que pasó. Eso no se escribe en
ningún documento del repo.

---

## Alcance y producto

**Claude maneja el IDE, y trabaja con el IDE en vez de con sus propias tools.** Todo lo que se pida hacer
en el IDE se hace en el IDE, proactivamente, para que el dev/DevOps tenga su entorno siempre al día.

**Sólo hablamos con el IDE.** No se envuelve `gh`, `glab`, `docker`, `kubectl`, `aws`, `terraform`, `ssh`
ni el CLI `ijhttp`. Si el IDE ya lo hace, se le pide al IDE; si el IDE no lo hace, no lo hacemos. El panel
de Services es el centro de la parte DevOps, no una colección de envoltorios de línea de comandos.

**Sin multiidioma.** No se publica texto que Lain no pueda revisar, y menos en avisos de seguridad.

## Al día, en el filo, y sin deuda

El código y las librerías van **al día**. No se arrastra deuda técnica ni código que acaba siendo bulto
para sostener a quien no renueva una licencia de pago: para eso están IntelliJ IDEA Community y PyCharm
Community.

- **Cuando aparece un deprecado, se migra en ese momento.** Si el sustituto exige una build más nueva,
  **se sube el `sinceBuild`** y se deja de soportar la anterior.
- **Nunca se escriben las dos formas.** Ninguna rama por versión, ninguna función vieja «por si acaso».
  Lo que deja de usarse se borra en el mismo commit.
- Lo único que se degrada es la **ausencia de un plugin** (Docker, `com.intellij.database`,
  `com.jetbrains.restClient`), nunca la ausencia de una versión de plataforma.
- **Ni APIs deprecadas ni internas.** El único warning tolerable es `@ApiStatus.Experimental`.
  `@ApiStatus.Internal` no lo es: ni siquiera promete estabilizarse.

## Coste de tokens

Lo que se añada tiene que **hacer más gastando menos**. Es criterio de aceptación, no aspiración: una
funcionalidad que suba el gasto no entra, por buena que sea.

El ahorro recurrente está en **la forma de la respuesta**, no sólo en el tamaño del catálogo. Cada tool
devuelve la respuesta más pequeña que zanja la pregunta, nunca un volcado; todo lo enumerable lleva
`limit` con tope duro; el IDE resuelve en lugar de reenviarnos material para que lo resuelva el modelo; y
ningún resultado obliga a una segunda llamada para ser útil.

## Arquitectura del MCP propio

1. **Servidores propios por stdio sobre sockets Unix. Sin puertos.**
2. **Cuatro servidores por dominio**: `code`, `run`, `vcs`, `ops`. JSON-RPC sin broker.
3. **Nada se carga por adelantado**: tres meta-tools por servidor, y **máximo cuatro tools por dominio**.
   Un dominio que no cabe en cuatro está mal partido y se divide.
4. **TOON al 100%** en todo lo que escribimos. En JSON sólo lo que no es nuestro: el sobre JSON-RPC y el
   `inputSchema`.
5. **El guard se evalúa dentro del servidor MCP**, en el despachador, porque abrir los sockets saca al
   binario de Claude Code del camino.
6. **Agente-agnóstico**: cualquier cliente MCP puede conectarse.
7. **Nada se queda clavado esperando**: cola por servidor, acuse inmediato, respuestas fuera de orden por
   `id`, timeout y cancelación en toda tool, y tope de profundidad de cola.
8. **Corrutinas sólo en el código nuevo del MCP** (`model/mcp/`, `controller/mcp/`). El resto del plugin
   conserva `AppExecutorUtil`, `ReadAction.compute`, `WriteCommandAction` y el `edt {}` único.

### Autenticación de los servidores

El token **se autogenera en el código**; nadie lo escribe ni lo configura. **Todo servicio stdio lo
reclama, sin excepciones**: no hay servidor exento ni modo sin auth para desarrollo. Se entrega en el
`init` de cada servidor, que lo guarda **sólo en memoria**. El cliente del plugin lo recoge. **Se regenera
en cada arranque y rota cada 30 minutos**, con una ventana corta de solape para no matar peticiones en
vuelo.

Se entrega al helper por la **primera línea de stdin**, nunca por variable de entorno: `/proc/<pid>/environ`
lo lee cualquier proceso del mismo usuario.

## Código

- **Cero comentarios.** El porqué va al nombre, al test o al mensaje de commit. Ver `CLAUDE.md`.
- **Techo de 250 líneas por fichero**, en Kotlin, TypeScript y CSS.
- **Nada de Swing en la UI**: todo lo visible va en JCEF.
- **Cada dependencia externa se nombra en un único fichero pasarela** (`GitGateway`, `TerminalLauncher`,
  `DbGateway`), con la comprobación de disponibilidad como primera línea.
- **Añadir una tool es añadir una fila a una tabla**, jamás editar un `when` central.
- **No se moldean los tests al código.** Un gate rojo se arregla en el código; cambiar la métrica o el
  umbral está prohibido.
- **Sin criptografía propia.** Sólo primitivas contrastadas.

## Trabajo

- **Un commit por unidad lógica**, en Conventional Commits, con el porqué en el cuerpo cuando no es obvio
  por el diff. Nunca `git add -A` a ciegas.
- **Los tags de release los corta el workflow**, nunca a mano.
- **`push`, tags y PRs los decide Lain.**
- **`package-lock.json` no se stagea**: es suyo.
- **Nunca `cd` en un comando**; rutas absolutas.
- **Los documentos del repo se actualizan en el mismo turno del cambio**, sin preguntar.
