package com.cgi.eoss.ftep.model;

import com.google.common.collect.ComparisonChain;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * <p>F-TEP terms and conditions.</p>
 */
@Data
@EqualsAndHashCode(exclude = {"id"})
@Table(name = "ftep_terms")
@NoArgsConstructor
@Entity
public class FtepTerms implements FtepEntity<FtepTerms> {

    /**
     * <p>Unique internal identifier of the terms.</p>
     */
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Terms version identifier.
     */
    @Column(name = "version")
    private String version;

    /**
     * Permanent URL to the terms version.
     */
    @Column(name = "url")
    private String url;

    /**
     * <p>The start date and time of the terms.</p>
     */
    @Column(name = "valid_start")
    private LocalDateTime validStart;

    /**
     * <p>The end date and time of the terms.</p>
     */
    @Column(name = "valid_end")
    private LocalDateTime validEnd;

    public FtepTerms(String version, String url, LocalDateTime validStart, LocalDateTime validEnd) {
        this.version = version;
        this.url = url;
        this.validStart = validStart;
        this.validEnd = validEnd;
    }

    public boolean isActive(LocalDateTime currentTime) {
        return currentTime.isAfter(Optional.ofNullable(validStart).orElse(LocalDateTime.MIN))
                && currentTime.isBefore(Optional.ofNullable(validEnd).orElse(LocalDateTime.MAX));
    }

    @Override
    public int compareTo(FtepTerms o) {
        return ComparisonChain.start().compare(version, o.version).result();
    }
}
