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

@WebServlet("/categories")
public class CategoriesServlet extends HttpServlet {

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        String sql =
            "SELECT category_id, category_name " +
            "FROM categories " +
            "ORDER BY category_name";

        try (
            Connection conn = Database.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();
        ) {

            StringBuilder json = new StringBuilder();

            json.append("[");

            boolean first = true;

            while (rs.next()) {

                if (!first) {
                    json.append(",");
                }

                long categoryId =
                    rs.getLong("category_id");

                String categoryName =
                    rs.getString("category_name");

                json.append("{");
                json.append("\"category_id\":");
                json.append(categoryId);
                json.append(",");
                json.append("\"category_name\":\"");
                json.append(escapeJson(categoryName));
                json.append("\"");
                json.append("}");

                first = false;
            }

            json.append("]");

            out.print(json);

        } catch (SQLException e) {

            response.setStatus(
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );

            out.print(
                "{\"error\":\"Database error\"}"
            );

            e.printStackTrace();
        }
    }


    private String escapeJson(String value) {

        if (value == null) {
            return "";
        }

        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}