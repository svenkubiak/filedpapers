[![Stable Version](https://img.shields.io/badge/version-stableVersion-brightgreen)](https://github.com/svenkubiak/filedpapers/pkgs/container/filedpapers%2Ffiledpapers?tag=latest)
[![Development Version](https://img.shields.io/badge/version-devVersion-blue)](https://github.com/svenkubiak/filedpapers/pkgs/container/filedpapers%2Ffiledpapers?tag=latest)
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

## Resources

**Homepage**   
[https://svenkubiak.de/apps/ios/filed-papers](https://svenkubiak.de/apps/ios/filed-papers)

**Buy me a Coffee**   
[https://buymeacoffee.com/svenkubiak](https://buymeacoffee.com/svenkubiak)

**Changelog**   
[https://github.com/svenkubiak/filedpapers/wiki/Changelog](https://github.com/svenkubiak/filedpapers/wiki/Changelog)

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
- **Web Frontend Server**: A frontend HTTP server (e.g., Nginx) to handle SSL termination and proxy requests to the backend.

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

Once the installation is complete, you can configure your environment in the compose.yml and .env files as required.