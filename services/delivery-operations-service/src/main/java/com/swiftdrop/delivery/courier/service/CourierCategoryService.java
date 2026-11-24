package com.swiftdrop.delivery.courier.service;

import com.swiftdrop.delivery.courier.api.CourierCategoryResponse;
import com.swiftdrop.delivery.courier.api.CreateCourierCategoryRequest;
import com.swiftdrop.delivery.courier.api.UpdateCourierCategoryRequest;
import com.swiftdrop.delivery.courier.domain.CategoryStatus;
import com.swiftdrop.delivery.courier.domain.CourierCategory;
import com.swiftdrop.delivery.courier.repository.CourierCategoryRepository;
import com.swiftdrop.delivery.exception.ConflictException;
import com.swiftdrop.delivery.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CourierCategoryService {

    private final CourierCategoryRepository categoryRepository;

    public CourierCategoryService(CourierCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public CourierCategoryResponse create(CreateCourierCategoryRequest request) {
        var code = request.code().trim().toUpperCase(Locale.ROOT);
        if (categoryRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Courier category code already exists: " + code);
        }
        var category = new CourierCategory(code, request.name().trim(), cleanNullable(request.description()),
                request.maxWeightKg());
        return CourierCategoryResponse.from(categoryRepository.save(category));
    }

    public Page<CourierCategoryResponse> list(Pageable pageable) {
        return categoryRepository.findAll(pageable).map(CourierCategoryResponse::from);
    }

    public CourierCategoryResponse get(UUID id) {
        return CourierCategoryResponse.from(requireCategory(id));
    }

    @Transactional
    public CourierCategoryResponse update(UUID id, UpdateCourierCategoryRequest request) {
        var category = requireCategory(id);
        category.update(request.name().trim(), cleanNullable(request.description()), request.maxWeightKg());
        return CourierCategoryResponse.from(category);
    }

    @Transactional
    public CourierCategoryResponse changeStatus(UUID id, CategoryStatus status) {
        var category = requireCategory(id);
        category.changeStatus(status);
        return CourierCategoryResponse.from(category);
    }

    public CourierCategory requireCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Courier category not found: " + id));
    }

    private String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
