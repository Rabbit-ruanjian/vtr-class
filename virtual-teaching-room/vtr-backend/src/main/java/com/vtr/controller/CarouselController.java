package com.vtr.controller;

import com.vtr.common.Result;
import com.vtr.dto.CarouselCreateDTO;
import com.vtr.service.CarouselService;
import com.vtr.vo.CarouselVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/carousel")
@RequiredArgsConstructor
public class CarouselController {

    private final CarouselService carouselService;

    @GetMapping("/list")
    public Result<List<CarouselVO>> getActiveCarousels() {
        return Result.success(carouselService.getActiveCarousels());
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<CarouselVO>> getAllCarousels() {
        return Result.success(carouselService.getAllCarousels());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Long> create(@RequestBody @Validated CarouselCreateDTO dto) {
        return Result.success(carouselService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> update(@PathVariable Long id,
                               @RequestBody @Validated CarouselCreateDTO dto) {
        carouselService.update(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        carouselService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> activate(@PathVariable Long id) {
        carouselService.activate(id);
        return Result.success();
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deactivate(@PathVariable Long id) {
        carouselService.deactivate(id);
        return Result.success();
    }

    @PostMapping("/{id}/click")
    public Result<Void> recordClick(@PathVariable Long id) {
        carouselService.recordClick(id);
        return Result.success();
    }

    @PutMapping("/order")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> updateOrder(@RequestBody List<Long> ids) {
        carouselService.updateOrder(ids);
        return Result.success();
    }
}