// モックアップ用の簡易JS。バックエンドは無く、localStorageを「仮のDB」として
// サインアップ・フォロー・投稿・コメント・いいねの状態をブラウザ内だけで再現する。
// 投稿/コメント一覧はすべてこのファイルの store から描画するデータ駆動方式にして、
// フォロー状態やいいね、編集・削除がどの画面からでも矛盾なく反映されるようにしている。

const STORAGE_KEYS = {
  currentUser: "mock_current_user",
  follows: "mock_follows",
  posts: "mock_posts",
  comments: "mock_comments",
};

const DEFAULT_USER = { id: "taro_yamada", displayName: "山田 太郎", initial: "太" };
const SEED_FOLLOWS = ["hanako_sato", "ken_takahashi"];

const SEED_POSTS = [
  {
    id: "demo-post-1",
    authorId: "taro_yamada",
    authorName: "山田 太郎",
    authorInitial: "太",
    text: "今日は新しいSNSアプリの設計を進めています。要件定義からER図まで一通り整理できました。次はAPI設計を詰めていきます。",
    images: 1,
    timeLabel: "2時間",
    likeCount: 12,
    likedByMe: false,
    deleted: false,
  },
  {
    id: "seed-2",
    authorId: "hanako_sato",
    authorName: "佐藤 花子",
    authorInitial: "花",
    text: "ランチはカレーにしました🍛 午後もがんばります。",
    images: 1,
    timeLabel: "3時間",
    likeCount: 8,
    likedByMe: false,
    deleted: false,
  },
  {
    id: "seed-3",
    authorId: "ichiro_suzuki",
    authorName: "鈴木 一郎",
    authorInitial: "鈴",
    text: "総務からのお知らせです。来週水曜はオフィスの空調点検のため、13時〜15時は少し暑くなるかもしれません。",
    images: 0,
    timeLabel: "5時間",
    likeCount: 20,
    likedByMe: false,
    deleted: false,
  },
  {
    id: "seed-4",
    authorId: "misaki_tanaka",
    authorName: "田中 美咲",
    authorInitial: "美",
    text: "新しいキーボード届いた!打鍵感が最高で仕事がはかどりそう。",
    images: 2,
    timeLabel: "昨日",
    likeCount: 15,
    likedByMe: true,
    deleted: false,
  },
  {
    id: "seed-5",
    authorId: "taro_yamada",
    authorName: "山田 太郎",
    authorInitial: "太",
    text: "週次の定例、今日は資料多めなので少し延長するかもしれません。",
    images: 0,
    timeLabel: "昨日",
    likeCount: 4,
    likedByMe: false,
    deleted: false,
  },
  {
    id: "seed-6",
    authorId: "ken_takahashi",
    authorName: "高橋 健",
    authorInitial: "健",
    text: "レビュー完了しました。いくつかコメント残してるので確認お願いします🙏",
    images: 0,
    timeLabel: "6時間",
    likeCount: 6,
    likedByMe: false,
    deleted: false,
  },
];

const SEED_COMMENTS = [
  {
    id: "c1",
    postId: "demo-post-1",
    parentId: null,
    authorId: "hanako_sato",
    authorName: "佐藤 花子",
    authorInitial: "花",
    text: "いいですね!ER図見てみたいです。",
    timeLabel: "1時間",
    likeCount: 2,
    likedByMe: false,
    deleted: false,
  },
  {
    id: "c1-r1",
    postId: "demo-post-1",
    parentId: "c1",
    authorId: "taro_yamada",
    authorName: "山田 太郎",
    authorInitial: "太",
    text: "ありがとうございます、まとまったら共有しますね!",
    timeLabel: "40分",
    likeCount: 1,
    likedByMe: false,
    deleted: false,
  },
  {
    id: "c2",
    postId: "demo-post-1",
    parentId: null,
    authorId: "ichiro_suzuki",
    authorName: "鈴木 一郎",
    authorInitial: "鈴",
    text: "画面設計はFigmaとかで作る予定ですか?",
    timeLabel: "30分",
    likeCount: 0,
    likedByMe: false,
    deleted: false,
  },
];

// --- ストレージ ---

