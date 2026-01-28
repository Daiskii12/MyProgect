package org.example.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "page",
        uniqueConstraints = @UniqueConstraint(
                name = "uc_page_site_path",
                columnNames = {"site_id", "path"}
        )
)
@Getter
@Setter
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false, foreignKey = @ForeignKey(name = "fk_page_site"))
    private Site site;

    @Column(name = "path", nullable = false, length = 500)
    private String path;

    @Column(nullable = false)
    private int code;

    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;
}