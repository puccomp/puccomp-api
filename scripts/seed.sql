-- Dados de exemplo para a EJ de desenvolvimento: diretorias, cargos, cursos, membros,
-- convites, PAT, processos seletivos, candidatos e lançamentos financeiros.
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

-- convites em estados distintos (pendente, expirado, revogado). Os tokens em claro ficam
-- aqui de propósito: são de desenvolvimento e o banco guarda só o sha256 deles.
--   pendente → inv_pendente_dev_2026
--   expirado → inv_expirado_dev_2026
--   revogado → inv_revogado_dev_2026
WITH context AS (
    SELECT t.id AS tenant_id, a.id AS owner_account_id
    FROM tenants t
    JOIN accounts a ON a.email = 'dono@ejcomp.dev'
    WHERE t.slug = 'ej-comp'
)
INSERT INTO invitations (
    id, tenant_id, email, standing, role_id, token_hash, token_prefix,
    expires_at, accepted_at, revoked_at, created_by_account_id, created_at, updated_at
)
SELECT gen_random_uuid(), c.tenant_id, i.email, i.standing,
       (SELECT id FROM roles WHERE tenant_id = c.tenant_id AND name = i.role_name),
       i.token_hash, i.token_prefix, i.expires_at, NULL, i.revoked_at,
       c.owner_account_id, now(), now()
FROM context c, (VALUES
    ('convite.pendente@ejcomp.dev', 'MEMBER', 'Trainee',
     'fc241f7f9086d36647dfbc0da4e67e57177f44a10a8d9df5b5f4fa21cb26fb74',
     'inv_pendente', now() + interval '72 hours', NULL::timestamptz),
    ('convite.expirado@ejcomp.dev', 'MEMBER', 'Trainee',
     'c23619e70118306c024f65673a6f265347e225ff6cba4173a8450d7666ed26ae',
     'inv_expirado', now() - interval '24 hours', NULL::timestamptz),
    ('convite.revogado@ejcomp.dev', 'MEMBER', 'Diretor de Marketing',
     'e0c177dd9180f2a7c1dbb3e25da982a44929ef7e245088779b1bfed5baf69006',
     'inv_revogado', now() + interval '24 hours', now() - interval '2 hours')
) AS i(email, standing, role_name, token_hash, token_prefix, expires_at, revoked_at)
WHERE NOT EXISTS (
    SELECT 1 FROM invitations existing
    WHERE existing.tenant_id = c.tenant_id AND lower(existing.email) = lower(i.email)
);

-- PAT do dono para chamadas locais: Authorization: Bearer pat_dev_seed_owner_token_2026
WITH context AS (
    SELECT t.id AS tenant_id, a.id AS owner_account_id
    FROM tenants t
    JOIN accounts a ON a.email = 'dono@ejcomp.dev'
    WHERE t.slug = 'ej-comp'
)
INSERT INTO personal_access_tokens (
    id, tenant_id, account_id, name, token_hash, token_prefix, scopes,
    expires_at, last_used_at, revoked_at, created_at, updated_at
)
SELECT gen_random_uuid(), c.tenant_id, c.owner_account_id, 'Token local do seed',
       '5540f3145c0a2ff752d896b084d415052eecbd21877e89e8c5cca5e0d628897c',
       'pat_dev_seed', NULL, now() + interval '1 year', NULL, NULL, now(), now()
FROM context c
WHERE NOT EXISTS (
    SELECT 1 FROM personal_access_tokens existing
    WHERE existing.tenant_id = c.tenant_id AND existing.name = 'Token local do seed'
);

-- processos seletivos cobrindo todos os estados, para exercitar listagens e filtros
WITH t AS (SELECT id FROM tenants WHERE slug = 'ej-comp')
INSERT INTO selection_processes (
    id, tenant_id, title, description, status, opens_at, closes_at, created_at, updated_at
)
SELECT gen_random_uuid(), t.id, p.title, p.description, p.status, p.opens_at, p.closes_at, now(), now()
FROM t, (VALUES
    ('Processo Seletivo 2027.1', 'Próximo ciclo de entrada da EJ', 'DRAFT',
     now() + interval '120 days', now() + interval '150 days'),
    ('Processo Seletivo 2026.2', 'Processo aberto para novos membros', 'OPEN',
     now() - interval '15 days', now() + interval '30 days'),
    ('Processo Seletivo Encerrado 2026.1', 'Processo com inscrições encerradas', 'CLOSED',
     now() - interval '180 days', now() - interval '120 days'),
    ('Processo Seletivo 2025.2', 'Ciclo concluído e arquivado', 'FINISHED',
     now() - interval '360 days', now() - interval '300 days'),
    ('Processo Seletivo Extraordinário', 'Ciclo cancelado', 'CANCELLED',
     now() - interval '60 days', now() - interval '30 days')
) AS p(title, description, status, opens_at, closes_at)
WHERE NOT EXISTS (
    SELECT 1 FROM selection_processes existing
    WHERE existing.tenant_id = t.id AND existing.title = p.title
);

