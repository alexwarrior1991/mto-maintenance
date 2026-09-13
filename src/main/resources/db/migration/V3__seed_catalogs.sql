-- Semillas de los catalogos: equipos, tipos de tarea y plantillas de inspeccion.
--
-- Los tipos de tarea y sus grupos salen del plan de mantenimiento OCS 2025-2026 (seccion 6-8). Los
-- tiempos que el plan no fija son ORIENTATIVOS y se corrigen por API o con una migracion posterior;
-- los umbrales de las plantillas los define ingenieria con ISR y aqui solo hay valores de partida.

-- ---------------------------------------------------------------------------------------------
-- Equipos (seccion 10 del plan)
-- ---------------------------------------------------------------------------------------------

INSERT INTO maintenance_team (id, code, name, base_name, vehicle) VALUES
    ('0195a000-0000-7000-8000-000000000001', 'A', 'Team A - Rishpon', 'Rishpon', 'Maintenance vehicle A'),
    ('0195a000-0000-7000-8000-000000000002', 'B', 'Team B - Mishmar', 'Mishmar', 'Maintenance vehicle B');

-- ---------------------------------------------------------------------------------------------
-- Tipos de tarea (secciones 6, 7 y 8 del plan)
-- ---------------------------------------------------------------------------------------------

INSERT INTO maintenance_task_type
    (code, description, functional_group, standard_minutes_per_unit, unit, fixed_minutes, requires_full_possession, is_diagnostic, order_index)
VALUES
    -- Grupo 1 - Soportes estructurales
    ('RG-01', 'Insulators (cantilever and anchorage zones)', 'STRUCTURAL_SUPPORTS', 5, 'UNIT', 0, false, false, 1),
    ('RG-02', 'Insulators in tunnels', 'STRUCTURAL_SUPPORTS', 6, 'UNIT', 0, false, false, 2),
    ('RG-05', 'Pole anchorages', 'STRUCTURAL_SUPPORTS', 10, 'UNIT', 0, false, false, 5),
    ('RG-11', 'Cantilevers', 'STRUCTURAL_SUPPORTS', 10, 'UNIT', 0, false, false, 11),
    ('RG-13', 'Steel poles', 'STRUCTURAL_SUPPORTS', 5, 'UNIT', 0, false, false, 13),
    ('RP-01', 'Tunnel insulators in cantilevers (particular revision)', 'STRUCTURAL_SUPPORTS', 10, 'UNIT', 0, false, false, 21),
    ('RP-08', 'Earthing devices', 'STRUCTURAL_SUPPORTS', 5, 'UNIT', 0, false, false, 28),
    -- Grupo 2 - Conductores aereos
    ('RG-08', 'Messenger wire', 'OVERHEAD_CONDUCTORS', 3, 'KM', 60, false, false, 8),
    ('RG-10', 'Contact wires', 'OVERHEAD_CONDUCTORS', 3, 'KM', 30, false, false, 10),
    ('RG-12', 'Droppers', 'OVERHEAD_CONDUCTORS', 5, 'SPAN', 0, false, false, 12),
    ('RG-14', 'Traction voltage return', 'OVERHEAD_CONDUCTORS', 2, 'KM', 30, false, false, 14),
    ('RP-05', 'Contact wire (particular revision)', 'OVERHEAD_CONDUCTORS', 20, 'DEFECT', 0, false, false, 25),
    -- Grupo 3 - Aparatos e interruptores (solo con posesion total)
    ('RP-06', 'Motor disconnectors', 'DEVICES_AND_SWITCHES', 45, 'UNIT', 0, true, false, 26),
    ('RP-07', 'Catenary insulators (particular revision)', 'DEVICES_AND_SWITCHES', 10, 'UNIT', 0, true, false, 27),
    -- Grupo 4 - Componentes de anclaje
    ('RG-09', 'Compensation equipment', 'ANCHORAGE_COMPONENTS', 20, 'UNIT', 0, false, false, 9),
    -- Grupo 5 - Desvios y aisladores de seccion (solo con posesion total)
    ('RG-03', 'Section insulators', 'TURNOUTS_AND_SWITCHES', 30, 'UNIT', 0, true, false, 3),
    ('RG-04', 'Turnouts', 'TURNOUTS_AND_SWITCHES', 45, 'UNIT', 0, true, false, 4),
    ('RP-02', 'Section insulators (particular revision)', 'TURNOUTS_AND_SWITCHES', 45, 'UNIT', 0, true, false, 22),
    ('RP-03', 'Turnouts (particular revision)', 'TURNOUTS_AND_SWITCHES', 60, 'UNIT', 0, true, false, 23),
    -- Grupo 6 - Diagnostico y medidas (campanas propias)
    ('RP-09', 'Static auscultation of catenary', 'DIAGNOSTICS', 15, 'PROFILE', 0, false, true, 29),
    ('RP-10', 'Height and stagger correction per record', 'DIAGNOSTICS', 10, 'DEFECT', 0, false, true, 30),
    ('RP-11', 'Dynamic auscultation (ISR)', 'DIAGNOSTICS', 0.3, 'KM', 0, false, true, 31),
    -- Sin grupo: operaciones independientes
    ('RG-06', 'Catenary automatic valves', 'NONE', 20, 'UNIT', 0, false, false, 6),
    ('RG-07', 'Fixed points', 'NONE', 15, 'UNIT', 0, false, false, 7),
    ('RG-15', 'Hanging gantries supports', 'NONE', 20, 'UNIT', 0, false, false, 15),
    ('RG-16', 'Joints and connections', 'NONE', 5, 'UNIT', 0, false, false, 16),
    ('RG-17', 'Feeders', 'NONE', 2, 'KM', 30, false, false, 17),
    ('RG-18', 'Rigid catenary', 'NONE', 5, 'KM', 60, false, false, 18),
    ('RP-04', 'Compensation equipment (particular revision)', 'NONE', 30, 'UNIT', 0, false, false, 24),
    ('RP-12', 'Neutral sections', 'NONE', 60, 'UNIT', 0, true, false, 32);

