package com.example.ticktetapi.controller;

import com.example.ticketapi.dto.EventoRequestDTO;
import com.example.ticketapi.exception.RecursoNaoEncontradoException;
import com.example.ticketapi.model.Evento;
import com.example.ticketapi.service.EventoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventoController.class)
class EventoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EventoService eventoService;

    @Test
    void deveCriarEventoERetornar201() throws Exception {
        EventoRequestDTO dto = new EventoRequestDTO();
        dto.setNome("Show de Rock");
        dto.setDataHora(LocalDateTime.now().plusDays(30));
        dto.setLocal("Arena Recife");
        dto.setPreco(new BigDecimal("150.00"));
        dto.setQuantidadeTotal(100);

        Evento salvo = Evento.builder()
                .id(1L)
                .nome(dto.getNome())
                .dataHora(dto.getDataHora())
                .local(dto.getLocal())
                .preco(dto.getPreco())
                .quantidadeTotal(100)
                .quantidadeDisponivel(100)
                .build();

        when(eventoService.criar(any())).thenReturn(salvo);

        mockMvc.perform(post("/api/eventos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.quantidadeDisponivel").value(100));
    }

    @Test
    void deveRetornar400QuandoNomeForVazio() throws Exception {
        EventoRequestDTO dto = new EventoRequestDTO();
        dto.setNome(""); // invalido: @NotBlank
        dto.setDataHora(LocalDateTime.now().plusDays(30));
        dto.setLocal("Arena Recife");
        dto.setPreco(new BigDecimal("150.00"));
        dto.setQuantidadeTotal(100);

        mockMvc.perform(post("/api/eventos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes").isArray());
    }

    @Test
    void deveRetornar404QuandoEventoNaoExiste() throws Exception {
        when(eventoService.buscarPorId(99L))
                .thenThrow(new RecursoNaoEncontradoException("Evento não encontrado com id: 99"));

        mockMvc.perform(get("/api/eventos/{id}", 99L))
                .andExpect(status().isNotFound());
    }
}
