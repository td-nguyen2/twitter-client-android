
require("dotenv").config();
const bcrypt = require("bcrypt");
const express = require("express");
const mysql = require("mysql2/promise");
const jwt = require("jsonwebtoken");
const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());

// MySQL connection pool
const db = mysql.createPool({
    host: process.env.DB_HOST,
    port: Number(process.env.DB_PORT || 3306),
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
    database: process.env.DB_NAME
});


async function requireAuth(req, res, next) {
    const authorization = req.headers.authorization;

    if (
        typeof authorization !== "string" ||
        !authorization.startsWith("Bearer ")
    ) {
        return res.status(401).json({
            error: "Authentication required"
        });
    }

    const token = authorization.slice(7).trim();

    try {
        // Verify the signature and expiration time.
        const payload = jwt.verify(
            token,
            process.env.JWT_SECRET,
            { algorithms: ["HS256"] }
        );

        if (
            !payload ||
            !Number.isSafeInteger(payload.userId) ||
            payload.userId <= 0
        ) {
            return res.status(401).json({
                error: "Invalid authentication token"
            });
        }

        // Confirm this account still exists.
        const [users] = await db.execute(
            "SELECT id, username FROM users WHERE id = ?",
            [payload.userId]
        );

        if (users.length === 0) {
            return res.status(401).json({
                error: "Account no longer exists"
            });
        }

        // This identity is verified by the server.
        req.authUser = users[0];
        next();

    } catch (error) {
        if (
            error.name === "JsonWebTokenError" ||
            error.name === "TokenExpiredError" ||
            error.name === "NotBeforeError"
        ) {
            return res.status(401).json({
                error: "Invalid or expired token"
            });
        }

        console.error("Authentication error:", error.message);
        return res.status(500).json({
            error: "Authentication unavailable"
        });
    }
}



// Check server and database
app.get("/api/health", async (req, res) => {
    try {
        await db.query("SELECT 1");

        res.json({
            status: "ok",
            message: "USTH Twitter server is running",
            database: "connected"
        });
    } catch (error) {
        console.error("Database error:", error.message);

        res.status(503).json({
            status: "error",
            message: "Database unavailable"
        });
    }
});

// Get all posts, newest first
app.get("/api/posts", async (req, res) => {
    try {
        const [posts] = await db.query(
            `SELECT id, username, content, created_at
             FROM posts
             ORDER BY id DESC`
        );

        res.json({ posts });
    } catch (error) {
        console.error("GET posts error:", error.message);

        res.status(500).json({
            error: "Unable to retrieve posts"
        });
    }
});

// Create a new post

app.post("/api/posts", requireAuth, async (req, res) => {
    const { content } = req.body || {};

    if (
        typeof content !== "string" ||
        !content.trim() ||
        content.length > 280
    ) {
        return res.status(400).json({
            error: "Valid tweet content (max 280 characters) required"
        });
    }

    try {
        // Username comes from verified authentication,
        // never from req.body.username.
        const username = req.authUser.username;

        const [result] = await db.execute(
            `INSERT INTO posts (username, content)
             VALUES (?, ?)`,
            [username, content.trim()]
        );

        const [rows] = await db.execute(
            `SELECT id, username, content, created_at
             FROM posts
             WHERE id = ?`,
            [result.insertId]
        );

        return res.status(201).json({
            post: rows[0]
        });

    } catch (error) {
        console.error("POST error:", error.message);

        return res.status(500).json({
            error: "Unable to create post"
        });
    }
});



