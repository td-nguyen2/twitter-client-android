'use strict';

/**
 * USTH Twitter development-only fixture importer.
 * Run from any directory: node backend/mock_seed/seed.js [--full] [--with-social]
 * Uses the parent backend's existing mysql2, bcrypt and dotenv dependencies.
 * Never touches the original student1 / student2 accounts.
 */

const fs = require('fs');
const path = require('path');
const dotenv = require('dotenv');
const mysql = require('mysql2/promise');
const bcrypt = require('bcrypt');

const BACKEND_DIR = path.resolve(__dirname, '..');
const DATA = JSON.parse(fs.readFileSync(path.join(__dirname, 'mock_data.json'), 'utf8'));
const REALISM = fs.existsSync(path.join(__dirname, 'realistic_data.json')) ? JSON.parse(fs.readFileSync(path.join(__dirname, 'realistic_data.json'), 'utf8')) : null;
const REAL_POSTS = new Map((REALISM?.posts || []).map(x => [x.key, x]));
const REAL_REPLIES = new Map((REALISM?.replies || []).map(x => [x.key, x]));
const ENV_FILE = path.join(BACKEND_DIR, '.env');
dotenv.config({ path: ENV_FILE, quiet: true });

const allowedOptions = new Set(['--full', '--with-social', '--check', '--setup-schema', '--help']);
const opts = new Set(process.argv.slice(2));
for (const opt of opts) {
    if (!allowedOptions.has(opt)) {
        console.error(`Unknown option: ${opt}`);
        process.exit(2);
    }
}

if (opts.has('--help')) {
    console.log(`USTH Twitter mock data seed (development only)
  node mock_seed/seed.js                  40 users + 100 posts + 60 replies = 200 records
  node mock_seed/seed.js --full           200 users + 200 posts + 200 replies = 600 records
  node mock_seed/seed.js --check          Verify schema and fixture files, no writes
  node mock_seed/seed.js --setup-schema   Also add missing posts.media_url and post_replies table
  node mock_seed/seed.js --with-social    Also add example follows, likes and reposts
  node mock_seed/seed.js --full --with-social

Reruns will skip records already present, identified by their demo_uXXX username and
post/reply content. Existing real users and their posts are never updated or deleted.
`);
    process.exit(0);
}

if (opts.has('--check') && opts.has('--setup-schema')) {
    console.error('Do not combine --check and --setup-schema: --check is read-only.');
    process.exit(2);
}

const full = opts.has('--full');
const users = full ? DATA.users : DATA.users.slice(0, 40);
const posts = full ? DATA.posts : DATA.posts.slice(0, 100);
const replies = full ? DATA.replies : DATA.replies.slice(0, 60);
const userHandles = new Set(users.map(u => u.username));
const postKeys = new Set(posts.map(p => p.key));

if (posts.some(p => !userHandles.has(p.username)) ||
    replies.some(r => !userHandles.has(r.username) || !postKeys.has(r.postKey))) {
    throw new Error('Invalid seed fixtures: selected replies/posts reference unavailable mock users or posts');
}

function mustHaveEnv() {
    const vars = ['DB_HOST', 'DB_USER', 'DB_NAME'];
    const missing = vars.filter(k => !process.env[k]);
    if (missing.length || !fs.existsSync(ENV_FILE)) {
        throw new Error(`Missing ${missing.join(', ') || 'backend/.env'}. Copy your local backend/.env into ${BACKEND_DIR}`);
    }
}

function verifyAssets() {
    const allPaths = new Set([...users.flatMap(u => [u.profileImageUrl, u.bannerImageUrl]),
                              ...posts.map(p => p.mediaUrl)].filter(Boolean));
    const prefix = '/uploads/mock_seed/';
    for (const url of allPaths) {
        if (!url.startsWith(prefix)) throw new Error(`Unexpected asset URL: ${url}`);
        const relative = url.slice(prefix.length);
        const source = path.join(__dirname, 'assets', relative);
        if (!fs.existsSync(source)) throw new Error(`Missing fixture media: ${source}`);
    }
    return allPaths.size;
}

async function columnSet(conn, table) {
    const [rows] = await conn.execute(
        'SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?',
        [process.env.DB_NAME, table]
    );
    return new Set(rows.map(r => r.COLUMN_NAME));
}

