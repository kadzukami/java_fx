package com.converter.pdf;

import com.converter.pdf.PdfParser.StudentRecord;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

public class DBService {

    public static void insertRecords(JdbcTemplate jdbcTemplate, List<StudentRecord> records) {
        String sql = "INSERT INTO students (faculty, fio, sum_points, some_code) VALUES (?, ?, ?, ?)";
        for (StudentRecord record : records) {
            jdbcTemplate.update(sql, record.getFaculty(), record.getFio(), record.getSumPoints(), record.getCode());
        }
    }
}