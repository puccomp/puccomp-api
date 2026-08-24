-- linkedin_url e portfolio_url fixavam dois links no esquema. Cada EJ pede o que faz sentido
-- para ela (GitHub, Behance, currículo), então a ficha passa a guardar uma lista ordenada.
create table candidate_links (
    candidate_id uuid not null,
    link_order integer not null,
    url varchar(500) not null,
    primary key (candidate_id, link_order),
    constraint fk_candidate_links_candidate foreign key (candidate_id)
        references candidates (id) on delete cascade
);

insert into candidate_links (candidate_id, link_order, url)
select id, 0, linkedin_url
from candidates
where linkedin_url is not null and btrim(linkedin_url) <> '';

-- O portfólio entra depois do linkedin de quem tinha os dois, e na posição 0 de quem só tinha ele.
insert into candidate_links (candidate_id, link_order, url)
select c.id,
       case when c.linkedin_url is not null and btrim(c.linkedin_url) <> '' then 1 else 0 end,
       c.portfolio_url
from candidates c
where c.portfolio_url is not null and btrim(c.portfolio_url) <> '';

alter table candidates drop column linkedin_url;
alter table candidates drop column portfolio_url;

-- O período deixa de ser número: cada curso tem uma grade diferente e há quem esteja
-- irregular ou formando, o que nenhum inteiro representa bem.
alter table candidacies alter column current_term type varchar(50) using current_term::varchar;
alter table candidacies alter column current_term drop not null;
