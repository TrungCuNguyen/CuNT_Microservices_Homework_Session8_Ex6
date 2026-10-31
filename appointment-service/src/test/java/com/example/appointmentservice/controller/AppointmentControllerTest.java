package com.example.appointmentservice.controller;

import com.example.appointmentservice.dto.request.AppointmentRequestDTO;
import com.example.appointmentservice.dto.response.AppointmentResponseDTO;
import com.example.appointmentservice.exception.ServiceUnavailableException;
import com.example.appointmentservice.service.AppointmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AppointmentService appointmentService;

    @Test
    void testCreateAppointment_Success_PluralPath() throws Exception {
        LocalDateTime appointmentDate = LocalDateTime.of(2026, 11, 1, 10, 0, 0);

        AppointmentRequestDTO requestDTO = AppointmentRequestDTO.builder()
                .patientId(1L)
                .doctorId(2L)
                .appointmentDate(appointmentDate)
                .reason("Kham suc khoe tong quat")
                .status("PENDING")
                .build();

        AppointmentResponseDTO responseDTO = AppointmentResponseDTO.builder()
                .id(1L)
                .patientId(1L)
                .doctorId(2L)
                .appointmentDate(appointmentDate)
                .reason("Kham suc khoe tong quat")
                .status("PENDING")
                .build();

        when(appointmentService.addAppointment(any(AppointmentRequestDTO.class))).thenReturn(responseDTO);
        when(appointmentService.createAppointment(any(AppointmentRequestDTO.class))).thenReturn(responseDTO);

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.patientId").value(1L))
                .andExpect(jsonPath("$.doctorId").value(2L))
                .andExpect(jsonPath("$.reason").value("Kham suc khoe tong quat"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void testCreateAppointment_Success_SingularPath() throws Exception {
        LocalDateTime appointmentDate = LocalDateTime.of(2026, 11, 1, 10, 0, 0);

        AppointmentRequestDTO requestDTO = AppointmentRequestDTO.builder()
                .patientId(1L)
                .doctorId(2L)
                .appointmentDate(appointmentDate)
                .reason("Kham suc khoe tong quat")
                .status("PENDING")
                .build();

        AppointmentResponseDTO responseDTO = AppointmentResponseDTO.builder()
                .id(1L)
                .patientId(1L)
                .doctorId(2L)
                .appointmentDate(appointmentDate)
                .reason("Kham suc khoe tong quat")
                .status("PENDING")
                .build();

        when(appointmentService.addAppointment(any(AppointmentRequestDTO.class))).thenReturn(responseDTO);
        when(appointmentService.createAppointment(any(AppointmentRequestDTO.class))).thenReturn(responseDTO);

        mockMvc.perform(post("/api/v1/appointment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.patientId").value(1L))
                .andExpect(jsonPath("$.doctorId").value(2L))
                .andExpect(jsonPath("$.reason").value("Kham suc khoe tong quat"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void testCreateAppointment_DoctorServiceUnavailable_Returns503() throws Exception {
        LocalDateTime appointmentDate = LocalDateTime.of(2026, 11, 1, 10, 0, 0);

        AppointmentRequestDTO requestDTO = AppointmentRequestDTO.builder()
                .patientId(1L)
                .doctorId(2L)
                .appointmentDate(appointmentDate)
                .reason("Kham suc khoe tong quat")
                .status("PENDING")
                .build();

        when(appointmentService.addAppointment(any(AppointmentRequestDTO.class)))
                .thenThrow(new ServiceUnavailableException("Hệ thống quản lý bác sĩ hiện không khả dụng. Vui lòng đặt lịch sau!"));
        when(appointmentService.createAppointment(any(AppointmentRequestDTO.class)))
                .thenThrow(new ServiceUnavailableException("Hệ thống quản lý bác sĩ hiện không khả dụng. Vui lòng đặt lịch sau!"));

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value("Hệ thống quản lý bác sĩ hiện không khả dụng. Vui lòng đặt lịch sau!"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testGetAllAppointments_Success() throws Exception {
        LocalDateTime appointmentDate = LocalDateTime.of(2026, 11, 1, 10, 0, 0);

        AppointmentResponseDTO responseDTO = AppointmentResponseDTO.builder()
                .id(1L)
                .patientId(1L)
                .doctorId(2L)
                .appointmentDate(appointmentDate)
                .reason("Kham dinh ky")
                .status("CONFIRMED")
                .build();

        when(appointmentService.getAllAppointments()).thenReturn(List.of(responseDTO));

        mockMvc.perform(get("/api/v1/appointments"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].patientId").value(1L))
                .andExpect(jsonPath("$[0].doctorId").value(2L));
    }
}
