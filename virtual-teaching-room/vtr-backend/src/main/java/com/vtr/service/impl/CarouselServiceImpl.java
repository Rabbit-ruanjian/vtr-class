package com.vtr.service.impl;

import com.vtr.common.exception.NotFoundException;
import com.vtr.dto.CarouselCreateDTO;
import com.vtr.entity.Carousel;
import com.vtr.repository.CarouselRepository;
import com.vtr.service.CarouselService;
import com.vtr.vo.CarouselVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarouselServiceImpl implements CarouselService {

    private final CarouselRepository carouselRepository;

    @Override
    @Transactional
    public Long create(CarouselCreateDTO dto) {
        Carousel carousel = new Carousel();
        BeanUtils.copyProperties(dto, carousel);

        carouselRepository.save(carousel);

        log.info("创建轮播图: id={}", carousel.getId());

        return carousel.getId();
    }

    @Override
    @Transactional
    public void update(Long id, CarouselCreateDTO dto) {
        Carousel carousel = carouselRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("轮播图", id));

        BeanUtils.copyProperties(dto, carousel, "id", "clickCount");

        carouselRepository.save(carousel);

        log.info("更新轮播图: id={}", id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        carouselRepository.deleteById(id);

        log.info("删除轮播图: id={}", id);
    }

    @Override
    public CarouselVO getById(Long id) {
        Carousel carousel = carouselRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("轮播图", id));

        return convertToVO(carousel);
    }

    @Override
    public List<CarouselVO> getAll() {
        return carouselRepository.findByStatusOrderBySortOrderAsc(Carousel.CarouselStatus.ACTIVE)
                .stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<CarouselVO> getActiveCarousels() {
        return carouselRepository.findActiveCarousels(LocalDateTime.now()).stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void activate(Long id) {
        Carousel carousel = carouselRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("轮播图", id));

        carousel.setStatus(Carousel.CarouselStatus.ACTIVE);
        carouselRepository.save(carousel);
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        carouselRepository.deactivate(id);
    }

    @Override
    @Transactional
    public void updateOrder(List<Long> ids) {
        for (int i = 0; i < ids.size(); i++) {
            Carousel carousel = carouselRepository.findById(ids.get(i)).orElse(null);
            if (carousel != null) {
                carousel.setSortOrder(i);
                carouselRepository.save(carousel);
            }
        }
    }

    @Override
    @Transactional
    public void incrementClickCount(Long id) {
        carouselRepository.incrementClickCount(id);
    }

    @Override
    public List<CarouselVO> getAllCarousels() {
        // 返回 CarouselVO 列表
        return carouselRepository.findAll()
                .stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void recordClick(Long id) {
        Carousel carousel = carouselRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("轮播图", id));

        // 增加点击次数
        carousel.setClickCount(carousel.getClickCount() + 1);
        carouselRepository.save(carousel);

        log.info("记录轮播图点击: id={}, 当前点击次数={}", id, carousel.getClickCount());
    }

    // 私有方法

    private CarouselVO convertToVO(Carousel carousel) {
        CarouselVO vo = new CarouselVO();
        BeanUtils.copyProperties(carousel, vo);

        vo.setStatus(carousel.getStatus().name());
        vo.setIsActive(carousel.isActive());

        return vo;
    }
}