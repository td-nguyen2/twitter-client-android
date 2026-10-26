require("dotenv").config();
const bcrypt = require("bcrypt");
const express = require("express");
const multer = require("multer");
const path = require("path");
const fs = require("fs");
const mysql = require("mysql2/promise");
const jwt = require("jsonwebtoken");
const app = express();
// Directory for uploaded tweet images and GIFs
const uploadDir = path.join(__dirname, "uploads");
fs.mkdirSync(uploadDir, { recursive: true });
// Configure storage for uploaded images and GIFs
const storage = multer.diskStorage({
    destination: (req, file, cb) => {
        cb(null, uploadDir);
    },

    filename: (req, file, cb) => {
        const extension = path.extname(file.originalname).toLowerCase();
        const filename = require("crypto").randomUUID() + extension;

        cb(null, filename);
    }
});

// Configure which files can be uploaded
const upload = multer({
    storage,

    limits: {
        fileSize: 10 * 1024 * 1024
    },

    fileFilter: (req, file, cb) => {
        console.log("Uploaded filename:", file.originalname);
        console.log("Uploaded MIME type:", file.mimetype);

        const allowedExtensions = {
            ".jpg": "image/jpeg",
            ".jpeg": "image/jpeg",
            ".png": "image/png",
            ".webp": "image/webp",
            ".gif": "image/gif"
        };

        const extension = path.extname(file.originalname).toLowerCase();
        const expectedMime = allowedExtensions[extension];

        if (expectedMime && file.mimetype === expectedMime) {
            cb(null, true);
        } else {
            cb(new Error("Unsupported image format"));
        }
    }
});
// Serve uploaded images and GIFs
app.use("/uploads", express.static(uploadDir));
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

// Helper: Lấy userId nếu người dùng có gửi kèm JWT token
async function getOptionalUserId(req) {
    const auth = req.headers.authorization;
    if (typeof auth !== "string" || !auth.startsWith("Bearer ")) return null;
    try {
        const payload = jwt.verify(auth.slice(7).trim(), process.env.JWT_SECRET, { algorithms: ["HS256"] });
        return Number.isSafeInteger(payload?.userId) ? payload.userId : null;
    } catch {
        return null;
    }
}

// Tự động khởi tạo 3 bảng post_likes, follows, notifications nếu chưa tồn tại trong MySQL
async function initSocialTables() {
    try {
        await db.execute(`
            CREATE TABLE IF NOT EXISTS post_likes (
                user_id INT NOT NULL,
                post_id INT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (user_id, post_id)
            )
        `);
        await db.execute(`
            CREATE TABLE IF NOT EXISTS follows (
                follower_id INT NOT NULL,
                following_id INT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (follower_id, following_id)
            )
        `);
        await db.execute(`
            CREATE TABLE IF NOT EXISTS notifications (
                id INT AUTO_INCREMENT PRIMARY KEY,
                recipient_id INT NOT NULL,
                actor_id INT NOT NULL,
                type VARCHAR(20) NOT NULL,
                post_id INT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        `);
        console.log("Social tables (likes, follows, notifications) verified!");
    } catch (err) {
        console.error("Failed to init social tables:", err.message);
    }
}
initSocialTables();


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

// Get all posts, newest first (with likeCount, likedByMe, displayName)
app.get("/api/posts", async (req, res) => {
    try {
        const currentUserId = await getOptionalUserId(req);

        const [posts] = await db.execute(
            `SELECT p.id, p.username,
                    COALESCE(u.display_name, p.username) AS displayName,
                    p.content, p.media_url AS mediaUrl, p.created_at,
                    (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) AS likeCount,
                    ${currentUserId ? `EXISTS(SELECT 1 FROM post_likes pl WHERE pl.post_id = p.id AND pl.user_id = ${Number(currentUserId)})` : "0"} AS likedByMe
             FROM posts p
             LEFT JOIN users u ON u.username = p.username
             ORDER BY p.id DESC`
        );

        res.json({ posts });
    } catch (error) {
        console.error("GET posts error:", error.message);

        res.status(500).json({
            error: "Unable to retrieve posts"
        });
    }
});

