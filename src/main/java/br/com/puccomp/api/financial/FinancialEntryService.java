package br.com.puccomp.api.financial;

import br.com.puccomp.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class FinancialEntryService {

    private final FinancialEntryRepository repository;

    @Transactional
    FinancialEntryResponse create(FinancialEntryRequest request) {
        FinancialEntry entry = FinancialEntry.builder()
                .date(request.date())
                .value(request.value())
                .description(requiredText(request.description(), "A descrição é obrigatória"))
                .type(request.type())
                .category(requiredText(request.category(), "A categoria é obrigatória"))
                .receiptUrl(optionalText(request.receiptUrl()))
                .build();
        return FinancialEntryResponse.from(repository.save(entry));
    }

    @Transactional(readOnly = true)
    Page<FinancialEntryResponse> findAll(LocalDate from, LocalDate to, FinancialEntryType type, Pageable pageable) {
        validatePeriod(from, to);
        return repository.findAll(FinancialEntryRepository.filters(from, to, type), stable(pageable))
                .map(FinancialEntryResponse::from);
    }

    @Transactional(readOnly = true)
    FinancialEntryResponse findById(UUID id) {
        return repository.findById(id)
                .map(FinancialEntryResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Lançamento financeiro não encontrado"));
    }

    @Transactional
    FinancialEntryResponse update(UUID id, FinancialEntryUpdateRequest request) {
        FinancialEntry entry = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lançamento financeiro não encontrado"));

        if (request.date() != null)
            entry.changeDate(request.date());
        if (request.value() != null)
            entry.changeValue(request.value());
        if (request.description() != null)
            entry.changeDescription(requiredText(request.description(), "A descrição não pode ficar em branco"));
        if (request.type() != null)
            entry.changeType(request.type());
        if (request.category() != null)
            entry.changeCategory(requiredText(request.category(), "A categoria não pode ficar em branco"));
        if (request.receiptUrl() != null)
            entry.changeReceiptUrl(optionalText(request.receiptUrl()));

        return FinancialEntryResponse.from(entry);
    }

    @Transactional
    void delete(UUID id) {
        FinancialEntry entry = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lançamento financeiro não encontrado"));
        repository.delete(entry);
    }

    private static void validatePeriod(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to))
            throw new IllegalArgumentException("Período inválido: from deve ser menor ou igual a to");
    }

    private static Pageable stable(Pageable pageable) {
        if (pageable.getSort().isSorted())
            return pageable;
        Sort sort = Sort.by(Sort.Order.desc("date"), Sort.Order.desc("createdAt"));
        if (pageable.isUnpaged())
            return Pageable.unpaged(sort);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private static String requiredText(String value, String message) {
        String trimmed = value == null ? null : value.trim();
        if (trimmed == null || trimmed.isBlank())
            throw new IllegalArgumentException(message);
        return trimmed;
    }

    private static String optionalText(String value) {
        String trimmed = value == null ? null : value.trim();
        if (trimmed == null || trimmed.isBlank())
            return null;
        validateUrl(trimmed);
        return trimmed;
    }

    private static void validateUrl(String value) {
        try {
            URI uri = new URI(value);
            if (uri.getScheme() == null || uri.getHost() == null)
                throw new IllegalArgumentException("A URL do comprovante deve ser válida");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("A URL do comprovante deve ser válida");
        }
    }
}
