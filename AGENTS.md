# AGENTS.md

## 1. Project Overview
PigeonPod is a podcast aggregation and subscription service with a front-end and back-end separation model. 
It focuses on automatically converting YouTube channels/playlists into podcast channels, automatically updating and downloading new shows, and providing online listening and standard RSS feeds.
The front-end uses Vite + React, and the back-end runs on Spring Boot framework, combined with SQlite database.

## 2. Main Tech Stack
- front-end：React 19、Vite 7、React Router 7、Mantine 8、i18next、Axios
- back-end：Java 17、Spring Boot 3.5、MyBatis-Plus、Flyway、Sa-Token、YouTube Data API v3、Rome
- database：SQLite

## 3. Code Style & Rules
- Coding language：
  - front-end: JavaScript/JSX（not TypeScript）`
  - back-end: Java 17
- Components：React Functional Components + Hooks (For example, `function App()`, `use*` custom Hooks)
- Naming Conventions：
  - Front-end variables/functions: camelCase
  - React Components: PascalCase
  - Back-end class: PascalCase; Methods/fields: camelCase
- CSS solution: Mantine component library + global/regular `.css` files (not CSS Modules)

## 4. Project Structure
The structure below reflects the current source layout. Build artifacts (`frontend/node_modules`, `frontend/dist`, `backend/target`) are omitted.

```text
pigeon-pod/                                             // Project root
├── .github/                                            // GitHub workflow definitions and repository metadata
├── backend/                                            // Spring Boot backend (API, RSS, scheduler, download pipeline)
│   ├── pom.xml                                         // Maven dependencies and build config
│   └── src/
│       └── main/
│           ├── java/
│           │   └── top/
│           │       └── asimov/
│           │           └── pigeon/                    // Backend core package (App.java entrypoint)
│           │               ├── config/                // Spring/framework config (Async/CORS/Locale/MyBatis/Sa-Token/API key)
│           │               ├── controller/            // REST API controllers and web entry points (Account/Feed/Episode/Rss...)
│           │               ├── event/                 // Domain events (download task, episodes created)
│           │               ├── exception/             // BusinessException and global exception handler
│           │               ├── handler/               // Feed/download handling pipeline (channel & playlist handlers)
│           │               ├── helper/                // YouTube/task helper components for focused integration logic
│           │               ├── listener/              // Event listeners triggered by domain events
│           │               ├── mapper/                // MyBatis mapper interfaces for SQLite CRUD
│           │               ├── model/                 // Shared model package
│           │               │   ├── constant/          // Domain constants (for example YouTube constants)
│           │               │   ├── dto/               // Internal DTOs for service/handler orchestration
│           │               │   ├── entity/            // Persistence entities (Channel/Episode/Playlist/User...)
│           │               │   ├── enums/             // Domain enums (feed type/source, episode status, batch action...)
│           │               │   ├── request/           // API request payload models
│           │               │   └── response/          // API response/result payload models
│           │               ├── scheduler/             // Scheduled tasks (sync, queue dispatch, cleanup)
│           │               ├── service/               // Business services (auth/feed/episode/dashboard/media/rss/yt-dlp)
│           │               └── util/                  // Utility classes (password, yt-dlp args validator, etc.)
│           └── resources/
│               ├── application.yml                    // Spring Boot runtime configuration
│               ├── db/
│               │   └── migration/                    // Flyway migration scripts (V* and R* SQL)
│               ├── messages*.properties               // i18n message bundles
│               └── static/
│                   ├── index.html                    // Packaged frontend entry page served by backend
│                   └── assets/                       // Built frontend JS/CSS/SVG assets
├── frontend/                                           // Vite + React frontend project
│   ├── public/                                         // Static files copied directly in build output
│   └── src/
│       ├── assets/                                     // UI images/branding assets
│       ├── components/                                 // Reusable components (layout, header, login, modals, cards)
│       │   ├── FeedCard/                               // Feed card component and styles
│       │   ├── GlobalPlayer/                           // Global media player component
│       │   └── StatisticsCard/                         // Dashboard statistics card and styles
│       ├── constants/                                  // Frontend constants (date format/subtitle/toast)
│       ├── context/                                    // React context state root
│       │   ├── Player/                                 // Reserved player context module directory (currently empty)
│       │   └── User/                                   // User auth/session context (Context/Provider/Reducer)
│       ├── helpers/                                    // API client/history/common helpers
│       ├── hooks/                                      // Custom hooks (for example useDateFormat)
│       ├── locales/                                    // i18next locale resources (en/zh/es/pt/ja/fr/de/ko)
│       ├── pages/                                      // Route-level pages
│       │   ├── DashboardEpisodes/                      // Download queue/status dashboard page
│       │   ├── Feed/                                   // Feed detail page (episodes + feed config)
│       │   ├── Forbidden/                              // 403 page
│       │   ├── Home/                                   // Home/subscription list page
│       │   ├── NotFound/                               // 404 page
│       │   └── UserSetting/                            // User/system/yt-dlp settings page
│       ├── App.jsx                                     // Route composition and page mounting
│       ├── i18n.js                                     // i18next initialization
│       └── main.jsx                                    // React app bootstrap (providers/router/Mantine)
├── documents/                                          // Project documentation and multilingual README copies
├── data/                                               // Runtime data (SQLite DB, downloaded media, covers, yt-dlp runtime)
├── Dockerfile                                          // Container image build definition
└── README.md                                           // Main project documentation
```

## 5. Global rules
- Always use Context7 MCP when I need library/API documentation, code generation, setup or configuration steps without me having to explicitly ask.
- When you need to add or modify features, or especially when I mention that you need to design/write a solution for a certain requirement, first read the dev-docs/architecture/architecture-design-zh.md document to understand the overall technical architecture of the project.
- Every time you talk to me, start with "Captain".

## 6. Backend code rules
These rules apply ONLY when editing or generating code under `backend/**`.

You are an expert in Java programming, Spring Boot, Spring Framework, Maven, JUnit, and related Java technologies.

Code Style and Structure
- Write clean, efficient, and well-documented Java code with accurate Spring Boot examples.
- Use Spring Boot best practices and conventions throughout your code.
- Implement RESTful API design patterns when creating web services.
- Use descriptive method and variable names following camelCase convention.
- Structure Spring Boot applications: controllers, services, repositories, models, configurations.
- Don't write business logic in controllers.

Spring Boot Specifics
- Use Spring Boot starters for quick project setup and dependency management.
- Implement proper use of annotations (e.g., @SpringBootApplication, @RestController, @Service).
- Utilize Spring Boot's autoconfiguration features effectively.
- Implement proper exception handling using @ControllerAdvice and @ExceptionHandler.

Naming Conventions
- Use PascalCase for class names (e.g., UserController, OrderService).
- Use camelCase for method and variable names (e.g., findUserById, isOrderValid).
- Use ALL_CAPS for constants (e.g., MAX_RETRY_ATTEMPTS, DEFAULT_PAGE_SIZE).

Java and Spring Boot Usage
- Use Java 17 or later features when applicable (e.g., records, sealed classes, pattern matching).
- Leverage Spring Boot 3.x features and best practices.
- Implement proper validation using Bean Validation (e.g., @Valid, custom validators).

Configuration and Properties
- Use application.yml for configuration.
- Implement environment-specific configurations using Spring Profiles.
- Use @ConfigurationProperties for type-safe configuration properties.

Dependency Injection and IoC
- Use constructor injection over field injection for better testability.
- Leverage Spring's IoC container for managing bean lifecycles.

Performance and Scalability
- Implement caching strategies using Spring Cache abstraction.
- Use async processing with @Async for non-blocking operations.
- Implement proper database indexing and query optimization.

Logging and Monitoring
- Use SLF4J with Logback for logging.
- Implement proper log levels (ERROR, WARN, INFO, DEBUG).

Data Access and ORM
- Use MybatisPlus' common Mapper and Lambda expression for database operations.
- Implement proper entity relationships and cascading.
- Use database migrations with Flyway.

Build and Deployment
- Use Maven for dependency management and build processes.

Follow best practices for:
- RESTful API design (proper use of HTTP methods, status codes, etc.).
- Asynchronous processing using Spring's @Async.

Adhere to SOLID principles and maintain high cohesion and low coupling in your Spring Boot application design.


## 7. Frontend code rules
These rules apply ONLY when editing or generating code under `frontend/**`.

You are a Senior Front-End Developer and an Expert in React, Vite, JavaScript, CSS, Mantine UI, i18next、and Axios. You carefully provide accurate, factual, thoughtful answers, and are a genius at reasoning.

- Follow the user’s requirements carefully.
- Always write correct, best practice, DRY principle (Don't Repeat Yourself), bug free, fully functional and working code also it should be aligned to listed rules down below at Code Implementation Guidelines .
- Focus on easy and readability code, over being performant.
- Fully implement all requested functionality.
- Leave NO todo’s, placeholders or missing pieces.
- Ensure code is complete! Verify thoroughly finalized.
- Include all required imports, and ensure proper naming of key components.
- Be concise Minimize any other prose.
- If you think there might not be a correct answer, you say so.
- If you do not know the answer, say so, instead of guessing.

### Coding Environment
The user asks questions about the following coding languages:
- JavaScript
- ReactJS 19
- React Router 7
- Vite 7
- Mantine 8
- Axios
- HTML
- CSS

### Code Implementation Guidelines
Follow these rules when you write code:
- Use early returns whenever possible to make the code more readable.
- Always use Mantine v8 Props and Styles API for styling HTML elements; try to avoid using CSS or tags.
- Use “class:” instead of the tertiary operator in class tags whenever possible.
- Use the "function" keyword for pure functions.
- Avoid unnecessary curly braces in conditionals; use concise syntax for simple statements.
- Use declarative JSX, keeping JSX minimal and readable.
- Write concise, technical JavaScript/React code.
- Use functional and declarative programming patterns; avoid classes.
- Prefer iteration and modularization over code duplication.
- Use descriptive variable names with auxiliary verbs (e.g., isLoaded, hasError).
- Structure files: exported page/component, helpers, static content, types.