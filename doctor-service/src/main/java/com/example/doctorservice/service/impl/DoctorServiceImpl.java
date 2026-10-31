package com.example.doctorservice.service.impl;

import com.example.doctorservice.constant.DoctorConstant;
import com.example.doctorservice.dto.request.DoctorRequestDTO;
import com.example.doctorservice.dto.response.DoctorDetailResponseDTO;
import com.example.doctorservice.dto.response.DoctorResponseDTO;
import com.example.doctorservice.entity.Doctor;
import com.example.doctorservice.exception.ResourceNotFoundException;
import com.example.doctorservice.repository.DoctorRepository;
import com.example.doctorservice.service.DoctorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorServiceImpl implements DoctorService {

    private final DoctorRepository doctorRepository;

    @Override
    @Transactional(readOnly = true)
    public List<DoctorResponseDTO> getAllDoctors() {
        return doctorRepository.findAll().stream()
                .map(this::mapToSummaryResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DoctorResponseDTO createDoctor(DoctorRequestDTO requestDTO) {
        Doctor doctor = Doctor.builder()
                .name(requestDTO.getName())
                .specialization(requestDTO.getSpecialization())
                .experienceYears(requestDTO.getExperienceYears())
                .email(requestDTO.getEmail())
                .status(requestDTO.getStatus() != null ? requestDTO.getStatus() : true)
                .build();

        Doctor savedDoctor = doctorRepository.save(doctor);
        return mapToSummaryResponse(savedDoctor);
    }

    @Override
    @Transactional(readOnly = true)
    public DoctorDetailResponseDTO getDoctorById(Long id) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(DoctorConstant.DOCTOR_NOT_FOUND, id)));
        return mapToDetailResponse(doctor);
    }

    @Override
    @Transactional
    public DoctorDetailResponseDTO updateDoctor(Long id, DoctorRequestDTO requestDTO) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(DoctorConstant.DOCTOR_NOT_FOUND, id)));

        doctor.setName(requestDTO.getName());
        doctor.setSpecialization(requestDTO.getSpecialization());
        doctor.setExperienceYears(requestDTO.getExperienceYears());
        doctor.setEmail(requestDTO.getEmail());
        if (requestDTO.getStatus() != null) {
            doctor.setStatus(requestDTO.getStatus());
        }

        Doctor updatedDoctor = doctorRepository.save(doctor);
        return mapToDetailResponse(updatedDoctor);
    }

    @Override
    @Transactional
    public void deleteDoctor(Long id) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(DoctorConstant.DOCTOR_NOT_FOUND, id)));
        doctorRepository.delete(doctor);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DoctorResponseDTO> searchDoctors(String name) {
        List<Doctor> doctors;
        if (name == null || name.trim().isEmpty()) {
            doctors = doctorRepository.findAll();
        } else {
            doctors = doctorRepository.findByNameContainingIgnoreCase(name.trim());
        }
        return doctors.stream()
                .map(this::mapToSummaryResponse)
                .collect(Collectors.toList());
    }

    private DoctorResponseDTO mapToSummaryResponse(Doctor doctor) {
        return DoctorResponseDTO.builder()
                .id(doctor.getId())
                .name(doctor.getName())
                .specialization(doctor.getSpecialization())
                .build();
    }

    private DoctorDetailResponseDTO mapToDetailResponse(Doctor doctor) {
        return DoctorDetailResponseDTO.builder()
                .id(doctor.getId())
                .name(doctor.getName())
                .specialization(doctor.getSpecialization())
                .experienceYears(doctor.getExperienceYears())
                .email(doctor.getEmail())
                .status(doctor.getStatus())
                .build();
    }
}
