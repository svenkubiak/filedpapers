[![Latest](https://img.shields.io/github/v/tag/svenkubiak/filedpapers?label=ghcr.io&sort=semver)](https://ghcr.io/svenkubiak/filedpapers/filedpapers)
[![iOS App](https://img.shields.io/badge/iOS-App_Store-blue?logo=apple)](https://apps.apple.com/de/app/filed-papers/id6740149712)
[![Chrome Web Store](https://img.shields.io/badge/Chrome-Extension-blue?logo=google-chrome)](https://chromewebstore.google.com/detail/filed-papers/dncigabekkedeannldbaggakellmkpmm)
[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20A%20Coffee-%F0%9F%8D%BA-yellow)](https://buymeacoffee.com/svenkubiak)

Filed Papers
================

Filed Papers is built for those who value privacy and control. Unlike traditional bookmark managers, it requires a self-hosted backend, ensuring your data remains exclusively in your hands—secure, private, and free from third-party access.

This repository includes the backend server, which powers the web interface, the iOS app, and the Google Chrome extension, providing a seamless, integrated experience.

Key Features:

- Organized Bookmarking: Save your bookmarks into custom categories for efficient management and easy retrieval.
- Customizable Categories: Create and tailor categories to suit your personal or professional needs.
- Self-Hosted Backend: You are in full control of your data. By hosting the backend yourself, you ensure that your bookmarks remain private and inaccessible to others.
- Use the Web-Interface along with the additional iOS App and Google Chrome extension

Why Choose Filed Papers?

- Complete Privacy: With no reliance on third-party servers, your data stays completely under your control.
- Focused Simplicity: A clean, intuitive interface designed to make managing bookmarks straightforward and effective.
- Perfect for Professionals and Privacy-Conscious Users: Whether for research, work, or personal use, Filed Papers offers the ideal balance of organization and security. 

## Available languages

- German
- English

## Resources

**Homepage**   
[https://svenkubiak.de/apps/ios/filed-papers](https://svenkubiak.de/apps/ios/filed-papers)

**Buy me a Coffee**   
[https://buymeacoffee.com/svenkubiak](https://buymeacoffee.com/svenkubiak)

**Changelog**   
[https://github.com/svenkubiak/filedpapers/releases](https://github.com/svenkubiak/filedpapers/releases)   
Versions up to 6.0.0: [https://github.com/svenkubiak/filedpapers/wiki/Changelog](https://github.com/svenkubiak/filedpapers/wiki/Changelog)

**Migrations**   
[https://github.com/svenkubiak/filedpapers/wiki/Migrations](https://github.com/svenkubiak/filedpapers/wiki/Migrations)

**Support**   
[https://github.com/svenkubiak/filedpapers/issues](https://github.com/svenkubiak/filedpapers/issues)

**iOS App**  
[https://apps.apple.com/de/app/filed-papers/id6740149712](https://apps.apple.com/de/app/filed-papers/id6740149712)

**Google Chrome Extension**  
[https://chromewebstore.google.com/detail/filed-papers/dncigabekkedeannldbaggakellmkpmm](https://chromewebstore.google.com/detail/filed-papers/dncigabekkedeannldbaggakellmkpmm)

# Installation Guide

### Prerequisites

Before starting the installation process, make sure you have the following prerequisites:

- **Docker**: Ensure Docker is installed and running on your system
- **Docker Compose**: Make sure Docker Compose is installed to manage multi-container applications
- **Web Frontend Server**: A frontend HTTP server (e.g. Nginx) to handle SSL termination and proxy requests to the backend.
- **SMTP Server**: A SMTP server for sending emails like registration confirmation, or password reset, etc.

## Installation

1. **Create the directory for your server installation:**

   First, create a folder where you want to install your server. For this example, we will use the folder name `filedpapers`.

   ```shell
   mkdir filedpapers
   cd filedpapers
   ```
2. **Download and execute the installation script:**

   ```shell
   curl -sSL https://raw.githubusercontent.com/svenkubiak/filedpapers/refs/heads/main/install.sh | bash
   ```
   
3. **After installation is complete open the .env file and set your custom configuration:**

   ```
   APPLICATION_URL=http://localhost
   ALLOW_REGISTRATION=true
   SMTP_HOST=localhost
   SMTP_PORT=25
   SMTP_AUTHENTICATION=true
   SMTP_USERNAME=username
   SMTP_PASSWORD=password
   SMTP_FROM=email@localhost
   SMTP_PROTOCOL=smtptls
   SMTP_DEBUG=false
   ```

4. **Start the containers**

   ```shell
   docker compose up -d
   ```

By default, the container with the application runs at 127.0.0.1 on port 9090. Adopt this as required in the compose.yaml file.

# Contributing

Contributions are welcome! This project follows the [Conventional Commits](https://www.conventionalcommits.org/) specification. The release notes are generated automatically from the commit history, so every commit message needs to follow this format:

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

### Types

| Type       | Use for                                                     | Release notes section |
|------------|-------------------------------------------------------------|-----------------------|
| `feat`     | A new feature                                               | Features              |
| `fix`      | A bug fix                                                   | Bug Fixes             |
| `perf`     | A performance improvement                                   | Performance           |
| `refactor` | Code changes that neither fix a bug nor add a feature       | Refactoring           |
| `docs`     | Documentation only                                          | Documentation         |
| `chore`    | Maintenance, e.g. dependency updates (`chore(deps): ...`)   | Maintenance           |
| `test`     | Adding or changing tests                                    | –                     |
| `build`    | Build system changes                                        | –                     |
| `ci`       | CI configuration                                            | –                     |

The scope is optional and names the affected area, e.g. `api`, `ui` or `metascraper`. Write the description in the imperative mood ("add", not "added").

### Breaking changes

Mark breaking changes with a `!` after the type or scope, or add a `BREAKING CHANGE:` footer. They are listed in a separate section of the release notes. If a change requires manual steps, also document them in the [Migrations](https://github.com/svenkubiak/filedpapers/wiki/Migrations) wiki page.

### Examples

```
feat(ui): add bulk move for bookmarks
fix(metascraper): handle pages without og:image
chore(deps): bump mangooio to 10.15.0
feat(api)!: remove deprecated token endpoint
```

### Releases

Releases are built and published by the maintainer with `release.sh`. Pushing the version tag triggers a GitHub Action that creates the GitHub release, with notes generated from the commits since the previous release. Commits that do not follow the format above do not appear in the release notes.
