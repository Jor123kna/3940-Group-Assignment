# Group Assignment 1 - Multimedia Trivia WebApp

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
- Umanga Bajgai
  - Index/login/trivia admin/lobby/trivia user page functionality
  - Code cleanup
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

## Tutorial

Requires an IDE, Tomcat, ability to git clone the repo, Java 17

- Login, authenticated users taken to landing page
- Choose between solo and group mode
- In group mode:
- Join a group of trivia competitors
- Moderator chooses trivia category
  - Admin can also add a category
  - If admin adds a question live, it doesn't show up live in the quiz
- Wait for the admin to broadcast a question (moderator chooses question)
- copy of solo quiz, only displayed if admin chooses to
- Points added whenever question is answered correctly
- Whatever index admin chooses -> question displayed
- Categories: art, english, geography, health, history, math, physics, science
- Landing solo or multiplayer
-
-
- need to know what happens when we delete questions live
- parsing to get to online media
-
-
