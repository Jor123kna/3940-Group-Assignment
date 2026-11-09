# Group Assignment 1 - Multimedia Trivia WebApp

## Tutorial

**Requirements:** any IDE, Tomcat, ability to git clone the repo, Docker Desktop, Java 17

1. Pull the repository and save it to an easily accessible folder on your computer.
2. Open the project folder in any IDE.
3. Open a new terminal and navigate to the project's root directory.
4. Open Docker Desktop and make sure it is running.
5. Build the Docker image using the project's Dockerfile

```
docker build -t trivia-app
```

6. Start the docker container.

```
docker run -d -p 8082:8080 --name trivia-app trivia-app
```

7. Open a web browser and navigate to http://localhost:8082/index.html
8. The user is taken to the landing page.

## Solo Mode

9. Click "Solo Quiz".
10. Choose a trivia category.
11. The first of 8 questions will pop up on the page, alongside a video or image corresponding to the question. Read the question and select one of four multiple-choice answers.
12. Upon button-click, the user is told if they answered correctly or not. They navigate to the next question throught the "Next" button on the bottom-right of the page. They can return to questions by clicking on the "Back" button on the bottom-left of the page but cannot retry the question.
13. Keep answering questions until the quiz finishes. Upon completion, the user is given their score in that quiz category and is provided the option to play again. By clicking "Play Again", they are taken to the same quiz, or given the list of categories to choose another.
14. At any point during the quiz, the user is able to switch categories, wherein a new quiz of eight questions is launched. The user is also able to click the "Home" arrow at the top-left, which takes them back to the landing page.

## Multiplayer Mode

15. Click "Multiplayer Quiz"
16. Enter a display name and save it.
17. An admin must create a group in order for the user to join it. Login as an admin, who will be taken to a new landing page that has an "Admin" button in the top-left corner. Clicking that button takes the admin to the Admin page, where they can add/remove/manage categories. They can also add/remove/manage questions. At the bottom, there is a "Create Admin Account" option allowing existing admins to create more admin accounts with users and passwords.
18. When the admin clicks on "Multiplayer" mode, they are prompted with the option to create a new group with a group name. They must click "Create group". Admins have the ability to close a group by clicking "Close group". They can also view how many members have joined their group.
19. A guest playing the game is granted the ability to join this new group. Once they join it, they must wait for the admin to provide a question live as a moderator of the quiz.
20. Admins can pick a category and use the "Next" and "Back buttons to browse through questions to project to the players in their group. They must click "Ask Group" for the question to appear on the player's screen. They have the option to repeat the question with the "Ask again" button.
21. The user answers the question and are granted a point for choosing correctly. Points are accumulated and displayed on a Leaderboard visible to both the players and admin.
22. The quiz ends once the admin closes the group.

## Contributors

Thank you to everyone in Set G for making this project possible.

##### Project Manager

- Jorja Knaus

##### DevOps Team

- Nicholas Cayla
- Adam Sehboub
- Sebastian Roby

##### System Analysis Team

- Jessie Yuen
  - ERD Design
  - GUI design
  - User stories
  - Trivia question-and-answer bank
  - README

- Grace Jung
  - ERD Design
  - Functional requirements List
  - GUI design
  - User stories
  - README

##### Development Team

- Hazel Fulljames
  - Lobby/trivia page functionality
  - WebSocket implementation
- Jack Moscovitch
  - Front-end/back-end integration
  - Tomcat integration
  - Trivia user page design
- Mahyar Golestanpour
  - Front-end/back-end integration
  - Tomcat integration
  - Index page design
  - Lobby page design
  - Leaderboard page
- Jemsel Jumapit
  - Front-end/back-end integreation
  - Tomcat integration
  - Index page design
  - Staff moderator mode
  - Leaderboard page
- Ziad Malik
  - Front-end/back-end integration
  - Tomcat integration
  - Index/login/admin/trivia admin/quiz creation/solo trivia page design
  - Quiz creation page functionality
  - Solo trivia page functionality
- Arjun Brahmbhatt
- Joseph Louwerse
  - Database implementation
  - Supabase setup and connectivity
  - User/question data
  - Database documentation
- Shivika Kapoor
  - Database implementation
  - Senior moderator mode
  - Database corrections and question data
- Evan Tang
  - Non-functional requirements list
  - Lobby page design
  - Code cleanup

