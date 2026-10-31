package com.example.doctorservice.controller;

import com.example.doctorservice.constant.DoctorConstant;
import com.example.doctorservice.dto.request.DoctorRequestDTO;
import com.example.doctorservice.dto.response.DoctorDetailResponseDTO;
import com.example.doctorservice.dto.response.DoctorResponseDTO;
import com.example.doctorservice.dto.response.RateLimitResponseDTO;
import com.example.doctorservice.service.DoctorService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    @GetMapping
    public ResponseEntity<List<DoctorResponseDTO>> getAllDoctors() {
        List<DoctorResponseDTO> response = doctorService.getAllDoctors();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    @RateLimiter(name = "searchDoctorLimit", fallbackMethod = "searchDoctorFallback")
    public ResponseEntity<?> searchDoctors(@RequestParam(value = "name", required = false) String name) {
        List<DoctorResponseDTO> response = doctorService.searchDoctors(name);
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<?> searchDoctorFallback(String name, RequestNotPermitted ex) {
        RateLimitResponseDTO response = RateLimitResponseDTO.builder()
                .data(DoctorConstant.RATE_LIMIT_EXCEEDED)
                .message("Error")
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .build();
        return new ResponseEntity<>(response, HttpStatus.TOO_MANY_REQUESTS);
    }

    @PostMapping
    public ResponseEntity<DoctorResponseDTO> createDoctor(@Valid @RequestBody DoctorRequestDTO requestDTO) {
        DoctorResponseDTO response = doctorService.createDoctor(requestDTO);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DoctorDetailResponseDTO> getDoctorById(@PathVariable Long id) {
        DoctorDetailResponseDTO response = doctorService.getDoctorById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DoctorDetailResponseDTO> updateDoctor(
            @PathVariable Long id,
            @Valid @RequestBody DoctorRequestDTO requestDTO) {
        DoctorDetailResponseDTO response = doctorService.updateDoctor(id, requestDTO);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDoctor(@PathVariable Long id) {
        doctorService.deleteDoctor(id);
        return ResponseEntity.noContent().build();
    }
}
