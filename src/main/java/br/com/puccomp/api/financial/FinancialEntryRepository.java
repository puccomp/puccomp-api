package br.com.puccomp.api.financial;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

interface FinancialEntryRepository extends JpaRepository<FinancialEntry, UUID>,
        JpaSpecificationExecutor<FinancialEntry> {
}