// Create a new post, optionally with an image or GIF
app.post(
    "/api/posts",
    requireAuth,
    upload.single("media"),
    async (req, res) => {
        const { content } = req.body || {};

        const text = typeof content === "string"
            ? content.trim()
            : "";

        const mediaUrl = req.file
            ? `/uploads/${req.file.filename}`
            : null;

        // A post needs text, media, or both.
        if ((!text && !req.file) || text.length > 280) {
            if (req.file) {
                fs.unlink(req.file.path, () => {});
            }

            return res.status(400).json({
                error: "A tweet must contain text or media (max 280 characters)"
            });
        }

        try {
            const username = req.authUser.username;

            const [result] = await db.execute(
                `INSERT INTO posts (username, content, media_url)
                 VALUES (?, ?, ?)`,
                [username, text, mediaUrl]
            );

            const [rows] = await db.execute(
                `SELECT id, username, content,
                        media_url AS mediaUrl, created_at
                 FROM posts
                 WHERE id = ?`,
                [result.insertId]
            );

            return res.status(201).json({
                post: rows[0]
            });

        } catch (error) {
            if (req.file) {
                fs.unlink(req.file.path, () => {});
            }

            console.error("POST error:", error.message);

            return res.status(500).json({
                error: "Unable to create post"
            });
        }
    }
);

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

