package com.example.doctorservice.service;

import com.example.doctorservice.dto.request.DoctorRequestDTO;
import com.example.doctorservice.dto.response.DoctorDetailResponseDTO;
import com.example.doctorservice.dto.response.DoctorResponseDTO;

import java.util.List;

public interface DoctorService {
    List<DoctorResponseDTO> getAllDoctors();
    DoctorResponseDTO createDoctor(DoctorRequestDTO requestDTO);
    DoctorDetailResponseDTO getDoctorById(Long id);
    DoctorDetailResponseDTO updateDoctor(Long id, DoctorRequestDTO requestDTO);
    void deleteDoctor(Long id);
    List<DoctorResponseDTO> searchDoctors(String name);
}
