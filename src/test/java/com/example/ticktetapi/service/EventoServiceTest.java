package com.example.ticktetapi.service;

import com.example.ticketapi.dto.EventoRequestDTO;
import com.example.ticketapi.exception.RecursoNaoEncontradoException;
import com.example.ticketapi.exception.RegraNegocioException;
import com.example.ticketapi.model.Evento;
import com.example.ticketapi.repository.EventoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventoServiceTest {

    @Mock
    private EventoRepository eventoRepository;

    @InjectMocks
    private EventoService eventoService;

    private EventoRequestDTO dtoValido;

    @BeforeEach
    void setUp() {
        dtoValido = new EventoRequestDTO();
        dtoValido.setNome("Show de Rock");
        dtoValido.setDescricao("Noite de rock nacional");
        dtoValido.setDataHora(LocalDateTime.now().plusDays(30));
        dtoValido.setLocal("Arena Recife");
        dtoValido.setPreco(new BigDecimal("150.00"));
        dtoValido.setQuantidadeTotal(100);
    }

    @Test
    void deveCriarEventoComQuantidadeDisponivelIgualATotal() {
        when(eventoRepository.save(any(Evento.class)))
                .thenAnswer(invocation -> {
                    Evento e = invocation.getArgument(0);
                    e.setId(1L);
                    return e;
                });

        Evento criado = eventoService.criar(dtoValido);

        assertThat(criado.getId()).isEqualTo(1L);
        assertThat(criado.getQuantidadeTotal()).isEqualTo(100);
        assertThat(criado.getQuantidadeDisponivel()).isEqualTo(100);
        verify(eventoRepository, times(1)).save(any(Evento.class));
    }

    @Test
    void deveListarTodosOsEventos() {
        Evento e1 = Evento.builder().id(1L).nome("Evento A").build();
        Evento e2 = Evento.builder().id(2L).nome("Evento B").build();
        when(eventoRepository.findAll()).thenReturn(List.of(e1, e2));

        List<Evento> eventos = eventoService.listarTodos();

        assertThat(eventos).hasSize(2);
        verify(eventoRepository).findAll();
    }

    @Test
    void deveBuscarEventoPorIdComSucesso() {
        Evento evento = Evento.builder().id(1L).nome("Show de Rock").build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        Evento encontrado = eventoService.buscarPorId(1L);

        assertThat(encontrado.getNome()).isEqualTo("Show de Rock");
    }

    @Test
    void deveLancarExcecaoQuandoEventoNaoEncontrado() {
        when(eventoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> eventoService.buscarPorId(99L));
    }

    @Test
    void deveAtualizarEventoRecalculandoDisponibilidade() {
        // evento com 100 no total e 80 disponiveis => 20 ja vendidos
        Evento existente = Evento.builder()
                .id(1L)
                .nome("Nome antigo")
                .quantidadeTotal(100)
                .quantidadeDisponivel(80)
                .build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(inv -> inv.getArgument(0));

        dtoValido.setQuantidadeTotal(150); // aumenta o total

        Evento atualizado = eventoService.atualizar(1L, dtoValido);

        // 20 ja vendidos, novo total 150 => disponivel deve ser 130
        assertThat(atualizado.getQuantidadeDisponivel()).isEqualTo(130);
        assertThat(atualizado.getQuantidadeTotal()).isEqualTo(150);
    }

    @Test
    void deveImpedirAtualizacaoQuandoNovoTotalForMenorQueVendidos() {
        // 20 ja vendidos (100 - 80)
        Evento existente = Evento.builder()
                .id(1L)
                .quantidadeTotal(100)
                .quantidadeDisponivel(80)
                .build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(existente));

        dtoValido.setQuantidadeTotal(10); // menor que os 20 ja vendidos

        assertThrows(RegraNegocioException.class, () -> eventoService.atualizar(1L, dtoValido));
        verify(eventoRepository, never()).save(any());
    }

    @Test
    void deveDeletarEventoExistente() {
        Evento evento = Evento.builder().id(1L).build();
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));

        eventoService.deletar(1L);

        verify(eventoRepository).delete(evento);
    }
}
