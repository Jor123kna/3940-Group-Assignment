import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DatabaseTest {

    private Connection conn;

    @BeforeEach
    void open() throws SQLException {
        conn = Database.getConnection();
        conn.setAutoCommit(false);
    }

    @AfterEach
    void close() throws SQLException {
        conn.rollback();
        conn.close();
    }

    private long count(String sql, Object... params) throws SQLException {
        try (PreparedStatement s = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                s.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = s.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long insertCategory(String name, String desc) throws SQLException {
        try (PreparedStatement s = conn.prepareStatement(
                "INSERT INTO categories (category_name, description) " +
                "VALUES (?, ?) RETURNING category_id")) {
            s.setString(1, name);
            s.setString(2, desc);
            try (ResultSet rs = s.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test //
    void connectionIsValid() throws SQLException {
        assertTrue(conn.isValid(5));
    }

    @Test // -010
    void requiredTablesExist() throws SQLException {
        String[] tables = {"users", "categories", "questions",
                           "answers", "question_categories"};
        for (String t : tables) {
            assertEquals(1, count(
                "SELECT count(*) FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_name = ?", t),
                "missing table: " + t);
        }
    }

    @Test
    void requiredColumnsExist() throws SQLException {
        String[][] cols = {
            {"users", "user_id"}, {"users", "user_display_name"},
            {"users", "password"}, {"users", "play_mode"},
            {"categories", "category_id"}, {"categories", "category_name"},
            {"categories", "description"},
            {"questions", "question_id"}, {"questions", "trivia_xml"},
            {"questions", "num_answer_choices"},
            {"answers", "answer_id"}, {"answers", "answer_name"},
            {"answers", "question_id"}, {"answers", "correctness"},
            {"question_categories", "question_id"},
            {"question_categories", "category_id"}
        };
        for (String[] c : cols) {
            assertEquals(1, count(
                "SELECT count(*) FROM information_schema.columns " +
                "WHERE table_schema = 'public' " +
                "AND table_name = ? AND column_name = ?", c[0], c[1]),
                "missing column: " + c[0] + "." + c[1]);
        }
    }

    @Test
    void insertedCategoryCanBeReaack() throws SQLException {
        long id = insertCategory("QA test category", "temp");
        assertEquals(1, count(
            "SELECT count(*) FROM categories WHERE category_id = ?", id));
    }

    @Test
    void categoryDescriptionMayBeNull() throws SQLException {
        assertDoesNotThrow(() -> insertCategory("QA null desc", null));
    }

    @Test
    void categoryNamesDifferingByCaseAreBlockeyDatabase() throws SQLException {
        insertCategory("QA Dupe", null);
        assertThrows(SQLException.class,
            () -> insertCategory("qa dupe", null),
            "database allows case-variant duplicate category names");
    }

    @Test
    void answerForMissingQuestionIsRejected() {
        assertThrows(SQLException.class, () -> {
            try (PreparedStatement s = conn.prepareStatement(
                    "INSERT INTO answers (answer_name, question_id, correctness) " +
                    "VALUES ('x', -1, false)")) {
                s.executeUpdate();
            }
        });
    }

    @Test
    void questionCategoryLinkToMissingRowsIsRejected() {
        assertThrows(SQLException.class, () -> {
            try (PreparedStatement s = conn.prepareStatement(
                    "INSERT INTO question_categories (question_id, category_id) " +
                    "VALUES (-1, -1)")) {
                s.executeUpdate();
            }
        });
    }

    @Test
    void triviaXmlAcceptsXmlText() throws SQLException {
        long userId = count("SELECT user_id FROM users LIMIT 1");
        assumeTrue(userId > 0, "needs at least one user row");

        try (PreparedStatement s = conn.prepareStatement(
                "INSERT INTO questions (uploaded_by, media_type, " +
                "num_answer_choices, trivia_xml, date_uploaded) " +
                "VALUES (?, 'text', 2, ?, now())")) {
            s.setLong(1, userId);
            s.setObject(2, "<trivia><question>Q?</question></trivia>",
                        Types.OTHER);
            assertEquals(1, s.executeUpdate());
        }
    }

    @Test
    void everyQuestionHasExactlyOneCorrectAnswer() throws SQLException {
        assertEquals(0, count(
            "SELECT count(*) FROM questions q WHERE " +
            "(SELECT count(*) FROM answers a " +
            " WHERE a.question_id = q.question_id AND a.correctness) <> 1"),
            "questions with 0 or 2+ correct answers");
    }

    @Test
    void noOrphanRows() throws SQLException {
        assertEquals(0, count(
            "SELECT count(*) FROM answers a WHERE NOT EXISTS " +
            "(SELECT 1 FROM questions q WHERE q.question_id = a.question_id)"),
            "answers without a question");
        assertEquals(0, count(
            "SELECT count(*) FROM question_categories qc WHERE " +
            "NOT EXISTS (SELECT 1 FROM questions q " +
            "            WHERE q.question_id = qc.question_id) " +
            "OR NOT EXISTS (SELECT 1 FROM categories c " +
            "               WHERE c.category_id = qc.category_id)"),
            "question_categories pointing at missing rows");
    }

    @Test
    void everyQuestionBelongsToACategory() throws SQLException {
        assertEquals(0, count(
            "SELECT count(*) FROM questions q WHERE NOT EXISTS " +
            "(SELECT 1 FROM question_categories qc " +
            " WHERE qc.question_id = q.question_id)"));
    }

    @Test
    void answerCountMatchesNumAnswerChoices() throws SQLException {
        assertEquals(0, count(
            "SELECT count(*) FROM questions q WHERE q.num_answer_choices <> " +
            "(SELECT count(*) FROM answers a " +
            " WHERE a.question_id = q.question_id)"));
    }

    @Test
    void everyTriviaXmlIsReadable() throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT question_id, trivia_xml FROM questions")) {
            while (rs.next()) {
                assertNotNull(
                    WebUtil.questionText(rs.getString("trivia_xml")),
                    "unreadable trivia_xml, question_id=" +
                    rs.getLong("question_id"));
            }
        }
    }

    @Test
    void displayNamesAreUnique() throws SQLException {
        assertEquals(0, count(
            "SELECT count(*) FROM (SELECT user_display_name FROM users " +
            "GROUP BY user_display_name HAVING count(*) > 1) d"));
    }

    @Test
    void noPlainTextPasswords() throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT user_id, password FROM users")) {
            while (rs.next()) {
                assertTrue(Passwords.isHashed(rs.getString("password")),
                    "plain-text password, user_id=" + rs.getLong("user_id"));
            }
        }
    }
}