app.post("/api/auth/change-password", requireAuth, async (req, res) => {
    const { currentPassword, newPassword } = req.body || {};

    if (
        typeof currentPassword !== "string" ||
        typeof newPassword !== "string" ||
        !currentPassword ||
        newPassword.length < 8 ||
        Buffer.byteLength(newPassword, "utf8") > 72
    ) {
        return res.status(400).json({
            error: "Enter your current password and a new password of at least 8 characters"
        });
    }

    if (currentPassword === newPassword) {
        return res.status(400).json({
            error: "New password must be different"
        });
    }

    try {
        const [rows] = await db.execute(
            "SELECT password_hash FROM users WHERE id = ?",
            [req.authUser.id]
        );

        if (rows.length === 0) {
            return res.status(401).json({ error: "Account not found" });
        }

        const correct = await bcrypt.compare(
            currentPassword,
            rows[0].password_hash
        );

        if (!correct) {
            return res.status(401).json({
                error: "Current password is incorrect"
            });
        }

        const newHash = await bcrypt.hash(newPassword, 12);

        await db.execute(
            "UPDATE users SET password_hash = ? WHERE id = ?",
            [newHash, req.authUser.id]
        );

        return res.json({ message: "Password changed successfully" });
    } catch (error) {
        console.error("Change password error:", error.message);
        return res.status(500).json({
            error: "Unable to change password"
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
        const currentUserId = await getOptionalUserId(req);

        // Find the username associated with the user ID
        const [users] = await db.execute(
            "SELECT username, display_name FROM users WHERE id = ?",
            [userId]
        );

        if (users.length === 0) {
            return res.status(404).json({
                error: "User not found"
            });
        }

        // Retrieve posts written by this user (with likeCount and likedByMe)
        const [posts] = await db.execute(
            `SELECT p.id, p.username,
                    COALESCE(u.display_name, p.username) AS displayName,
                    p.content, p.media_url AS mediaUrl, p.created_at,
                    (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) AS likeCount,
                    ${currentUserId ? `EXISTS(SELECT 1 FROM post_likes pl WHERE pl.post_id = p.id AND pl.user_id = ${Number(currentUserId)})` : "0"} AS likedByMe
             FROM posts p
             LEFT JOIN users u ON u.username = p.username
             WHERE p.username = ?
             ORDER BY p.created_at DESC, p.id DESC`,
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

// ==========================================
// SOCIAL FEATURES: LIKES, FOLLOWS, FEED, NOTIFICATIONS
// ==========================================

// 1. Get Following-only Feed
app.get("/api/posts/following", requireAuth, async (req, res) => {
    try {
        const uid = req.authUser.id;
        const [posts] = await db.execute(
            `SELECT p.id, p.username,
                    COALESCE(u.display_name, p.username) AS displayName,
                    p.content, p.media_url AS mediaUrl, p.created_at,
                    (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) AS likeCount,
                    EXISTS(SELECT 1 FROM post_likes pl WHERE pl.post_id = p.id AND pl.user_id = ?) AS likedByMe
             FROM posts p
             JOIN users u ON u.username = p.username
             JOIN follows f ON f.following_id = u.id
             WHERE f.follower_id = ?
             ORDER BY p.id DESC`,
            [uid, uid]
        );
        return res.json({ posts });
    } catch (error) {
        console.error("GET following posts error:", error.message);
        return res.status(500).json({ error: "Unable to retrieve following feed" });
    }
});

// 2. Toggle Like / Unlike a post
app.post("/api/posts/:id/like", requireAuth, async (req, res) => {
    const postId = Number(req.params.id);
    const userId = req.authUser.id;

    if (!Number.isSafeInteger(postId) || postId <= 0) {
        return res.status(400).json({ error: "Invalid post ID" });
    }

    try {
        const [posts] = await db.execute("SELECT id, username, content FROM posts WHERE id = ?", [postId]);
        if (posts.length === 0) {
            return res.status(404).json({ error: "Post not found" });
        }

        const [existing] = await db.execute(
            "SELECT 1 FROM post_likes WHERE user_id = ? AND post_id = ?",
            [userId, postId]
        );

        let likedByMe = false;
        if (existing.length > 0) {
            await db.execute("DELETE FROM post_likes WHERE user_id = ? AND post_id = ?", [userId, postId]);
            likedByMe = false;
        } else {
            await db.execute("INSERT INTO post_likes (user_id, post_id) VALUES (?, ?)", [userId, postId]);
            likedByMe = true;

            const [authors] = await db.execute("SELECT id FROM users WHERE username = ?", [posts[0].username]);
            if (authors.length > 0 && authors[0].id !== userId) {
                await db.execute(
                    "INSERT INTO notifications (recipient_id, actor_id, type, post_id) VALUES (?, ?, 'LIKE', ?)",
                    [authors[0].id, userId, postId]
                );
            }
        }

        const [countRows] = await db.execute("SELECT COUNT(*) AS count FROM post_likes WHERE post_id = ?", [postId]);
        return res.json({
            postId,
            likedByMe,
            likeCount: countRows[0].count
        });
    } catch (error) {
        console.error("Toggle like error:", error.message);
        return res.status(500).json({ error: "Unable to toggle like" });
    }
});

// 3. Get Follow status & counts for a user
app.get("/api/users/:id/follow-status", async (req, res) => {
    const targetId = Number(req.params.id);
    if (!Number.isSafeInteger(targetId) || targetId <= 0) {
        return res.status(400).json({ error: "Invalid user ID" });
    }

    try {
        const currentUserId = await getOptionalUserId(req);
        const [[followers]] = await db.execute("SELECT COUNT(*) AS count FROM follows WHERE following_id = ?", [targetId]);
        const [[following]] = await db.execute("SELECT COUNT(*) AS count FROM follows WHERE follower_id = ?", [targetId]);

        let isFollowing = false;
        if (currentUserId && currentUserId !== targetId) {
            const [rows] = await db.execute(
                "SELECT 1 FROM follows WHERE follower_id = ? AND following_id = ?",
                [currentUserId, targetId]
            );
            isFollowing = rows.length > 0;
        }

        return res.json({
            followersCount: followers.count,
            followingCount: following.count,
            isFollowing
        });
    } catch (error) {
        console.error("Follow status error:", error.message);
        return res.status(500).json({ error: "Unable to get follow status" });
    }
});

// 4. Toggle Follow / Unfollow a user
app.post("/api/users/:id/follow", requireAuth, async (req, res) => {
    const targetId = Number(req.params.id);
    const followerId = req.authUser.id;

    if (!Number.isSafeInteger(targetId) || targetId <= 0 || targetId === followerId) {
        return res.status(400).json({ error: "Invalid user ID to follow" });
    }

    try {
        const [existing] = await db.execute(
            "SELECT 1 FROM follows WHERE follower_id = ? AND following_id = ?",
            [followerId, targetId]
        );

        let isFollowing = false;
        if (existing.length > 0) {
            await db.execute("DELETE FROM follows WHERE follower_id = ? AND following_id = ?", [followerId, targetId]);
            isFollowing = false;
        } else {
            await db.execute("INSERT INTO follows (follower_id, following_id) VALUES (?, ?)", [followerId, targetId]);
            isFollowing = true;

            await db.execute(
                "INSERT INTO notifications (recipient_id, actor_id, type, post_id) VALUES (?, ?, 'FOLLOW', NULL)",
                [targetId, followerId]
            );
        }

        const [[followers]] = await db.execute("SELECT COUNT(*) AS count FROM follows WHERE following_id = ?", [targetId]);
        const [[following]] = await db.execute("SELECT COUNT(*) AS count FROM follows WHERE follower_id = ?", [targetId]);

        return res.json({
            isFollowing,
            followersCount: followers.count,
            followingCount: following.count
        });
    } catch (error) {
        console.error("Toggle follow error:", error.message);
        return res.status(500).json({ error: "Unable to toggle follow" });
    }
});

// 5. Get Notifications for logged-in user
app.get("/api/notifications", requireAuth, async (req, res) => {
    try {
        const [notifications] = await db.execute(
            `SELECT n.id, n.type, n.post_id AS postId, n.created_at AS createdAt,
                    u.username AS actorUsername, u.display_name AS actorDisplayName,
                    p.content AS postSnippet
             FROM notifications n
             JOIN users u ON u.id = n.actor_id
             LEFT JOIN posts p ON p.id = n.post_id
             WHERE n.recipient_id = ?
             ORDER BY n.id DESC
             LIMIT 50`,
            [req.authUser.id]
        );
        return res.json({ notifications });
    } catch (error) {
        console.error("GET notifications error:", error.message);
        return res.status(500).json({ error: "Unable to load notifications" });
    }
});

/* ================= REPLY API ================= */

// Get replies for a post
app.get("/api/posts/:id/replies", async (req, res) => {
    const postId = Number(req.params.id);

    if (!Number.isSafeInteger(postId) || postId <= 0) {
        return res.status(400).json({ error: "Invalid post ID" });
    }

    try {
        const [posts] = await db.execute(
            "SELECT id FROM posts WHERE id = ?",
            [postId]
        );

        if (posts.length === 0) {
            return res.status(404).json({ error: "Post not found" });
        }

        const [replies] = await db.execute(
            `SELECT id, post_id AS postId, username,
                    content, created_at AS createdAt
             FROM post_replies
             WHERE post_id = ?
             ORDER BY created_at ASC, id ASC`,
            [postId]
        );

        return res.json({ replies });
    } catch (error) {
        console.error("Get replies error:", error.message);
        return res.status(500).json({ error: "Unable to retrieve replies" });
    }
});

// Create a reply
app.post("/api/posts/:id/replies", requireAuth, async (req, res) => {
    const postId = Number(req.params.id);
    const { content } = req.body || {};

    if (!Number.isSafeInteger(postId) || postId <= 0) {
        return res.status(400).json({ error: "Invalid post ID" });
    }

    if (
        typeof content !== "string" ||
        !content.trim() ||
        content.trim().length > 280
    ) {
        return res.status(400).json({
            error: "Reply must contain 1-280 characters"
        });
    }

    try {
        const [posts] = await db.execute(
            "SELECT id FROM posts WHERE id = ?",
            [postId]
        );

        if (posts.length === 0) {
            return res.status(404).json({ error: "Post not found" });
        }

        const [result] = await db.execute(
            `INSERT INTO post_replies (post_id, username, content)
             VALUES (?, ?, ?)`,
            [postId, req.authUser.username, content.trim()]
        );

        const [rows] = await db.execute(
            `SELECT id, post_id AS postId, username,
                    content, created_at AS createdAt
             FROM post_replies
             WHERE id = ?`,
            [result.insertId]
        );

        return res.status(201).json({
            message: "Reply added",
            reply: rows[0]
        });
    } catch (error) {
        console.error("Create reply error:", error.message);
        return res.status(500).json({ error: "Unable to create reply" });
    }
});


/* ================= REPOST API ================= */

// Get current user's repost status
app.get(
    "/api/posts/:id/repost-status",
    requireAuth,
    async (req, res) => {
        const postId = Number(req.params.id);

        if (!Number.isSafeInteger(postId) || postId <= 0) {
            return res.status(400).json({ error: "Invalid post ID" });
        }

        try {
            const [posts] = await db.execute(
                "SELECT id FROM posts WHERE id = ?",
                [postId]
            );

            if (posts.length === 0) {
                return res.status(404).json({ error: "Post not found" });
            }

            const [rows] = await db.execute(
                `SELECT id FROM post_reposts
                 WHERE post_id = ? AND username = ?`,
                [postId, req.authUser.username]
            );

            return res.json({ reposted: rows.length > 0 });
        } catch (error) {
            console.error("Repost status error:", error.message);
            return res.status(500).json({
                error: "Unable to retrieve repost status"
            });
        }
    }
);

// Repost
app.post(
    "/api/posts/:id/repost",
    requireAuth,
    async (req, res) => {
        const postId = Number(req.params.id);

        if (!Number.isSafeInteger(postId) || postId <= 0) {
            return res.status(400).json({ error: "Invalid post ID" });
        }

        try {
            const [posts] = await db.execute(
                "SELECT id FROM posts WHERE id = ?",
                [postId]
            );

            if (posts.length === 0) {
                return res.status(404).json({ error: "Post not found" });
            }

            await db.execute(
                `INSERT INTO post_reposts (post_id, username)
                 VALUES (?, ?)
                 ON DUPLICATE KEY UPDATE id = id`,
                [postId, req.authUser.username]
            );

            return res.json({
                message: "Post reposted",
                reposted: true
            });
        } catch (error) {
            console.error("Repost error:", error.message);
            return res.status(500).json({ error: "Unable to repost" });
        }
    }
);

// Undo repost
app.delete(
    "/api/posts/:id/repost",
    requireAuth,
    async (req, res) => {
        const postId = Number(req.params.id);

        if (!Number.isSafeInteger(postId) || postId <= 0) {
            return res.status(400).json({ error: "Invalid post ID" });
        }

        try {
            const [posts] = await db.execute(
                "SELECT id FROM posts WHERE id = ?",
                [postId]
            );

            if (posts.length === 0) {
                return res.status(404).json({ error: "Post not found" });
            }

            await db.execute(
                `DELETE FROM post_reposts
                 WHERE post_id = ? AND username = ?`,
                [postId, req.authUser.username]
            );

            return res.json({
                message: "Repost removed",
                reposted: false
            });
        } catch (error) {
            console.error("Undo repost error:", error.message);
            return res.status(500).json({
                error: "Unable to undo repost"
            });
        }
    }
);

require("./passwordResetRoutes")(app, db);

// Start the server
app.listen(PORT, () => {
    console.log(`Server running at http://localhost:${PORT}`);
});