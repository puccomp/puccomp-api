-- Dados de exemplo (cargos, departamentos e membros) para a EJ de desenvolvimento.
-- Pressupõe que o tenant 'ej-comp' já exista — ele é criado no primeiro `bootRun` (DevDataSeeder).
-- Tudo é escopado a esse tenant e idempotente: rodar de novo não duplica.
-- Os membros nascem sem conta vinculada (account_id NULL) e com standing STAFF; o dono continua
-- sendo a conta OWNER criada pelo DevDataSeeder.

-- cargos
WITH t AS (SELECT id FROM tenants WHERE slug = 'ej-comp')
INSERT INTO roles (id, tenant_id, name, description, hierarchy_level, max_seats, active, created_at, updated_at)
SELECT gen_random_uuid(), t.id, r.name, r.description, r.hierarchy_level, r.max_seats, true, now(), now()
FROM t, (VALUES
    ('Presidente',      'Lidera a EJ e responde pela organização',   1, 1),
    ('Vice-Presidente', 'Apoia a presidência e coordena diretorias', 2, 1),
    ('Diretor',         'Coordena uma diretoria',                    3, NULL::int),
    ('Trainee',         'Membro em formação inicial',                4, NULL::int)
) AS r(name, description, hierarchy_level, max_seats)
ON CONFLICT (tenant_id, name) DO NOTHING;

-- departamentos (lead_role aponta para o cargo que lidera o setor)
WITH t AS (SELECT id FROM tenants WHERE slug = 'ej-comp')
INSERT INTO departments (id, tenant_id, name, slug, description, lead_role_id, active, created_at, updated_at)
SELECT gen_random_uuid(), t.id, d.name, d.slug, d.description,
       (SELECT id FROM roles WHERE tenant_id = t.id AND name = d.lead_role), true, now(), now()
FROM t, (VALUES
    ('Presidência', 'presidencia', 'Coordenação geral da EJ',             'Presidente'),
    ('Comercial',   'comercial',   'Prospecção e fechamento de projetos', 'Diretor'),
    ('Marketing',   'marketing',   'Comunicação, marca e captação',       'Diretor'),
    ('Projetos',    'projetos',    'Execução e entrega dos projetos',     'Diretor'),
    ('Tecnologia',  'tecnologia',  'Desenvolvimento e infraestrutura',    'Diretor')
) AS d(name, slug, description, lead_role)
ON CONFLICT (tenant_id, name) DO NOTHING;

-- membros
WITH t AS (SELECT id FROM tenants WHERE slug = 'ej-comp')
INSERT INTO members (id, tenant_id, account_id, name, standing, status, course, role_id, department_id)
SELECT gen_random_uuid(), t.id, NULL, m.name, 'STAFF', m.status, m.course,
       (SELECT id FROM roles       WHERE tenant_id = t.id AND name = m.role),
       (SELECT id FROM departments WHERE tenant_id = t.id AND name = m.department)
FROM t, (VALUES
    ('Ana Lima',         'ACTIVE',   'SOFTWARE_ENGINEERING', 'Presidente',      'Presidência'),
    ('Bruno Carvalho',   'ACTIVE',   'COMPUTER_SCIENCE',     'Vice-Presidente', 'Presidência'),
    ('Carla Mendes',     'ACTIVE',   'INFORMATION_SYSTEMS',  'Diretor',         'Comercial'),
    ('Diego Souza',      'ACTIVE',   'COMPUTER_ENGINEERING', 'Diretor',         'Tecnologia'),
    ('Eduarda Ferreira', 'ACTIVE',   'DATA_SCIENCE',         'Diretor',         'Marketing'),
    ('Felipe Rocha',     'ACTIVE',   'COMPUTER_SCIENCE',     'Trainee',         'Tecnologia'),
    ('Gabriela Costa',   'ACTIVE',   'SOFTWARE_ENGINEERING', 'Trainee',         'Projetos'),
    ('Henrique Alves',   'PENDING',  'INFORMATION_SYSTEMS',  'Trainee',         'Comercial'),
    ('Isabela Nunes',    'PENDING',  'COMPUTER_SCIENCE',     'Trainee',         'Marketing'),
    ('João Pereira',     'INACTIVE', 'SOFTWARE_ENGINEERING', 'Trainee',         'Projetos')
) AS m(name, status, course, role, department)
WHERE NOT EXISTS (SELECT 1 FROM members ex WHERE ex.tenant_id = t.id AND ex.name = m.name);
