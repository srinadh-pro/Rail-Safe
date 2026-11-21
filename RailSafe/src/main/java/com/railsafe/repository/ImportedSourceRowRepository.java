package com.railsafe.repository;

import com.railsafe.entity.ImportedSourceRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Read/write access to the restricted external-source review table. */
public interface ImportedSourceRowRepository extends JpaRepository<ImportedSourceRow, Long> {

    boolean existsByDedupeKey(String dedupeKey);

    Optional<ImportedSourceRow> findByDedupeKey(String dedupeKey);
}
