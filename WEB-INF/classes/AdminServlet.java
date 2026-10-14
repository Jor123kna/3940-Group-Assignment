import java.io.IOException;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Everything adminPage.html calls. Staff only (users.play_mode = 'staff').
 *
 *   GET  /admin/categories
 *   GET  /admin/questions?category_id=
 *   POST /admin/category-add      name, description
 *   POST /admin/category-delete   category_id
 *   POST /admin/question-add      category_id, question_text, correct,
 *                                 answer (4 times, empty ones are skipped)
 *   POST /admin/question-delete   question_id
 *
 * A logged-out request gets 401 (the page then goes to login.html);
 * a logged-in user who is not staff gets 403.
 */
@WebServlet("/admin/*")
public class AdminServlet extends HttpServlet {

    private static final int MAX_CATEGORY_NAME = 50;
    private static final int MAX_DESCRIPTION   = 200;
    private static final int MAX_QUESTION      = 500;
    private static final int MAX_ANSWER        = 150;

    // ------------------------------------------------------------------
    // routing
    // ------------------------------------------------------------------

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        if (!authorize(request, response)) {
            return;
        }

        String path = request.getPathInfo();

        if ("/categories".equals(path)) {
            listCategories(response);
        } else if ("/questions".equals(path)) {
            listQuestions(request, response);
        } else {
            WebUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND,
                              "Unknown admin page.");
        }
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        if (!authorize(request, response)) {
            return;
        }

        if (!WebUtil.requireAjax(request, response)) {
            return;
        }

        String path = request.getPathInfo();

        if ("/category-add".equals(path)) {
            addCategory(request, response);
        } else if ("/category-delete".equals(path)) {
            deleteCategory(request, response);
        } else if ("/question-add".equals(path)) {
            addQuestion(request, response);
        } else if ("/question-delete".equals(path)) {
            deleteQuestion(request, response);
        } else {
            WebUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND,
                              "Unknown admin action.");
        }
    }

    private boolean authorize(
            HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        if (!WebUtil.isLoggedIn(request)) {
            WebUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                              "Please log in.");
            return false;
        }

        if (!WebUtil.isStaff(request)) {
            WebUtil.sendError(response, HttpServletResponse.SC_FORBIDDEN,
                              "Only staff can use the admin page.");
            return false;
        }

        return true;
    }

    private void databaseError(
            HttpServletResponse response, SQLException e)
            throws IOException {

        e.printStackTrace();

        WebUtil.sendError(
            response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
            "Database error"
        );
    }

    // ------------------------------------------------------------------
    // categories
    // ------------------------------------------------------------------

    private static final String LIST_CATEGORIES =
        "SELECT c.category_id, c.category_name, c.description, " +
        "       COUNT(qc.question_id) AS question_count " +
        "FROM categories c " +
        "LEFT JOIN question_categories qc " +
        "       ON qc.category_id = c.category_id " +
        "GROUP BY c.category_id, c.category_name, c.description " +
        "ORDER BY c.category_name";

    private void listCategories(HttpServletResponse response)
            throws IOException {

        StringBuilder json = new StringBuilder("[");

        try (
            Connection conn = Database.getConnection();
            PreparedStatement stmt = conn.prepareStatement(LIST_CATEGORIES);
            ResultSet rs = stmt.executeQuery();
        ) {

            boolean first = true;

            while (rs.next()) {

                if (!first) {
                    json.append(",");
                }

                json.append("{\"category_id\":")
                    .append(rs.getLong("category_id"))
                    .append(",\"category_name\":\"")
                    .append(WebUtil.escapeJson(rs.getString("category_name")))
                    .append("\",\"description\":\"")
                    .append(WebUtil.escapeJson(rs.getString("description")))
                    .append("\",\"question_count\":")
                    .append(rs.getLong("question_count"))
                    .append("}");

                first = false;
            }

        } catch (SQLException e) {
            databaseError(response, e);
            return;
        }

        json.append("]");

        WebUtil.sendJson(response, HttpServletResponse.SC_OK, json.toString());
    }

    private void addCategory(
            HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String name = request.getParameter("name");
        String description = request.getParameter("description");

        name = (name == null) ? "" : name.trim();
        description = (description == null) ? "" : description.trim();

        if (name.isEmpty()) {
            WebUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                              "Enter a category name.");
            return;
        }

        if (name.length() > MAX_CATEGORY_NAME ||
            description.length() > MAX_DESCRIPTION) {

            WebUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                              "The name or description is too long.");
            return;
        }

        try (Connection conn = Database.getConnection()) {

            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT 1 FROM categories " +
                    "WHERE lower(category_name) = lower(?)")) {

                check.setString(1, name);

                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        WebUtil.sendError(
                            response, HttpServletResponse.SC_CONFLICT,
                            "A category called \"" + name + "\" already exists."
                        );
                        return;
                    }
                }
            }

            long newId;

            try (PreparedStatement insert = conn.prepareStatement(
                    "INSERT INTO categories (category_name, description) " +
                    "VALUES (?, ?) RETURNING category_id")) {

                insert.setString(1, name);
                insert.setString(2, description.isEmpty() ? null : description);

                try (ResultSet rs = insert.executeQuery()) {
                    rs.next();
                    newId = rs.getLong(1);
                }
            }

            WebUtil.sendJson(
                response, HttpServletResponse.SC_OK,
                "{\"category_id\":" + newId + "}"
            );

        } catch (SQLException e) {
            databaseError(response, e);
        }
    }

    /**
     * Deletes the category and the questions that belong to it. A question
     * that is also in another category is kept (only unlinked from this one).
     */
    private void deleteCategory(
            HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        Long categoryId = WebUtil.longParam(request, "category_id");

        if (categoryId == null) {
            WebUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                              "category_id is required and must be a number.");
            return;
        }

        try (Connection conn = Database.getConnection()) {

            conn.setAutoCommit(false);

            try {

                try (PreparedStatement check = conn.prepareStatement(
                        "SELECT 1 FROM categories WHERE category_id = ?")) {

                    check.setLong(1, categoryId);

                    try (ResultSet rs = check.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            WebUtil.sendError(
                                response, HttpServletResponse.SC_NOT_FOUND,
                                "That category no longer exists."
                            );
                            return;
                        }
                    }
                }

                // questions that belong to this category and no other
                List<Long> owned = new ArrayList<>();

                try (PreparedStatement find = conn.prepareStatement(
                        "SELECT qc.question_id FROM question_categories qc " +
                        "WHERE qc.category_id = ? AND NOT EXISTS (" +
                        "  SELECT 1 FROM question_categories other " +
                        "  WHERE other.question_id = qc.question_id " +
                        "    AND other.category_id <> qc.category_id)")) {

                    find.setLong(1, categoryId);

                    try (ResultSet rs = find.executeQuery()) {
                        while (rs.next()) {
                            owned.add(rs.getLong(1));
                        }
                    }
                }

                run(conn, "DELETE FROM question_categories WHERE category_id = ?",
                    categoryId);

                if (!owned.isEmpty()) {
                    Array ids = conn.createArrayOf("bigint", owned.toArray());

                    try (PreparedStatement del = conn.prepareStatement(
                            "DELETE FROM answers WHERE question_id = ANY(?)")) {
                        del.setArray(1, ids);
                        del.executeUpdate();
                    }

                    try (PreparedStatement del = conn.prepareStatement(
                            "DELETE FROM questions WHERE question_id = ANY(?)")) {
                        del.setArray(1, ids);
                        del.executeUpdate();
                    }
                }

                run(conn, "DELETE FROM categories WHERE category_id = ?",
                    categoryId);

                conn.commit();

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

            WebUtil.sendJson(response, HttpServletResponse.SC_OK,
                             "{\"deleted\":true}");

        } catch (SQLException e) {
            databaseError(response, e);
        }
    }

    // ------------------------------------------------------------------
    // questions
    // ------------------------------------------------------------------

    private static final String LIST_QUESTIONS =
        "SELECT q.question_id, q.trivia_xml, " +
        "       a.answer_id, a.answer_name, a.correctness " +
        "FROM question_categories qc " +
        "JOIN questions q ON q.question_id = qc.question_id " +
        "LEFT JOIN answers a ON a.question_id = q.question_id " +
        "WHERE qc.category_id = ? " +
        "ORDER BY q.question_id, a.answer_id";

    private void listQuestions(
            HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        Long categoryId = WebUtil.longParam(request, "category_id");

        if (categoryId == null) {
            WebUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                              "category_id is required and must be a number.");
            return;
        }

        // question_id -> JSON text of its answers, in order
        Map<Long, String> texts = new LinkedHashMap<>();
        Map<Long, StringBuilder> answers = new LinkedHashMap<>();

        try (
            Connection conn = Database.getConnection();
            PreparedStatement stmt = conn.prepareStatement(LIST_QUESTIONS);
        ) {

            stmt.setLong(1, categoryId);

            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {

                    long questionId = rs.getLong("question_id");

                    if (!texts.containsKey(questionId)) {

                        String text = WebUtil.questionText(
                            rs.getString("trivia_xml")
                        );

                        // keep unreadable rows visible so they can be deleted
                        texts.put(questionId,
                                  text != null ? text : "(unreadable question)");
                        answers.put(questionId, new StringBuilder());
                    }

                    long answerId = rs.getLong("answer_id");

                    if (rs.wasNull()) {
                        continue;     // question with no answers
                    }

                    StringBuilder list = answers.get(questionId);

                    if (list.length() > 0) {
                        list.append(",");
                    }

                    list.append("{\"answer_id\":").append(answerId)
                        .append(",\"answer_name\":\"")
                        .append(WebUtil.escapeJson(rs.getString("answer_name")))
                        .append("\",\"correct\":")
                        .append(rs.getBoolean("correctness"))
                        .append("}");
                }
            }

        } catch (SQLException e) {
            databaseError(response, e);
            return;
        }

        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        for (Map.Entry<Long, String> entry : texts.entrySet()) {

            if (!first) {
                json.append(",");
            }

            json.append("{\"question_id\":").append(entry.getKey())
                .append(",\"question_text\":\"")
                .append(WebUtil.escapeJson(entry.getValue()))
                .append("\",\"answers\":[")
                .append(answers.get(entry.getKey()))
                .append("]}");

            first = false;
        }

        json.append("]");

        WebUtil.sendJson(response, HttpServletResponse.SC_OK, json.toString());
    }

    private void addQuestion(
            HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        Long categoryId = WebUtil.longParam(request, "category_id");
        Long correctIndex = WebUtil.longParam(request, "correct");
        String text = request.getParameter("question_text");
        String[] boxes = request.getParameterValues("answer");

        text = (text == null) ? "" : text.trim();

        if (categoryId == null || correctIndex == null || boxes == null) {
            WebUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                              "The form is incomplete.");
            return;
        }

        if (text.isEmpty()) {
            WebUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                              "Enter the question.");
            return;
        }

        if (text.length() > MAX_QUESTION) {
            WebUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                              "The question is too long.");
            return;
        }

        // keep the filled-in answers; remember which one was ticked
        List<String> names = new ArrayList<>();
        int correctPosition = -1;

        for (int i = 0; i < boxes.length && i < 4; i++) {

            String name = (boxes[i] == null) ? "" : boxes[i].trim();

            if (name.isEmpty()) {
                continue;
            }

            if (name.length() > MAX_ANSWER) {
                WebUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                                  "An answer is too long.");
                return;
            }

            if (i == correctIndex) {
                correctPosition = names.size();
            }

            names.add(name);
        }

        if (names.size() < 2) {
            WebUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                              "Enter at least two answers.");
            return;
        }

        if (correctPosition < 0) {
            WebUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                              "The answer ticked as correct is empty.");
            return;
        }

        // trivia_xml, in the same shape as the existing rows
        StringBuilder xml = new StringBuilder();
        xml.append("<trivia>\n");
        xml.append("       <question>")
           .append(WebUtil.escapeXml(text)).append("</question>\n");
        xml.append("       <answers>\n");

        for (int i = 0; i < names.size(); i++) {
            xml.append("         <answer correct=\"")
               .append(i == correctPosition)
               .append("\">")
               .append(WebUtil.escapeXml(names.get(i)))
               .append("</answer>\n");
        }

        xml.append("       </answers>\n");
        xml.append("     </trivia>");

        try (Connection conn = Database.getConnection()) {

            conn.setAutoCommit(false);

            try {

                try (PreparedStatement check = conn.prepareStatement(
                        "SELECT 1 FROM categories WHERE category_id = ?")) {

                    check.setLong(1, categoryId);

                    try (ResultSet rs = check.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            WebUtil.sendError(
                                response, HttpServletResponse.SC_NOT_FOUND,
                                "That category no longer exists."
                            );
                            return;
                        }
                    }
                }

                long questionId;

                try (PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO questions " +
                        "(uploaded_by, media_type, num_answer_choices, " +
                        " trivia_xml, date_uploaded) " +
                        "VALUES (?, 'text', ?, ?, now()) " +
                        "RETURNING question_id")) {

                    insert.setLong(1, WebUtil.currentUserId(request));
                    insert.setInt(2, names.size());
                    // OTHER = let the database convert the text to whatever
                    // type the column is (text or xml)
                    insert.setObject(3, xml.toString(), Types.OTHER);

                    try (ResultSet rs = insert.executeQuery()) {
                        rs.next();
                        questionId = rs.getLong(1);
                    }
                }

                try (PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO answers " +
                        "(answer_name, question_id, correctness) " +
                        "VALUES (?, ?, ?)")) {

                    for (int i = 0; i < names.size(); i++) {
                        insert.setString(1, names.get(i));
                        insert.setLong(2, questionId);
                        insert.setBoolean(3, i == correctPosition);
                        insert.addBatch();
                    }

                    insert.executeBatch();
                }

                try (PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO question_categories " +
                        "(question_id, category_id) VALUES (?, ?)")) {

                    insert.setLong(1, questionId);
                    insert.setLong(2, categoryId);
                    insert.executeUpdate();
                }

                conn.commit();

                WebUtil.sendJson(
                    response, HttpServletResponse.SC_OK,
                    "{\"question_id\":" + questionId + "}"
                );

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            databaseError(response, e);
        }
    }

    private void deleteQuestion(
            HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        Long questionId = WebUtil.longParam(request, "question_id");

        if (questionId == null) {
            WebUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                              "question_id is required and must be a number.");
            return;
        }

        try (Connection conn = Database.getConnection()) {

            conn.setAutoCommit(false);

            int removed;

            try {
                run(conn, "DELETE FROM answers WHERE question_id = ?",
                    questionId);
                run(conn, "DELETE FROM question_categories WHERE question_id = ?",
                    questionId);
                removed = run(conn,
                    "DELETE FROM questions WHERE question_id = ?", questionId);

                conn.commit();

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

            if (removed == 0) {
                WebUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND,
                                  "That question no longer exists.");
                return;
            }

            WebUtil.sendJson(response, HttpServletResponse.SC_OK,
                             "{\"deleted\":true}");

        } catch (SQLException e) {
            databaseError(response, e);
        }
    }

    // ------------------------------------------------------------------

    /** Runs a statement with one numeric parameter; returns rows changed. */
    private int run(Connection conn, String sql, long id)
            throws SQLException {

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            return stmt.executeUpdate();
        }
    }
}
