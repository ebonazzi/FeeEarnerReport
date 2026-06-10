package net.javalover.feeearner;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal in-memory JDBC fakes (dynamic proxies) for unit-testing transaction logic
 * without a database. Records commit/rollback/close and can inject a SQL failure on a
 * chosen statement execution.
 */
public final class FakeJdbc {

    public boolean committed, rolledBack, closed;
    public boolean autoCommit = true;
    public int updateCount, batchCount, commitCount;
    /** 1-based index of the executeUpdate/executeBatch call that should throw; -1 = never. */
    public int failOnUpdate = -1, failOnBatch = -1;
    public final List<String> prepared = new ArrayList<>();

    public DataSource dataSource() {
        return (DataSource) Proxy.newProxyInstance(loader(), new Class<?>[]{DataSource.class},
            (p, m, a) -> m.getName().equals("getConnection") ? connection() : def(m.getReturnType()));
    }

    public Connection connection() {
        return (Connection) Proxy.newProxyInstance(loader(), new Class<?>[]{Connection.class},
            (p, m, a) -> switch (m.getName()) {
                case "setAutoCommit"    -> { autoCommit = (boolean) a[0]; yield null; }
                case "getAutoCommit"    -> autoCommit;
                case "commit"           -> { committed = true; commitCount++; yield null; }
                case "rollback"         -> { rolledBack = true; yield null; }
                case "close"            -> { closed = true; yield null; }
                case "prepareStatement" -> { prepared.add((String) a[0]); yield statement(); }
                default                 -> def(m.getReturnType());
            });
    }

    private PreparedStatement statement() {
        return (PreparedStatement) Proxy.newProxyInstance(loader(), new Class<?>[]{PreparedStatement.class},
            (p, m, a) -> switch (m.getName()) {
                case "executeUpdate" -> {
                    if (++updateCount == failOnUpdate)
                        throw new SQLException("simulated failure on update " + updateCount);
                    yield 0;
                }
                case "executeBatch" -> {
                    if (++batchCount == failOnBatch)
                        throw new SQLException("simulated failure on batch " + batchCount);
                    yield new int[0];
                }
                default -> def(m.getReturnType());
            });
    }

    private ClassLoader loader() { return getClass().getClassLoader(); }

    private static Object def(Class<?> t) {
        if (!t.isPrimitive() || t == void.class) return null;
        if (t == boolean.class) return false;
        if (t == long.class)    return 0L;
        if (t == double.class)  return 0d;
        if (t == float.class)   return 0f;
        if (t == char.class)    return '\0';
        return 0;   // int, short, byte
    }
}
