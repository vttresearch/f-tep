package com.cgi.eoss.ftep.model;

import com.google.common.collect.ComparisonChain;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * <p>F-TEP terms acceptance enabling users to access the platform.</p>
 */
@Data
@EqualsAndHashCode(exclude = {"id"})
@Table(name = "ftep_terms_acceptance",
        indexes = {
            @Index(name = "ftep_terms_acceptance_owner_idx", columnList = "owner"),
        })
@NoArgsConstructor
@Entity
public class FtepTermsAcceptance implements FtepEntityWithOwner<FtepTermsAcceptance> {

    /**
     * <p>Unique internal identifier of the acceptance.</p>
     */
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * <p>The user of this acceptance.</p>
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user", nullable = false)
    private User user;

    /**
     * <p>The date and time the acceptance was created.</p>
     */
    @Column(name = "accepted_time")
    private LocalDateTime acceptedTime = LocalDateTime.now(ZoneOffset.UTC);

    @Override
    public int compareTo(FtepTermsAcceptance o) {
        return ComparisonChain.start().compare(user.getName(), o.user.getName()).result();
    }

    public FtepTermsAcceptance(User currentUser, LocalDateTime currentTime) {
        this.user = currentUser;
        this.acceptedTime = currentTime;
    }

    /* Acceptance is valid if it has been made after the validity start of the terms */
    public boolean isAcceptedAfter(LocalDateTime termsValidStartTime) {
        return acceptedTime.isAfter(termsValidStartTime);
    }
}
