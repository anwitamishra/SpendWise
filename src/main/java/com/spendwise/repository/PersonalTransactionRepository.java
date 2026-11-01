package com.spendwise.repository;

import com.spendwise.model.DashboardSummary.MonthlyPoint;
import com.spendwise.model.PersonalTransaction;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Repository
public class PersonalTransactionRepository {

    private final JdbcTemplate jdbcTemplate;

    public PersonalTransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<PersonalTransaction> transactionRowMapper = (rs, rowNum) -> {
        PersonalTransaction pt = new PersonalTransaction();
        pt.setId(rs.getLong("id"));
        pt.setUserId(rs.getLong("user_id"));
        pt.setTitle(rs.getString("title"));
        pt.setAmount(rs.getBigDecimal("amount"));
        pt.setTransactionType(rs.getString("transaction_type"));
        pt.setCategory(rs.getString("category"));
        pt.setDescription(rs.getString("description"));
        Date date = rs.getDate("transaction_date");
        if (date != null) {
            pt.setTransactionDate(date.toLocalDate());
        }
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            pt.setCreatedAt(ts.toLocalDateTime());
        }
        return pt;
    };

    public PersonalTransaction save(PersonalTransaction pt) {
        String sql = "INSERT INTO personal_transactions (user_id, title, amount, transaction_type, category, description, transaction_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, pt.getUserId());
            ps.setString(2, pt.getTitle());
            ps.setBigDecimal(3, pt.getAmount());
            ps.setString(4, pt.getTransactionType());
            ps.setString(5, pt.getCategory());
            ps.setString(6, pt.getDescription());
            ps.setDate(7, Date.valueOf(pt.getTransactionDate()));
            return ps;
        }, keyHolder);

        Long generatedId = KeyHolderUtil.extractId(keyHolder);
        if (generatedId != null) {
            pt.setId(generatedId);
        }
        return pt;
    }

    public int update(PersonalTransaction pt) {
        String sql = "UPDATE personal_transactions SET title = ?, amount = ?, transaction_type = ?, category = ?, " +
                "description = ?, transaction_date = ? WHERE id = ? AND user_id = ?";
        return jdbcTemplate.update(sql,
                pt.getTitle(),
                pt.getAmount(),
                pt.getTransactionType(),
                pt.getCategory(),
                pt.getDescription(),
                Date.valueOf(pt.getTransactionDate()),
                pt.getId(),
                pt.getUserId()
        );
    }

    public int delete(Long id, Long userId) {
        String sql = "DELETE FROM personal_transactions WHERE id = ? AND user_id = ?";
        return jdbcTemplate.update(sql, id, userId);
    }

    public Optional<PersonalTransaction> findById(Long id) {
        String sql = "SELECT id, user_id, title, amount, transaction_type, category, description, transaction_date, created_at " +
                "FROM personal_transactions WHERE id = ?";
        try {
            PersonalTransaction pt = jdbcTemplate.queryForObject(sql, transactionRowMapper, id);
            return Optional.ofNullable(pt);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<PersonalTransaction> findByUserIdWithFilters(
            Long userId,
            String type,
            String category,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            String sortBy,
            String sortDirection) {

        StringBuilder sql = new StringBuilder("SELECT id, user_id, title, amount, transaction_type, category, description, transaction_date, created_at ");
        sql.append("FROM personal_transactions WHERE user_id = ? ");

        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (type != null && !type.trim().isEmpty() && !type.equalsIgnoreCase("ALL")) {
            sql.append("AND transaction_type = ? ");
            params.add(type.toUpperCase());
        }

        if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("ALL")) {
            sql.append("AND category = ? ");
            params.add(category);
        }

        if (startDate != null) {
            sql.append("AND transaction_date >= ? ");
            params.add(Date.valueOf(startDate));
        }

        if (endDate != null) {
            sql.append("AND transaction_date <= ? ");
            params.add(Date.valueOf(endDate));
        }

        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (LOWER(title) LIKE ? OR LOWER(description) LIKE ?) ");
            String searchPattern = "%" + search.toLowerCase().trim() + "%";
            params.add(searchPattern);
            params.add(searchPattern);
        }

        // Sorting
        String column = "transaction_date";
        if ("amount".equalsIgnoreCase(sortBy)) {
            column = "amount";
        } else if ("title".equalsIgnoreCase(sortBy)) {
            column = "title";
        }

        String direction = "DESC";
        if ("ASC".equalsIgnoreCase(sortDirection)) {
            direction = "ASC";
        }

        sql.append("ORDER BY ").append(column).append(" ").append(direction).append(", id DESC");

        return jdbcTemplate.query(sql.toString(), transactionRowMapper, params.toArray());
    }

    public Map<String, BigDecimal> getTotals(Long userId, LocalDate startDate, LocalDate endDate) {
        StringBuilder sql = new StringBuilder("SELECT transaction_type, COALESCE(SUM(amount), 0) AS total FROM personal_transactions WHERE user_id = ? ");
        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (startDate != null) {
            sql.append("AND transaction_date >= ? ");
            params.add(Date.valueOf(startDate));
        }
        if (endDate != null) {
            sql.append("AND transaction_date <= ? ");
            params.add(Date.valueOf(endDate));
        }

        sql.append("GROUP BY transaction_type");

        Map<String, BigDecimal> result = new HashMap<>();
        result.put("INCOME", BigDecimal.ZERO);
        result.put("EXPENSE", BigDecimal.ZERO);

        jdbcTemplate.query(sql.toString(), (rs) -> {
            String type = rs.getString("transaction_type");
            BigDecimal total = rs.getBigDecimal("total");
            if (type != null && total != null) {
                result.put(type.toUpperCase(), total);
            }
        }, params.toArray());

        return result;
    }

    public Map<String, BigDecimal> getCategoryExpenses(Long userId, LocalDate startDate, LocalDate endDate) {
        StringBuilder sql = new StringBuilder("SELECT category, COALESCE(SUM(amount), 0) AS total FROM personal_transactions WHERE user_id = ? AND transaction_type = 'EXPENSE' ");
        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (startDate != null) {
            sql.append("AND transaction_date >= ? ");
            params.add(Date.valueOf(startDate));
        }
        if (endDate != null) {
            sql.append("AND transaction_date <= ? ");
            params.add(Date.valueOf(endDate));
        }

        sql.append("GROUP BY category ORDER BY total DESC");

        Map<String, BigDecimal> map = new LinkedHashMap<>();
        jdbcTemplate.query(sql.toString(), (rs) -> {
            String cat = rs.getString("category");
            BigDecimal total = rs.getBigDecimal("total");
            map.put(cat, total);
        }, params.toArray());

        return map;
    }

    public List<PersonalTransaction> getRecentTransactions(Long userId, int limit) {
        String sql = "SELECT id, user_id, title, amount, transaction_type, category, description, transaction_date, created_at " +
                "FROM personal_transactions WHERE user_id = ? ORDER BY transaction_date DESC, id DESC LIMIT ?";
        return jdbcTemplate.query(sql, transactionRowMapper, userId, limit);
    }

    public List<MonthlyPoint> getMonthlyPoints(Long userId, int months) {
        // Collect points for the last N calendar months
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(months - 1).withDayOfMonth(1);

        String sql = "SELECT YEAR(transaction_date) as y, MONTH(transaction_date) as m, transaction_type, COALESCE(SUM(amount), 0) as total " +
                "FROM personal_transactions " +
                "WHERE user_id = ? AND transaction_date >= ? " +
                "GROUP BY YEAR(transaction_date), MONTH(transaction_date), transaction_type " +
                "ORDER BY y ASC, m ASC";

        Map<String, MonthlyPoint> pointsMap = new LinkedHashMap<>();
        LocalDate cur = start;
        DateTimeFormatter keyFmt = DateTimeFormatter.ofPattern("yyyy-MM");
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("MMM yyyy");

        while (!cur.isAfter(end)) {
            String key = cur.format(keyFmt);
            String label = cur.format(labelFmt);
            pointsMap.put(key, new MonthlyPoint(label, BigDecimal.ZERO, BigDecimal.ZERO));
            cur = cur.plusMonths(1);
        }

        jdbcTemplate.query(sql, (rs) -> {
            int y = rs.getInt("y");
            int m = rs.getInt("m");
            String key = String.format("%04d-%02d", y, m);
            String type = rs.getString("transaction_type");
            BigDecimal total = rs.getBigDecimal("total");

            MonthlyPoint point = pointsMap.get(key);
            if (point != null) {
                if ("INCOME".equalsIgnoreCase(type)) {
                    point.setIncome(total);
                } else if ("EXPENSE".equalsIgnoreCase(type)) {
                    point.setExpense(total);
                }
            }
        }, userId, Date.valueOf(start));

        return new ArrayList<>(pointsMap.values());
    }
}
