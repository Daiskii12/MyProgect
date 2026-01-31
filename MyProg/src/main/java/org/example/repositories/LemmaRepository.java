package org.example.repositories;

import org.example.model.Lemma;
import org.example.model.Site;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    List<Lemma> findBySite(Site site);

    @Query("SELECT COUNT(l) FROM Lemma l WHERE l.site = :site")
    int countBySite(Site site);

    @Query("SELECT SUM(l.frequency) FROM Lemma l WHERE l.site = :site")
    Integer sumFrequencyBySite(Site site);

    Optional<Lemma> findByLemmaAndSite(String lemmaText, Site site);
}