-- ---------------------------------------------------------------------------------------------
-- Plantillas de inspeccion por tipo de activo
-- ---------------------------------------------------------------------------------------------

INSERT INTO inspection_template (id, asset_type, version, name) VALUES
    ('0195a000-0000-7000-8000-00000000a001', 'PROFILE', 1, 'Profile (pole, cantilever and wires) inspection'),
    ('0195a000-0000-7000-8000-00000000a002', 'DISCONNECTOR', 1, 'Disconnector inspection'),
    ('0195a000-0000-7000-8000-00000000a003', 'SECTION_INSULATOR', 1, 'Section insulator inspection');

-- Perfil. Alturas y descentramientos en mm; desgaste como espesor residual del hilo en mm.
INSERT INTO inspection_template_item (template_id, code, label, unit, min_value, max_value, requires_measure, order_index) VALUES
    ('0195a000-0000-7000-8000-00000000a001', 'CW_HEIGHT', 'Contact wire height at the pole', 'mm', 5000, 5500, true, 1),
    ('0195a000-0000-7000-8000-00000000a001', 'CW_STAGGER', 'Contact wire stagger at the pole (signed)', 'mm', -300, 300, true, 2),
    ('0195a000-0000-7000-8000-00000000a001', 'CW_WEAR', 'Contact wire residual thickness', 'mm', 8.5, NULL, true, 3),
    ('0195a000-0000-7000-8000-00000000a001', 'CW_GRADIENT', 'Contact wire gradient to the next profile', 'mm/m', NULL, 3, true, 4),
    ('0195a000-0000-7000-8000-00000000a001', 'TENSION', 'Mechanical tension (contact and messenger wire)', 'kN', NULL, NULL, false, 5),
    ('0195a000-0000-7000-8000-00000000a001', 'CANTILEVER', 'Cantilever geometry, inclination and screws tightened', NULL, NULL, NULL, false, 6),
    ('0195a000-0000-7000-8000-00000000a001', 'STEADY_ARM', 'Steady arm angle and fixing', NULL, NULL, NULL, false, 7),
    ('0195a000-0000-7000-8000-00000000a001', 'DROPPERS', 'Droppers straight, loops attached, no damage', NULL, NULL, NULL, false, 8),
    ('0195a000-0000-7000-8000-00000000a001', 'INSULATORS', 'Insulators clean, no cracks or flashover marks', NULL, NULL, NULL, false, 9),
    ('0195a000-0000-7000-8000-00000000a001', 'POLE_FOUNDATION', 'Pole and foundation state, foundation bolts secured', NULL, NULL, NULL, false, 10),
    ('0195a000-0000-7000-8000-00000000a001', 'SUPPORT', 'Support / registration hardware (supportType) state', NULL, NULL, NULL, false, 11),
    ('0195a000-0000-7000-8000-00000000a001', 'ANCHORAGE', 'Anchorage and balance weight state (if any)', NULL, NULL, NULL, false, 12),
    ('0195a000-0000-7000-8000-00000000a001', 'EARTHING', 'Earthing and rail bonding (E&B) cable connected', NULL, NULL, NULL, false, 13),
    ('0195a000-0000-7000-8000-00000000a001', 'FOREIGN_OBJECTS', 'No bird nests or foreign objects', NULL, NULL, NULL, false, 14);