// Register a new user
app.post("/api/auth/register", async (req, res) => {
    const {
        username,
        displayName,
        email,
        password
    } = req.body || {};

    // 1. Validate required fields
    if (
        typeof username !== "string" ||
        typeof displayName !== "string" ||
        typeof email !== "string" ||
        typeof password !== "string"
    ) {
        return res.status(400).json({
            error: "Username, display name, email and password are required"
        });
    }

    const cleanUsername = username.trim();
    const cleanDisplayName = displayName.trim();
    const cleanEmail = email.trim().toLowerCase();

    if (
        !/^[a-zA-Z0-9_]{3,50}$/.test(cleanUsername) ||
        cleanDisplayName.length === 0 ||
        cleanDisplayName.length > 100 ||
        cleanEmail.length > 255 ||
        !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(cleanEmail) ||
        password.length < 8 ||
        Buffer.byteLength(password, "utf8") > 72
    ) {
        return res.status(400).json({
            error: "Invalid username, display name, email or password"
        });
    }

    try {
        // 2. Hash the password before saving it
        const passwordHash = await bcrypt.hash(password, 12);

        // 3. Create the account in MySQL
        const [result] = await db.execute(
            `INSERT INTO users
             (username, display_name, email, password_hash)
             VALUES (?, ?, ?, ?)`,
            [
                cleanUsername,
                cleanDisplayName,
                cleanEmail,
                passwordHash
            ]
        );

        // 4. Retrieve the safe account information
        const [rows] = await db.execute(
            `SELECT id, username,
                    display_name AS displayName,
                    email, created_at AS createdAt
             FROM users
             WHERE id = ?`,
            [result.insertId]
        );

        // 5. Respond without exposing the password hash
        return res.status(201).json({
            message: "Registration successful",
            user: rows[0]
        });

    } catch (error) {
        // MySQL duplicate username/email constraint
        if (error.code === "ER_DUP_ENTRY") {
            return res.status(409).json({
                error: "Username or email already registered"
            });
        }

        console.error("Registration error:", error.message);

        return res.status(500).json({
            error: "Unable to register user"
        });
    }
});



app.post("/api/auth/login", async (req, res) => {
    const { username, password } = req.body || {};

    if (
        typeof username !== "string" ||
        typeof password !== "string" ||
        !username.trim() ||
        !password
    ) {
        return res.status(400).json({
            error: "Username and password are required"
        });
    }

    try {
        // Find the user in MySQL
        const [rows] = await db.execute(
            `SELECT id, username,
                    display_name AS displayName,
                    email, password_hash,
                    created_at AS createdAt
             FROM users
             WHERE username = ?`,
            [username.trim()]
        );

        if (rows.length === 0) {
            return res.status(401).json({
                error: "Invalid username or password"
            });
        }

        const user = rows[0];

        // Compare submitted password against stored hash
        const validPassword = await bcrypt.compare(
            password,
            user.password_hash
        );

        if (!validPassword) {
            return res.status(401).json({
                error: "Invalid username or password"
            });
        }

        // Generate authentication token
        const token = jwt.sign(
            { userId: user.id },
            process.env.JWT_SECRET,
            { expiresIn: "24h" }
        );

        // Return safe user fields
        return res.status(200).json({
            message: "Login successful",
            token,
            user: {
                id: user.id,
                username: user.username,
                displayName: user.displayName,
                email: user.email,
                createdAt: user.createdAt
            }
        });

    } catch (error) {
        console.error("Registration error:", error);
        console.error(error?.stack);
        return res.status(500).json({
            error: "Unable to log in"
        });
    }
});



// Get the currently logged-in user's profile
app.get("/api/users/me", requireAuth, async (req, res) => {
    try {
        const [rows] = await db.execute(
            `SELECT
                id,
                username,
                display_name AS displayName,
                email,
                bio,
                profile_image_url AS profileImageUrl,
                banner_image_url AS bannerImageUrl,
                created_at AS createdAt
             FROM users
             WHERE id = ?`,
            [req.authUser.id]
        );

        if (rows.length === 0) {
            return res.status(404).json({
                error: "User not found"
            });
        }

        return res.json({ user: rows[0] });

    } catch (error) {
        console.error("Profile error:", error.message);
        return res.status(500).json({
            error: "Unable to load profile"
        });
    }
});

