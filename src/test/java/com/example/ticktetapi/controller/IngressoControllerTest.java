package com.example.ticktetapi.controller;

import com.example.ticketapi.dto.CompraIngressoRequestDTO;
import com.example.ticketapi.exception.RecursoNaoEncontradoException;
import com.example.ticketapi.exception.RegraNegocioException;
import com.example.ticketapi.model.Cliente;
import com.example.ticketapi.model.Evento;
import com.example.ticketapi.model.Ingresso;
import com.example.ticketapi.model.StatusIngresso;
import com.example.ticketapi.service.IngressoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IngressoController.class)
class IngressoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IngressoService ingressoService;

    private Ingresso construirIngresso(Long id, StatusIngresso status) {
        Evento evento = Evento.builder().id(1L).nome("Show de Rock").build();
        Cliente cliente = Cliente.builder().id(1L).nome("Maria Silva").build();
        return Ingresso.builder()
                .id(id)
                .codigo("codigo-teste")
                .evento(evento)
                .cliente(cliente)
                .dataCompra(LocalDateTime.now())
                .valorPago(new BigDecimal("150.00"))
                .status(status)
                .build();
    }

    @Test
    void deveComprarIngressosERetornar201() throws Exception {
        CompraIngressoRequestDTO dto = new CompraIngressoRequestDTO();
        dto.setEventoId(1L);
        dto.setClienteId(1L);
        dto.setQuantidade(1);

        when(ingressoService.comprar(any())).thenReturn(List.of(construirIngresso(10L, StatusIngresso.ATIVO)));

        mockMvc.perform(post("/api/ingressos/comprar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].status").value("ATIVO"))
                .andExpect(jsonPath("$[0].eventoNome").value("Show de Rock"));
    }

    @Test
    void deveRetornar400QuandoRequisicaoDeCompraForInvalida() throws Exception {
        String jsonInvalido = "{\"clienteId\": 1}"; // faltando eventoId, que e @NotNull

        mockMvc.perform(post("/api/ingressos/comprar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void deveRetornar422QuandoEstoqueEsgotado() throws Exception {
        CompraIngressoRequestDTO dto = new CompraIngressoRequestDTO();
        dto.setEventoId(1L);
        dto.setClienteId(1L);
        dto.setQuantidade(5);

        when(ingressoService.comprar(any()))
                .thenThrow(new RegraNegocioException("Ingressos esgotados. Disponível: 0, solicitado: 5"));

        mockMvc.perform(post("/api/ingressos/comprar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Ingressos esgotados. Disponível: 0, solicitado: 5"));
    }

    @Test
    void deveCancelarIngressoERetornar200() throws Exception {
        when(ingressoService.cancelar(eq(10L))).thenReturn(construirIngresso(10L, StatusIngresso.CANCELADO));

        mockMvc.perform(patch("/api/ingressos/{id}/cancelar", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));
    }

    @Test
    void deveRetornar404AoBuscarIngressoInexistente() throws Exception {
        when(ingressoService.buscarPorId(999L))
                .thenThrow(new RecursoNaoEncontradoException("Ingresso não encontrado com id: 999"));

        mockMvc.perform(get("/api/ingressos/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Ingresso não encontrado com id: 999"));
    }
}