async function checkSchema(conn) {
    let cols = await columnSet(conn, 'users');
    if (cols.size === 0) throw new Error('Missing users table. Start with your existing USTH Twitter database.');
    const requiredUser = ['id','username','display_name','email','password_hash','bio','profile_image_url','banner_image_url','created_at'];
    const missingUser = requiredUser.filter(c => !cols.has(c));
    if (missingUser.length) throw new Error('Missing users columns: ' + missingUser.join(', '));

    cols = await columnSet(conn, 'posts');
    if (cols.size === 0) throw new Error('Missing posts table. Start with your existing USTH Twitter database.');
    if (!cols.has('media_url')) {
        if (!opts.has('--setup-schema')) {
            throw new Error('Missing posts.media_url. Run with --setup-schema to add this nullable column, or apply your project migration.');
        }
        console.log('Adding posts.media_url column (existing posts are preserved) ...');
        await conn.query('ALTER TABLE posts ADD COLUMN media_url TEXT NULL');
        cols.add('media_url');
    }
    for (const c of ['id','username','content','created_at','media_url']) {
        if (!cols.has(c)) throw new Error('Missing posts column: '+c);
    }

    cols = await columnSet(conn, 'post_replies');
    if (cols.size === 0) {
        if (!opts.has('--setup-schema')) {
            throw new Error('Missing post_replies table. Run with --setup-schema to create it, or apply your project migration.');
        }
        console.log('Creating post_replies table (if missing) ...');
        await conn.query(`CREATE TABLE IF NOT EXISTS post_replies (
            id INT AUTO_INCREMENT PRIMARY KEY,
            post_id INT NOT NULL,
            username VARCHAR(50) NOT NULL,
            content VARCHAR(280) NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            INDEX (post_id)
        )`);
        cols = await columnSet(conn, 'post_replies');
    }
    for (const c of ['id','post_id','username','content','created_at']) {
        if (!cols.has(c)) throw new Error('Missing post_replies column: '+c);
    }

    if (opts.has('--with-social')) {
        for (const [table,expected] of [
            ['follows',['follower_id','following_id']],
            ['post_likes',['user_id','post_id']],
            ['post_reposts',['post_id','username']]
        ]) {
            const available = await columnSet(conn,table);
            if (expected.some(c => !available.has(c))) {
                throw new Error(`Missing ${table} or its required columns. Start the backend once to initialize social tables.`);
            }
        }
    }
}

function copyMockFiles() {
    const src = path.join(__dirname, 'assets');
    const target = path.join(BACKEND_DIR, 'uploads', 'mock_seed');
    fs.mkdirSync(target, { recursive: true });
    // Only our designated subdirectory is written; existing uploads outside it are untouched.
    fs.cpSync(src, target, { recursive: true, force: true });
    return target;
}

function counts() {
    return `${users.length} profiles, ${posts.length} tweets (${posts.filter(p => !!p.mediaUrl).length} with images), ${replies.length} replies`;
}

