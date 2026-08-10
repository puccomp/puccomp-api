package br.com.puccomp.api.financial;

import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class FinancialEntryService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("occurredOn"), Sort.Order.desc("createdAt"));

    private final FinancialEntryRepository repository;

    @Transactional
    FinancialEntryResponse create(FinancialEntryRequest request) {
        FinancialEntry entry = FinancialEntry.builder()
                .occurredOn(request.occurredOn())
                .amount(request.amount())
                .description(request.description().trim())
                .type(request.type())
                .category(request.category().trim())
                .receiptUrl(blankToNull(request.receiptUrl()))
                .build();
        return FinancialEntryResponse.from(repository.save(entry));
    }

    @Transactional(readOnly = true)
    Page<FinancialEntryResponse> findAll(LocalDate from, LocalDate to, FinancialEntryType type, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to))
            throw new IllegalArgumentException("Período inválido: from deve ser menor ou igual a to");

        return repository.findAll(FinancialEntryFilters.of(from, to, type), newestFirstBy(pageable))
                .map(FinancialEntryResponse::from);
    }

    @Transactional(readOnly = true)
    FinancialEntryResponse findById(UUID id) {
        return FinancialEntryResponse.from(findEntry(id));
    }

    @Transactional
    FinancialEntryResponse update(UUID id, FinancialEntryUpdateRequest request) {
        FinancialEntry entry = findEntry(id);

        if (request.occurredOn() != null)
            entry.changeOccurredOn(request.occurredOn());
        if (request.amount() != null)
            entry.changeAmount(request.amount());
        if (request.description() != null)
            entry.changeDescription(request.description().trim());
        if (request.type() != null)
            entry.changeType(request.type());
        if (request.category() != null)
            entry.changeCategory(request.category().trim());
        if (request.receiptUrl() != null)
            entry.changeReceiptUrl(blankToNull(request.receiptUrl()));

        return FinancialEntryResponse.from(repository.save(entry));
    }

    @Transactional
    void delete(UUID id) {
        repository.delete(findEntry(id));
    }

    private FinancialEntry findEntry(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lançamento financeiro não encontrado"));
    }

    /** Mantém a ordenação pedida pelo cliente; sem ela, o extrato sai do mais recente para o mais antigo. */
    private static Pageable newestFirstBy(Pageable pageable) {
        if (pageable.getSort().isSorted())
            return pageable;
        if (pageable.isUnpaged())
            return Pageable.unpaged(NEWEST_FIRST);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank())
            return null;
        return value.trim();
    }
}
