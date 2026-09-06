-- "Aceitamos do 1º ao 6º período" vivia só no texto do edital, sem ninguém conferir. Com o período
-- da inscrição já numérico (V14), a regra passa a ser verificável.
alter table selection_processes add column min_term smallint;
alter table selection_processes add column max_term smallint;

alter table selection_processes add constraint ck_selection_processes_term_range
    check ((min_term is null or min_term between 1 and 12)
       and (max_term is null or max_term between 1 and 12)
       and (min_term is null or max_term is null or min_term <= max_term));
