---
name: unit-test
description: Check if unit test coverage is more than 90% for both backend (Java) and frontend (React). If not, write unit and integration tests until 90% is achieved.
---

# When to use

- When a developer requests to "check test coverage" or "ensure 90% test coverage".
- When a new feature has been added and you need to ensure the system maintains a high bar of test reliability (≥90%).
- When explicitly asked to write unit and integration tests to bump coverage.

# When NOT to use

- When the user just wants to run tests without checking the coverage percentage.
- When fixing a localized bug without needing a full-scale test suite expansion.
- When the codebase is not Java/Spring Boot (Backend) or Next.js/React (Frontend), as the specific commands in this workflow target Maven/JaCoCo and Vitest.

# Inputs required

No specific inputs are required, but you should execute the test commands in the `finsight-backend` and `finsight-frontend` directories.

# Workflow

1. **Check Backend Coverage (Java/Spring Boot)**
   - Change directory to `finsight-backend`.
   - Run `mvn clean test jacoco:report` to execute tests and generate the JaCoCo coverage report.
   - Read the summary from `target/site/jacoco/index.html` or `target/site/jacoco/jacoco.csv` (using `read_file` or a shell text-parser script).
   - If the total instruction/line coverage is below 90%, identify packages or classes with low coverage.

2. **Check Frontend Coverage (Next.js/React)**
   - Change directory to `finsight-frontend`.
   - Ensure the testing dependencies are available. Run `npm run test -- --coverage` (assuming Vitest is configured for coverage).
   - If Vitest is not yet configured for coverage, you may need to install `@vitest/coverage-v8` (`npm install -D @vitest/coverage-v8`) and run `npx vitest run --coverage`.
   - Analyze the output table in the terminal or `coverage/index.html` to find components or hooks below 90% coverage.

3. **Write Missing Tests**
   - For the **backend**, use Mockito and JUnit 5 to write missing unit and integration tests for low-coverage classes. Prefer `@WebMvcTest` for controllers and `@DataJpaTest` for repositories where applicable.
   - For the **frontend**, use `@testing-library/react` and `vitest` to write tests for low-coverage UI components. Mock API calls using `vi.fn()` or Mock Service Worker (MSW).

4. **Iterate and Re-verify**
   - After writing new tests, re-run the coverage commands.
   - Repeat the process until the overall test coverage exceeds 90% for both the backend and frontend.

# Examples

**User:** "Ensure the project has 90% test coverage."
**Agent:** 
1. Runs `mvn clean test jacoco:report` in `finsight-backend`.
2. Notices `SurveyAiService.java` has 60% coverage.
3. Adds test cases for missing branches in `SurveyAiServiceTest.java`.
4. Re-runs tests to confirm coverage is now > 90%.
5. Moves to `finsight-frontend` and runs `npx vitest run --coverage`.
6. Adds missing UI tests for `DashboardPage`.

# Troubleshooting

- **JaCoCo Not Found:** If the `jacoco-maven-plugin` is missing or fails, check `finsight-backend/pom.xml` to ensure it is correctly defined in the `<build><plugins>` section.
- **Vitest Coverage Fails:** If the command fails with a missing provider error, ensure you install `@vitest/coverage-v8` inside `finsight-frontend`.
- **Database Conflicts:** Integration tests might fail if they conflict with an active local database. Ensure Spring tests use an in-memory H2 database or clean SQLite setup (`@DataJpaTest` usually handles this).
