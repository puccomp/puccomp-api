-- Dados de exemplo (diretorias, cargos e membros) para a EJ de desenvolvimento.
-- Pressupõe que o tenant 'ej-comp' já exista — ele é criado no primeiro `bootRun` (DevDataSeeder).
-- Tudo é escopado a esse tenant e idempotente: rodar de novo não duplica.
-- Os membros nascem sem conta vinculada (account_id NULL) e com standing MEMBER; o dono continua
-- sendo a conta OWNER criada pelo DevDataSeeder.

-- diretorias (antes dos cargos: roles.department_id referencia esta tabela)
WITH t AS (SELECT id FROM tenants WHERE slug = 'ej-comp')
INSERT INTO departments (id, tenant_id, name, description, active, created_at, updated_at)
SELECT gen_random_uuid(), t.id, d.name, d.description, true, now(), now()
FROM t, (VALUES
    ('Presidência', 'Coordenação geral da EJ'),
    ('Comercial',   'Prospecção e fechamento de projetos'),
    ('Marketing',   'Comunicação, marca e captação'),
    ('Projetos',    'Execução e entrega dos projetos'),
    ('Tecnologia',  'Desenvolvimento e infraestrutura')
) AS d(name, description)
ON CONFLICT (tenant_id, name) DO NOTHING;

-- cargos (department_id nulo em cargo genérico, que não pertence a nenhuma diretoria)
WITH t AS (SELECT id FROM tenants WHERE slug = 'ej-comp')
INSERT INTO roles (id, tenant_id, name, description, department_id, max_seats, active, created_at, updated_at)
SELECT gen_random_uuid(), t.id, r.name, r.description,
       (SELECT id FROM departments WHERE tenant_id = t.id AND name = r.department),
       r.max_seats, true, now(), now()
FROM t, (VALUES
    ('Presidente',            'Lidera a EJ e responde pela organização',   NULL,          1),
    ('Vice-Presidente',       'Apoia a presidência e coordena diretorias', NULL,          1),
    ('Diretor Comercial',     'Coordena a diretoria comercial',            'Comercial',   1),
    ('Diretor de Marketing',  'Coordena a diretoria de marketing',         'Marketing',   1),
    ('Diretor de Projetos',   'Coordena a diretoria de projetos',          'Projetos',    1),
    ('Diretor de Tecnologia', 'Coordena a diretoria de tecnologia',        'Tecnologia',  1),
    ('Trainee',               'Membro em formação inicial',                NULL,          NULL::int)
) AS r(name, description, department, max_seats)
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

-- membros: quem tem cargo de diretoria herda a diretoria do cargo; quem tem cargo genérico
-- (Presidente, Vice-Presidente, Trainee) recebe a diretoria informada aqui.
WITH t AS (SELECT id FROM tenants WHERE slug = 'ej-comp')
INSERT INTO members (id, tenant_id, account_id, name, standing, status, course_id, role_id, department_id)
SELECT gen_random_uuid(), t.id, NULL, m.name, 'MEMBER', m.status,
       (SELECT id FROM courses WHERE tenant_id = t.id AND name = m.course),
       (SELECT id FROM roles WHERE tenant_id = t.id AND name = m.role),
       COALESCE(
           (SELECT department_id FROM roles WHERE tenant_id = t.id AND name = m.role),
           (SELECT id FROM departments WHERE tenant_id = t.id AND name = m.department))
FROM t, (VALUES
    ('Ana Lima',         'ACTIVE',   'Engenharia de Software',   'Presidente',            'Presidência'),
    ('Bruno Carvalho',   'ACTIVE',   'Ciência da Computação',    'Vice-Presidente',       'Presidência'),
    ('Carla Mendes',     'ACTIVE',   'Sistemas de Informação',   'Diretor Comercial',     NULL),
    ('Diego Souza',      'ACTIVE',   'Engenharia de Computação', 'Diretor de Tecnologia', NULL),
    ('Eduarda Ferreira', 'ACTIVE',   'Ciência de Dados',         'Diretor de Marketing',  NULL),
    ('Felipe Rocha',     'ACTIVE',   'Ciência da Computação',    'Trainee',               'Tecnologia'),
    ('Gabriela Costa',   'ACTIVE',   'Engenharia de Software',   'Trainee',               'Projetos'),
    ('Henrique Alves',   'PENDING',  'Sistemas de Informação',   'Trainee',               'Comercial'),
    ('Isabela Nunes',    'PENDING',  'Ciência da Computação',    'Trainee',               'Marketing'),
    ('João Pereira',     'INACTIVE', 'Engenharia de Software',   'Trainee',               'Projetos')
) AS m(name, status, course, role, department)
WHERE NOT EXISTS (SELECT 1 FROM members ex WHERE ex.tenant_id = t.id AND ex.name = m.name);
