import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * POST /login   name=...&password=...
 *
 * Checks the name and password against the users table. On success the
 * user is remembered in the HTTP session and {"loggedIn":true,...} is
 * returned. Otherwise a JSON {"error":"..."} comes back with a 4xx/5xx
 * status, which login.html shows on the page.
 */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    private static final String FIND_USER =
        "SELECT user_id, user_display_name, password, play_mode " +
        "FROM users " +
        "WHERE user_display_name = ?";

    private static final String UPGRADE_PASSWORD =
        "UPDATE users SET password = ? WHERE user_id = ?";

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        if (!WebUtil.requireAjax(request, response)) {
            return;
        }

        String name     = request.getParameter("name");
        String password = request.getParameter("password");

        if (name == null || name.trim().isEmpty() ||
            password == null || password.isEmpty()) {

            WebUtil.sendError(
                response, HttpServletResponse.SC_BAD_REQUEST,
                "Enter your name and password."
            );
            return;
        }

        name = name.trim();

        long userId = -1;
        String displayName = null;
        String stored = null;
        String playMode = null;

        try (
            Connection conn = Database.getConnection();
            PreparedStatement stmt = conn.prepareStatement(FIND_USER);
        ) {

            stmt.setString(1, name);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    userId      = rs.getLong("user_id");
                    displayName = rs.getString("user_display_name");
                    stored      = rs.getString("password");
                    playMode    = rs.getString("play_mode");
                }
            }

        } catch (SQLException e) {

            e.printStackTrace();

            WebUtil.sendError(
                response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Could not reach the database. Please try again."
            );
            return;
        }

        // Same message for "no such user" and "wrong password", so the
        // page does not reveal which names exist.
        if (!Passwords.matches(stored, password)) {

            WebUtil.sendError(
                response, HttpServletResponse.SC_UNAUTHORIZED,
                "Wrong name or password."
            );
            return;
        }

        // A plain-text password just worked: store a hash instead.
        // (If the column is too short for a hash this is skipped and
        // login still works.)
        if (!Passwords.isHashed(stored)) {
            upgradePassword(userId, password);
        }

        // Start a fresh session so an old session id cannot be reused.
        HttpSession old = request.getSession(false);

        if (old != null) {
            old.invalidate();
        }

        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(30 * 60);
        session.setAttribute(WebUtil.USER_ID, userId);
        session.setAttribute(WebUtil.DISPLAY_NAME, displayName);
        session.setAttribute(WebUtil.PLAY_MODE, playMode);

        WebUtil.sendJson(
            response, HttpServletResponse.SC_OK,
            "{\"loggedIn\":true,\"displayName\":\"" +
            WebUtil.escapeJson(displayName) + "\"}"
        );
    }

    private void upgradePassword(long userId, String password) {

        try (
            Connection conn = Database.getConnection();
            PreparedStatement stmt = conn.prepareStatement(UPGRADE_PASSWORD);
        ) {

            stmt.setString(1, Passwords.hash(password));
            stmt.setLong(2, userId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println(
                "users.user_id=" + userId +
                ": could not store a hashed password (" +
                e.getMessage() + ")"
            );
        }
    }
}
