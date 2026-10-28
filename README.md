# USTH Twitter — Android Client & Express Backend

A Twitter-like mobile application built as a USTH group project. The Android client is written in **Java/XML** and communicates with a **Node.js/Express** API backed by **MySQL**. This is an educational prototype, not an official X/Twitter client.

## Project status

**Implemented and tested locally:**

- Account registration with unique username/email and bcrypt password hashing.
- Login with JWT authentication (24-hour access token).
- Android login and registration screens using Fragments.
- Local session persistence using `SharedPreferences`.
- Timeline populated from MySQL via `GET /api/posts`, newest first.
- Tweet views counted once per signed-in account and shown on feed rows and the post detail screen.
- Save other users' posts to a personal Bookmarks list, stored in MySQL.
- Compose and publish text-only, image-only, and image/GIF tweets (maximum 280 characters of text).
- Store uploaded files locally in backend/uploads/ using Multer.
- Display uploaded images and animated GIFs in the timeline using Glide.
- Authenticated tweet creation: Express verifies a Bearer JWT and obtains the author's username from the authenticated account, **not** from a client-supplied username.
- Posts remain in MySQL after the app or server restarts.

**Verified during development:** creating `student2`, logging in, and publishing posts attributed to `student2`; older test posts retain their original usernames. Invalid login credentials return HTTP 401.

**Not implemented yet:** an in-app logout button, expired-token recovery, production-ready credential storage, user profiles, follow relationships, likes/replies/retweets, search, notifications, and production deployment.

## Tech stack

| Layer                 | Technology                                  |
| --------------------- | ------------------------------------------- |
| Android               | Java, XML, AndroidX Fragments, RecyclerView |
| HTTP client           | Volley                                      |
| Image and GIF loading | Glide 4.16.0                                |
| Backend               | Node.js, Express (CommonJS)                 |
| File uploading        | Multer (`multipart/form-data`)              |
| Media storage         | Local `backend/uploads/` directory          |
| Database              | MySQL 8.x, `mysql2/promise`                 |
| Authentication        | `bcrypt`, `jsonwebtoken` (JWT)              |
| Configuration         | `dotenv` (`backend/.env`, not committed)    |
| Tooling               | Android Studio, Gradle, npm, Git            |

## Repository layout

```text
app/src/main/
├── java/com/example/twitterclient/
│   ├── MainActivity.java
│   ├── auth/SessionManager.java
│   ├── model/Tweet.java
│   ├── network/ApiService.java
│   ├── repository/
│   │   ├── AuthRepository.java
│   │   └── TweetRepository.java
│   └── ui/
│       ├── auth/
│       │   ├── LoginFragment.java
│       │   └── RegisterFragment.java
│       └── timeline/
│           ├── TimelineFragment.java
│           ├── TweetAdapter.java
│           └── TweetViewHolder.java
├── res/layout/
│   ├── fragment_login.xml
│   ├── fragment_register.xml
│   └── fragment_timeline.xml
└── AndroidManifest.xml
backend/
├── server.js
├── package.json
└── package-lock.json
README.md
```

This diagram highlights important files, not every generated/configuration file.

## Local setup

### Prerequisites

- Windows with Android Studio, Android SDK, and an emulator (tested with API 34).
- Node.js and npm.
- MySQL Server 8.x.
- Java/Gradle toolchain compatible with this Android project.

### 1. Set up MySQL

Connect to your local MySQL server, then run:

```sql
CREATE DATABASE IF NOT EXISTS usth_twitter;
USE usth_twitter;

CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    date_of_birth DATE,
    bio VARCHAR(280) DEFAULT '',
    profile_image_url TEXT,
    banner_image_url TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS posts (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    media_url VARCHAR(500) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

```
### Database migration for existing installations

If the posts table already exists, add the media_url column:

```sql
ALTER TABLE posts
ADD COLUMN media_url VARCHAR(500) NULL;
```
The current `posts` schema still stores an author username. A future migration should introduce `posts.user_id` referencing `users.id` and preserve existing test posts.

### 2. Configure and start the backend

From the project root:

```powershell
cd backend
npm install
```

Create `backend/.env` with your **own local** settings:

```dotenv
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USER=root
DB_PASSWORD=YOUR_LOCAL_MYSQL_PASSWORD
DB_NAME=usth_twitter
PORT=3000
JWT_SECRET=REPLACE_WITH_A_RANDOM_SECRET
```

Generate a secret (run inside `backend`):

```powershell
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

Copy the generated value into `JWT_SECRET`. Never commit `.env` or share a real signing secret.

Start the backend:

```powershell
node server.js
```

Keep this terminal running. To verify connectivity, open `http://localhost:3000/api/health` on the development computer. A working backend should report that its database is connected.

### 3. Run the Android app

