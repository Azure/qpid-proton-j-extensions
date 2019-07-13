# Contributor's Guide:<br>Extensions for Apache Proton-J library

## Code of Conduct

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/).
For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or contact
[opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.

## Getting Started

### Prerequisites

To build and test this locally, make sure you install:

- Java Development Kit (JDK) with version 8 or above.
- [Maven](https://maven.apache.org/).

### Building and packaging

Open a command prompt/terminal:
1. Execute `git clone https://github.com/Azure/azure-sdk-for-java.git`.
1. Traverse to the repository root.
1. Execute `mvn package`.

This should successfully run all unit/integration tests and build the qpid-proton-j-extensions JAR.

## Filing Issues

You can find all of the issues that have been filed in the [Issues](https://github.com/Azure/proton-extensions/issues)
section of the repository.

If you encounter any bugs, would like to request a feature, or have general
questions/concerns/comments, feel free to file an issue [here](https://github.com/Azure/proton-extensions/issues/new).

## Pull Requests

### Required Guidelines

When filing a pull request, the following must be true:

- Tests have been added (if needed) to validate changes.
- Zero CheckStyle violations.
    - `mvn checkstyle:check` has no violations.
    - `mvn compile` will fail if there are any Checkstyle violations.
- All the tests pass.
    - Execute `mvn clean test` to recompile and run the tests.

### General Guidelines

If you would like to make changes to this library, **break up the change into small, logical, testable chunks, and
organize your pull requests accordingly**. This makes for a cleaner, less error-prone development process.

If you'd like to get involved, but don't know what to work on, then please reach out to us by opening an issue.

If you're new to opening pull requests - or would like some additional guidance - the following list is a good set of
best practices!

- Title of the pull request is clear and informative
- There are a small number of commits that each have an informative message
- A description of the changes the pull request makes is included, and a reference to the bug/issue the pull request
  fixes is included, if applicable
- Pull request includes comprehensive test coverage for the included changes
