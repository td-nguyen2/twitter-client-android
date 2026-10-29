# USTH Twitter – More natural mock data (v2)

This update works with the **existing** mock data package and the group's current `usth_twitter` MySQL database. It does **not** require changes to your Android UI or Express API. It was designed for the seeded `demo_u001` … `demo_u200` users and their 200 sample posts.

## Improvements

- Replaces repetitive post text and mismatched hashtags with 200 topic-aware, varied short posts (English and some natural Vietnamese).
- Uses original illustration assets with captions that **describe the visible artwork**, rather than random statements about the wrong subject.
- Updates 200 mock profile bios to more varied styles, including some empty and short bios.
- Improves the 200 initial replies to reflect the subject of the parent tweet.
- Adds **301 context-aware replies** to 41 more active posts, especially recent ones, instead of evenly distributing engagement. When the initial full dataset was installed, the database should contain about **501 mock replies** after upgrade.
- Makes 80 newest mock posts appear from Oct 24–28, 2026, so the top of the feed is not filled exclusively with older examples.
- Optionally adds skewed likes and following relationships (`--boost-social`). No existing relationships are removed.
- Keeps usernames, user IDs, post IDs, passwords, media paths, and original student accounts intact.

The illustrations themselves are unchanged and remain stylized. This improves **content realism and activity patterns**, not photographic realism.

## Install in your existing group project

**Important:** This updates existing seeded demo records in place. Back up your local database first if you want a way to undo the changes.

1. Extract the ZIP. Copy its `mock_seed` folder into:

   `F:\USTH-Twitter-Upload\backend\mock_seed`

   Replace matching `seed.js` if prompted; your existing `backend/.env`, `uploads/`, and other project files must be left alone.

2. Open **Windows PowerShell** in Android Studio and run:

   ```powershell
   cd F:\USTH-Twitter-Upload\backend
   node .\mock_seed\seed.js --check
   ```

3. If you seeded only the small demo set, populate the full dataset first:

   ```powershell
   node .\mock_seed\seed.js --full --with-social
   ```

   If you already seeded all 200 profiles, posts and replies, the command safely skips them.

4. Preview the new content operation **without modifying the database**:

   ```powershell
   node .\mock_seed\realism_upgrade.js --check
   ```

5. Apply the update (the command is **not** executed automatically):

   ```powershell
   node .\mock_seed\realism_upgrade.js --apply --boost-social
   ```

   If you do not want additional likes/follows, omit `--boost-social`.

6. Refresh the app or restart Express if needed; posting tables and API code are unchanged. The Home timeline should display more varied content and recent discussions. Opening an active tweet should show multiple context-aware replies.

## Safety

- **No `DROP`, `DELETE`, or `TRUNCATE` operations.** SQL updates match the exact earlier fixture text or bio; manually edited demo content is left alone.
- Only profiles with seeded `demo_uXXX` usernames **and exact seeded `@example.test` email values** are considered. No changes are made to student1, student2, or other real test users.
- Existing post IDs and reply IDs are preserved, so existing likes, reposts and replies remain linked correctly.
- Only missing extra mock replies/social engagements are inserted. The script is **idempotent**: rerunning it does not add the same replies twice.
- Media files remain in `backend/uploads/mock_seed`; no images are rewritten by the upgrade. No external URLs or internet calls.
- Requires `backend/.env` locally; **the ZIP contains no `.env`**, passwords, or MySQL data.
- Works only with local MySQL hostnames `localhost`, `127.0.0.1` or `::1`; refuses `NODE_ENV=production`.
- Uses MySQL transactions for `--apply`. If a SQL operation fails, the current transaction is rolled back.

### Recommended local backup (optional)

Run in **PowerShell**, not at the `mysql>` prompt:

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe" `
  -u root -p `
  --result-file="F:\usth_twitter_before_mock_upgrade.sql" `
  usth_twitter
```

Keep this backup private; it contains your local database records.

## Verify with MySQL

```sql
USE usth_twitter;
SELECT COUNT(*) AS demo_profiles FROM users WHERE username LIKE 'demo_u%';
SELECT COUNT(*) AS demo_posts FROM posts WHERE username LIKE 'demo_u%';
SELECT COUNT(*) AS demo_replies FROM post_replies WHERE username LIKE 'demo_u%';
SELECT p.id, LEFT(p.content, 80) AS tweet, COUNT(r.id) AS replies
FROM posts p
LEFT JOIN post_replies r ON r.post_id=p.id
WHERE p.username LIKE 'demo_u%'
GROUP BY p.id, p.content
ORDER BY replies DESC
LIMIT 10;
```

The final count of mock replies may exceed 501 if you or your teammates have manually added replies. That's normal.

## Notes for the team

- `mock_data.json` retains the original fixture text so the upgrade can identify **only records generated by the earlier seed**. `realistic_data.json` contains the revised text and new conversations.
- `seed.js` has been updated so rerunning the original seed after an upgrade also recognizes the revised text; it will not duplicate the upgraded posts and replies.
- Keep `mock_seed` local and out of normal Git commits unless your team explicitly agrees to version mock fixtures. Do not push `backend/uploads` or `backend/.env`.
- Only run the upgrade against your **local development database**.
- Tested with structural fixture validation and Node.js syntax checks; live MySQL execution must be verified on your machine.
