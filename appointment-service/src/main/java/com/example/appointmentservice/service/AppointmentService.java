package com.example.appointmentservice.service;

import com.example.appointmentservice.dto.request.AppointmentRequestDTO;
import com.example.appointmentservice.dto.response.AppointmentResponseDTO;

import java.util.List;

public interface AppointmentService {

    AppointmentResponseDTO createAppointment(AppointmentRequestDTO requestDTO);

    AppointmentResponseDTO addAppointment(AppointmentRequestDTO requestDTO);

    AppointmentResponseDTO getAppointmentById(Long id);

    List<AppointmentResponseDTO> getAllAppointments();

    AppointmentResponseDTO updateAppointment(Long id, AppointmentRequestDTO requestDTO);

    void deleteAppointment(Long id);
}
