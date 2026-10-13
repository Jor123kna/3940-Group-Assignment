
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class LoginSqlInjectionTest {

    static String[] attacks() {
        return new String[] {
            "' OR '1'='1",
            "' OR 1=1 --",
            "' OR '1'='1' --",
            "admin' --",
            "admin'/*",
            "' OR ''='",
            "\" OR \"\"=\"",
            "') OR ('1'='1",
            "1' OR '1'='1' #",
            "' UNION SELECT 1, 'hacked', 'x', 'solo' --",
            "'; DROP TABLE users; --",
            "'; UPDATE users SET password = 'x'; --",
            "' AND pg_sleep(5) --"
        };
    }

    @Nested
    class Unit {

        private LoginServlet servlet;
        private HttpServletRequest request;
        private HttpServletResponse response;
        private MockedStatic<WebUtil> web;
        private MockedStatic<Database> db;
        private Connection conn;
        private PreparedStatement stmt;
        private ResultSet rs;

        @BeforeEach
        void setUp() throws Exception {
            servlet = new LoginServlet();
            request = mock(HttpServletRequest.class);
            response = mock(HttpServletResponse.class);
            when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

            web = mockStatic(WebUtil.class, CALLS_REAL_METHODS);
            web.when(() -> WebUtil.requireAjax(request, response)).thenReturn(true);

            conn = mock(Connection.class);
            stmt = mock(PreparedStatement.class);
            rs = mock(ResultSet.class);
            db = mockStatic(Database.class);
            db.when(Database::getConnection).thenReturn(conn);
            when(conn.prepareStatement(anyString())).thenReturn(stmt);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(false);
        }

        @AfterEach
        void tearDown() {
            web.close();
            db.close();
        }

        private void login(String name, String password) throws Exception {
            when(request.getParameter("name")).thenReturn(name);
            when(request.getParameter("password")).thenReturn(password);
            servlet.doPost(request, response);
        }

        private String sqlSent() throws Exception {
            ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
            verify(conn).prepareStatement(sql.capture());
            return sql.getValue();
        }

        private void expectRejected() throws Exception {
            web.verify(() -> WebUtil.sendError(eq(response), eq(401), anyString()));
            verify(request, never()).getSession(true);
            verify(conn, never()).createStatement();
            verify(stmt, never()).executeUpdate();
            verify(stmt, never()).execute();
        }

        @ParameterizedTest
        @MethodSource("LoginSqlInjectionTest#attacks")
        void attackInNameIsSentAsDataNotSql(String attack) throws Exception {
            login(attack, "anything");

            String sql = sqlSent();
            assertFalse(sql.contains(attack.trim()), "attack ended up in the SQL: " + sql);
            assertTrue(sql.contains("user_display_name = ?"), sql);
            verify(stmt).setString(1, attack.trim());
            expectRejected();
        }

        @ParameterizedTest
        @MethodSource("LoginSqlInjectionTest#attacks")
        void attackInPasswordNeverReachesSql(String attack) throws Exception {
            when(rs.next()).thenReturn(true, false);
            when(rs.getLong("user_id")).thenReturn(1L);
            when(rs.getString("user_display_name")).thenReturn("admin");
            when(rs.getString("password")).thenReturn(Passwords.hash("realPassword"));
            when(rs.getString("play_mode")).thenReturn("solo");

            login("admin", attack);

            assertFalse(sqlSent().contains(attack), "attack ended up in the SQL");
            verify(stmt).setString(1, "admin");
            verify(stmt, never()).setString(anyInt(), eq(attack));
            expectRejected();
        }

        @Test
        void attackInBothFieldsIsRejected() throws Exception {
            login("' OR '1'='1", "' OR '1'='1");
            verify(stmt).setString(1, "' OR '1'='1");
            expectRejected();
        }
    }

    @Nested
    class Live {

        private HttpClient client;

        @BeforeAll
        static void ensureTomcatIsUp() throws Exception {
            TomcatConnectionTest.ensureTomcatIsUp();
        }

        @BeforeEach
        void setUp() {
            client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        }

        private HttpResponse<String> login(String name, String password) throws Exception {
            String form = "name=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
            return client.send(
                HttpRequest.newBuilder(URI.create(TomcatConnectionTest.APP + "/login"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        }

        private void assertStillLoggedOut() throws Exception {
            HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create(TomcatConnectionTest.APP + "/session"))
                    .GET().build(),
                HttpResponse.BodyHandlers.ofString());
            assertTrue(res.body().contains("\"loggedIn\":false"), res.body());
        }

        private void assertRejected(HttpResponse<String> res) throws Exception {
            assertEquals(401, res.statusCode(), res.body());
            assertTrue(res.body().contains("Wrong name or password."), res.body());
            assertStillLoggedOut();
        }

        @ParameterizedTest
        @MethodSource("LoginSqlInjectionTest#attacks")
        void attackInNameIsRejected(String attack) throws Exception {
            assertRejected(login(attack, "anything"));
        }

        @ParameterizedTest
        @MethodSource("LoginSqlInjectionTest#attacks")
        void attackInPasswordIsRejected(String attack) throws Exception {
            assertRejected(login("admin", attack));
        }

        @Test
        void attackInBothFieldsIsRejected() throws Exception {
            assertRejected(login("' OR '1'='1", "' OR '1'='1"));
        }

        @Test
        void sleepAttackDoesNotDelayResponse() throws Exception {
            long start = System.nanoTime();
            login("' OR pg_sleep(5) IS NULL --", "x");
            long seconds = (System.nanoTime() - start) / 1_000_000_000L;
            assertTrue(seconds < 5, "login took " + seconds + "s - pg_sleep may have run");
        }

        @Test
        void usersTableSurvivesDropAttack() throws Exception {
            login("'; DROP TABLE users; --", "x");
            assertRejected(login("noSuchUser_qa", "wrongPassword"));
        }
    }
}
