package com.example.appointmentservice.service;

import com.example.appointmentservice.constant.AppointmentConstant;
import com.example.appointmentservice.dto.request.AppointmentRequestDTO;
import com.example.appointmentservice.dto.response.AppointmentResponseDTO;
import com.example.appointmentservice.entity.Appointment;
import com.example.appointmentservice.exception.ApiResponseError;
import com.example.appointmentservice.exception.BadRequestException;
import com.example.appointmentservice.exception.ResourceNotFoundException;
import com.example.appointmentservice.exception.ServiceUnavailableException;
import com.example.appointmentservice.repository.AppointmentRepository;
import com.example.appointmentservice.service.impl.AppointmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceImplTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AppointmentServiceImpl appointmentService;

    private AppointmentRequestDTO requestDTO;
    private Appointment appointment;
    private LocalDateTime appointmentDate;

    @BeforeEach
    void setUp() {
        appointmentDate = LocalDateTime.of(2026, 11, 1, 9, 30, 0);

        requestDTO = AppointmentRequestDTO.builder()
                .patientId(1L)
                .doctorId(2L)
                .appointmentDate(appointmentDate)
                .reason("Dau dau, chong mat")
                .status("PENDING")
                .build();

        appointment = Appointment.builder()
                .id(10L)
                .patientId(1L)
                .doctorId(2L)
                .appointmentDate(appointmentDate)
                .reason("Dau dau, chong mat")
                .status("PENDING")
                .build();
    }

    @Test
    void testCreateAppointment_Success() {
        when(restTemplate.getForEntity(eq("http://patient-service/api/v1/patients/{id}"), eq(Object.class), eq(1L)))
                .thenReturn(new ResponseEntity<>(new Object(), HttpStatus.OK));

        when(restTemplate.getForEntity(eq("http://doctor-service/api/v1/doctors/{id}"), eq(Object.class), eq(2L)))
                .thenReturn(new ResponseEntity<>(new Object(), HttpStatus.OK));

        when(appointmentRepository.save(any(Appointment.class))).thenReturn(appointment);

        AppointmentResponseDTO response = appointmentService.createAppointment(requestDTO);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals(1L, response.getPatientId());
        assertEquals(2L, response.getDoctorId());
        assertEquals("PENDING", response.getStatus());

        verify(appointmentRepository, times(1)).save(any(Appointment.class));
    }

    @Test
    void testCreateAppointment_PatientNotFound() {
        when(restTemplate.getForEntity(eq("http://patient-service/api/v1/patients/{id}"), eq(Object.class), eq(1L)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> appointmentService.createAppointment(requestDTO)
        );

        assertTrue(exception.getMessage().contains(String.format(AppointmentConstant.PATIENT_NOT_FOUND, 1L)));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void testCreateAppointment_DoctorNotFound() {
        when(restTemplate.getForEntity(eq("http://patient-service/api/v1/patients/{id}"), eq(Object.class), eq(1L)))
                .thenReturn(new ResponseEntity<>(new Object(), HttpStatus.OK));

        when(restTemplate.getForEntity(eq("http://doctor-service/api/v1/doctors/{id}"), eq(Object.class), eq(2L)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> appointmentService.createAppointment(requestDTO)
        );

        assertTrue(exception.getMessage().contains(String.format(AppointmentConstant.DOCTOR_NOT_FOUND, 2L)));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void testCreateAppointment_DoctorServiceDown() {
        when(restTemplate.getForEntity(eq("http://patient-service/api/v1/patients/{id}"), eq(Object.class), eq(1L)))
                .thenReturn(new ResponseEntity<>(new Object(), HttpStatus.OK));

        when(restTemplate.getForEntity(eq("http://doctor-service/api/v1/doctors/{id}"), eq(Object.class), eq(2L)))
                .thenThrow(new ResourceAccessException("Connect to http://doctor-service/api/v1/doctors/2 failed: Connection refused"));

        ServiceUnavailableException exception = assertThrows(
                ServiceUnavailableException.class,
                () -> appointmentService.createAppointment(requestDTO)
        );

        assertEquals("Hiện tại không thể kiểm tra thông tin bác sĩ, vui lòng thử lại sau vài giây", exception.getMessage());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void testGetDoctorFallback() {
        ApiResponseError error = appointmentService.getDoctorFallback(new RuntimeException("Server error"));
        assertNotNull(error);
        assertEquals(503, error.getStatus());
        assertEquals("Doctor Service Error", error.getError());
        assertEquals("Hiện tại không thể kiểm tra thông tin bác sĩ, vui lòng thử lại sau vài giây", error.getMessage());
    }

    @Test
    void testCreateAppointment_PatientServiceDown() {
        when(restTemplate.getForEntity(eq("http://patient-service/api/v1/patients/{id}"), eq(Object.class), eq(1L)))
                .thenThrow(new ResourceAccessException("Connect to http://patient-service/api/v1/patients/1 failed: Connection refused"));

        assertThrows(
                ResourceAccessException.class,
                () -> appointmentService.createAppointment(requestDTO)
        );

        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void testPatientFallback() {
        ServiceUnavailableException exception = assertThrows(
                ServiceUnavailableException.class,
                () -> appointmentService.patientFallback(1L, new ResourceAccessException("Connection refused"))
        );
        assertEquals(AppointmentConstant.PATIENT_SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @Test
    void testCreateAppointment_InvalidStatus() {
        when(restTemplate.getForEntity(eq("http://patient-service/api/v1/patients/{id}"), eq(Object.class), eq(1L)))
                .thenReturn(new ResponseEntity<>(new Object(), HttpStatus.OK));

        when(restTemplate.getForEntity(eq("http://doctor-service/api/v1/doctors/{id}"), eq(Object.class), eq(2L)))
                .thenReturn(new ResponseEntity<>(new Object(), HttpStatus.OK));

        requestDTO.setStatus("INVALID_STATUS");

        assertThrows(
                BadRequestException.class,
                () -> appointmentService.createAppointment(requestDTO)
        );

        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void testGetAppointmentById_Success() {
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(appointment));

        AppointmentResponseDTO response = appointmentService.getAppointmentById(10L);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("Dau dau, chong mat", response.getReason());
    }

    @Test
    void testGetAppointmentById_NotFound() {
        when(appointmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> appointmentService.getAppointmentById(999L)
        );
    }

    @Test
    void testGetAllAppointments_Success() {
        when(appointmentRepository.findAll()).thenReturn(List.of(appointment));

        List<AppointmentResponseDTO> result = appointmentService.getAllAppointments();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getId());
    }

    @Test
    void testDeleteAppointment_Success() {
        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(appointment));
        doNothing().when(appointmentRepository).delete(appointment);

        assertDoesNotThrow(() -> appointmentService.deleteAppointment(10L));
        verify(appointmentRepository, times(1)).delete(appointment);
    }
}
