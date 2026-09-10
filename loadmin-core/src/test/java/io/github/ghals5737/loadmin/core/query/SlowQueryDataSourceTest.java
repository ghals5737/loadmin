package io.github.ghals5737.loadmin.core.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

class SlowQueryDataSourceTest {

    /** A JDBC stack that does nothing but take the given time to execute. */
    private static DataSource fakeDataSource(long executionMillis) {
        ResultSet resultSet = (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(), new Class<?>[] { ResultSet.class },
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(), new Class<?>[] { PreparedStatement.class },
                (proxy, method, args) -> {
                    if (method.getName().startsWith("execute")) {
                        Thread.sleep(executionMillis);
                        return method.getReturnType() == ResultSet.class ? resultSet : 0;
                    }
                    return null;
                });
        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
                (proxy, method, args) -> method.getName().startsWith("prepare") ? statement : null);
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(), new Class<?>[] { DataSource.class },
                (proxy, method, args) -> method.getName().equals("getConnection") ? connection : null);
    }

    private static List<String> collect(SlowQueryRecorder recorder, List<Long> timings) {
        List<String> seen = new ArrayList<>();
        recorder.collectInto((sql, millis) -> {
            seen.add(sql);
            timings.add(millis);
        });
        return seen;
    }

    @Test
    void aSlowStatementIsRecordedWithItsSql() throws Exception {
        SlowQueryRecorder recorder = new SlowQueryRecorder(5);
        List<Long> timings = new ArrayList<>();
        List<String> seen = collect(recorder, timings);
        DataSource dataSource = SlowQueryDataSource.wrap(fakeDataSource(20), recorder);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("select 1 from dual")) {
            statement.executeQuery();
        }

        assertEquals(List.of("select 1 from dual"), seen);
        assertTrue(timings.get(0) >= 15, "timing looks wrong: " + timings);
    }

    @Test
    void aFastStatementIsNotRecorded() throws Exception {
        SlowQueryRecorder recorder = new SlowQueryRecorder(100);
        List<String> seen = collect(recorder, new ArrayList<>());
        DataSource dataSource = SlowQueryDataSource.wrap(fakeDataSource(0), recorder);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("select 1")) {
            statement.executeQuery();
        }

        assertEquals(List.of(), seen);
    }

    @Test
    void nothingIsTimedWhileNoRunIsCollecting() throws Exception {
        SlowQueryRecorder recorder = new SlowQueryRecorder(0);
        DataSource dataSource = SlowQueryDataSource.wrap(fakeDataSource(10), recorder);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("select 1")) {
            statement.executeQuery();
        }

        // No sink installed: the call still works, it is simply not measured.
        assertEquals(false, recorder.collecting());
    }
}
