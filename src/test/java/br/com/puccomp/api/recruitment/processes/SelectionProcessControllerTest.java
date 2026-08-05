package br.com.puccomp.api.recruitment.processes;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class SelectionProcessControllerTest {

    @Test
    void shouldReturnApplicationsPage() {
        SelectionProcessService service = Mockito.mock(SelectionProcessService.class);
        when(service.listApplications(any(UUID.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        UUID processId = UUID.fromString("5b7532ee-c515-41ed-84d5-4f49b4903747");

        assertThat(new SelectionProcessController(service)
                .listApplications(processId, Pageable.unpaged()))
                .isEmpty();
    }
}
