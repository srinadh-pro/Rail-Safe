package com.railsafe.repository;

import com.railsafe.entity.DataSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataSourceRepository extends JpaRepository<DataSourceEntity, Long> {
    List<DataSourceEntity> findAllByOrderByNameAsc();
}
