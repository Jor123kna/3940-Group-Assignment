
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.eq;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AdminServletTest {

    private AdminServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private MockedStatic<WebUtil> web;
    private MockedStatic<Database> db;

    @BeforeEach
    void setUp() throws Exception {
        servlet = new AdminServlet();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(response.getWriter())
            .thenReturn(new PrintWriter(new StringWriter()));

        // real WebUtil methods run, except the ones we stub below
        web = mockStatic(WebUtil.class, CALLS_REAL_METHODS);

        // any DB access in these tests is a failure
        db = mockStatic(Database.class);

        loginAs(true, true);
    }

    @AfterEach
    void tearDown() {
        web.close();
        db.close();
    }

    // ---------- helpers ----------

    private void loginAs(boolean loggedIn, boolean staff) {
    web.when(() -> WebUtil.isLoggedIn(request)).thenReturn(loggedIn);
    web.when(() -> WebUtil.isStaff(request)).thenReturn(staff);
    web.when(() -> WebUtil.requireAjax(request, response)).thenReturn(true);
    web.clearInvocations();
}

    private void path(String p) {
        when(request.getPathInfo()).thenReturn(p);
    }

    private void param(String name, String value) {
        when(request.getParameter(name)).thenReturn(value);
    }

    private void expectError(int status) {
        web.verify(() -> WebUtil.sendError(eq(response), eq(status), anyString()));
        db.verifyNoInteractions();
    }

    // ---------- access control ----------

    @Test // BE-ADM-001
    void loggedOutGets401() throws Exception {
        loginAs(false, false);
        path("/categories");
        servlet.doGet(request, response);
        expectError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test // BE-ADM-002
    void nonStaffGets403() throws Exception {
        loginAs(true, false);
        path("/categories");
        servlet.doGet(request, response);
        expectError(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test // BE-ADM-004
    void postWithoutAjaxHeaderIsRejected() throws Exception {
        web.when(() -> WebUtil.requireAjax(request, response)).thenReturn(false);
        path("/category-add");
        servlet.doPost(request, response);
        db.verifyNoInteractions();
    }

    @Test // BE-ADM-005
    void unknownGetPathGets404() throws Exception {
        path("/nothing");
        servlet.doGet(request, response);
        expectError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test // BE-ADM-006
    void unknownPostPathGets404() throws Exception {
        path("/nothing");
        servlet.doPost(request, response);
        expectError(HttpServletResponse.SC_NOT_FOUND);
    }

    // ---------- category validation ----------

    @Test // BE-ADM-012
    void blankCategoryNameGets400() throws Exception {
        path("/category-add");
        param("name", "   ");
        servlet.doPost(request, response);
        expectError(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test // BE-ADM-013
    void categoryNameOver50CharsGets400() throws Exception {
        path("/category-add");
        param("name", "a".repeat(51));
        servlet.doPost(request, response);
        expectError(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test // BE-ADM-014
    void categoryDescriptionOver200CharsGets400() throws Exception {
        path("/category-add");
        param("name", "Science");
        param("description", "a".repeat(201));
        servlet.doPost(request, response);
        expectError(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test // BE-ADM-019
    void deleteCategoryWithBadIdGets400() throws Exception {
        path("/category-delete");
        param("category_id", "abc");
        servlet.doPost(request, response);
        expectError(HttpServletResponse.SC_BAD_REQUEST);
    }

    // ---------- question validation ----------

    private void validQuestion() {
        path("/question-add");
        param("category_id", "1");
        param("question_text", "What is 2+2?");
        param("correct", "0");
        when(request.getParameterValues("answer"))
            .thenReturn(new String[] {"4", "5", "", ""});
    }

    @Test // BE-ADM-030
    void listQuestionsWithoutCategoryGets400() throws Exception {
        path("/questions");
        servlet.doGet(request, response);
        expectError(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test // BE-ADM-042
    void onlyOneAnswerGets400() throws Exception {
        validQuestion();
        when(request.getParameterValues("answer"))
            .thenReturn(new String[] {"4", "", "", ""});
        servlet.doPost(request, response);
        expectError(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test // BE-ADM-043
    void blankQuestionGets400() throws Exception {
        validQuestion();
        param("question_text", "  ");
        servlet.doPost(request, response);
        expectError(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test // BE-ADM-044
    void questionOver500CharsGets400() throws Exception {
        validQuestion();
        param("question_text", "a".repeat(501));
        servlet.doPost(request, response);
        expectError(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test // BE-ADM-045
    void answerOver150CharsGets400() throws Exception {
        validQuestion();
        when(request.getParameterValues("answer"))
            .thenReturn(new String[] {"a".repeat(151), "5", "", ""});
        servlet.doPost(request, response);
        expectError(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test // BE-ADM-046
    void correctAnswerBlankGets400() throws Exception {
        validQuestion();
        param("correct", "3"); // box 3 is empty
        servlet.doPost(request, response);
        expectError(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test // BE-ADM-048
    void correctIndexNotANumberGets400() throws Exception {
        validQuestion();
        param("correct", "abc");
        servlet.doPost(request, response);
        expectError(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test // BE-ADM-049
    void missingAnswersGets400() throws Exception {
        validQuestion();
        when(request.getParameterValues("answer")).thenReturn(null);
        servlet.doPost(request, response);
        expectError(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test // BE-ADM-055
    void deleteQuestionWithBadIdGets400() throws Exception {
        path("/question-delete");
        param("question_id", "xyz");
        servlet.doPost(request, response);
        expectError(HttpServletResponse.SC_BAD_REQUEST);
    }
}