[![Latest Release](https://ghcr-badge.egpl.dev/svenkubiak/filedpapers/filedpapers/latest_tag?trim=major&label=Latest)](https://github.com/svenkubiak/filedpapers/pkgs/container/filedpapers%2Ffiledpapers/331704697?tag=latest)
[![iOS App](https://img.shields.io/badge/iOS-App_Store-blue?logo=apple)](https://...)
[![Chrome Web Store](https://img.shields.io/badge/Chrome-Extension-blue?logo=google-chrome)](https://chrome.google.com/webstore/detail/your-extension-id)
[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20A%20Coffee-%F0%9F%8D%BA-yellow)](https://www.buymeacoffee.com/svenkubiak)

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

> **Attention:**  
> The backend is currently in active development, and the iOS and Chrome extensions are currently being tested. Please note that certain features may still be in progress and could change.

## Resources

**Homepage**   
[https://svenkubiak.de/filed-papers](https://svenkubiak.de/filed-papers)

**Changelog**   
[https://github.com/svenkubiak/filedpapers/blob/main/CHANGELOG.md](https://github.com/svenkubiak/filedpapers/blob/main/CHANGELOG.md)

**Support**   
[https://github.com/svenkubiak/filedpapers/issues](https://github.com/svenkubiak/filedpapers/issues)

**iOS App**  
[https://github.com/svenkubiak/filedpapers/issues](https://github.com/svenkubiak/filedpapers/issues)

**Google Chrome Extension**  
[https://chrome.google.com/webstore/detail/your-extension-id](hhttps://chrome.google.com/webstore/detail/your-extension-id)

# Installation Guide

### Prerequisites

Before starting the installation process, make sure you have the following prerequisites:

- **Docker**: Ensure Docker is installed and running on your system
- **Docker Compose**: Make sure Docker Compose is installed to manage multi-container applications
- **Web Frontend Server**: A frontend HTTP server (e.g., Nginx) to handle SSL termination and proxy requests to the backend. This is required for configuring the web server in step 10.

## Automatic installation

1. **Create the directory for your server installation:**

   First, create a folder where you want to install your server. For this example, we will use the folder name `filedpapers`.

   ```shell
   mkdir filedpapers
   cd filedpapers
   ```
2. **Download and execute the installation script:**

   ```shell
   curl -sSL https://raw.githubusercontent.com/yourusername/yourrepo/main/your-script.sh | bash
   ```

## Manual installation

1. **Create the directory for your server installation:**

   First, create a folder where you want to install your server. For this example, we will use the folder name `filedpapers`.

   ```shell
   mkdir filedpapers
   cd filedpapers
   ```

2. **Ensure you're in the correct directory:**

   You should now be in the newly created folder. If you aren't, navigate back to it.

   ```shell
   cd filedpapers
   ```

3. **Create an `.env` file and add configuration variables:**

   Create a `.env` file in the `filedpapers` directory and add the following variables. You can customize the `username`, `password`, and `database` fields as needed.

   ```shell
   MONGODB_INITDB_ROOT_USERNAME=filedpapers
   MONGODB_INITDB_ROOT_PASSWORD=filedpapers
   MONGODB_INITDB_DATABASE=filedpapers
   ```

4. **Create the `config` folder:**

   Next, create a `config` folder where configuration files will reside.

   ```shell
   mkdir config
   cd config
   ```

5. **Download the default `config.yaml`:**

   Fetch the default configuration file from the repository.

   ```shell
   curl -O https://raw.githubusercontent.com/svenkubiak/filedpapers/refs/heads/main/config.yaml
   ```

6. **Edit the `config.yaml` file:**

   Open the `config.yaml` file in a text editor. There are several placeholders marked as "Change Me!" which need to be replaced. Scroll to the bottom and update all secret values. It's recommended to use at least 64 characters for each secret.

7. **Update database credentials:**

   If you modified the database name, username, or password in Step 3, make sure to update the corresponding fields in the `config.yaml` file.

8. **Return to the installation directory:**

   Once you're done editing the configuration files, navigate back to the installation directory.

   ```shell
   cd ..
   ```

9. **Download the `compose.yaml`:**

   Fetch the default `compose.yaml` file for Docker Compose from the repository.

   ```shell
   curl -O https://raw.githubusercontent.com/svenkubiak/filedpapers/refs/heads/main/compose.yml
   ```

10. **Configure the web server:**

   By default, the web server is exposed to `127.0.0.1`. This setup assumes that you have a frontend HTTP server where SSL termination, etc., is handled. Adjust the `Host` and `Port` sections in the `compose.yaml` file as necessary for your setup.

11. **Start the Docker containers:**

   Finally, start the Docker containers using Docker Compose:

   ```shell
   docker compose up -d
   ```


