# snkrlab-ms-ordenes

Administra las órdenes generadas por compras y sus comprobantes. También publica eventos asíncronos para notificaciones y registro de ventas destinado a Reportes.

## Endpoints

El controlador define la base `/api/v1/ordenes`, con operaciones para órdenes, consulta por identificador (`/{id}`) y comprobante (`/{id}/comprobante`). Para parámetros y tipos de petición concretos, consultar `OrdenController.java`.

## Integraciones

Persiste información en MySQL (base dedicada en `docker-compose.yml`), publica correos en `notificaciones.email.queue` y ventas en `reportes.ventas.queue`. Puerto local del Compose: **8085**.

## Desarrollo y ejecución

Desde esta carpeta, con Java y el wrapper Maven:

```bash
./mvnw spring-boot:run
```

En Windows: `mvnw.cmd spring-boot:run`. Para el entorno integral, iniciar dependencias con `docker compose up -d --build` desde la raíz del proyecto. Ajustar las variables de entorno y servicios dependientes antes de ejecutar.

## Seguridad y configuración

Consultar `src/main/resources/application.yaml` o `application.properties` y la configuración de seguridad del servicio. No publicar contraseñas, secretos de correo, tokens ni valores de producción. Se deben verificar los permisos en el servidor y no solo en React.

## Órdenes pendientes de pago

El POST crea únicamente `PENDIENTE_PAGO`, con `checkoutId` UUID obligatorio,
`moneda=CLP` y `pagoId=null`. `CONFIRMADA` está definido para una etapa posterior;
no existe operación de confirmación. Se mantienen JWT MSAL/Cognito, rol Cliente,
identidad desde el token y la cabecera `X-Internal-Key` configurada en el servicio.

El contrato POST cambia de lista de detalles a objeto y devuelve la orden completa
(201, también al repetir). El cliente actual de Carrito aún no es compatible y
no se ha modificado en esta etapa. No probar el checkout integral hasta adaptarlo.

Ejemplo Postman: `POST http://localhost:8085/api/v1/ordenes`, Bearer JWT Cliente,
`Content-Type: application/json` y `X-Internal-Key` con el valor configurado:

```json
{
  "checkoutId": "c4602727-9f0d-41d4-b3d9-0e1820e147dd",
  "items": [{"productoId": 1, "nombreProducto": "Zapatilla", "cantidad": 2, "precioUnitario": 15000}]
}
```

Subtotales y total se recalculan; cualquier subtotal enviado se ignora. Se compara
el usuario y la lista de detalles en el mismo orden, incluidos nombre, precio y
cantidad. Repetir devuelve el mismo id/fecha sin guardar otra orden; cambiar datos
o propietario devuelve 409. Un UUID distinto representa un checkout nuevo.
La restricción UNIQUE resuelve carreras entre instancias; una colisión se recupera
en una transacción nueva después del rollback. Un bloqueo transitorio devuelve 409
y permite reintentar el mismo UUID. Precios y productos siguen llegando del emisor
interno: todavía no se validan contra otros servicios.

`GET /api/v1/ordenes/checkout/{checkoutId}` requiere JWT del propietario, devuelve
404 para checkout inexistente o ajeno y 409 si la orden ya no está pendiente.
Las consultas anteriores por id y listado siguen disponibles.

Las históricas REGISTRADA conservan consultas y comprobantes; los nuevos campos
pueden ser NULL. El flujo histórico del servicio y sus eventos se conservan,
pero el POST utiliza exclusivamente el flujo pendiente sin eventos de venta.
No se modificaron mensajes, colas ni publicadores RabbitMQ. Solicitar comprobante
de una pendiente devuelve 409 antes de publicar correo. CONFIRMADA tampoco está
habilitada para comprobantes hasta implementar la confirmación en otra etapa.

### Esquema y verificación

Revisar `docs/ordenes-pendientes.sql` antes del primer arranque. Es aditivo y de una
sola ejecución sobre el esquema anterior; conserva todos los registros. MySQL
permite varios NULL históricos en UNIQUE(checkout_id). Se conserva ddl-auto:update:
Hibernate también puede crear columnas/índice al arrancar; no ejecutar el script
completo si ya existen. Para revisión manual estricta, arrancar con
`SPRING_JPA_HIBERNATE_DDL_AUTO=validate` después de aplicar el SQL.

Pruebas unitarias desde esta carpeta (Java 21):

```powershell
./mvnw.cmd "-Dtest=OrdenServiceTest,ComprobanteServiceTest" test
./mvnw.cmd spring-boot:run
```

Para `./mvnw.cmd test`, configurar MySQL, RabbitMQ y las variables de seguridad
necesarias para el test existente contextLoads. Las pruebas unitarias usan mocks;
la restricción y las transacciones concurrentes requieren verificación en MySQL.
No se ejecutaron pruebas, compilación ni migraciones durante esta implementación.

En Postman: crear, repetir, cambiar cantidad conservando UUID (409), consultar por
checkout y probar con otro Cliente (404). Solicitar comprobante de la pendiente
(409); repetir con una REGISTRADA propia y correo válido conserva el envío (202).
Enviar simultáneamente el mismo checkout debe dejar una sola fila.

```sql
SHOW CREATE TABLE ordenes;
SELECT id, checkout_id, pago_id, moneda, estado, total FROM ordenes ORDER BY id DESC;
SELECT checkout_id, COUNT(*) FROM ordenes WHERE checkout_id IS NOT NULL
GROUP BY checkout_id HAVING COUNT(*) > 1;
SELECT id, estado, checkout_id, pago_id, moneda FROM ordenes WHERE estado = 'REGISTRADA';
SELECT * FROM orden_detalles WHERE orden_id = 1 ORDER BY posicion;
```

## Contexto de documentación

Este microservicio se incorporó después de EV1 y no contaba con README en el ZIP original. Este documento se añade siguiendo el formato de los README existentes.
