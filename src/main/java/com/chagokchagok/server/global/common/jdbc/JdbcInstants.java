package com.chagokchagok.server.global.common.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

// java.sql.Timestamp의 1582년 이전 Julian 달력 변환 회피
public final class JdbcInstants {

    private JdbcInstants() {
    }

    public static OffsetDateTime bind(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    public static Instant read(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }
}
