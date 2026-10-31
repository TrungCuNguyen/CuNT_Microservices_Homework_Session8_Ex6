package com.example.appointmentservice.controller;

import com.example.appointmentservice.constant.AppointmentConstant;
import com.example.appointmentservice.dto.request.AppointmentRequestDTO;
import com.example.appointmentservice.dto.response.AppointmentResponseDTO;
import com.example.appointmentservice.exception.ApiResponseError;
import com.example.appointmentservice.service.AppointmentService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/appointments", "/api/v1/appointment"})
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    @CircuitBreaker(name = "doctorServiceCB", fallbackMethod = "getDoctorFallback")
    public Object createAppointment(@RequestBody AppointmentRequestDTO requestDTO) {
        AppointmentResponseDTO response = appointmentService.addAppointment(requestDTO);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    public ApiResponseError getDoctorFallback(Exception e) {
        return ApiResponseError.builder()
                .timestamp(LocalDateTime.now())
                .status(503)
                .error(AppointmentConstant.DOCTOR_SERVICE_ERROR)
                .message(AppointmentConstant.DOCTOR_SERVICE_FALLBACK_MESSAGE)
                .build();
    }

    @GetMapping
    public ResponseEntity<List<AppointmentResponseDTO>> getAllAppointments() {
        List<AppointmentResponseDTO> response = appointmentService.getAllAppointments();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponseDTO> getAppointmentById(@PathVariable Long id) {
        AppointmentResponseDTO response = appointmentService.getAppointmentById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AppointmentResponseDTO> updateAppointment(
            @PathVariable Long id,
            @RequestBody AppointmentRequestDTO requestDTO) {
        AppointmentResponseDTO response = appointmentService.updateAppointment(id, requestDTO);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAppointment(@PathVariable Long id) {
        appointmentService.deleteAppointment(id);
        return ResponseEntity.noContent().build();
    }
}
