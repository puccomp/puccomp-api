--Troca o tipo da coluna do período do candidato de String para Int
alter table candidacies alter column current_term type integer using current_term::integer;