package com.swiftdrop.delivery.courier.service;

import com.swiftdrop.delivery.courier.api.CourierResponse;
import com.swiftdrop.delivery.courier.api.CreateCourierRequest;
import com.swiftdrop.delivery.courier.api.UpdateCourierRequest;
import com.swiftdrop.delivery.courier.domain.CategoryStatus;
import com.swiftdrop.delivery.courier.domain.Courier;
import com.swiftdrop.delivery.courier.domain.CourierCategory;
import com.swiftdrop.delivery.courier.domain.CourierStatus;
import com.swiftdrop.delivery.courier.repository.CourierRepository;
import com.swiftdrop.delivery.exception.ConflictException;
import com.swiftdrop.delivery.exception.InvalidOperationException;
import com.swiftdrop.delivery.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CourierService {

    private final CourierRepository courierRepository;
    private final CourierCategoryService categoryService;

    public CourierService(CourierRepository courierRepository, CourierCategoryService categoryService) {
        this.courierRepository = courierRepository;
        this.categoryService = categoryService;
    }

    @Transactional
    public CourierResponse create(CreateCourierRequest request) {
        if (courierRepository.existsByIdentityUserId(request.identityUserId())) {
            throw new ConflictException("A courier already exists for identity user: " + request.identityUserId());
        }
        var category = requireActiveCategory(request.categoryId());
        var courier = new Courier(request.identityUserId(), request.fullName().trim(), request.phone().trim(),
                cleanNullable(request.vehicleNumber()), category);
        return CourierResponse.from(courierRepository.save(courier));
    }

    public Page<CourierResponse> list(CourierStatus status, UUID categoryId, Pageable pageable) {
        if (status != null && categoryId != null) {
            return courierRepository.findAllByStatusAndCategory_Id(status, categoryId, pageable)
                    .map(CourierResponse::from);
        }
        if (status != null) {
            return courierRepository.findAllByStatus(status, pageable).map(CourierResponse::from);
        }
        if (categoryId != null) {
            return courierRepository.findAllByCategory_Id(categoryId, pageable).map(CourierResponse::from);
        }
        return courierRepository.findAll(pageable).map(CourierResponse::from);
    }

    public CourierResponse get(UUID id) {
        return CourierResponse.from(requireCourier(id));
    }

    public CourierResponse getByIdentityUser(UUID identityUserId) {
        return CourierResponse.from(courierRepository.findByIdentityUserId(identityUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Courier not found for identity user: " + identityUserId)));
    }

    /** The courier profile id for an identity user, if one has been set up. */
    public Optional<UUID> findIdByIdentityUser(UUID identityUserId) {
        return courierRepository.findByIdentityUserId(identityUserId).map(Courier::getId);
    }

    @Transactional
    public CourierResponse update(UUID id, UpdateCourierRequest request) {
        var courier = requireCourier(id);
        CourierCategory category = courier.getCategory().getId().equals(request.categoryId())
                ? courier.getCategory()
                : requireActiveCategory(request.categoryId());
        courier.update(request.fullName().trim(), request.phone().trim(), cleanNullable(request.vehicleNumber()),
                category);
        return CourierResponse.from(courier);
    }

    @Transactional
    public CourierResponse changeStatus(UUID id, CourierStatus status) {
        var courier = requireCourier(id);
        courier.changeStatus(status);
        return CourierResponse.from(courier);
    }

    public Courier requireCourier(UUID id) {
        return courierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Courier not found: " + id));
    }

    private CourierCategory requireActiveCategory(UUID categoryId) {
        var category = categoryService.requireCategory(categoryId);
        if (category.getStatus() != CategoryStatus.ACTIVE) {
            throw new InvalidOperationException("Courier category is not active: " + category.getCode());
        }
        return category;
    }

    private String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
