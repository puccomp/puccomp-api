-- Curso da inscrição deixa de ser texto livre e passa a apontar para o catálogo da EJ. Como texto,
-- "Ciência da Computação", "ciencia da computacao" e "CC" eram três cursos diferentes, e não havia
-- como agrupar nem filtrar depois.
alter table candidate_applications add column course_id uuid;

update candidate_applications a
set course_id = c.id
from courses c
where c.tenant_id = a.tenant_id
  and lower(trim(c.name)) = lower(trim(a.course));

-- O que não casou com o catálogo vira curso INATIVO: preserva o histórico da inscrição sem
-- ressuscitar no formulário um curso que a EJ nunca declarou aceitar.
insert into courses (id, tenant_id, name, active, created_at, updated_at)
select gen_random_uuid(), a.tenant_id, trim(a.course), false, now(), now()
from candidate_applications a
where a.course_id is null
group by a.tenant_id, trim(a.course);

update candidate_applications a
set course_id = c.id
from courses c
where a.course_id is null
  and c.tenant_id = a.tenant_id
  and lower(trim(c.name)) = lower(trim(a.course));

alter table candidate_applications alter column course_id set not null;
alter table candidate_applications add constraint fk_candidate_applications_course
    foreign key (course_id) references courses;
alter table candidate_applications drop column course;

-- Período vira número: "3º período", "3o periodo" e "terceiro" eram o mesmo dado em três formatos.
-- O que não tiver número reconhecível ou cair fora de 1..12 fica nulo — o campo sempre foi opcional.
alter table candidate_applications add column current_term_number smallint;

update candidate_applications
set current_term_number = substring(current_term from '[0-9]{1,2}')::smallint
where current_term ~ '[0-9]';

update candidate_applications
set current_term_number = null
where current_term_number is not null and current_term_number not between 1 and 12;

alter table candidate_applications drop column current_term;
alter table candidate_applications rename column current_term_number to current_term;
alter table candidate_applications add constraint ck_candidate_applications_current_term
    check (current_term is null or current_term between 1 and 12);
