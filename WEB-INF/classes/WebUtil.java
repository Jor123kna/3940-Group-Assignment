import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Small helpers shared by LoginServlet, SessionServlet, LogoutServlet
 * and AdminServlet.
 */
public final class WebUtil {

    // names of the values kept in the HTTP session after a login
    public static final String USER_ID      = "userId";
    public static final String DISPLAY_NAME = "displayName";
    public static final String PLAY_MODE    = "playMode";

    private WebUtil() { }

    // ---------- responses ----------

    public static void sendJson(
            HttpServletResponse response, int status, String json)
            throws IOException {

        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        PrintWriter out = response.getWriter();
        out.print(json);
    }

    public static void sendError(
            HttpServletResponse response, int status, String message)
            throws IOException {

        sendJson(
            response, status,
            "{\"error\":\"" + escapeJson(message) + "\"}"
        );
    }

    // ---------- request checks ----------

    /**
     * Every change (POST) must come from our own pages, which send
     * X-Requested-With. A form on another website cannot add that header,
     * so this blocks cross-site request forgery.
     */
    public static boolean requireAjax(
            HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return true;
        }

        sendError(response, HttpServletResponse.SC_FORBIDDEN,
                  "Request not allowed.");
        return false;
    }

    public static boolean isLoggedIn(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute(USER_ID) != null;
    }

    public static boolean isStaff(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null) {
            return false;
        }

        Object mode = session.getAttribute(PLAY_MODE);
        return mode != null && "staff".equalsIgnoreCase(mode.toString());
    }

    /** The logged-in user's id, or -1 when nobody is logged in. */
    public static long currentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null) {
            return -1;
        }

        Object id = session.getAttribute(USER_ID);
        return (id instanceof Long) ? (Long) id : -1;
    }

    /** Parses a numeric request parameter; null when missing or invalid. */
    public static Long longParam(HttpServletRequest request, String name) {
        String value = request.getParameter(name);

        if (value == null) {
            return null;
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---------- text escaping ----------

    public static String escapeJson(String value) {

        if (value == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {

            char c = value.charAt(i);

            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }

        return sb.toString();
    }

    public static String escapeXml(String value) {

        if (value == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {

            char c = value.charAt(i);

            switch (c) {
                case '&':  sb.append("&amp;");  break;
                case '<':  sb.append("&lt;");   break;
                case '>':  sb.append("&gt;");   break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default:   sb.append(c);
            }
        }

        return sb.toString();
    }

    // ---------- trivia_xml ----------

    /**
     * The text of the first &lt;question&gt; element in a trivia_xml value,
     * or null when the XML is empty or unreadable.
     */
    public static String questionText(String xml) {

        if (xml == null || xml.trim().isEmpty()) {
            return null;
        }

        try {
            DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

            // trivia_xml is data from the database: no DOCTYPE or
            // external entities (XXE protection).
            factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl", true
            );
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();

            Document doc = builder.parse(
                new InputSource(new StringReader(xml.trim()))
            );

            Element root = doc.getDocumentElement();
            NodeList nodes = root.getElementsByTagName("question");

            if (nodes.getLength() == 0) {
                return null;
            }

            String text = nodes.item(0).getTextContent().trim();
            return text.isEmpty() ? null : text;

        } catch (Exception e) {
            return null;
        }
    }
}
