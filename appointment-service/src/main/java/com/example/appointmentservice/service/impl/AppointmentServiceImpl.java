package com.example.appointmentservice.service.impl;

import com.example.appointmentservice.constant.AppointmentConstant;
import com.example.appointmentservice.dto.request.AppointmentRequestDTO;
import com.example.appointmentservice.dto.response.AppointmentResponseDTO;
import com.example.appointmentservice.entity.Appointment;
import com.example.appointmentservice.exception.BadRequestException;
import com.example.appointmentservice.exception.ResourceNotFoundException;
import com.example.appointmentservice.exception.ServiceUnavailableException;
import com.example.appointmentservice.repository.AppointmentRepository;
import com.example.appointmentservice.service.AppointmentService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final RestTemplate restTemplate;

    private static final String PATIENT_SERVICE_URL = "http://patient-service/api/v1/patients/{id}";
    private static final String DOCTOR_SERVICE_URL = "http://doctor-service/api/v1/doctors/{id}";
    private static final Set<String> VALID_STATUSES = Set.of("PENDING", "CONFIRMED", "CANCELLED");

    @Override
    @Transactional
    @CircuitBreaker(name = "doctorServiceCB", fallbackMethod = "fallbackDoctorService")
    public AppointmentResponseDTO createAppointment(AppointmentRequestDTO requestDTO) {
        validatePatientExists(requestDTO.getPatientId());
        validateDoctorExists(requestDTO.getDoctorId());

        String status = resolveAndValidateStatus(requestDTO.getStatus());

        Appointment appointment = Appointment.builder()
                .patientId(requestDTO.getPatientId())
                .doctorId(requestDTO.getDoctorId())
                .appointmentDate(requestDTO.getAppointmentDate())
                .reason(requestDTO.getReason())
                .status(status)
                .build();

        Appointment savedAppointment = appointmentRepository.save(appointment);
        return mapToResponse(savedAppointment);
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "doctorServiceCB", fallbackMethod = "fallbackDoctorService")
    public AppointmentResponseDTO addAppointment(AppointmentRequestDTO requestDTO) {
        return createAppointment(requestDTO);
    }

    public AppointmentResponseDTO fallbackDoctorService(AppointmentRequestDTO requestDTO, Throwable ex) {
        log.error("CircuitBreaker [doctorServiceCB] fallback triggered: {}", ex.getMessage());
        if (ex instanceof ResourceNotFoundException) {
            throw (ResourceNotFoundException) ex;
        }
        if (ex instanceof BadRequestException) {
            throw (BadRequestException) ex;
        }
        throw new ServiceUnavailableException(AppointmentConstant.DOCTOR_SERVICE_UNAVAILABLE);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentResponseDTO getAppointmentById(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(AppointmentConstant.APPOINTMENT_NOT_FOUND, id)));
        return mapToResponse(appointment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentResponseDTO> getAllAppointments() {
        return appointmentRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AppointmentResponseDTO updateAppointment(Long id, AppointmentRequestDTO requestDTO) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(AppointmentConstant.APPOINTMENT_NOT_FOUND, id)));

        if (requestDTO.getPatientId() != null && !requestDTO.getPatientId().equals(appointment.getPatientId())) {
            validatePatientExists(requestDTO.getPatientId());
            appointment.setPatientId(requestDTO.getPatientId());
        }

        if (requestDTO.getDoctorId() != null && !requestDTO.getDoctorId().equals(appointment.getDoctorId())) {
            validateDoctorExists(requestDTO.getDoctorId());
            appointment.setDoctorId(requestDTO.getDoctorId());
        }

        if (requestDTO.getAppointmentDate() != null) {
            appointment.setAppointmentDate(requestDTO.getAppointmentDate());
        }

        if (requestDTO.getReason() != null) {
            appointment.setReason(requestDTO.getReason());
        }

        if (requestDTO.getStatus() != null && !requestDTO.getStatus().isBlank()) {
            String status = resolveAndValidateStatus(requestDTO.getStatus());
            appointment.setStatus(status);
        }

        Appointment updatedAppointment = appointmentRepository.save(appointment);
        return mapToResponse(updatedAppointment);
    }

    @Override
    @Transactional
    public void deleteAppointment(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(AppointmentConstant.APPOINTMENT_NOT_FOUND, id)));
        appointmentRepository.delete(appointment);
    }

    private void validatePatientExists(Long patientId) {
        try {
            ResponseEntity<Object> response = restTemplate.getForEntity(PATIENT_SERVICE_URL, Object.class, patientId);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ResourceNotFoundException(String.format(AppointmentConstant.PATIENT_NOT_FOUND, patientId));
            }
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException(String.format(AppointmentConstant.PATIENT_NOT_FOUND, patientId));
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException(String.format(AppointmentConstant.PATIENT_NOT_FOUND, patientId));
            }
            log.error("Patient-service gặp sự cố HTTP {}: {}", ex.getStatusCode(), ex.getMessage());
            throw new ServiceUnavailableException(AppointmentConstant.PATIENT_SERVICE_UNAVAILABLE);
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Không thể kết nối đến patient-service (service sập hoặc lỗi): {}", ex.getMessage());
            throw new ServiceUnavailableException(AppointmentConstant.PATIENT_SERVICE_UNAVAILABLE);
        }
    }

    private void validateDoctorExists(Long doctorId) {
        try {
            ResponseEntity<Object> response = restTemplate.getForEntity(DOCTOR_SERVICE_URL, Object.class, doctorId);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ResourceNotFoundException(String.format(AppointmentConstant.DOCTOR_NOT_FOUND, doctorId));
            }
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException(String.format(AppointmentConstant.DOCTOR_NOT_FOUND, doctorId));
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException(String.format(AppointmentConstant.DOCTOR_NOT_FOUND, doctorId));
            }
            log.error("Doctor-service gặp sự cố HTTP {}: {}", ex.getStatusCode(), ex.getMessage());
            throw new ServiceUnavailableException(AppointmentConstant.DOCTOR_SERVICE_UNAVAILABLE);
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Không thể kết nối đến doctor-service (service sập hoặc lỗi): {}", ex.getMessage());
            throw new ServiceUnavailableException(AppointmentConstant.DOCTOR_SERVICE_UNAVAILABLE);
        }
    }

    private String resolveAndValidateStatus(String status) {
        if (status == null || status.isBlank()) {
            return "PENDING";
        }
        String upperStatus = status.trim().toUpperCase();
        if (!VALID_STATUSES.contains(upperStatus)) {
            throw new BadRequestException("Trạng thái không hợp lệ: " + status + ". Trạng thái hợp lệ: PENDING, CONFIRMED, CANCELLED");
        }
        return upperStatus;
    }

    private AppointmentResponseDTO mapToResponse(Appointment appointment) {
        return AppointmentResponseDTO.builder()
                .id(appointment.getId())
                .patientId(appointment.getPatientId())
                .doctorId(appointment.getDoctorId())
                .appointmentDate(appointment.getAppointmentDate())
                .reason(appointment.getReason())
                .status(appointment.getStatus())
                .build();
    }
}
