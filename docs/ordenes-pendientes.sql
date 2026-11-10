-- Ejecutar una vez sobre el esquema anterior, antes del primer arranque.
-- Si Hibernate ya creó columnas o índice, revisar SHOW CREATE TABLE ordenes
-- y aplicar únicamente las partes faltantes. No elimina ni actualiza filas.
ALTER TABLE ordenes
    ADD COLUMN checkout_id CHAR(36) NULL,
    ADD COLUMN pago_id BIGINT NULL,
    ADD COLUMN moneda VARCHAR(3) NULL,
    ADD CONSTRAINT uk_ordenes_checkout_id UNIQUE (checkout_id);
