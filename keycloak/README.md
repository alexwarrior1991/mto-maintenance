# Realm de Keycloak para `mto-maintenance`

Estos ficheros son la definición de lo que `mto-maintenance` necesita en el servidor de identidad.
Se versionan para que la configuración de Keycloak se revise en pull request como cualquier otro
cambio, y para que los entornos no diverjan por lo que alguien pinchó un día en la consola.

No contienen ningún secreto. La cuenta de servicio `mto-maintenance-svc` sí tiene un secreto de
cliente, pero lo genera Keycloak al importar y se lee desde la consola (o se fija por variable de
entorno en `mto-platform`); aquí no se guarda.

## El realm es compartido

`mto-maintenance`, `mto-stock` y `mto-configuration` viven en el **mismo realm** (`mto`). Se
separa por entorno y no por aplicación, porque los usuarios son los mismos y un realm compartido
evita duplicar identidades. El aislamiento entre servicios lo dan los roles de cliente.

El realm base lo crea y lo posee
[`mto-platform`](https://github.com/alexwarrior1991/mto-platform), y cada servicio aporta desde su
propio repositorio lo suyo. Este trae dos ficheros:

| Fichero | Qué aporta |
|---|---|
| `mto-maintenance-partial-import.json` | Los clientes `mto-maintenance-api` y `mto-maintenance-svc`, los permisos y los perfiles `mto-maintenance-*`. Vale para cualquier entorno. |
| `mto-maintenance-dev.json` | Los tres usuarios de desarrollo. Aparte a propósito, para poder aplicar lo anterior en un entorno desplegado sin arrastrarlos. |

Los aplica `mto-platform/keycloak/apply-partials.sh`, que fija el orden: primero las parciales que
crean los clientes, después `mto-ops-cross-service.json`, que los nombra.

## Qué hay dentro

### Clientes

| Cliente | Tipo | Para qué |
|---|---|---|
| `mto-maintenance-api` | Confidencial, sin flujos | Declara los permisos como roles de cliente y es la **audiencia** de los tokens que valida esta API. |
| `mto-maintenance-svc` | Cuenta de servicio (`client_credentials`) | Con ella `mto-maintenance` llama a `mto-stock` para reservar, consumir y liberar material. Lleva un *audience mapper* hacia `mto-stock-api`. |

`mto-maintenance` no declara ningún cliente de navegador propio: usa el `mto-frontend` del realm
base de `mto-platform`, que lleva un *audience mapper* hacia `mto-maintenance-api`.

### Permisos y perfiles

Los **permisos** son roles de cliente de `mto-maintenance-api` y son lo que comprueba el código
(`SecurityRoles`). Los **perfiles** son roles de realm compuestos que los agrupan, y son lo que se
asigna a las personas.

| Permiso | Concede |
|---|---|
| `maintenance-read` | Todo `GET` bajo `/api/v1/maintenance` |
| `maintenance-write` | Alta y modificación; transiciones ordinarias (`plan`, `assign`, `start`, `complete`, turnos, tareas, inspecciones, materiales) |
| `maintenance-delete` | `DELETE /assets/{id}` (desactivación) |
| `maintenance-supervise` | **Además de** `maintenance-write`: `cancel` de una orden, `complete` con `force`, `resolve`/`close`/`discard` de un defecto |
| `ops-metrics` | Lectura de los endpoints de Actuator |
| `ops-write` | Operaciones de Actuator que modifican estado |

| Perfil | Agrupa |
|---|---|
| `mto-maintenance-viewer` | `maintenance-read` |
| `mto-maintenance-technician` | `maintenance-read`, `maintenance-write` |
| `mto-maintenance-manager` | los del técnico + `maintenance-delete`, `maintenance-supervise` |

`maintenance-supervise` va aparte porque son las decisiones que deshacen o fuerzan trabajo: cancelar
una orden libera reservas y devuelve defectos a `OPEN`; completar con `force` acepta una orden con
material sin sincronizar en stock; cerrar o descartar un defecto lo saca del circuito. Quien
registra el trabajo de campo no tiene por qué firmar esas decisiones.

## Cómo cargarlo

```bash
cd ../mto-platform
docker compose up -d
./keycloak/apply-partials.sh                 # con los usuarios de desarrollo
./keycloak/apply-partials.sh --no-dev-users  # solo clientes, permisos y perfiles
```

Usuarios de desarrollo (contraseña `local`):

| Usuario | Perfil |
|---|---|
| `mantenimiento.lector` | `mto-maintenance-viewer` |
| `mantenimiento.tecnico` | `mto-maintenance-technician` |
| `mantenimiento.responsable` | `mto-maintenance-manager` |

A mano, sobre un realm que ya existe: **Realm settings → Partial import** con
`mto-maintenance-partial-import.json` y estrategia **Skip**, o por la API de administración
(`POST /admin/realms/mto/partialImport`) añadiendo `"ifResourceExists": "OVERWRITE"` para poder
reejecutar.

## Después de importar

1. **Dar permisos en el almacén a la cuenta de servicio.** `mto-maintenance-svc` llega con el
   *audience mapper* hacia `mto-stock-api` pero **sin roles**: hay que asignarle `stock-read` y
   `stock-write` de `mto-stock-api` (Clients → `mto-maintenance-svc` → Service accounts roles).
   Sin ellos, cada reserva falla con 403 y la línea de material queda en `FAILED`. No se conceden
   aquí a propósito: qué puede tocar un servicio en el almacén de otro es una decisión, no un valor
   por defecto. `mto-platform/keycloak/apply-partials.sh` lo hace en local.
2. **Copiar el secreto del cliente** `mto-maintenance-svc` (Clients → Credentials) a
   `KEYCLOAK_SERVICE_CLIENT_SECRET`.
3. **Crear los usuarios y asignarles su perfil.** La parcial no trae ninguno; los de desarrollo están
   en `mto-maintenance-dev.json`.

## Lo que aportan los otros repositorios

- `mto-platform/keycloak/mto-realm.json` da a `mto-frontend` un *audience mapper*
  `audiencia-mto-maintenance-api`. Sin él, un token del navegador puede llegar aquí sin
  `mto-maintenance-api` en `aud` y la API lo rechaza con 401.
- `mto-platform/keycloak/mto-ops-cross-service.json` redefine `mto-ops` con los `ops-*` de las
  cuatro APIs. Se aplica **después** de esta parcial, cuando `mto-maintenance-api` ya existe: un
  compuesto solo puede nombrar roles de clientes que existan en el realm.

## Comprobar que quedó bien

`KeycloakAuthorizationIT` levanta un Keycloak real con un realm de la misma forma
(`src/test/resources/keycloak/mto-maintenance-test-realm.json`) y verifica contra él los permisos
por verbo, la expansión de los compuestos, que un rol de realm homónimo no concede el permiso y la
audiencia:

```bash
./mvnw verify -Dit.test=KeycloakAuthorizationIT -Dtest=NONE \
  -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false
```

Necesita Docker. Sin Docker el test se salta en lugar de fallar.
