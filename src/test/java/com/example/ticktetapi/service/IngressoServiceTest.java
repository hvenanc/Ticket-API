package com.example.ticktetapi.service;

import com.example.ticketapi.dto.CompraIngressoRequestDTO;
import com.example.ticketapi.exception.RecursoNaoEncontradoException;
import com.example.ticketapi.exception.RegraNegocioException;
import com.example.ticketapi.model.Cliente;
import com.example.ticketapi.model.Evento;
import com.example.ticketapi.model.Ingresso;
import com.example.ticketapi.model.StatusIngresso;
import com.example.ticketapi.repository.ClienteRepository;
import com.example.ticketapi.repository.EventoRepository;
import com.example.ticketapi.repository.IngressoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class IngressoServiceTest {

    @Mock
    private IngressoRepository ingressoRepository;
    @Mock
    private EventoRepository eventoRepository;
    @Mock
    private ClienteRepository clienteRepository;

    @InjectMocks
    private IngressoService ingressoService;

    private Evento evento;
    private Cliente cliente;
    private CompraIngressoRequestDTO compraDTO;

    @BeforeEach
    void setUp() {
        evento = Evento.builder()
                .id(1L)
                .nome("Show de Rock")
                .dataHora(LocalDateTime.now().plusDays(10))
                .preco(new BigDecimal("150.00"))
                .quantidadeTotal(100)
                .quantidadeDisponivel(10)
                .build();

        cliente = Cliente.builder().id(1L).nome("Maria Silva").build();

        compraDTO = new CompraIngressoRequestDTO();
        compraDTO.setEventoId(1L);
        compraDTO.setClienteId(1L);
        compraDTO.setQuantidade(2);
    }

    @Test
    void deveComprarIngressosEDecrementarEstoqueDoEvento() {
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(ingressoRepository.save(any(Ingresso.class))).thenAnswer(inv -> inv.getArgument(0));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Ingresso> comprados = ingressoService.comprar(compraDTO);

        assertThat(comprados).hasSize(2);
        assertThat(comprados).allMatch(i -> i.getStatus() == StatusIngresso.ATIVO);
        assertThat(comprados).allMatch(i -> i.getEvento().equals(evento));
        assertThat(comprados).allMatch(i -> i.getCliente().equals(cliente));

        // estoque deve cair de 10 para 8 (comprou 2)
        ArgumentCaptor<Evento> eventoCaptor = ArgumentCaptor.forClass(Evento.class);
        verify(eventoRepository).save(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().getQuantidadeDisponivel()).isEqualTo(8);

        verify(ingressoRepository, times(2)).save(any(Ingresso.class));
    }

    @Test
    void deveLancarExcecaoQuandoEventoNaoExiste() {
        when(eventoRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> ingressoService.comprar(compraDTO));
        verify(ingressoRepository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoQuandoClienteNaoExiste() {
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(clienteRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> ingressoService.comprar(compraDTO));
        verify(ingressoRepository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoAoComprarParaEventoQueJaOcorreu() {
        evento.setDataHora(LocalDateTime.now().minusDays(1));
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));

        assertThrows(RegraNegocioException.class, () -> ingressoService.comprar(compraDTO));
        verify(ingressoRepository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoQuandoEstoqueForInsuficiente() {
        evento.setQuantidadeDisponivel(1); // so 1 disponivel, pediu 2
        when(eventoRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));

        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> ingressoService.comprar(compraDTO));

        assertThat(ex.getMessage()).contains("esgotados");
        verify(ingressoRepository, never()).save(any());
        verify(eventoRepository, never()).save(any());
    }

    @Test
    void deveCancelarIngressoEDevolverEstoqueAoEvento() {
        Ingresso ingresso = Ingresso.builder()
                .id(5L)
                .evento(evento)
                .cliente(cliente)
                .status(StatusIngresso.ATIVO)
                .build();
        when(ingressoRepository.findById(5L)).thenReturn(Optional.of(ingresso));
        when(ingressoRepository.save(any(Ingresso.class))).thenAnswer(inv -> inv.getArgument(0));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(inv -> inv.getArgument(0));

        Ingresso cancelado = ingressoService.cancelar(5L);

        assertThat(cancelado.getStatus()).isEqualTo(StatusIngresso.CANCELADO);
        assertThat(evento.getQuantidadeDisponivel()).isEqualTo(11); // 10 + 1 devolvido
        verify(eventoRepository).save(evento);
    }

    @Test
    void deveLancarExcecaoAoCancelarIngressoJaCancelado() {
        Ingresso ingresso = Ingresso.builder().id(5L).evento(evento).status(StatusIngresso.CANCELADO).build();
        when(ingressoRepository.findById(5L)).thenReturn(Optional.of(ingresso));

        assertThrows(RegraNegocioException.class, () -> ingressoService.cancelar(5L));
        verify(eventoRepository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoAoCancelarIngressoJaUtilizado() {
        Ingresso ingresso = Ingresso.builder().id(5L).evento(evento).status(StatusIngresso.UTILIZADO).build();
        when(ingressoRepository.findById(5L)).thenReturn(Optional.of(ingresso));

        assertThrows(RegraNegocioException.class, () -> ingressoService.cancelar(5L));
        verify(eventoRepository, never()).save(any());
    }

    @Test
    void deveMarcarIngressoAtivoComoUtilizado() {
        Ingresso ingresso = Ingresso.builder().id(5L).status(StatusIngresso.ATIVO).build();
        when(ingressoRepository.findById(5L)).thenReturn(Optional.of(ingresso));
        when(ingressoRepository.save(any(Ingresso.class))).thenAnswer(inv -> inv.getArgument(0));

        Ingresso resultado = ingressoService.marcarComoUtilizado(5L);

        assertThat(resultado.getStatus()).isEqualTo(StatusIngresso.UTILIZADO);
    }

    @Test
    void deveLancarExcecaoAoUtilizarIngressoQueNaoEstaAtivo() {
        Ingresso ingresso = Ingresso.builder().id(5L).status(StatusIngresso.CANCELADO).build();
        when(ingressoRepository.findById(5L)).thenReturn(Optional.of(ingresso));

        assertThrows(RegraNegocioException.class, () -> ingressoService.marcarComoUtilizado(5L));
        verify(ingressoRepository, never()).save(any());
    }
}
