
Filed Papers
================

Take charge of your bookmarks with Filed Papers, designed for those who value privacy and control. Unlike traditional bookmark managers, Filed Papers requires you to host the backend yourself, ensuring your data remains fully in your hands—private, secure, and free from third-party access.

Key Features:

- Organized Bookmarking: Save your bookmarks into custom categories for efficient management and easy retrieval.
- Customizable Categories: Create and tailor categories to suit your personal or professional needs.
- Self-Hosted Backend: You are in full control of your data. By hosting the backend yourself, you ensure that your bookmarks remain private and inaccessible to others.
- Use the Web-Interface along with the additional iOS App and Google Chrome extension

Why Choose Filed Papers?

- Complete Privacy: With no reliance on third-party servers, your data stays completely under your control.
- Focused Simplicity: A clean, intuitive interface designed to make managing bookmarks straightforward and effective.
- Perfect for Professionals and Privacy-Conscious Users: Whether for research, work, or personal use, Filed Papers offers the ideal balance of organization and security.

# Installation

1. Create a directory where you want to install your server and change into that folder. We are assuming the folders is "filedpapers"

```shell
mkdir filedpapers
cd filedpapers
```

2. Change into the newly created folder

```shell
cd filedpapers
```

3. Create an .env file and add the following variables. Feel free to change username, password and database

```shell
MONGODB_INITDB_ROOT_USERNAME=filedpapers
MONGODB_INITDB_ROOT_PASSWORD=filedpapers
MONGODB_INITDB_DATABASE=filedpapers
```

4. Create a config folder and change into the newly created folder

```shell
mkdir config
cd config
```

5. Grep the default config.yaml from the repository

```shell
curl -O https://.../config.yaml
```

6. Open the config.yaml file. There are a couple of secrets that needs to be replaced. Scroll to the bottom and change all secrents stating "Change Me!". Use at least 64 characters

7. Change the dbname, username and password if you have change it in Step 3. in you .env file

8. Go back into your install directory

```shell
cd ..
```

9. Grep the default compose.yaml from the repository

```shell
curl -O https://.../compose.yaml
```

10. By default the Webserver is exposed to 127.0.0.1 and expect you to have a frontend HTTP-Server where you do SSL termination, etc. Adopt the Host and Port section based on your needs.

11. Start the docker containers

```shell
docker compose up -d
```