function getCurrentUser() {
  const raw = localStorage.getItem(STORAGE_KEYS.currentUser);
  return raw ? JSON.parse(raw) : DEFAULT_USER;
}

function setCurrentUser(user) {
  localStorage.setItem(STORAGE_KEYS.currentUser, JSON.stringify(user));
}

function getFollowedIds() {
  const raw = localStorage.getItem(STORAGE_KEYS.follows);
  if (raw) return JSON.parse(raw);
  localStorage.setItem(STORAGE_KEYS.follows, JSON.stringify(SEED_FOLLOWS));
  return SEED_FOLLOWS.slice();
}

function toggleFollow(userId) {
  const ids = getFollowedIds();
  const idx = ids.indexOf(userId);
  if (idx === -1) {
    ids.push(userId);
  } else {
    ids.splice(idx, 1);
  }
  localStorage.setItem(STORAGE_KEYS.follows, JSON.stringify(ids));
  return ids.includes(userId);
}

function getPosts() {
  const raw = localStorage.getItem(STORAGE_KEYS.posts);
  if (raw) return JSON.parse(raw);
  savePosts(SEED_POSTS);
  return SEED_POSTS.map((p) => ({ ...p }));
}

function savePosts(posts) {
  localStorage.setItem(STORAGE_KEYS.posts, JSON.stringify(posts));
}

function addPost(post) {
  const posts = getPosts();
  posts.unshift(post);
  savePosts(posts);
  return posts;
}

function updatePost(id, patch) {
  const posts = getPosts();
  const idx = posts.findIndex((p) => p.id === id);
  if (idx === -1) return null;
  posts[idx] = { ...posts[idx], ...patch };
  savePosts(posts);
  return posts[idx];
}

function getComments() {
  const raw = localStorage.getItem(STORAGE_KEYS.comments);
  if (raw) return JSON.parse(raw);
  saveComments(SEED_COMMENTS);
  return SEED_COMMENTS.map((c) => ({ ...c }));
}

function saveComments(list) {
  localStorage.setItem(STORAGE_KEYS.comments, JSON.stringify(list));
}

function addComment(comment) {
  const list = getComments();
  list.push(comment);
  saveComments(list);
  return list;
}

function updateComment(id, patch) {
  const list = getComments();
  const idx = list.findIndex((c) => c.id === id);
  if (idx === -1) return null;
  list[idx] = { ...list[idx], ...patch };
  saveComments(list);
  return list[idx];
}

function deleteCommentCascade(id) {
  const list = getComments();
  const toDelete = new Set([id]);
  let changed = true;
  while (changed) {
    changed = false;
    list.forEach((c) => {
      if (toDelete.has(c.parentId) && !toDelete.has(c.id)) {
        toDelete.add(c.id);
        changed = true;
      }
    });
  }
  const next = list.map((c) => (toDelete.has(c.id) ? { ...c, deleted: true } : c));
  saveComments(next);
}

function getCommentCountForPost(postId) {
  return getComments().filter((c) => c.postId === postId && !c.deleted).length;
}

// --- 描画: タイムライン/プロフィールの投稿カード ---

function imagesMarkup(count) {
  if (!count) return "";
  if (count >= 2) {
    return '<div class="post-images"><div class="post-image">画像1</div><div class="post-image">画像2</div></div>';
  }
  return '<div class="post-images single"><div class="post-image">画像イメージ</div></div>';
}

function renderPostCard(post, currentUser) {
  const isMine = post.authorId === currentUser.id;
  const followBtn = isMine
    ? ""
    : `<button class="btn btn-follow" type="button" style="margin-left:auto;" data-user-id="${post.authorId}">フォローする</button>`;
  const commentCount = post.id === "demo-post-1" ? getCommentCountForPost(post.id) : post.commentCount || 0;
  const textAndImages = `<p class="post-text"></p>${imagesMarkup(post.images)}`;
  const bodyLink = post.id === "demo-post-1"
    ? `<a href="post-detail.html" style="display:block; color:inherit;">${textAndImages}</a>`
    : `<div>${textAndImages}</div>`;

  const el = document.createElement("article");
  el.className = "post";
  el.dataset.postCardId = post.id;
  el.innerHTML = `
    <div class="avatar">${post.authorInitial}</div>
    <div class="post-body">
      <div class="post-head">
        <span class="name">${post.authorName}</span>
        <span class="sub">${post.timeLabel}</span>
        ${followBtn}
      </div>
      ${bodyLink}
      <div class="post-actions">
        <button class="comment-btn" type="button"><span class="icon">💬</span><span>${commentCount}</span></button>
        <button class="like-btn${post.likedByMe ? " is-liked" : ""}" type="button" data-like-post="${post.id}"><span class="icon">${post.likedByMe ? "♥" : "♡"}</span><span class="like-count">${post.likeCount}</span></button>
      </div>
    </div>
  `;
  el.querySelector(".post-text").textContent = post.text;
  return el;
}

