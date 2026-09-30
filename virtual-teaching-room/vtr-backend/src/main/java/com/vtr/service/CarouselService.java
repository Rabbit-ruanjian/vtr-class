package com.vtr.service;

import com.vtr.dto.CarouselCreateDTO;
import com.vtr.vo.CarouselVO;

import java.util.List;

public interface CarouselService {

    // CRUD
    Long create(CarouselCreateDTO dto);

    void update(Long id, CarouselCreateDTO dto);

    void delete(Long id);

    CarouselVO getById(Long id);

    // 查询
    List<CarouselVO> getAll();

    List<CarouselVO> getActiveCarousels();

    // 状态
    void activate(Long id);

    void deactivate(Long id);

    // 排序
    void updateOrder(List<Long> ids);

    // 统计
    void incrementClickCount(Long id);

    List<CarouselVO> getAllCarousels();

    void recordClick(Long id);
}