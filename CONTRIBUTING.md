# Contributor's Guide:<br>Extensions for Apache Proton-J library

## Code of conduct

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/).
For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or contact
[opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.

## Getting started

### Prerequisites

To build and test this locally, make sure you install:

- Java Development Kit (JDK) with version 8 or above.
- [Maven](https://maven.apache.org/).

### Building and packaging

Open a command prompt/terminal:

1. Execute `git clone https://github.com/Azure/qpid-proton-j-extensions.git`.
2. Traverse to the repository root.
3. Execute `mvn install`.

This should successfully run all unit/integration tests, build the qpid-proton-j-extensions JAR, and install it to your
local Maven repository.

### Updating pom.xml dependencies

To update pom.xml dependencies, we leverage the Azure SDKs for Java's update scripts.

1. Install Python 3.
2. Clone `https://github.com/Azure/azure-sdk-for-java`
3. From the root of the `azure-sdk-for-java` repository, execute:
    * `python ./eng/versioning/update_versions.py --ut external_dependency --bt client --tf ${PATH_TO_QPID_PROTON_J_EXTENSIONS_POM.XML}`

## Filing issues

You can find the issues that have been filed in the [Issues](https://github.com/Azure/qpid-proton-j-extensions/issues)
section of the repository.

If you encounter any bugs, would like to request a feature, or have general questions/concerns/comments, feel free to
file an issue [here](https://github.com/Azure/qpid-proton-j-extensions/issues/new).

## Pull requests

### Required guidelines

When filing a pull request, it must pass our CI build.

- Tests have been added to validate changes.
- All tests pass.
- Zero CheckStyle and Spotbugs violations.
    - `mvn verify` has no violations.

### General guidelines

If you would like to make changes to this library, **break up the change into small, logical, testable chunks, and
organize your pull requests accordingly**. This makes for a cleaner, less error-prone development process.

If you'd like to get involved, but don't know what to work on, then please reach out to us by opening an issue.

If you're new to opening pull requests - or would like some additional guidance - the following list is a good set of
best practices!

- Title of the pull request is clear and informative.
- Commits are small and each have an informative message.
- A description of the changes the pull request makes is included, and a reference to the bug/issue the pull request
  fixes is included, if applicable.
- Pull request includes comprehensive test coverage for the included changes.
