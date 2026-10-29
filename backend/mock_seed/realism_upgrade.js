'use strict';
/**
 * Upgrade ONLY the synthetic demo_uXXX seed accounts/posts/replies.
 * Keeps all primary keys, existing human users, passwords, media files, and user-created posts.
 * Default is read-only (--check); --apply required for changes.
 * Requires the existing mock_seed/seed.js and mock_data.json package.
 */
const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');
const dotenv = require('dotenv');

const backendDir = path.resolve(__dirname, '..');
dotenv.config({path:path.join(backendDir,'.env'),quiet:true});
const BEFORE = JSON.parse(fs.readFileSync(path.join(__dirname,'mock_data.json'),'utf8'));
const AFTER = JSON.parse(fs.readFileSync(path.join(__dirname,'realistic_data.json'),'utf8'));
const opts = new Set(process.argv.slice(2));
const valid = new Set(['--check','--apply','--boost-social','--help']);
if ([...opts].some(o=>!valid.has(o))) throw new Error('Unknown option; use --help');
if (opts.has('--help')) {
    console.log('Usage: node .\\mock_seed\\realism_upgrade.js [--check] | --apply [--boost-social]\n' +
       '  --check: read-only preview, default\n' +
       '  --apply: update only original demo seed records and add contextual replies\n' +
       '  --boost-social: with --apply, add example likes/follows/reposts; no deletions\n' +
       'No files are modified and no existing rows are deleted. Make a DB backup first.');
    process.exit(0);
}
if (opts.has('--check') && opts.has('--apply')) throw new Error('Use either --check or --apply');
if (opts.has('--boost-social') && !opts.has('--apply')) throw new Error('--boost-social requires --apply');
const apply = opts.has('--apply');
const beforeUsers=new Map(BEFORE.users.map(x=>[x.username,x]));
const beforePosts=new Map(BEFORE.posts.map(x=>[x.key,x]));
const afterPosts=new Map(AFTER.posts.map(x=>[x.key,x]));
const afterUsers=new Map(AFTER.profiles.map(x=>[x.username,x]));
const afterReplies=new Map(AFTER.replies.map(x=>[x.key,x]));
const demoHandles=new Set(BEFORE.users.map(x=>x.username));
function validateFixture() {
  if (BEFORE.users.length!==200 || BEFORE.posts.length!==200 || BEFORE.replies.length!==200) throw new Error('Expected original 200/200/200 fixtures');
  if(AFTER.profiles.length!==200 || AFTER.posts.length!==200 || AFTER.replies.length!==200) throw new Error('Unexpected revised fixture lengths');
  for (const p of AFTER.posts) {
    const old=beforePosts.get(p.key);
    if (!old || p.username!==old.username || p.mediaUrl!==old.mediaUrl || p.content.length>280) throw new Error('Invalid revised post: '+p.key);
  }
  for (const r of [...AFTER.replies,...AFTER.extraReplies]) {
    if (!afterPosts.has(r.postKey) || !demoHandles.has(r.username) || r.content.length>280) throw new Error('Invalid revised reply '+r.key);
  }
}
function requireLocalEnv(){
  if (!['localhost','127.0.0.1','::1'].includes(process.env.DB_HOST)) throw new Error('Only local development DB_HOST is allowed');
  if (!process.env.DB_NAME || !process.env.DB_USER) throw new Error('Missing backend .env database settings');
  if (process.env.NODE_ENV==='production') throw new Error('Never run mock seed against production');
}
function rngFromSeed(n){let state=n>>>0;return ()=>{state^=state<<13;state^=state>>>17;state^=state<<5;return ((state>>>0)/4294967296)};}
async function main(){
 validateFixture(); requireLocalEnv();
 const conn=await mysql.createConnection({host:process.env.DB_HOST,port:Number(process.env.DB_PORT||3306),user:process.env.DB_USER,password:process.env.DB_PASSWORD,database:process.env.DB_NAME,charset:'utf8mb4'});
 const stats={demoProfilesMatched:0,biosUpdated:0,postsMatched:0,postsUpdated:0,repliesMatched:0,repliesUpdated:0,extraRepliesAdded:0,likesAdded:0,followsAdded:0,repostsAdded:0,customizedRowsSkipped:0};
 const userIds=new Map(),postIds=new Map();
 try {
   if(apply) await conn.beginTransaction();
   try {
    for(const user of AFTER.profiles){
      const previous=beforeUsers.get(user.username);
      if(!previous || previous.email!==user.email) throw new Error('Unexpected account mapping '+user.username);
      const [rows]=await conn.execute('SELECT id,email,bio FROM users WHERE username=? LIMIT 1',[user.username]);
      if(!rows.length)continue;
      if(rows[0].email!==previous.email) {stats.customizedRowsSkipped++;continue;}
      stats.demoProfilesMatched++;userIds.set(user.username,rows[0].id);
      if(rows[0].bio===previous.bio && previous.bio!==user.bio){
         if(apply){const [q]=await conn.execute('UPDATE users SET bio=? WHERE id=? AND bio=?',[user.bio,rows[0].id,previous.bio]);stats.biosUpdated+=q.affectedRows;}else stats.biosUpdated++;
      }else if(rows[0].bio!==user.bio){stats.customizedRowsSkipped++;}
    }
    for(const post of AFTER.posts){
      if(!userIds.has(post.username))continue;
      const previous=beforePosts.get(post.key);
      const [rows]=await conn.execute('SELECT id, content FROM posts WHERE username=? AND (content=? OR content=?) ORDER BY id LIMIT 2',[post.username,previous.content,post.content]);
      if(!rows.length)continue;
      if(rows.length!==1){stats.customizedRowsSkipped++;continue;}
      const row=rows[0];stats.postsMatched++;postIds.set(post.key,row.id);
      if(row.content===previous.content && previous.content!==post.content){
        if(apply){const [q]=await conn.execute('UPDATE posts SET content=?, created_at=? WHERE id=? AND username=? AND content=?',[post.content,post.createdAt,row.id,post.username,previous.content]);stats.postsUpdated+=q.affectedRows;}else stats.postsUpdated++;
      }
    }
    for(const reply of AFTER.replies){
      const previous=BEFORE.replies.find(r=>r.key===reply.key);
      const postId=postIds.get(reply.postKey);
      if(!postId || !userIds.has(reply.username))continue;
      const [rows]=await conn.execute('SELECT id, content FROM post_replies WHERE post_id=? AND username=? AND (content=? OR content=?) ORDER BY id LIMIT 2',[postId,reply.username,previous.content,reply.content]);
      if(!rows.length)continue;
      if(rows.length!==1){stats.customizedRowsSkipped++;continue;}
      stats.repliesMatched++;
      if(rows[0].content===previous.content && previous.content!==reply.content){
        if(apply){const [q]=await conn.execute('UPDATE post_replies SET content=?, created_at=? WHERE id=? AND post_id=? AND username=? AND content=?',[reply.content,reply.createdAt,rows[0].id,postId,reply.username,previous.content]);stats.repliesUpdated+=q.affectedRows;}else stats.repliesUpdated++;
      }
    }
    for(const reply of AFTER.extraReplies){
      const id=postIds.get(reply.postKey);
      if(!id || !userIds.has(reply.username))continue;
      const [rows]=await conn.execute('SELECT id FROM post_replies WHERE post_id=? AND username=? AND content=? LIMIT 1',[id,reply.username,reply.content]);
      if(rows.length)continue;
      if(apply){await conn.execute('INSERT INTO post_replies (post_id,username,content,created_at) VALUES(?,?,?,?)',[id,reply.username,reply.content,reply.createdAt]);}
      stats.extraRepliesAdded++;
    }
    if(opts.has('--boost-social')) {
      const random=rngFromSeed(0x15B7);
      const active=new Set(['post_199','post_200','post_196','post_191','post_186','post_175','post_189','post_182','post_161','post_193']);
      const engagementPosts=AFTER.posts.filter(p=>postIds.has(p.key));
      const handles=[...userIds.keys()];
      // Moderate long-tail popularity; some posts receive much more engagement.
      for(let i=0;i<engagementPosts.length;i++){
        const post=engagementPosts[i]; const count=active.has(post.key)?12+Math.floor(random()*12):(i%5===0?3+Math.floor(random()*5):Math.floor(random()*3));
        const chosen=new Set();
        for(let j=0;j<count;j++){
           const idx=Math.floor(random()*handles.length);const who=handles[idx];
           if(!who || chosen.has(who)||who===post.username)continue;chosen.add(who);
           if(apply){const [q]=await conn.execute('INSERT IGNORE INTO post_likes (user_id,post_id) VALUES(?,?)',[userIds.get(who),postIds.get(post.key)]);stats.likesAdded+=q.affectedRows;}
           else stats.likesAdded++;
        }
      }
      const popular=handles.filter((_,i)=>i%13===0).slice(0,12);
      for(const who of handles){
        for(let j=0;j<(who.endsWith('1')?4:2);j++){
          const target=popular[Math.floor(random()*popular.length)];
          if(!target||target===who)continue;
          if(apply){const [q]=await conn.execute('INSERT IGNORE INTO follows (follower_id,following_id) VALUES(?,?)',[userIds.get(who),userIds.get(target)]);stats.followsAdded+=q.affectedRows;}
          else stats.followsAdded++;
        }
      }
      for(const post of engagementPosts.filter((p,i)=>active.has(p.key) && i%2===0)){
        const who=handles[Math.floor(random()*handles.length)];
        if(!who || who===post.username)continue;
        if(apply){const [q]=await conn.execute('INSERT IGNORE INTO post_reposts (post_id,username) VALUES(?,?)',[postIds.get(post.key),who]);stats.repostsAdded+=q.affectedRows;}
        else stats.repostsAdded++;
      }
    }
    if(apply)await conn.commit();
    console.log(apply?'Updated existing fictional demo data safely.':'Read-only preview (nothing was changed).');
    console.log(stats);
    if (stats.demoProfilesMatched===0) console.log('No demo accounts found. Run node .\\mock_seed\\seed.js --full --with-social first.');
    console.log('Your real test accounts, media files and non-demo posts were not modified.');
   } catch(e){if(apply)await conn.rollback();throw e;}
 } finally {await conn.end();}
}
main().catch(e=>{console.error('Realism upgrade failed:',e.message);process.exitCode=1;});
