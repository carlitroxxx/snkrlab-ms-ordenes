-- Revisar duplicados y SHOW CREATE TABLE ordenes antes de aplicar una vez.
-- Si hay duplicados, resolverlos antes del ALTER; este script no modifica filas.
SELECT pago_id, COUNT(*) FROM ordenes
WHERE pago_id IS NOT NULL GROUP BY pago_id HAVING COUNT(*) > 1;

ALTER TABLE ordenes ADD CONSTRAINT uk_ordenes_pago_id UNIQUE (pago_id);