function renderTimeline() {
  const feedAll = document.getElementById("feedAll");
  const feedFollowing = document.getElementById("feedFollowing");
  if (!feedAll || !feedFollowing) return;

  const currentUser = getCurrentUser();
  const followed = getFollowedIds();
  const posts = getPosts().filter((p) => !p.deleted);

  feedAll.innerHTML = "";
  posts.forEach((post) => feedAll.appendChild(renderPostCard(post, currentUser)));

  const followingPosts = posts.filter(
    (p) => p.authorId !== currentUser.id && followed.includes(p.authorId)
  );
  feedFollowing.innerHTML = "";
  followingPosts.forEach((post) => feedFollowing.appendChild(renderPostCard(post, currentUser)));
  if (followingPosts.length === 0) {
    const note = document.createElement("p");
    note.className = "empty-note";
    note.textContent = "フォロー中のユーザーの投稿はここまでです。「全体」タブでもっと見つけましょう。";
    feedFollowing.appendChild(note);
  }
}

function renderProfilePosts() {
  const list = document.getElementById("profilePostList");
  if (!list) return;
  const currentUser = getCurrentUser();
  const posts = getPosts().filter((p) => !p.deleted && p.authorId === currentUser.id);
  list.innerHTML = "";
  if (posts.length === 0) {
    const note = document.createElement("p");
    note.className = "empty-note";
    note.textContent = "まだ投稿がありません。";
    list.appendChild(note);
    return;
  }
  posts.forEach((post) => list.appendChild(renderPostCard(post, currentUser)));
}

// --- 描画: 投稿詳細 ---

function renderPostDetail() {
  const main = document.getElementById("postDetailMain");
  if (!main) return;
  const postId = main.dataset.postId;
  const currentUser = getCurrentUser();
  const post = getPosts().find((p) => p.id === postId);
  const commentsSection = document.getElementById("commentsSection");

  if (!post || post.deleted) {
    main.innerHTML = '<div class="post-detail-main"><p class="empty-note">この投稿は削除されました。</p></div>';
    if (commentsSection) commentsSection.style.display = "none";
    return;
  }
  if (commentsSection) commentsSection.style.display = "";

  const isMine = post.authorId === currentUser.id;
  const ownerActions = isMine
    ? `<div class="post-owner-actions">
         <button class="link-btn" type="button" data-post-edit="${post.id}">編集</button>
         <button class="link-btn" type="button" data-post-delete="${post.id}">削除</button>
       </div>`
    : "";

  main.className = "";
  main.innerHTML = `
    <div class="post-detail-main">
      <div class="post-head">
        <div class="avatar">${post.authorInitial}</div>
        <div>
          <div><span class="name">${post.authorName}</span></div>
          <div class="sub">@${post.authorId}</div>
        </div>
        ${ownerActions}
      </div>
      <p class="post-text"></p>
      ${imagesMarkup(post.images)}
      <div class="timestamp">${post.timeLabel}</div>
      <div class="post-stats">
        <span><strong>${getCommentCountForPost(post.id)}</strong> コメント</span>
        <span><strong>${post.likeCount}</strong> いいね</span>
      </div>
      <div class="post-actions">
        <button class="comment-btn" type="button"><span class="icon">💬</span><span>コメント</span></button>
        <button class="like-btn${post.likedByMe ? " is-liked" : ""}" type="button" data-like-post="${post.id}"><span class="icon">${post.likedByMe ? "♥" : "♡"}</span><span class="like-count">${post.likeCount}</span></button>
      </div>
    </div>
  `;
  main.querySelector(".post-text").textContent = post.text;
}

