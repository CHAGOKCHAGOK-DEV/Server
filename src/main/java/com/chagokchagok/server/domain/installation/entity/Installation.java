package com.chagokchagok.server.domain.installation.entity;

import com.chagokchagok.server.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "installations")
@Getter
public class Installation extends BaseTimeEntity {

    @Id
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, unique = true, length = 64, columnDefinition = "char(64)")
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstallationStatus status;

    protected Installation() {
    }

    public Installation(UUID id, String tokenHash) {
        this.id = Objects.requireNonNull(id);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.status = InstallationStatus.ACTIVE;
    }

    public void revoke() {
        this.status = InstallationStatus.REVOKED;
    }

}
