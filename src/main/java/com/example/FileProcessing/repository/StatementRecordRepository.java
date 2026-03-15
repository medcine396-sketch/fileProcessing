package com.example.FileProcessing.repository;

import com.example.FileProcessing.entity.StatementRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StatementRecordRepository extends JpaRepository<StatementRecordEntity, Long> {

    List<StatementRecordEntity> findBySettledFalse();
}

