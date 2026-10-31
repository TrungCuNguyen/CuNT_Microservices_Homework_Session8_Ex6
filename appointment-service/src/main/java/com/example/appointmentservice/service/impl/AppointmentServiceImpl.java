package com.example.appointmentservice.service.impl;

import com.example.appointmentservice.constant.AppointmentConstant;
import com.example.appointmentservice.dto.request.AppointmentRequestDTO;
import com.example.appointmentservice.dto.response.AppointmentResponseDTO;
import com.example.appointmentservice.entity.Appointment;
import com.example.appointmentservice.exception.ApiResponseError;
import com.example.appointmentservice.exception.BadRequestException;
import com.example.appointmentservice.exception.ResourceNotFoundException;
import com.example.appointmentservice.exception.ServiceUnavailableException;
import com.example.appointmentservice.repository.AppointmentRepository;
import com.example.appointmentservice.service.AppointmentService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final RestTemplate restTemplate;

    @Autowired
    @Lazy
    private AppointmentService self;

    private static final String PATIENT_SERVICE_URL = "http://patient-service/api/v1/patients/{id}";
    private static final String DOCTOR_SERVICE_URL = "http://doctor-service/api/v1/doctors/{id}";
    private static final Set<String> VALID_STATUSES = Set.of("PENDING", "CONFIRMED", "CANCELLED");

    @Override
    @Transactional
    public AppointmentResponseDTO createAppointment(AppointmentRequestDTO requestDTO) {
        if (requestDTO.getPatientId() != null) {
            getSelf().validatePatientExists(requestDTO.getPatientId());
        }
        if (requestDTO.getDoctorId() != null) {
            validateDoctorExists(requestDTO.getDoctorId());
        }

        String status = resolveAndValidateStatus(requestDTO.getStatus());
        LocalDateTime appointmentDate = requestDTO.getAppointmentDate() != null
                ? requestDTO.getAppointmentDate()
                : LocalDateTime.now();

        Appointment appointment = Appointment.builder()
                .patientId(requestDTO.getPatientId() != null ? requestDTO.getPatientId() : 1L)
                .doctorId(requestDTO.getDoctorId() != null ? requestDTO.getDoctorId() : 1L)
                .appointmentDate(appointmentDate)
                .reason(requestDTO.getReason())
                .status(status)
                .build();

        Appointment savedAppointment = appointmentRepository.save(appointment);
        return mapToResponse(savedAppointment);
    }

    private AppointmentService getSelf() {
        return self != null ? self : this;
    }

    @Override
    @Transactional
    public AppointmentResponseDTO addAppointment(AppointmentRequestDTO requestDTO) {
        return createAppointment(requestDTO);
    }

    @Override
    @CircuitBreaker(name = "doctorServiceCB", fallbackMethod = "getDoctorFallback")
    public Object getDoctor(Long doctorId) {
        return restTemplate.getForEntity(DOCTOR_SERVICE_URL, Object.class, doctorId).getBody();
    }

    @Override
    public ApiResponseError getDoctorFallback(Exception e) {
        log.error("CircuitBreaker [doctorServiceCB] getDoctorFallback invoked: {}", e.getMessage());
        return ApiResponseError.builder()
                .timestamp(LocalDateTime.now())
                .status(503)
                .error(AppointmentConstant.DOCTOR_SERVICE_ERROR)
                .message(AppointmentConstant.DOCTOR_SERVICE_FALLBACK_MESSAGE)
                .build();
    }

    @Override
    @Retry(name = "patientRetry", fallbackMethod = "patientFallback")
    public Object getPatient(Long patientId) {
        log.info("Gọi Patient-Service với patientId: {}", patientId);
        ResponseEntity<Object> response = restTemplate.getForEntity(PATIENT_SERVICE_URL, Object.class, patientId);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new ResourceNotFoundException(String.format(AppointmentConstant.PATIENT_NOT_FOUND, patientId));
        }
        return response.getBody();
    }

    @Override
    @Retry(name = "patientRetry", fallbackMethod = "patientFallback")
    public void validatePatientExists(Long patientId) {
        try {
            log.info("Đang kiểm tra thông tin bệnh nhân ID: {}", patientId);
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
        } catch (ResourceAccessException ex) {
            log.warn("Mất kết nối tới Patient-Service (ResourceAccessException), đang thử lại...: {}", ex.getMessage());
            throw ex;
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Lỗi không xác định khi gọi patient-service: {}", ex.getMessage());
            throw new ServiceUnavailableException(AppointmentConstant.PATIENT_SERVICE_UNAVAILABLE);
        }
    }

    public Object patientFallback(Long patientId, Exception e) {
        log.error("Patient-Service retry thất bại sau 3 lần thử với patientId {}: {}", patientId, e.getMessage());
        if (e instanceof ResourceNotFoundException) {
            throw (ResourceNotFoundException) e;
        }
        throw new ServiceUnavailableException(AppointmentConstant.PATIENT_SERVICE_UNAVAILABLE);
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
            getSelf().validatePatientExists(requestDTO.getPatientId());
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
            throw new ServiceUnavailableException(AppointmentConstant.DOCTOR_SERVICE_FALLBACK_MESSAGE);
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Không thể kết nối đến doctor-service: {}", ex.getMessage());
            throw new ServiceUnavailableException(AppointmentConstant.DOCTOR_SERVICE_FALLBACK_MESSAGE);
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
