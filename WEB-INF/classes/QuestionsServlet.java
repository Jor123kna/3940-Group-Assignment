import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * GET /questions?category_id=N
 *
 * Returns every question in a category, with its answer choices, as JSON:
 *
 * [
 *   {
 *     "question_id": 1,
 *     "question_text": "What percentage is equivalent to 3/4?",
 *     "media": {"type": "image", "url": "fraction.png"}, // or null
 *     "answers": [
 *       {"answer_id": 1, "answer_name": "25%"},
 *       ...
 *     ]
 *   }
 * ]
 *
 * How the tables fit together:
 * categories ---< question_categories >--- questions ---< answers
 *
 * The question text (and optional media) is stored inside the
 * questions.trivia_xml column, so it is read out of the XML here.
 * The answer choices come from the answers table.
 *
 * The "correctness" flag is deliberately NOT sent to the browser
 * the page asks AnswerServlet (POST /answer) whether a choice was right.
 */
@WebServlet("/questions")
public class QuestionsServlet extends HttpServlet {

    private static final String SQL =
        "SELECT q.question_id, q.trivia_xml, " +
        "       a.answer_id, a.answer_name " +
        "FROM question_categories qc " +
        "JOIN questions q ON q.question_id = qc.question_id " +
        "JOIN answers   a ON a.question_id = q.question_id " +
        "WHERE qc.category_id = ? " +
        "ORDER BY q.question_id, a.answer_id";

    private static class Answer {
        long id;
        String name;
    }

    private static class Question {
        long id;
        String text;
        String mediaType;   // null when the question has no media
        String mediaUrl;
        List<Answer> answers = new ArrayList<>();
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        // validate the category_id parameter
        long categoryId;

        try {
            categoryId = Long.parseLong(
                request.getParameter("category_id").trim()
            );
        } catch (NullPointerException | NumberFormatException e) {

            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

            out.print(
                "{\"error\":\"category_id is required " +
                "and must be a number\"}"
            );

            return;
        }

        // query the database
        Map<Long, Question> questions = new LinkedHashMap<>();
        Set<Long> unreadable = new HashSet<>();

        try (
            Connection conn = Database.getConnection();
            PreparedStatement stmt = conn.prepareStatement(SQL);
        ) {

            stmt.setLong(1, categoryId);

            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {

                    long questionId = rs.getLong("question_id");

                    if (unreadable.contains(questionId)) {
                        continue;
                    }

                    Question q = questions.get(questionId);

                    if (q == null) {

                        q = parseQuestion(
                            questionId, rs.getString("trivia_xml")
                        );

                        if (q == null) {
                            unreadable.add(questionId);
                            continue;
                        }

                        questions.put(questionId, q);
                    }

                    Answer a = new Answer();
                    a.id = rs.getLong("answer_id");
                    a.name = rs.getString("answer_name");
                    q.answers.add(a);
                }
            }

        } catch (SQLException e) {

            response.setStatus(
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );

            out.print("{\"error\":\"Database error\"}");

            e.printStackTrace();

            return;
        }

        out.print(toJson(questions));
    }


    /**
     * Reads the question text and optional media out of trivia_xml.
     * Returns null (and logs why) if the XML is missing or unusable,
     * so one bad row never breaks the whole category.
     */
    private Question parseQuestion(long questionId, String xml) {

        if (xml == null || xml.trim().isEmpty()) {
            System.err.println(
                "questions.question_id=" + questionId + ": trivia_xml is empty"
            );
            return null;
        }

        try {
            DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

            // trivia_xml is data from the database: never allow DOCTYPE /
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

            String text = firstText(root, "question");

            if (text == null || text.isEmpty()) {
                System.err.println(
                    "questions.question_id=" + questionId +
                    ": trivia_xml has no <question> text"
                );
                return null;
            }

            Question q = new Question();
            q.id = questionId;
            q.text = text;

            NodeList mediaNodes = root.getElementsByTagName("media");

            if (mediaNodes.getLength() > 0) {
                Element media = (Element) mediaNodes.item(0);
                String url = firstText(media, "url");

                if (url != null && !url.isEmpty()) {
                    q.mediaUrl = url;
                    q.mediaType = firstText(media, "type");

                    if (q.mediaType == null || q.mediaType.isEmpty()) {
                        q.mediaType = "image";
                    }
                }
            }

            return q;

        } catch (Exception e) {
            System.err.println(
                "questions.question_id=" + questionId +
                ": could not read trivia_xml (" + e.getMessage() + ")"
            );
            return null;
        }
    }

    /** Trimmed text of the first descendant element with this name. */
    private String firstText(Element parent, String tagName) {

        NodeList nodes = parent.getElementsByTagName(tagName);

        if (nodes.getLength() == 0) {
            return null;
        }

        return nodes.item(0).getTextContent().trim();
    }


    private String toJson(Map<Long, Question> questions) {

        StringBuilder json = new StringBuilder();

        json.append("[");

        boolean firstQuestion = true;

        for (Question q : questions.values()) {

            if (!firstQuestion) {
                json.append(",");
            }

            json.append("{");
            json.append("\"question_id\":").append(q.id).append(",");
            json.append("\"question_text\":\"")
                .append(escapeJson(q.text)).append("\",");

            if (q.mediaUrl != null) {
                json.append("\"media\":{");
                json.append("\"type\":\"")
                    .append(escapeJson(q.mediaType)).append("\",");
                json.append("\"url\":\"")
                    .append(escapeJson(q.mediaUrl)).append("\"},");
            } else {
                json.append("\"media\":null,");
            }

            json.append("\"answers\":[");

            boolean firstAnswer = true;

            for (Answer a : q.answers) {

                if (!firstAnswer) {
                    json.append(",");
                }

                json.append("{");
                json.append("\"answer_id\":").append(a.id).append(",");
                json.append("\"answer_name\":\"")
                    .append(escapeJson(a.name)).append("\"");
                json.append("}");

                firstAnswer = false;
            }

            json.append("]");
            json.append("}");

            firstQuestion = false;
        }

        json.append("]");

        return json.toString();
    }


    private String escapeJson(String value) {

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
}
