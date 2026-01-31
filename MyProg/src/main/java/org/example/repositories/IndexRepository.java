package org.example.repositories;

import org.example.model.Index;
import org.example.model.Lemma;
import org.example.model.Page;
import org.example.model.Site;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {

    List<Index> findByLemma(Lemma lemma);

    Optional<Index> findByPageAndLemma(Page page, Lemma lemma);

    @Query("SELECT i FROM Index i WHERE i.page.site = :site AND i.lemma IN :lemmas")
    List<Index> findBySiteAndLemmas(@Param("site") Site site, @Param("lemmas") List<Lemma> lemmas);

    @Query("SELECT SUM(i.rank) FROM Index i WHERE i.page = :page AND i.lemma IN :lemmas")
    Double sumRankByPageAndLemmas(@Param("page") Page page, @Param("lemmas") List<Lemma> lemmas);
}