package br.com.puccomp.api.shared.aggregation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * O share vem calculado do servidor para que toda distribuição de uma resposta use o mesmo
 * denominador. Isso o torna parte do contrato — e o contrato inclui os casos em que dividir
 * não faz sentido.
 */
class SliceTest {

    @Test
    @DisplayName("distribuição exaustiva soma o denominador, e os shares fecham em 1")
    void shouldSumToOneAcrossAnExhaustiveDistribution() {
        List<Slice> tercos = List.of(
                Slice.of(CategoryKey.of(1, "um"), 1, 3),
                Slice.of(CategoryKey.of(2, "dois"), 1, 3),
                Slice.of(CategoryKey.absent("Sem vínculo"), 1, 3));

        assertThat(tercos.stream().mapToLong(Slice::count).sum()).isEqualTo(3);
        assertThat(tercos.stream().mapToDouble(Slice::share).sum()).isCloseTo(1d, within(1e-9));
    }

    @Test
    @DisplayName("total zero produz share zero, não divisão por zero")
    void shouldNotDivideByZero() {
        assertThat(Slice.of(CategoryKey.absent("Nenhum"), 0, 0).share()).isZero();
    }

    @Test
    @DisplayName("contagem negativa e contagem acima do total são recusadas na origem")
    void shouldRejectImpossibleCounts() {
        CategoryKey key = CategoryKey.of(1, "um");

        assertThatThrownBy(() -> Slice.of(key, -1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Slice.of(key, 11, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Slice.of(key, 1, -1)).isInstanceOf(IllegalArgumentException.class);
        // A categoria sem vínculo tem id nulo, mas key nula não é categoria nenhuma.
        assertThatThrownBy(() -> Slice.of(null, 1, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a chave distingue UUID textual, enum, número e ausência de vínculo")
    void shouldRepresentEveryCategoryKind() {
        java.util.UUID id = java.util.UUID.randomUUID();

        assertThat(CategoryKey.of(id, "Design").id()).isEqualTo(id.toString());
        assertThat(CategoryKey.of(java.time.DayOfWeek.MONDAY, "Segunda").id()).isEqualTo("MONDAY");
        assertThat(CategoryKey.of((short) 3, "3º período").id()).isEqualTo("3");
        assertThat(CategoryKey.absent("Sem cargo").id()).isNull();
        // Nunca a string "null": ela seria enviada de volta como filtro e casaria com nada.
        assertThat(CategoryKey.of((java.util.UUID) null, "Sem cargo").id()).isNull();
    }
}
