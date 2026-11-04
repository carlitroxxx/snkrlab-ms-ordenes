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

## Contexto de documentación

Este microservicio se incorporó después de EV1 y no contaba con README en el ZIP original. Este documento se añade siguiendo el formato de los README existentes.
