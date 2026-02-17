# Contributing to Atomic Habit

First off, thank you for considering contributing to Atomic Habit! We're thrilled you're here. This project is a labor of love, and every contribution, no matter how small, helps us build a kinder, more effective tool for everyone.

This document provides a set of guidelines for contributing to the project. These are mostly guidelines, not strict rules. Use your best judgment, and feel free to propose changes to this document in a pull request.

## Code of Conduct

This project and everyone participating in it is governed by the [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior.

## How Can I Contribute?

There are many ways to contribute, from writing code and documentation to reporting bugs and suggesting new features.

*   **🐛 Reporting Bugs**: If you find a bug, please open an issue and provide as much detail as possible, including steps to reproduce it.
*   **✨ Suggesting Enhancements**: Have an idea for a new feature or an improvement to an existing one? Open an issue to start a discussion.
*   **📝 Writing Documentation**: Great documentation is key. Help us improve the README, add tutorials, or clarify our guides.
*   **💻 Submitting Pull Requests**: Ready to write some code? We'd love to have your help. See the sections below for how to get started.

## Your First Code Contribution

Unsure where to begin? Look for issues tagged `good first issue`. These are a great way to get your feet wet.

### Development Setup

Our `README.md` has a comprehensive [Getting Started](README.md#🚀-getting-started) section. Please follow it to set up your local development environment. The recommended approach is to use Docker Compose, as it provides the most consistent experience.

### Pull Request Process

1.  **Fork the repository** and create your branch from `main`.
2.  **Make your changes** in a separate git branch and write descriptive commit messages.
3.  **Run all quality checks** before submitting your pull request.

    ```bash
    # Run backend tests
    mvn -f backend/pom.xml clean test

    # Run frontend checks
    npm --prefix frontend install
    npm --prefix frontend run lint
    npm --prefix frontend run build
    ```

4.  **Open a Pull Request** to the `main` branch. Provide a clear description of the problem and solution. Include screenshots for any UI changes. Link to any relevant issues.
5.  **Wait for review.** One of the project maintainers will review your PR, provide feedback, and merge it once it's ready.

### Coding Style

*   **Follow existing conventions**: We use `.editorconfig` to maintain consistent coding styles. Please ensure your editor is configured to use it.
*   **Write meaningful commit messages**: Follow the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) specification.
*   **Keep it focused**: Avoid mixing unrelated changes in a single pull request. Create separate PRs for separate features or fixes.

Thank you for your contribution!
