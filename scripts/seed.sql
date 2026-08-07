-- Dados de exemplo (cargos, departamentos e membros) para a EJ de desenvolvimento.
-- Pressupõe que o tenant 'ej-comp' já exista — ele é criado no primeiro `bootRun` (DevDataSeeder).
-- Tudo é escopado a esse tenant e idempotente: rodar de novo não duplica.
-- Os membros nascem sem conta vinculada (account_id NULL) e com standing MEMBER; o dono continua
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

-- cursos aceitos pela EJ (mesmo catálogo criado pelo DevDataSeeder no primeiro boot)
WITH t AS (SELECT id FROM tenants WHERE slug = 'ej-comp')
INSERT INTO courses (id, tenant_id, name, active, created_at, updated_at)
SELECT gen_random_uuid(), t.id, c.name, true, now(), now()
FROM t, (VALUES
    ('Ciência da Computação'),
    ('Ciência de Dados'),
    ('Engenharia de Software'),
    ('Engenharia de Computação'),
    ('Sistemas de Informação')
) AS c(name)
ON CONFLICT (tenant_id, name) DO NOTHING;

-- membros (course_id resolvido pelo nome do curso)
WITH t AS (SELECT id FROM tenants WHERE slug = 'ej-comp')
INSERT INTO members (id, tenant_id, account_id, name, standing, status, course_id, role_id, department_id)
SELECT gen_random_uuid(), t.id, NULL, m.name, 'MEMBER', m.status,
       (SELECT id FROM courses     WHERE tenant_id = t.id AND name = m.course),
       (SELECT id FROM roles       WHERE tenant_id = t.id AND name = m.role),
       (SELECT id FROM departments WHERE tenant_id = t.id AND name = m.department)
FROM t, (VALUES
    ('Ana Lima',         'ACTIVE',   'Engenharia de Software',   'Presidente',      'Presidência'),
    ('Bruno Carvalho',   'ACTIVE',   'Ciência da Computação',    'Vice-Presidente', 'Presidência'),
    ('Carla Mendes',     'ACTIVE',   'Sistemas de Informação',   'Diretor',         'Comercial'),
    ('Diego Souza',      'ACTIVE',   'Engenharia de Computação', 'Diretor',         'Tecnologia'),
    ('Eduarda Ferreira', 'ACTIVE',   'Ciência de Dados',         'Diretor',         'Marketing'),
    ('Felipe Rocha',     'ACTIVE',   'Ciência da Computação',    'Trainee',         'Tecnologia'),
    ('Gabriela Costa',   'ACTIVE',   'Engenharia de Software',   'Trainee',         'Projetos'),
    ('Henrique Alves',   'PENDING',  'Sistemas de Informação',   'Trainee',         'Comercial'),
    ('Isabela Nunes',    'PENDING',  'Ciência da Computação',    'Trainee',         'Marketing'),
    ('João Pereira',     'INACTIVE', 'Engenharia de Software',   'Trainee',         'Projetos')
) AS m(name, status, course, role, department)
WHERE NOT EXISTS (SELECT 1 FROM members ex WHERE ex.tenant_id = t.id AND ex.name = m.name);
