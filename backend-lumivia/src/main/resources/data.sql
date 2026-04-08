INSERT INTO cameras (nombre, lat, lng, descripcion)
SELECT 'camara_insurgentes_reforma', 19.4326, -99.1332, 'Insurgentes y Reforma'
WHERE NOT EXISTS (SELECT 1 FROM cameras WHERE nombre = 'camara_insurgentes_reforma');

INSERT INTO cameras (nombre, lat, lng, descripcion)
SELECT 'camara_viaducto_cuauhtemoc', 19.4105, -99.1602, 'Viaducto y Cuauhtemoc'
WHERE NOT EXISTS (SELECT 1 FROM cameras WHERE nombre = 'camara_viaducto_cuauhtemoc');

INSERT INTO cameras (nombre, lat, lng, descripcion)
SELECT 'camara_eje_central_bellas_artes', 19.4352, -99.1412, 'Eje Central y Bellas Artes'
WHERE NOT EXISTS (SELECT 1 FROM cameras WHERE nombre = 'camara_eje_central_bellas_artes');

INSERT INTO cameras (nombre, lat, lng, descripcion)
SELECT 'camara_tlalpan_taxquena', 19.3441, -99.1398, 'Tlalpan y Taxquena'
WHERE NOT EXISTS (SELECT 1 FROM cameras WHERE nombre = 'camara_tlalpan_taxquena');

INSERT INTO cameras (nombre, lat, lng, descripcion)
SELECT 'camara_periferico_san_jeronimo', 19.3322, -99.2045, 'Periferico y San Jeronimo'
WHERE NOT EXISTS (SELECT 1 FROM cameras WHERE nombre = 'camara_periferico_san_jeronimo');


INSERT INTO cameras (nombre, lat, lng, descripcion)
SELECT 'camara_ruta_prueba', 19.429450, -99.129000, 'Camara en medio de ruta de prueba'
WHERE NOT EXISTS (SELECT 1 FROM cameras WHERE nombre = 'camara_ruta_prueba');

INSERT INTO cameras (nombre, lat, lng, descripcion)
SELECT 'camara_insurgentes_reforma', 19.4390, -99.1332, 'Insurgentes × Reforma'
WHERE NOT EXISTS (SELECT 1 FROM cameras WHERE nombre = 'camara_insurgentes_reforma');

INSERT INTO cameras (nombre, lat, lng, descripcion)
SELECT 'camara_reforma_poniente', 19.4248, -99.1820, 'Reforma Poniente'
WHERE NOT EXISTS (SELECT 1 FROM cameras WHERE nombre = 'camara_reforma_poniente');

INSERT INTO cameras (nombre, lat, lng, descripcion)
SELECT 'camara_insurgentes_norte', 19.4580, -99.1476, 'Insurgentes Norte'
WHERE NOT EXISTS (SELECT 1 FROM cameras WHERE nombre = 'camara_insurgentes_norte');

INSERT INTO cameras (nombre, lat, lng, descripcion)
SELECT 'camara_eje_central', 19.4420, -99.1410, 'Eje Central Lázaro Cárdenas'
WHERE NOT EXISTS (SELECT 1 FROM cameras WHERE nombre = 'camara_eje_central');

INSERT INTO cameras (nombre, lat, lng, descripcion)
SELECT 'camara_juarez', 19.4356, -99.1530, 'Av. Juárez'
WHERE NOT EXISTS (SELECT 1 FROM cameras WHERE nombre = 'camara_juarez');