// --- 描画: コメント(ネスト) ---

function commentTemplate(comment, currentUser) {
  const isMine = comment.authorId === currentUser.id;
  const followBtn = isMine
    ? ""
    : `<button class="btn btn-follow" type="button" style="margin-left:auto;" data-user-id="${comment.authorId}">フォローする</button>`;
  const ownerActions = isMine
    ? `<div class="post-owner-actions">
         <button class="link-btn" type="button" data-comment-edit="${comment.id}">編集</button>
         <button class="link-btn" type="button" data-comment-delete="${comment.id}">削除</button>
       </div>`
    : "";
  const replyFormId = `reply-form-${comment.id}`;

  const wrapper = document.createElement("div");
  wrapper.className = "comment";
  wrapper.dataset.commentId = comment.id;
  wrapper.innerHTML = `
    <div class="comment-row">
      <div class="avatar sm">${comment.authorInitial}</div>
      <div class="comment-body">
        <div class="post-head">
          <span class="name">${comment.authorName}</span>
          <span class="sub">${comment.timeLabel}</span>
          ${followBtn}
          ${ownerActions}
        </div>
        <p class="post-text"></p>
        <div class="post-actions">
          <button type="button" data-reply-toggle="${replyFormId}">返信</button>
          <button class="like-btn${comment.likedByMe ? " is-liked" : ""}" type="button" data-like-comment="${comment.id}"><span class="icon">${comment.likedByMe ? "♥" : "♡"}</span><span class="like-count">${comment.likeCount}</span></button>
        </div>
        <form class="reply-form" id="${replyFormId}" data-parent-id="${comment.id}">
          <textarea placeholder="返信を入力"></textarea>
          <button type="submit" class="btn btn-outline">送信</button>
        </form>
        <div class="comment-replies"></div>
      </div>
    </div>
  `;
  wrapper.querySelector(".post-text").textContent = comment.text;
  return wrapper;
}

function renderComments() {
  const root = document.getElementById("commentList");
  if (!root) return;
  const postId = document.getElementById("postDetailMain")?.dataset.postId;
  if (!postId) return;

  const currentUser = getCurrentUser();
  const all = getComments().filter((c) => c.postId === postId && !c.deleted);
  const byParent = new Map();
  all.forEach((c) => {
    const key = c.parentId || "";
    if (!byParent.has(key)) byParent.set(key, []);
    byParent.get(key).push(c);
  });

  root.innerHTML = "";

  function appendChildren(parentKey, container) {
    const children = byParent.get(parentKey) || [];
    children.forEach((comment) => {
      const node = commentTemplate(comment, currentUser);
      container.appendChild(node);
      const repliesContainer = node.querySelector(":scope > .comment-row > .comment-body > .comment-replies");
      appendChildren(comment.id, repliesContainer);
    });
  }

  appendChildren("", root);
}

// --- インライン編集 ---

function enterEditMode(textEl, currentText, onSave) {
  const textarea = document.createElement("textarea");
  textarea.value = currentText;
  textarea.style.width = "100%";
  textarea.style.minHeight = "60px";
  textarea.style.fontFamily = "inherit";
  textarea.style.fontSize = "14px";
  textarea.style.padding = "8px";
  textarea.style.borderRadius = "8px";
  textarea.style.border = "1px solid var(--color-border)";

  const controls = document.createElement("div");
  controls.style.display = "flex";
  controls.style.gap = "8px";
  controls.style.margin = "6px 0";

  const saveBtn = document.createElement("button");
  saveBtn.type = "button";
  saveBtn.className = "btn btn-primary";
  saveBtn.textContent = "保存";

  const cancelBtn = document.createElement("button");
  cancelBtn.type = "button";
  cancelBtn.className = "btn btn-outline";
  cancelBtn.textContent = "キャンセル";

  controls.appendChild(saveBtn);
  controls.appendChild(cancelBtn);

  textEl.replaceWith(textarea);
  textarea.after(controls);

  saveBtn.addEventListener("click", () => {
    const newText = textarea.value.trim();
    controls.remove();
    if (newText) onSave(newText);
  });
  cancelBtn.addEventListener("click", () => {
    textarea.replaceWith(textEl);
    controls.remove();
  });
}

