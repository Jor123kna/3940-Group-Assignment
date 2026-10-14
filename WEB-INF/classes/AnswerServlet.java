import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * POST /answer (form field: answer_id=N)
 *
 * Checks the answer a player picked and replies with JSON:
 *
 *   {"correct": true, "correct_answer_id": 3}
 *
 * correct_answer_id lets the page highlight the right choice when the
 * player picked a wrong one. Keeping this check on the server means the
 * answer key is never sent to the browser along with the questions.
 */
@WebServlet("/answer")
public class AnswerServlet extends HttpServlet {

    // All the answers that belong to the same question as the chosen one.
    private static final String SQL =
        "SELECT b.answer_id, b.correctness " +
        "FROM answers a " +
        "JOIN answers b ON b.question_id = a.question_id " +
        "WHERE a.answer_id = ?";

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        // validate the answer_id parameter
        long chosenId;

        try {
            chosenId = Long.parseLong(
                request.getParameter("answer_id").trim()
            );
        } catch (NullPointerException | NumberFormatException e) {

            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

            out.print(
                "{\"error\":\"answer_id is required " +
                "and must be a number\"}"
            );

            return;
        }

        // look the answer up
        boolean found = false;
        boolean chosenIsCorrect = false;
        long correctId = -1;

        try (
            Connection conn = Database.getConnection();
            PreparedStatement stmt = conn.prepareStatement(SQL);
        ) {

            stmt.setLong(1, chosenId);

            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {

                    found = true;

                    long id = rs.getLong("answer_id");
                    boolean isCorrect = rs.getBoolean("correctness");

                    if (isCorrect) {
                        correctId = id;
                    }

                    if (id == chosenId) {
                        chosenIsCorrect = isCorrect;
                    }
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

        if (!found) {

            response.setStatus(HttpServletResponse.SC_NOT_FOUND);

            out.print("{\"error\":\"Unknown answer\"}");

            return;
        }

        out.print(
            "{\"correct\":" + chosenIsCorrect +
            ",\"correct_answer_id\":" + correctId + "}"
        );
    }
}
