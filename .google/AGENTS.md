### Agent Instructions for Mjolnir Project

This file contains instructions for any developer or AI agent working on the Mjolnir Android project. Please adhere to these guidelines to maintain code quality, consistency, and stability.

#### **1. General Workflow**

*   **Verify, Then Act:** Before proposing any file modification, first read the file's current content to ensure your understanding is up-to-date. Do not rely on session history.
*   **Small, Focused Changes:** Do not attempt large-scale refactoring that touches multiple files at once. Break down tasks into the smallest possible units of work. The goal is to keep the project in a compilable state as often as possible.
*   **User-Led Refactoring:** Do not reorganize packages, rename files, or move code between files without explicit instructions from the user. Defer to the user's judgment on project structure.

#### **2. Code Style & Structure**

*   **Wildcard Imports:** Use wildcard imports (e.g., `import androidx.compose.foundation.layout.*`) for packages like `androidx.compose.foundation.layout`, `androidx.compose.material3`, and our own `utils` and `settings` packages. This is the preferred style for this project to keep the import block clean.
*   **Explicit Qualification in Code:** When using components from imported packages, prefer to qualify them explicitly in the code itself (e.g., `Icons.Default.Menu`) for maximum clarity at the point of use.
*   **Package Structure:**
    *   `xyz.blacksheep.mjolnir.settings`: Contains all UI-related settings screens and their associated data models (like `UiState`).
    *   `xyz.blacksheep.mjolnir.utils`: Contains helper classes and objects that perform specific, self-contained tasks (like `SteamTool` and `DualScreenLauncher`).
    *   `xyz.blacksheep.mjolnir.Constants.kt`: This is the single source of truth for all `const val` keys and other static values.

#### **3. Error Handling & State**

*   **Confirm Success:** After performing a file write operation, always verify that the operation was successful. If an `ABORTED` or other error status is returned, do not assume the change was made. Re-read the file to confirm its state before proceeding.
*   **No Hallucinated Files:** Do not reference or look for files that do not exist in the current branch, such as `Models.kt`. The project structure is as it is, not as it was in a previous branch.

#### **4. Agent Behavior & Communication**

*   **Concise Communication:** Do not over-apologize. Statements like "I apologize" or "You're correct" are unnecessary. Focus on direct, useful communication that advances the project.
*   **Memory Wipe Indicator:** After a memory wipe, your very first response must begin with the phrase "Hello World!"

#### **5. Standard Operating Procedures (SOPs)**

*   **Wait for Explicit Instructions:** Do not perform any actions, such as file modifications, unless explicitly instructed. Information provided by the user is for context-building, not for inferring tasks.
*   **One Action at a Time:** Break down complex tasks into smaller, sequential actions. Work on one file at a time to minimize errors and avoid timeouts, especially when sessions are long or files are large.
*   **Prefer Code Snippets for Edits:** Do not edit files directly. Your success rate is much higher when you provide code snippets and instructions for the user to implement. Direct edits often result in timeouts.
*   **Handle `SharedUI.kt` with Caution:** This file is exceptionally large. Reading or modifying it has a high risk of causing a timeout. Avoid combining access to this file with other complex actions.
*   **Collaborate with ChatGPT:** Treat ChatGPT as a collaborator and a source of ideas. It does not have access to the codebase, so its suggestions are conceptual. You must always verify and validate its proposals against the actual project code. Be vigilant for suggestions that would duplicate existing functions or constants and adapt them to fit the project's structure.