1. Open the repository root in Android Studio and complete Gradle sync.
2. Start the emulator and select the `app` run configuration.
3. Keep MySQL and Express running on the host PC.
4. Run the Android app, register a test account, then log in.

The Android emulator reaches a server on the host PC at **`http://10.0.2.2:3000`**, configured in the Android networking classes. `localhost` inside the emulator points to the emulator itself, not the computer. A physical phone requires a reachable host LAN address or a deployed HTTPS backend.

## REST API

| Method   | Route                     | Authentication | Purpose                                  |
| -------- | ------------------------- | -------------- | ---------------------------------------- |
| `GET`    | `/api/health`             | No             | Check API and DB availability            |
| `POST`   | `/api/auth/register`      | No             | Create a user and hash their password    |
| `POST`   | `/api/auth/login`         | No             | Verify credentials, issue JWT            |
| `GET`    | `/api/posts`              | No             | List posts, newest first                 |
| `POST`   | `/api/posts`              | **Bearer JWT** | Create a tweet as the authenticated user |
| `POST`   | `/api/posts/:id/view`     | **Bearer JWT** | Count one view per account and post      |
| `GET`    | `/api/bookmarks`          | **Bearer JWT** | List the signed-in user's saved posts    |
| `POST`   | `/api/posts/:id/bookmark` | **Bearer JWT** | Save a post to Bookmarks                 |
| `DELETE` | `/api/posts/:id/bookmark` | **Bearer JWT** | Remove a post from Bookmarks             |

### Registration

```http
POST /api/auth/register
Content-Type: application/json
```

```json
{
  "username": "student2",
  "displayName": "Second Student",
  "email": "student2@example.com",
  "password": "ExamplePassword456!"
}
```

Returns HTTP **201** and safe account details; a duplicate username or email returns **409**. The password hash is never returned.

### Login

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "username": "student2",
  "password": "ExamplePassword456!"
}
```

Returns a `token` (JWT) and `user` object on success; invalid credentials return **401**. The JWT is signed using the server-side `JWT_SECRET` and expires after **24 hours**.

### Publish a tweet

```http
POST /api/posts
Authorization: Bearer <JWT_FROM_LOGIN>
Content-Type: application/json
```

```json
{
  "content": "Hello from my account!"
}
```

The server verifies the JWT, looks up the account in MySQL, and saves the authenticated username with the post. The client **does not** send a `username` field for this endpoint. Missing, invalid, or expired JWTs should return **401**. A successful creation returns **201** with the new post.

## Session behavior and testing

- `LoginFragment` reads the JWT returned by `/api/auth/login`.
- `SessionManager` saves the JWT and username using Android `SharedPreferences`.
- `MainActivity` opens the timeline when a locally saved token exists, otherwise it shows Login.
- `ApiService` sends `Authorization: Bearer <token>` when publishing posts.
- This local session check **does not validate token expiry**; the server verifies the JWT for protected requests.

There is currently no in-app Logout button. To reset a **test emulator** without changing code, use this from PowerShell:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell pm clear com.example.twitterclient
```

This removes the app's local storage, including its saved token, and will also erase other local app data. It **does not** delete MySQL users or posts, and it does **not** revoke previously issued JWTs on the server. Relaunch the app to display Login.

Useful backend test cases:

- Register a new user; query MySQL to verify a bcrypt hash rather than a plaintext password.
- Log in with correct credentials; confirm a JWT is returned.
- Log in with a wrong password; expect HTTP 401.
- Post with a valid JWT; confirm the resulting author matches the authenticated user.
- Attempt `POST /api/posts` without a JWT; expect HTTP 401.
- Restart the app and server; confirm posts remain in MySQL.

## Current limitations and next steps

1. **Identity model:** replace the `posts.username` text link with a `posts.user_id` foreign key, while migrating existing posts safely.
2. **Session UX:** add logout, detect expired tokens, and redirect to Login when authentication fails.
3. **Security:** move JWT credentials to encryption-backed storage; avoid real credentials in logs; use HTTPS for nonlocal deployment; apply rate limiting and stronger production authentication controls.
4. **Product features:** profiles, editing user details, follows, likes, replies, search, and improved timeline refresh.
5. **Quality:** automated backend and Android tests, error-state tests, migrations, and deployment documentation.

This is a local **course prototype**. The current local HTTP and session-storage configuration is not suitable for production deployment as-is.

## Group workflow

The team should work from feature branches and submit pull requests to `main`. Keep database/API field names agreed across Android and Express before developing dependent features. Do not commit `backend/.env`, API credentials, database passwords, JWT secrets, or generated build artifacts.

Example workflow:

```powershell
git switch -c feature/my-feature
# edit and test files
git add .
git commit -m "Describe the completed feature"
git push -u origin feature/my-feature
```

The project is an independent USTH coursework implementation informed by Android/Node.js course material and Twitter-like app reference projects; it is not affiliated with X/Twitter.