// --- イベント委譲 ---

function handleClick(event) {
  const followBtn = event.target.closest(".btn-follow[data-user-id]");
  if (followBtn) {
    const isFollowing = toggleFollow(followBtn.dataset.userId);
    document.querySelectorAll(`.btn-follow[data-user-id="${followBtn.dataset.userId}"]`).forEach((btn) => {
      btn.classList.toggle("is-following", isFollowing);
      btn.textContent = isFollowing ? "フォロー中" : "フォローする";
    });
    renderTimeline();
    return;
  }

  const likePostBtn = event.target.closest("[data-like-post]");
  if (likePostBtn) {
    const posts = getPosts();
    const post = posts.find((p) => p.id === likePostBtn.dataset.likePost);
    if (post) {
      const updated = updatePost(post.id, {
        likedByMe: !post.likedByMe,
        likeCount: post.likedByMe ? post.likeCount - 1 : post.likeCount + 1,
      });
      document.querySelectorAll(`[data-like-post="${post.id}"]`).forEach((btn) => {
        btn.classList.toggle("is-liked", updated.likedByMe);
        btn.querySelector(".icon").textContent = updated.likedByMe ? "♥" : "♡";
        btn.querySelector(".like-count").textContent = updated.likeCount;
      });
    }
    return;
  }

  const likeCommentBtn = event.target.closest("[data-like-comment]");
  if (likeCommentBtn) {
    const comments = getComments();
    const comment = comments.find((c) => c.id === likeCommentBtn.dataset.likeComment);
    if (comment) {
      const updated = updateComment(comment.id, {
        likedByMe: !comment.likedByMe,
        likeCount: comment.likedByMe ? comment.likeCount - 1 : comment.likeCount + 1,
      });
      likeCommentBtn.classList.toggle("is-liked", updated.likedByMe);
      likeCommentBtn.querySelector(".icon").textContent = updated.likedByMe ? "♥" : "♡";
      likeCommentBtn.querySelector(".like-count").textContent = updated.likeCount;
    }
    return;
  }

  const replyToggleBtn = event.target.closest("[data-reply-toggle]");
  if (replyToggleBtn) {
    const form = document.getElementById(replyToggleBtn.dataset.replyToggle);
    if (form) form.classList.toggle("is-open");
    return;
  }

  const postEditBtn = event.target.closest("[data-post-edit]");
  if (postEditBtn) {
    const post = getPosts().find((p) => p.id === postEditBtn.dataset.postEdit);
    const textEl = document.querySelector("#postDetailMain .post-text");
    if (post && textEl) {
      enterEditMode(textEl, post.text, (newText) => {
        updatePost(post.id, { text: newText });
        renderPostDetail();
        renderTimeline();
        renderProfilePosts();
      });
    }
    return;
  }

  const postDeleteBtn = event.target.closest("[data-post-delete]");
  if (postDeleteBtn) {
    updatePost(postDeleteBtn.dataset.postDelete, { deleted: true });
    renderPostDetail();
    renderTimeline();
    renderProfilePosts();
    return;
  }

  const commentEditBtn = event.target.closest("[data-comment-edit]");
  if (commentEditBtn) {
    const comment = getComments().find((c) => c.id === commentEditBtn.dataset.commentEdit);
    const commentEl = document.querySelector(`.comment[data-comment-id="${commentEditBtn.dataset.commentEdit}"]`);
    const textEl = commentEl?.querySelector(":scope > .comment-row > .comment-body > .post-text");
    if (comment && textEl) {
      enterEditMode(textEl, comment.text, (newText) => {
        updateComment(comment.id, { text: newText });
        renderComments();
      });
    }
    return;
  }

  const commentDeleteBtn = event.target.closest("[data-comment-delete]");
  if (commentDeleteBtn) {
    deleteCommentCascade(commentDeleteBtn.dataset.commentDelete);
    renderComments();
    renderPostDetail();
    renderTimeline();
    return;
  }

  const tabBtn = event.target.closest(".tab[data-feed]");
  if (tabBtn) {
    const tabGroup = tabBtn.closest("[data-tabs]");
    tabGroup.querySelectorAll(".tab").forEach((t) => t.classList.remove("is-active"));
    tabBtn.classList.add("is-active");
    const targetFeed = tabBtn.dataset.feed;
    document.querySelectorAll("[data-feed-panel]").forEach((panel) => {
      panel.style.display = panel.dataset.feedPanel === targetFeed ? "" : "none";
    });
  }
}

