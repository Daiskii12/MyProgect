// SiteRepository.java
package org.example.repositories;

import org.example.model.Site;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {
    Optional<Site> findByUrl(String url);

    @Query("SELECT s FROM Site s WHERE s.status = 'INDEXING'")
    List<Site> findIndexingSites();

    @Query("SELECT COUNT(s) FROM Site s WHERE s.status = 'INDEXING'")
    int countIndexingSites();

    @Query("SELECT COUNT(s) FROM Site s WHERE s.status = 'FAILED'")
    int countFailedSites();
}