async function main() {
    mustHaveEnv();
    const assets = verifyAssets();
    console.log(`Fixtures: ${counts()}, ${assets} distinct referenced media paths.`);
    console.log(`Database: ${process.env.DB_NAME} at ${process.env.DB_HOST}:${process.env.DB_PORT || 3306}`);
    if (process.env.NODE_ENV === 'production') {
        throw new Error('Refusing to seed with NODE_ENV=production. Use a local development database only.');
    }

    const conn = await mysql.createConnection({
        host: process.env.DB_HOST,
        port: Number(process.env.DB_PORT || 3306),
        user: process.env.DB_USER,
        password: process.env.DB_PASSWORD,
        database: process.env.DB_NAME,
        charset: 'utf8mb4',
        multipleStatements: false
    });

    try {
        await checkSchema(conn);
        if (opts.has('--check')) {
            console.log('Schema and media files OK. No database rows or files were changed.');
            return;
        }
        const location = copyMockFiles();
        console.log(`Copied media into ${location}`);

        // DEMO login password (not a production credential); same for every demo account.
        const hash = await bcrypt.hash(DATA.password, 10);
        const ids = new Map();
        const postIds = new Map();
        const created = { users:0, posts:0, replies:0, follows:0, likes:0, reposts:0 };
        const skipped = { users:0, posts:0, replies:0 };

        await conn.beginTransaction();
        try {
            for (const u of users) {
                const [rows] = await conn.execute('SELECT id, email FROM users WHERE username = ? LIMIT 1', [u.username]);
                if (rows.length) {
                    if (rows[0].email !== u.email) throw new Error(`Username collision: ${u.username} exists with a different email. No changes committed.`);
                    ids.set(u.username, rows[0].id);
                    skipped.users++;
                } else {
                    const [result] = await conn.execute(
                        `INSERT INTO users (username, display_name, email, password_hash, bio, profile_image_url, banner_image_url, created_at)
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
                        [u.username,u.displayName,u.email,hash,u.bio,u.profileImageUrl,u.bannerImageUrl,u.createdAt]
                    );
                    ids.set(u.username,result.insertId);
                    created.users++;
                }
            }
            for (const p of posts) {
                const [rows] = await conn.execute(
                    'SELECT id FROM posts WHERE username = ? AND (content = ? OR content = ?) LIMIT 1',
                    [p.username,p.content,REAL_POSTS.get(p.key)?.content || p.content]
                );
                if (rows.length) {
                    postIds.set(p.key,rows[0].id);
                    skipped.posts++;
                } else {
                    const [result] = await conn.execute(
                        'INSERT INTO posts (username, content, media_url, created_at) VALUES (?, ?, ?, ?)',
                        [p.username,p.content,p.mediaUrl,p.createdAt]
                    );
                    postIds.set(p.key,result.insertId);
                    created.posts++;
                }
            }
            for (const r of replies) {
                const postId = postIds.get(r.postKey);
                const [rows] = await conn.execute(
                    'SELECT id FROM post_replies WHERE post_id = ? AND username = ? AND (content = ? OR content = ?) LIMIT 1',
                    [postId,r.username,r.content,REAL_REPLIES.get(r.key)?.content || r.content]
                );
                if (rows.length) skipped.replies++;
                else {
                    await conn.execute(
                        'INSERT INTO post_replies (post_id, username, content, created_at) VALUES (?, ?, ?, ?)',
                        [postId,r.username,r.content,r.createdAt]
                    );
                    created.replies++;
                }
            }
            if (opts.has('--with-social')) {
                for (let i=0;i<Math.min(users.length*3,600);i++) {
                    const a = users[(i*11)%users.length];
                    const b = users[(i*17+3)%users.length];
                    if (a.username===b.username) continue;
                    const [result] = await conn.execute(
                        'INSERT IGNORE INTO follows (follower_id,following_id) VALUES (?, ?)',
                        [ids.get(a.username),ids.get(b.username)]
                    );
                    created.follows += result.affectedRows;
                }
                for (let i=0;i<Math.min(posts.length*2,500);i++) {
                    const u = users[(i*7+3)%users.length];
                    const p = posts[(i*19)%posts.length];
                    const [result] = await conn.execute(
                        'INSERT IGNORE INTO post_likes (user_id,post_id) VALUES (?, ?)',
                        [ids.get(u.username),postIds.get(p.key)]
                    );
                    created.likes += result.affectedRows;
                }
                for (let i=0;i<Math.min(posts.length/3,60);i++) {
                    const u = users[(i*9+4)%users.length];
                    const p = posts[(i*13+4)%posts.length];
                    const [result] = await conn.execute(
                        'INSERT IGNORE INTO post_reposts (post_id,username) VALUES (?, ?)',
                        [postIds.get(p.key),u.username]
                    );
                    created.reposts += result.affectedRows;
                }
            }
            await conn.commit();
        } catch (err) {
            await conn.rollback();
            throw err;
        }
        console.log('Seed complete. Created:',created);
        console.log('Already present (skipped):',skipped);
        console.log(`Demo account: demo_u001 / ${DATA.password}`);
        console.log('Preview: http://localhost:3000/api/posts');
        console.log('Images: http://localhost:3000/uploads/mock_seed/posts/p05.webp');
        console.log('Your existing student1/student2 data was not changed.');
    } finally {
        await conn.end();
    }
}

main().catch(e=>{
    console.error('Seed failed:',e.message);
    process.exitCode=1;
});