-- candidatos: a pessoa é única por EJ e e-mail, e vive fora da inscrição
WITH t AS (SELECT id FROM tenants WHERE slug = 'ej-comp')
INSERT INTO candidates (id, tenant_id, full_name, email, phone, created_at, updated_at)
SELECT gen_random_uuid(), t.id, c.full_name, c.email, c.phone, now(), now()
FROM t, (VALUES
    ('Mariana Oliveira', 'mariana.oliveira@example.com', '(19) 99911-2233'),
    ('Rafael Santos',    'rafael.santos@example.com',    '(19) 99822-3344'),
    ('Larissa Gomes',    'larissa.gomes@example.com',    '(19) 99733-4455')
) AS c(full_name, email, phone)
ON CONFLICT (tenant_id, lower(email)) DO NOTHING;

-- links da ficha, na ordem em que apareceriam no formulário
WITH t AS (SELECT id FROM tenants WHERE slug = 'ej-comp')
INSERT INTO candidate_links (candidate_id, link_order, url)
SELECT ca.id, l.link_order, l.url
FROM t
JOIN candidates ca ON ca.tenant_id = t.id
JOIN (VALUES
    ('mariana.oliveira@example.com', 0, 'https://www.linkedin.com/in/mariana-oliveira'),
    ('mariana.oliveira@example.com', 1, 'https://github.com/mariana-oliveira'),
    ('rafael.santos@example.com',    0, 'https://www.linkedin.com/in/rafael-santos'),
    ('larissa.gomes@example.com',    0, 'https://larissagomes.dev')
) AS l(email, link_order, url) ON lower(l.email) = lower(ca.email)
ON CONFLICT (candidate_id, link_order) DO NOTHING;

-- inscrições ligadas ao processo aberto; curso e período são fato do momento da inscrição
WITH context AS (
    SELECT t.id AS tenant_id, p.id AS process_id
    FROM tenants t
    JOIN selection_processes p ON p.tenant_id = t.id
    WHERE t.slug = 'ej-comp' AND p.title = 'Processo Seletivo 2026.2'
)
INSERT INTO candidacies (
    id, tenant_id, process_id, candidate_id, course, current_term,
    status, privacy_consent_at, created_at, updated_at
)
SELECT gen_random_uuid(), c.tenant_id, c.process_id, ca.id, a.course, a.current_term,
       'SUBMITTED', now(), now(), now()
FROM context c
JOIN candidates ca ON ca.tenant_id = c.tenant_id
JOIN (VALUES
    ('mariana.oliveira@example.com', 'Ciência da Computação',  '4º semestre'),
    ('rafael.santos@example.com',    'Engenharia de Software', '2º semestre'),
    ('larissa.gomes@example.com',    'Sistemas de Informação', 'formanda')
) AS a(email, course, current_term) ON lower(a.email) = lower(ca.email)
ON CONFLICT (process_id, candidate_id) DO NOTHING;

-- movimentações financeiras de receitas e despesas
WITH t AS (SELECT id FROM tenants WHERE slug = 'ej-comp')
INSERT INTO financial_entries (
    id, tenant_id, occurred_on, amount, description, type, category,
    receipt_url, created_at, updated_at
)
SELECT gen_random_uuid(), t.id, f.occurred_on, f.amount, f.description, f.type,
       f.category, f.receipt_url, now(), now()
FROM t, (VALUES
    (current_date - 45, 8500.00::numeric, 'Projeto de desenvolvimento web', 'INCOME', 'Projetos',
     'https://example.com/comprovantes/projeto-web.pdf'),
    (current_date - 30, 3200.00::numeric, 'Consultoria em dados', 'INCOME', 'Projetos',
     'https://example.com/comprovantes/consultoria-dados.pdf'),
    (current_date - 20, 450.90::numeric, 'Assinaturas de ferramentas', 'EXPENSE', 'Software', NULL),
    (current_date - 12, 780.00::numeric, 'Material para evento de recrutamento', 'EXPENSE', 'Eventos',
     'https://example.com/comprovantes/evento.pdf'),
    (current_date - 5, 190.50::numeric, 'Hospedagem e infraestrutura', 'EXPENSE', 'Infraestrutura', NULL),
    (current_date - 1, 1250.00::numeric, 'Parcela de projeto mobile', 'INCOME', 'Projetos', NULL)
) AS f(occurred_on, amount, description, type, category, receipt_url)
WHERE NOT EXISTS (
    SELECT 1 FROM financial_entries existing
    WHERE existing.tenant_id = t.id
      AND existing.occurred_on = f.occurred_on
      AND existing.amount = f.amount
      AND existing.description = f.description
      AND existing.type = f.type
);
