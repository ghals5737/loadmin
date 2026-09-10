package io.github.ghals5737.loadmin.core.query;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import javax.sql.DataSource;

/**
 * Wraps a {@link DataSource} so statement executions can be timed.
 *
 * <p>Dynamic proxies rather than a driver-specific hook, so this works with any
 * pool and adds no dependency. When no run is collecting, every call goes
 * straight through and costs one volatile read.
 *
 * <p>What it does <em>not</em> do: rewrite SQL, hold statements open longer, or
 * change what the application sees. Anything it cannot handle is delegated
 * untouched.
 */
public final class SlowQueryDataSource {

    private SlowQueryDataSource() {
    }

    public static DataSource wrap(DataSource delegate, SlowQueryRecorder recorder) {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[] { DataSource.class },
                (proxy, method, args) -> {
                    Object result = invoke(delegate, method, args);
                    if (result instanceof Connection connection && recorder.collecting()) {
                        return wrapConnection(connection, recorder);
                    }
                    return result;
                });
    }

    private static Connection wrapConnection(Connection delegate, SlowQueryRecorder recorder) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    Object result = invoke(delegate, method, args);
                    if (!(result instanceof Statement statement)) {
                        return result;
                    }
                    // prepareStatement/prepareCall carry their SQL up front;
                    // createStatement gets it with each execute call.
                    String sql = args != null && args.length > 0 && args[0] instanceof String text
                            ? text
                            : null;
                    return wrapStatement(statement, sql, recorder);
                });
    }

    private static Statement wrapStatement(Statement delegate, String preparedSql,
            SlowQueryRecorder recorder) {
        Class<?> type = delegate instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
        InvocationHandler handler = (proxy, method, args) -> {
            if (!method.getName().startsWith("execute")) {
                return invoke(delegate, method, args);
            }
            String sql = preparedSql;
            if (sql == null && args != null && args.length > 0 && args[0] instanceof String text) {
                sql = text;
            }
            long started = System.nanoTime();
            try {
                return invoke(delegate, method, args);
            } finally {
                recorder.record(sql, (System.nanoTime() - started) / 1_000_000);
            }
        };
        return (Statement) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