##### Testing and QA

- Jeevan Jandu
  - Quiz creation/editing/removal test cases
  - Multiplayer Testing
  - Leaderboard Testing
  - Database Testing
- Vicente Goncalves
  - Individual quiz session tests
  - Quiz API route tests
  - New user tests
- Umanga Bajgai
  - Index/login/trivia admin/lobby/trivia user page functionality
  - Code cleanup
- Yehor Skudilov
  - Tomcat connection tests
  - SQL injection/sercurity testing for login and signup
  - Login tests

## Functional Requirements

- Home page (index) opens to trivia categories page
  - Therefore you dont NEED to be logged in.
  - Allow guests to plays games(if we want game levels to the app dont store them)
  - Add restrictions to creating a new quiz and hosting a session.
  - You do not need an account to join a session but they will need to enter dummy name
- Ability to make accounts for the authenticated users
  - Again guests can play
- Clicking a category will take the user to the categories trivia page
  - We need to show all games on first load
  - Categories need to be sorted
- Individual Mode
  - Games that (don't create a session)
- Authenticated users can upload new questions
  - Restriction on uploading/editing quizzes and questions
- Newly uploaded questions must have tags for categories
  - New quizzes must choose AT LEAST 1 category
- The question will save with user uploaded the question
- Authorized users can delete questions
  - Restriction on deleting questions, adding questions, no edit
- Group Trivia Mode, Moderated/Kahoot Mode, Kahoot-like quiz
- Accounts for authorized users and moderators
- Use of AJAX to prefetch questions
- Tomcat/Servlet to develop the backend
- Each trivia question shall auto-play an audio/video clip or display a picture or display a document upon which the trivia question is based. One of the four (or two) possible answers being displayed shall be the correct one. The app shall confirm if the pressed answer is correct or incorrect.
- The trivial question along with the answers and link to the youtube video shall be uploaded as an XML string in one of the columns.
- The identity of the user which uploaded the content along with the date the content was uploaded shall be recorded.

## Non-functional Requirements

- Gui Design
- Maintainability: ease of use, deletion/addition of questions
- Authentication & Authorization: access to staff functions.
- Media streaming/uploading: youtube videos or uploaded videos instead of just images
- Security for accounts: password hashing, validation, etc
- Device accessibility: Make available/compatible on tablets, phones, computers.

## User Stories

End Users: Seniors in homes, Children in classrooms, the Authorized Users/Moderators making questions on the site itself

- As a senior in a care home, I want a simple and intuitive UI so that I can navigate the trivia section without worry.

- As a nurse in a care home with my hands full taking care of multiple duties, I want a simple and intuitive UI so that I can host trivia night without worry.

- As a Moderator I want to be able to create new trivia questions and set them to categories quickly and conveniently.

- As a person who loves trivia games, I want to test my knowledge in a quiz with multiple-choice style questions.

- As a young child, I want to play a simple electronic game to compete with my friends.

- As a senior wanting to stay healthy, I want to sharpen my cognition by testing my general knowledge.

- As a parent of a fifth-grade student, I want my child to have access to a game that can advance their learning ahead of their peers.

## App Features

- User Authentication
  - Users can sign up and login in the application
- Home Page
  - Displays different categories for the user to choose from
- Trivia Categories
  - Organized by genre
- Solo Trivia Mode
  - Player plays alone
- Multiplayer Trivia Mode
  - Kahoot style, participants join and compete with each other to score points
- Moderator Mode
  - A moderator adds questions with answers to the quiz
- Quiz Creation
  - A user addeds a new quiz into the quiz pool
- Quiz Management
  - Add and remove questions
- Multimedia Resources
  - An image or video that is related to the quiz question
- Answer Validation
  - Up to one answer is correct out of 4 multiple choice
- Game Lobby
- Leaderboard
  - Displays which users have the most points
- Admin Dashboard
- Real-Time Updates
- Database Integration

## Tools/API:

##### Frontend

- HTML
- CSS
- JavaScript
- AJAX / Fetch API

##### Backend

- Java
- Apache Tomcat
- Jakarta Servlets
- JDBC

##### Database

- Supabase
- PostgreSQL

##### Real-Time Communication

- WebSockets

##### Development & Collaboration

- Git / GitHub
- Figma
- Google Docs
