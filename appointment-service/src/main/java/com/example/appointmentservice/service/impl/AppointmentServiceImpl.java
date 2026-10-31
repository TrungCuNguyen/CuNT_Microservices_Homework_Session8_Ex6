package com.example.appointmentservice.service.impl;

import com.example.appointmentservice.constant.AppointmentConstant;
import com.example.appointmentservice.dto.request.AppointmentRequestDTO;
import com.example.appointmentservice.dto.response.AppointmentResponseDTO;
import com.example.appointmentservice.entity.Appointment;
import com.example.appointmentservice.exception.BadRequestException;
import com.example.appointmentservice.exception.ResourceNotFoundException;
import com.example.appointmentservice.repository.AppointmentRepository;
import com.example.appointmentservice.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
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
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException(String.format(AppointmentConstant.PATIENT_NOT_FOUND, patientId));
            }
            log.error("Lỗi từ patient-service: {}", ex.getMessage());
            throw new BadRequestException("Lỗi khi kết nối tới Patient-Service: " + ex.getStatusCode());
        } catch (ResourceAccessException ex) {
            log.error("Không thể kết nối đến patient-service: {}", ex.getMessage());
            throw new BadRequestException("Không thể kết nối đến Patient-Service: Dịch vụ có thể chưa khởi động hoặc chưa đăng ký trên Eureka");
        } catch (Exception ex) {
            log.error("Lỗi xác thực bệnh nhân: {}", ex.getMessage());
            throw new BadRequestException("Lỗi xác thực bệnh nhân: " + ex.getMessage());
        }
    }

    private void validateDoctorExists(Long doctorId) {
        try {
            ResponseEntity<Object> response = restTemplate.getForEntity(DOCTOR_SERVICE_URL, Object.class, doctorId);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ResourceNotFoundException(String.format(AppointmentConstant.DOCTOR_NOT_FOUND, doctorId));
            }
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException(String.format(AppointmentConstant.DOCTOR_NOT_FOUND, doctorId));
            }
            log.error("Lỗi từ doctor-service: {}", ex.getMessage());
            throw new BadRequestException("Lỗi khi kết nối tới Doctor-Service: " + ex.getStatusCode());
        } catch (ResourceAccessException ex) {
            log.error("Không thể kết nối đến doctor-service: {}", ex.getMessage());
            throw new BadRequestException("Không thể kết nối đến Doctor-Service: Dịch vụ có thể chưa khởi động hoặc chưa đăng ký trên Eureka");
        } catch (Exception ex) {
            log.error("Lỗi xác thực bác sĩ: {}", ex.getMessage());
            throw new BadRequestException("Lỗi xác thực bác sĩ: " + ex.getMessage());
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