// Get a user's public profile
app.get("/api/users/:id", async (req, res) => {
    const userId = Number(req.params.id);

    if (!Number.isSafeInteger(userId) || userId <= 0) {
        return res.status(400).json({
            error: "Invalid user ID"
        });
    }

    try {
        const [rows] = await db.execute(
            `SELECT
                id,
                username,
                display_name AS displayName,
                bio,
                profile_image_url AS profileImageUrl,
                banner_image_url AS bannerImageUrl,
                created_at AS createdAt
             FROM users
             WHERE id = ?`,
            [userId]
        );

        if (rows.length === 0) {
            return res.status(404).json({
                error: "User not found"
            });
        }

        return res.json({ user: rows[0] });

    } catch (error) {
        console.error("Profile error:", error.message);
        return res.status(500).json({
            error: "Unable to load profile"
        });
    }
});


app.get("/api/users/by-username/:username", async (req, res) => {
    const username = req.params.username;

    if (!/^[a-zA-Z0-9_]{3,50}$/.test(username)) {
        return res.status(400).json({
            error: "Invalid username"
        });
    }

    try {
        const [rows] = await db.execute(
            `SELECT
                id,
                username,
                display_name AS displayName,
                bio,
                profile_image_url AS profileImageUrl,
                banner_image_url AS bannerImageUrl,
                created_at AS createdAt
             FROM users
             WHERE username = ?`,
            [username]
        );

        if (rows.length === 0) {
            return res.status(404).json({
                error: "User not found"
            });
        }

        return res.json({ user: rows[0] });

    } catch (error) {
        console.error("Username lookup error:", error.message);

        return res.status(500).json({
            error: "Unable to load profile"
        });
    }
});



app.get("/api/users/:id/posts", async (req, res) => {
    const userId = Number(req.params.id);

    if (!Number.isSafeInteger(userId) || userId <= 0) {
        return res.status(400).json({
            error: "Invalid user ID"
        });
    }

    try {
        // Find the username associated with the user ID
        const [users] = await db.execute(
            "SELECT username FROM users WHERE id = ?",
            [userId]
        );

        if (users.length === 0) {
            return res.status(404).json({
                error: "User not found"
            });
        }

        // Retrieve posts written by this user
        const [posts] = await db.execute(
            `SELECT id, username, content, created_at
             FROM posts
             WHERE username = ?
             ORDER BY created_at DESC, id DESC`,
            [users[0].username]
        );

        return res.json({ posts });

    } catch (error) {
        console.error("User posts error:", error.message);

        return res.status(500).json({
            error: "Unable to retrieve user posts"
        });
    }
});


app.patch("/api/users/me", requireAuth, async (req, res) => {
    const { displayName, bio } = req.body || {};

    if (
        typeof displayName !== "string" ||
        typeof bio !== "string"
    ) {
        return res.status(400).json({
            error: "Display name and bio must be strings"
        });
    }

    const cleanName = displayName.trim();
    const cleanBio = bio.trim();

    if (
        cleanName.length < 1 ||
        cleanName.length > 100 ||
        cleanBio.length > 280
    ) {
        return res.status(400).json({
            error: "Display name must be 1-100 characters; bio max 280"
        });
    }

    try {
        await db.execute(
            `UPDATE users
             SET display_name = ?, bio = ?
             WHERE id = ?`,
            [cleanName, cleanBio, req.authUser.id]
        );

        const [rows] = await db.execute(
            `SELECT id, username,
                    display_name AS displayName,
                    bio,
                    profile_image_url AS profileImageUrl,
                    banner_image_url AS bannerImageUrl,
                    created_at AS createdAt
             FROM users
             WHERE id = ?`,
            [req.authUser.id]
        );

        return res.json({
            message: "Profile updated",
            user: rows[0]
        });

    } catch (error) {
        console.error("Profile update error:", error.message);

        return res.status(500).json({
            error: "Unable to update profile"
        });
    }
});


// Start the server
app.listen(PORT, () => {
    console.log(`Server running at http://localhost:${PORT}`);
});
