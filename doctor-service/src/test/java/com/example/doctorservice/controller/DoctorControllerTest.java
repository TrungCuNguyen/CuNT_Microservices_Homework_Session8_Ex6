package com.example.doctorservice.controller;

import com.example.doctorservice.dto.request.DoctorRequestDTO;
import com.example.doctorservice.dto.response.DoctorResponseDTO;
import com.example.doctorservice.service.DoctorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DoctorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DoctorService doctorService;

    @Test
    void testGetAllDoctors_Success() throws Exception {
        DoctorResponseDTO doctor1 = DoctorResponseDTO.builder()
                .id(1L)
                .name("BS. Nguyen Van A")
                .specialization("Noi khoa")
                .build();

        DoctorResponseDTO doctor2 = DoctorResponseDTO.builder()
                .id(2L)
                .name("BS. Tran Thi B")
                .specialization("Nhi khoa")
                .build();

        when(doctorService.getAllDoctors()).thenReturn(List.of(doctor1, doctor2));

        mockMvc.perform(get("/api/v1/doctors"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].name").value("BS. Nguyen Van A"))
                .andExpect(jsonPath("$[0].specialization").value("Noi khoa"))
                .andExpect(jsonPath("$[1].id").value(2L))
                .andExpect(jsonPath("$[1].name").value("BS. Tran Thi B"))
                .andExpect(jsonPath("$[1].specialization").value("Nhi khoa"));
    }

    @Test
    void testCreateDoctor_Success() throws Exception {
        DoctorRequestDTO requestDTO = DoctorRequestDTO.builder()
                .name("BS. Nguyen Van A")
                .specialization("Noi khoa")
                .experienceYears(10)
                .email("doctor.a@hospital.com")
                .status(true)
                .build();

        DoctorResponseDTO responseDTO = DoctorResponseDTO.builder()
                .id(1L)
                .name("BS. Nguyen Van A")
                .specialization("Noi khoa")
                .build();

        when(doctorService.createDoctor(any(DoctorRequestDTO.class))).thenReturn(responseDTO);

        mockMvc.perform(post("/api/v1/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("BS. Nguyen Van A"))
                .andExpect(jsonPath("$.specialization").value("Noi khoa"));
    }

    @Test
    void testSearchDoctor_RateLimiterTriggersOnSixthRequest() throws Exception {
        DoctorResponseDTO doctor = DoctorResponseDTO.builder()
                .id(1L)
                .name("BS. Huong")
                .specialization("Noi khoa")
                .build();

        when(doctorService.searchDoctors("huong")).thenReturn(List.of(doctor));

        // First 5 requests should succeed
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/doctors/search").param("name", "huong"))
                    .andExpect(status().isOk());
        }

        // 6th request should be blocked by RateLimiter and return HTTP 429
        mockMvc.perform(get("/api/v1/doctors/search").param("name", "huong"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Error"))
                .andExpect(jsonPath("$.data").value("Bạn đã gửi quá 5 request mỗi 10 giây"));
    }
}
