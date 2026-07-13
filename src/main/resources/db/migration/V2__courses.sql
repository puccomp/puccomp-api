create table courses (
    active boolean not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    id uuid not null,
    tenant_id uuid not null,
    name varchar(255) not null,
    primary key (id),
    constraint uk_courses_tenant_name unique (tenant_id, name)
);

-- Curso deixa de ser enum global e passa a ser catálogo por EJ. Cada EJ existente
-- herda como catálogo próprio exatamente os cursos que seus membros já usavam.
insert into courses (id, tenant_id, name, active, created_at, updated_at)
select gen_random_uuid(), src.tenant_id, src.name, true, now(), now()
from (
    select distinct m.tenant_id,
        case m.course
            when 'COMPUTER_SCIENCE' then 'Ciência da Computação'
            when 'DATA_SCIENCE' then 'Ciência de Dados'
            when 'SOFTWARE_ENGINEERING' then 'Engenharia de Software'
            when 'COMPUTER_ENGINEERING' then 'Engenharia de Computação'
            when 'INFORMATION_SYSTEMS' then 'Sistemas de Informação'
        end as name
    from members m
) src;

alter table members add column course_id uuid;

update members m
set course_id = c.id
from courses c
where c.tenant_id = m.tenant_id
  and c.name = case m.course
        when 'COMPUTER_SCIENCE' then 'Ciência da Computação'
        when 'DATA_SCIENCE' then 'Ciência de Dados'
        when 'SOFTWARE_ENGINEERING' then 'Engenharia de Software'
        when 'COMPUTER_ENGINEERING' then 'Engenharia de Computação'
        when 'INFORMATION_SYSTEMS' then 'Sistemas de Informação'
      end;

alter table members alter column course_id set not null;
alter table members add constraint fk_members_course foreign key (course_id) references courses;
alter table members drop column course;