function handleSubmit(event) {
  const form = event.target;

  if (form.id === "signupForm") {
    event.preventDefault();
    const displayName = form.querySelector("#displayName").value.trim() || "名無しユーザー";
    const id = `user-${Date.now()}`;
    setCurrentUser({ id, displayName, initial: displayName.charAt(0) || "?" });
    location.href = "timeline.html";
    return;
  }

  if (form.id === "loginForm") {
    event.preventDefault();
    if (!localStorage.getItem(STORAGE_KEYS.currentUser)) {
      setCurrentUser(DEFAULT_USER);
    }
    location.href = "timeline.html";
    return;
  }

  if (form.id === "composeForm") {
    event.preventDefault();
    const textarea = form.querySelector("textarea");
    const text = textarea.value.trim();
    if (!text) return;
    const user = getCurrentUser();
    addPost({
      id: `p-${Date.now()}`,
      authorId: user.id,
      authorName: user.displayName,
      authorInitial: user.initial,
      text,
      images: 0,
      timeLabel: "たった今",
      likeCount: 0,
      likedByMe: false,
      deleted: false,
    });
    textarea.value = "";
    textarea.dispatchEvent(new Event("input"));
    renderTimeline();
    return;
  }

  if (form.matches("#commentComposeForm, .reply-form")) {
    event.preventDefault();
    const textarea = form.querySelector("textarea");
    const text = textarea.value.trim();
    if (!text) return;

    const postId = document.getElementById("postDetailMain")?.dataset.postId;
    const user = getCurrentUser();
    addComment({
      id: `c-${Date.now()}`,
      postId,
      parentId: form.dataset.parentId || null,
      authorId: user.id,
      authorName: user.displayName,
      authorInitial: user.initial,
      text,
      timeLabel: "たった今",
      likeCount: 0,
      likedByMe: false,
      deleted: false,
    });
    textarea.value = "";
    textarea.dispatchEvent(new Event("input"));
    renderComments();
    renderPostDetail();
    renderTimeline();
  }
}

function syncFollowButtons() {
  const followed = getFollowedIds();
  document.querySelectorAll(".btn-follow[data-user-id]").forEach((btn) => {
    const following = followed.includes(btn.dataset.userId);
    btn.classList.toggle("is-following", following);
    btn.textContent = following ? "フォロー中" : "フォローする";
  });
}

function syncHeaderIdentity() {
  const user = getCurrentUser();
  document.querySelectorAll("[data-me-avatar]").forEach((el) => {
    el.textContent = user.initial;
  });
  document.querySelectorAll("[data-me-name]").forEach((el) => {
    el.textContent = user.displayName;
  });
  document.querySelectorAll("[data-me-handle]").forEach((el) => {
    el.textContent = `@${user.id}`;
  });
}

function initCharCounters() {
  document.querySelectorAll("[data-char-count]").forEach((textarea) => {
    const max = parseInt(textarea.dataset.charCount, 10) || 280;
    const counter = document.querySelector(textarea.dataset.charCountTarget);
    if (!counter) return;
    const update = () => {
      const remaining = max - textarea.value.length;
      counter.textContent = `${remaining}`;
      counter.style.color = remaining < 0 ? "#f4212e" : "";
    };
    textarea.addEventListener("input", update);
    update();
  });
}

document.addEventListener("DOMContentLoaded", () => {
  syncHeaderIdentity();
  renderTimeline();
  renderProfilePosts();
  renderPostDetail();
  renderComments();
  syncFollowButtons();
  initCharCounters();
  document.addEventListener("click", handleClick);
  document.addEventListener("submit", handleSubmit);
});
