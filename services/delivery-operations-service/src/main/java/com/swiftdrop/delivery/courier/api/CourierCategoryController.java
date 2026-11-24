package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.service.CourierCategoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courier-categories")
public class CourierCategoryController {

    private final CourierCategoryService categoryService;

    public CourierCategoryController(CourierCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourierCategoryResponse create(@Valid @RequestBody CreateCourierCategoryRequest request) {
        return categoryService.create(request);
    }

    @GetMapping
    public Page<CourierCategoryResponse> list(@PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return categoryService.list(pageable);
    }

    @GetMapping("/{categoryId}")
    public CourierCategoryResponse get(@PathVariable UUID categoryId) {
        return categoryService.get(categoryId);
    }

    @PutMapping("/{categoryId}")
    public CourierCategoryResponse update(@PathVariable UUID categoryId,
                                          @Valid @RequestBody UpdateCourierCategoryRequest request) {
        return categoryService.update(categoryId, request);
    }

    @PatchMapping("/{categoryId}/status")
    public CourierCategoryResponse changeStatus(@PathVariable UUID categoryId,
                                                @Valid @RequestBody CourierCategoryStatusRequest request) {
        return categoryService.changeStatus(categoryId, request.status());
    }
}
