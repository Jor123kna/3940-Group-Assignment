import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Database {

    private static final String URL =
        "jdbc:postgresql://aws-0-us-west-2.pooler.supabase.com:5432/postgres?sslmode=require";

    private static final String USER =
        "postgres.szqqicnuhbdhbcrlyivn";

    private static final String PASSWORD =
        "Database3940Group";

    public static Connection getConnection()
            throws SQLException {

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException(
                "PostgreSQL JDBC driver not found",
                e
            );
        }

        return DriverManager.getConnection(
            URL,
            USER,
            PASSWORD
        );
    }
}