package org.example.repositories;

import org.example.model.Page;
import org.example.model.Site;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {

    @Query("SELECT DISTINCT p FROM Page p WHERE p.path = :path AND p.site = :site")
    Optional<Page> findByPathAndSite(@Param("path") String path, @Param("site") Site site);

    @Query("SELECT COUNT(p) > 0 FROM Page p WHERE p.path = :path AND p.site = :site")
    boolean existsByPathAndSite(@Param("path") String path, @Param("site") Site site);

    @Query(value = "DELETE p1 FROM page p1 " +
            "INNER JOIN page p2 " +
            "WHERE p1.id > p2.id " +
            "AND p1.path = p2.path " +
            "AND p1.site_id = p2.site_id",
            nativeQuery = true)
    @Modifying
    @Transactional
    void deleteDuplicates();

    @Query(value = "SELECT path, site_id, COUNT(*) as count " +
            "FROM page " +
            "GROUP BY path, site_id " +
            "HAVING COUNT(*) > 1",
            nativeQuery = true)
    List<Object[]> findDuplicates();
}