import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * GET /session
 *
 * Tells the pages who is logged in:
 *   {"loggedIn":false}
 *   {"loggedIn":true,"displayName":"admin","isStaff":true}
 */
@WebServlet("/session")
public class SessionServlet extends HttpServlet {

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        if (!WebUtil.isLoggedIn(request)) {
            WebUtil.sendJson(
                response, HttpServletResponse.SC_OK,
                "{\"loggedIn\":false}"
            );
            return;
        }

        HttpSession session = request.getSession(false);
        Object name = session.getAttribute(WebUtil.DISPLAY_NAME);

        WebUtil.sendJson(
            response, HttpServletResponse.SC_OK,
            "{\"loggedIn\":true,\"displayName\":\"" +
            WebUtil.escapeJson(name == null ? "" : name.toString()) +
            "\",\"isStaff\":" + WebUtil.isStaff(request) + "}"
        );
    }
}
