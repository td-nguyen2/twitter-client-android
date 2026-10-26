const crypto = require("crypto");
const bcrypt = require("bcrypt");
const nodemailer = require("nodemailer");

const EXPIRY_MINUTES = 10;
const RESEND_SECONDS = 60;
const MAX_ATTEMPTS = 5;
const MESSAGE = "If this email is registered, a reset code has been sent.";

module.exports = function registerPasswordResetRoutes(app, db) {
    const mailUser = process.env.MAIL_USER;
    const mailPassword = process.env.MAIL_APP_PASSWORD;
    const transporter = mailUser && mailPassword
        ? nodemailer.createTransport({
            service: "gmail",
            auth: { user: mailUser, pass: mailPassword }
        })
        : null;

    function hashCode(userId, code) {
        return crypto.createHmac("sha256", process.env.JWT_SECRET)
            .update(`${userId}:${code}`)
            .digest("hex");
    }

    app.post("/api/auth/forgot-password", async (req, res) => {
        const email = typeof req.body?.email === "string"
            ? req.body.email.trim().toLowerCase() : "";
        if (email.length > 255 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
            return res.status(400).json({ error: "Enter a valid email address" });
        }
        if (!transporter || !process.env.JWT_SECRET) {
            return res.status(503).json({ error: "Email service is not configured" });
        }

        try {
            const [users] = await db.execute(
                "SELECT id FROM users WHERE email = ? LIMIT 1", [email]
            );
            if (users.length === 0) return res.json({ message: MESSAGE });

            const userId = users[0].id;
            const [existing] = await db.execute(
                "SELECT sent_at FROM password_reset_codes WHERE user_id = ?", [userId]
            );
            if (existing.length && Date.now() - new Date(existing[0].sent_at).getTime() < RESEND_SECONDS * 1000) {
                return res.json({ message: MESSAGE });
            }

            const code = crypto.randomInt(0, 1000000).toString().padStart(6, "0");
            const codeHash = hashCode(userId, code);
            await db.execute(
                `INSERT INTO password_reset_codes (user_id, code_hash, expires_at, sent_at, attempts)
                 VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 10 MINUTE), NOW(), 0)
                 ON DUPLICATE KEY UPDATE code_hash = VALUES(code_hash),
                   expires_at = VALUES(expires_at), sent_at = VALUES(sent_at), attempts = 0`,
                [userId, codeHash]
            );

            try {
                await transporter.sendMail({
                    from: mailUser,
                    to: email,
                    subject: "USTH Twitter password reset code",
                    text: `Your password reset code is ${code}. It expires in ${EXPIRY_MINUTES} minutes. If you did not request this, ignore this email.`
                });
            } catch (mailError) {
                await db.execute(
                    "DELETE FROM password_reset_codes WHERE user_id = ? AND code_hash = ?",
                    [userId, codeHash]
                );
                throw mailError;
            }

            return res.json({ message: MESSAGE });
        } catch (error) {
            console.error("Forgot password error:", error.message);
            return res.status(500).json({ error: "Unable to send reset code" });
        }
    });

    app.post("/api/auth/reset-password", async (req, res) => {
        const email = typeof req.body?.email === "string"
            ? req.body.email.trim().toLowerCase() : "";
        const code = req.body?.code;
        const newPassword = req.body?.newPassword;
        if (email.length > 255 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ||
            typeof code !== "string" || !/^\d{6}$/.test(code) ||
            typeof newPassword !== "string" || newPassword.length < 8 ||
            Buffer.byteLength(newPassword, "utf8") > 72) {
            return res.status(400).json({ error: "Invalid email, code or new password" });
        }
        if (!process.env.JWT_SECRET) {
            return res.status(503).json({ error: "Reset service is not configured" });
        }

        let connection;
        try {
            connection = await db.getConnection();
            await connection.beginTransaction();
            const [rows] = await connection.execute(
                `SELECT u.id, r.code_hash, r.expires_at, r.attempts
                 FROM users u JOIN password_reset_codes r ON r.user_id = u.id
                 WHERE u.email = ? FOR UPDATE`, [email]
            );
            if (!rows.length) {
                await connection.rollback();
                return res.status(400).json({ error: "Invalid or expired code" });
            }
            const row = rows[0];
            if (row.attempts >= MAX_ATTEMPTS || new Date(row.expires_at).getTime() <= Date.now()) {
                await connection.execute("DELETE FROM password_reset_codes WHERE user_id = ?", [row.id]);
                await connection.commit();
                return res.status(400).json({ error: "Invalid or expired code" });
            }

            const submittedHash = hashCode(row.id, code);
            if (!crypto.timingSafeEqual(Buffer.from(submittedHash, "hex"), Buffer.from(row.code_hash, "hex"))) {
                await connection.execute(
                    "UPDATE password_reset_codes SET attempts = attempts + 1 WHERE user_id = ?", [row.id]
                );
                await connection.commit();
                return res.status(400).json({ error: "Invalid or expired code" });
            }

            const passwordHash = await bcrypt.hash(newPassword, 12);
            await connection.execute("UPDATE users SET password_hash = ? WHERE id = ?", [passwordHash, row.id]);
            await connection.execute("DELETE FROM password_reset_codes WHERE user_id = ?", [row.id]);
            await connection.commit();
            return res.json({ message: "Password reset successfully. Please log in." });
        } catch (error) {
            if (connection) await connection.rollback();
            console.error("Reset password error:", error.message);
            return res.status(500).json({ error: "Unable to reset password" });
        } finally {
            if (connection) connection.release();
        }
    });
};