-- Seccionador.
INSERT INTO inspection_template_item (template_id, code, label, unit, min_value, max_value, requires_measure, order_index) VALUES
    ('0195a000-0000-7000-8000-00000000a002', 'CONTACTS', 'Main contacts state and alignment', NULL, NULL, NULL, false, 1),
    ('0195a000-0000-7000-8000-00000000a002', 'DRIVE', 'Operating mechanism (manual / motor) operates freely', NULL, NULL, NULL, false, 2),
    ('0195a000-0000-7000-8000-00000000a002', 'INSULATORS', 'Post insulators clean, no cracks', NULL, NULL, NULL, false, 3),
    ('0195a000-0000-7000-8000-00000000a002', 'EARTHING', 'Earthing connection and earthing blade', NULL, NULL, NULL, false, 4),
    ('0195a000-0000-7000-8000-00000000a002', 'CONTINUITY', 'Electrical continuity / contact resistance', 'uOhm', NULL, 100, true, 5),
    ('0195a000-0000-7000-8000-00000000a002', 'POSITION', 'Position indication matches actual state', NULL, NULL, NULL, false, 6),
    ('0195a000-0000-7000-8000-00000000a002', 'LUBRICATION', 'Lubrication of moving parts', NULL, NULL, NULL, false, 7),
    ('0195a000-0000-7000-8000-00000000a002', 'CONNECTIONS', 'Terminal connections and jumpers tightened', NULL, NULL, NULL, false, 8);

-- Aislador de seccion.
INSERT INTO inspection_template_item (template_id, code, label, unit, min_value, max_value, requires_measure, order_index) VALUES
    ('0195a000-0000-7000-8000-00000000a003', 'RUNNERS', 'Runners / skids wear', 'mm', NULL, NULL, true, 1),
    ('0195a000-0000-7000-8000-00000000a003', 'ARCING_HORNS', 'Arcing horns state and gap', 'mm', NULL, NULL, true, 2),
    ('0195a000-0000-7000-8000-00000000a003', 'BODY', 'Insulating body without cracks or tracking', NULL, NULL, NULL, false, 3),
    ('0195a000-0000-7000-8000-00000000a003', 'ALIGNMENT', 'Alignment and level with the contact wire', 'mm', -5, 5, true, 4),
    ('0195a000-0000-7000-8000-00000000a003', 'CLAMPS', 'Fixing clamps tightened', NULL, NULL, NULL, false, 5),
    ('0195a000-0000-7000-8000-00000000a003', 'INSULATION', 'Insulation resistance', 'MOhm', 100, NULL, true, 6),
    ('0195a000-0000-7000-8000-00000000a003', 'HEIGHT', 'Height at the section insulator', 'mm', 5000, 5500, true, 7);
