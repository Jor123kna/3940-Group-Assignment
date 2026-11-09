
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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
import java.sql.SQLException;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class LoginServletTest {

    @Nested
    class Unit {

        private LoginServlet servlet;
        private HttpServletRequest request;
        private HttpServletResponse response;
        private StringWriter body;
        private HttpSession newSession;
        private MockedStatic<Database> db;
        private Connection conn;
        private PreparedStatement findStmt;
        private PreparedStatement updateStmt;
        private ResultSet rs;
        private PrintStream realErr;
        private ByteArrayOutputStream errLog;

        @BeforeEach
        void captureErrorLog() {
            realErr = System.err;
            errLog = new ByteArrayOutputStream();
            System.setErr(new PrintStream(errLog, true));
        }

        @AfterEach
        void restoreErrorLog() {
            System.setErr(realErr);
        }

        @BeforeEach
        void setUp() throws Exception {
            servlet = new LoginServlet();
            request = mock(HttpServletRequest.class);
            response = mock(HttpServletResponse.class);
            body = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(body));
            when(request.getHeader("X-Requested-With")).thenReturn("XMLHttpRequest");

            newSession = mock(HttpSession.class);
            when(request.getSession(true)).thenReturn(newSession);

            conn = mock(Connection.class);
            findStmt = mock(PreparedStatement.class);
            updateStmt = mock(PreparedStatement.class);
            rs = mock(ResultSet.class);
            db = mockStatic(Database.class);
            db.when(Database::getConnection).thenReturn(conn);
            when(conn.prepareStatement(startsWith("SELECT"))).thenReturn(findStmt);
            when(conn.prepareStatement(startsWith("UPDATE"))).thenReturn(updateStmt);
            when(findStmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(false);
        }

        @AfterEach
        void tearDown() {
            db.close();
        }

        private void user(String displayName, String storedPassword, String playMode)
                throws Exception {
            when(rs.next()).thenReturn(true, false);
            when(rs.getLong("user_id")).thenReturn(7L);
            when(rs.getString("user_display_name")).thenReturn(displayName);
            when(rs.getString("password")).thenReturn(storedPassword);
            when(rs.getString("play_mode")).thenReturn(playMode);
        }

        private void login(String name, String password) throws Exception {
            when(request.getParameter("name")).thenReturn(name);
            when(request.getParameter("password")).thenReturn(password);
            servlet.doPost(request, response);
        }

        private void expectStatus(int status) {
            verify(response).setStatus(status);
        }

        private void expectNoLogin() {
            verify(request, never()).getSession(true);
        }

        private void expectRejectedBeforeDatabase(int status) {
            expectStatus(status);
            expectNoLogin();
            db.verifyNoInteractions();
        }

        @Test
        void withoutAjaxHeaderGets403() throws Exception {
            when(request.getHeader("X-Requested-With")).thenReturn(null);
            login("alice", "secret");
            expectRejectedBeforeDatabase(403);
        }

        @Test
        void missingNameGets400() throws Exception {
            login(null, "secret");
            expectRejectedBeforeDatabase(400);
        }

        @Test
        void blankNameGets400() throws Exception {
            login("   ", "secret");
            expectRejectedBeforeDatabase(400);
        }

        @Test
        void missingPasswordGets400() throws Exception {
            login("alice", null);
            expectRejectedBeforeDatabase(400);
        }

        @Test
        void emptyPasswordGets400() throws Exception {
            login("alice", "");
            expectRejectedBeforeDatabase(400);
        }

        @Test
        void nameIsTrimmedBeforeLookup() throws Exception {
            login("  alice  ", "secret");
            verify(findStmt).setString(1, "alice");
        }

        @Test
        void unknownUserGets401() throws Exception {
            login("nobody", "secret");
            expectStatus(401);
            assertTrue(body.toString().contains("Wrong name or password."), body.toString());
            expectNoLogin();
        }

        @Test
        void wrongPasswordGets401() throws Exception {
            user("alice", Passwords.hash("secret"), "solo");
            login("alice", "wrong");
            expectStatus(401);
            expectNoLogin();
        }

        @Test
        void unknownUserAndWrongPasswordGiveSameMessage() throws Exception {
            login("nobody", "secret");
            String unknownUser = body.toString();

            tearDown();
            setUp();
            user("alice", Passwords.hash("secret"), "solo");
            login("alice", "wrong");

            assertEquals(unknownUser, body.toString());
        }

        @Test
        void passwordIsCaseSensitive() throws Exception {
            user("alice", Passwords.hash("secret"), "solo");
            login("alice", "SECRET");
            expectStatus(401);
            expectNoLogin();
        }

        @Test
        void passwordIsNotTrimmed() throws Exception {
            user("alice", Passwords.hash("secret"), "solo");
            login("alice", " secret ");
            expectStatus(401);
            expectNoLogin();
        }

        @Test
        void correctPasswordLogsIn() throws Exception {
            user("alice", Passwords.hash("secret"), "solo");
            login("alice", "secret");

            expectStatus(200);
            assertTrue(body.toString().contains("\"loggedIn\":true"), body.toString());
            assertTrue(body.toString().contains("\"displayName\":\"alice\""), body.toString());
        }

        @Test
        void loginStoresUserInSession() throws Exception {
            user("alice", Passwords.hash("secret"), "staff");
            login("alice", "secret");

            verify(newSession).setAttribute(WebUtil.USER_ID, 7L);
            verify(newSession).setAttribute(WebUtil.DISPLAY_NAME, "alice");
            verify(newSession).setAttribute(WebUtil.PLAY_MODE, "staff");
        }

        @Test
        void sessionExpiresAfter30Minutes() throws Exception {
            user("alice", Passwords.hash("secret"), "solo");
            login("alice", "secret");
            verify(newSession).setMaxInactiveInterval(30 * 60);
        }

        @Test
        void oldSessionIsReplacedOnLogin() throws Exception {
            HttpSession oldSession = mock(HttpSession.class);
            when(request.getSession(false)).thenReturn(oldSession);
            user("alice", Passwords.hash("secret"), "solo");

            login("alice", "secret");

            verify(oldSession).invalidate();
            verify(request).getSession(true);
        }

        @Test
        void plainTextPasswordIsUpgradedToHash() throws Exception {
            user("alice", "secret", "solo");
            login("alice", "secret");

            expectStatus(200);
            verify(updateStmt).setString(eq(1),
                argThat(h -> Passwords.isHashed(h) && Passwords.matches(h, "secret")));
            verify(updateStmt).setLong(2, 7L);
            verify(updateStmt).executeUpdate();
        }

        @Test
        void hashedPasswordIsNotUpgraded() throws Exception {
            user("alice", Passwords.hash("secret"), "solo");
            login("alice", "secret");
            verify(conn, never()).prepareStatement(startsWith("UPDATE"));
        }

        @Test
        void failedUpgradeStillLogsIn() throws Exception {
            user("alice", "secret", "solo");
            when(updateStmt.executeUpdate()).thenThrow(new SQLException("column too short"));
            login("alice", "secret");
            expectStatus(200);
            assertTrue(errLog.toString().contains("could not store a hashed password"), errLog.toString());
        }

        @Test
        void databaseDownGets500() throws Exception {
            db.when(Database::getConnection).thenThrow(new SQLException("down"));
            login("alice", "secret");
            expectStatus(500);
            expectNoLogin();
            assertTrue(errLog.toString().contains("SQLException: down"), errLog.toString());
        }

        @Test
        void displayNameIsEscapedInJson() throws Exception {
            user("al\"ice", Passwords.hash("secret"), "solo");
            login("al\"ice", "secret");
            assertTrue(body.toString().contains("\"displayName\":\"al\\\"ice\""), body.toString());
        }
    }

    @Nested
    class Live {

        private static final String NAME = System.getenv("TEST_LOGIN_NAME");
        private static final String PASSWORD = System.getenv("TEST_LOGIN_PASSWORD");

        private HttpClient client;

        @BeforeAll
        static void ensureTomcatIsUp() throws Exception {
            TomcatConnectionTest.ensureTomcatIsUp();
        }

        @BeforeEach
        void setUp() {
            assumeTrue(NAME != null && PASSWORD != null,
                "set TEST_LOGIN_NAME and TEST_LOGIN_PASSWORD to run live login tests");
            client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        }

        private HttpResponse<String> post(String path, String form) throws Exception {
            return client.send(
                HttpRequest.newBuilder(URI.create(TomcatConnectionTest.APP + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        }

        private HttpResponse<String> login(String name, String password) throws Exception {
            return post("/login",
                "name=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8));
        }

        private String session() throws Exception {
            return client.send(
                HttpRequest.newBuilder(URI.create(TomcatConnectionTest.APP + "/session"))
                    .GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();
        }

        @Test
        void realAccountLogsIn() throws Exception {
            HttpResponse<String> res = login(NAME, PASSWORD);
            assertEquals(200, res.statusCode(), res.body());
            assertTrue(res.body().contains("\"loggedIn\":true"), res.body());
        }

        @Test
        void sessionShowsLoggedInAfterLogin() throws Exception {
            login(NAME, PASSWORD);
            assertTrue(session().contains("\"loggedIn\":true"), session());
        }

        @Test
        void logoutEndsTheSession() throws Exception {
            login(NAME, PASSWORD);
            assertEquals(200, post("/logout", "").statusCode());
            assertTrue(session().contains("\"loggedIn\":false"), session());
        }

        @Test
        void realAccountWithWrongPasswordGets401() throws Exception {
            HttpResponse<String> res = login(NAME, PASSWORD + "x");
            assertEquals(401, res.statusCode(), res.body());
            assertTrue(session().contains("\"loggedIn\":false"), session());
        }
    }
}
