// モックアップ用の簡易JS。バックエンドは無く、localStorageを「仮のDB」として
// サインアップ・フォロー・コメントの状態をブラウザ内だけで再現する。
// クリックのたびに動的要素が増える(コメント返信など)ため、イベントは
// document への委譲(delegation)で登録し、後から追加した要素にも効くようにしている。

const STORAGE_KEYS = {
  currentUser: "mock_current_user",
  follows: "mock_follows",
  comments: "mock_comments",
};

const DEFAULT_USER = { id: "taro_yamada", displayName: "山田 太郎", initial: "太" };
const SEED_FOLLOWS = ["hanako_sato", "ken_takahashi"];

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

function getComments() {
  const raw = localStorage.getItem(STORAGE_KEYS.comments);
  return raw ? JSON.parse(raw) : [];
}

function addComment(comment) {
  const comments = getComments();
  comments.push(comment);
  localStorage.setItem(STORAGE_KEYS.comments, JSON.stringify(comments));
  return comments;
}

// --- 見た目の初期化・同期 ---

function syncHeaderIdentity() {
  const user = getCurrentUser();
  document.querySelectorAll("[data-me-avatar]").forEach((el) => {
    el.textContent = user.initial;
  });
  document.querySelectorAll("[data-me-name]").forEach((el) => {
    el.textContent = user.displayName;
  });
}

function syncFollowButtons() {
  const followed = getFollowedIds();
  document.querySelectorAll(".btn-follow[data-user-id]").forEach((btn) => {
    applyFollowState(btn, followed.includes(btn.dataset.userId));
  });
}

function applyFollowState(btn, isFollowing) {
  btn.classList.toggle("is-following", isFollowing);
  btn.textContent = isFollowing ? "フォロー中" : "フォローする";
}

function renderStoredComments() {
  const listRoot = document.getElementById("commentList");
  if (!listRoot) return;

  const postId = document.querySelector("[data-post-id]")?.dataset.postId;
  if (!postId) return;

  const stored = getComments().filter((c) => c.postId === postId);
  let addedCount = 0;
  stored.forEach((comment) => {
    appendCommentToDom(comment, { persist: false });
    addedCount += 1;
  });
  if (addedCount > 0) {
    bumpCommentCount(addedCount);
  }
}

function bumpCommentCount(delta) {
  const counter = document.querySelector("[data-comment-count]");
  if (!counter) return;
  counter.textContent = String((parseInt(counter.textContent, 10) || 0) + delta);
}

function findRepliesContainer(parentId) {
  const parentComment = document.querySelector(`.comment[data-comment-id="${parentId}"]`);
  if (!parentComment) return null;
  const commentBody = parentComment.querySelector(":scope > .comment-row > .comment-body");
  if (!commentBody) return null;
  let repliesEl = commentBody.querySelector(":scope > .comment-replies");
  if (!repliesEl) {
    repliesEl = document.createElement("div");
    repliesEl.className = "comment-replies";
    commentBody.appendChild(repliesEl);
  }
  return repliesEl;
}

function appendCommentToDom(comment, { persist }) {
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
          <span class="sub">たった今</span>
        </div>
        <p class="post-text"></p>
        <div class="post-actions">
          <button type="button" data-reply-toggle="${replyFormId}">返信</button>
          <button class="like-btn" type="button"><span class="icon">♡</span><span class="like-count">0</span></button>
        </div>
        <form class="reply-form" id="${replyFormId}" data-parent-id="${comment.id}">
          <textarea placeholder="返信を入力"></textarea>
          <button type="submit" class="btn btn-outline">送信</button>
        </form>
      </div>
    </div>
  `;
  wrapper.querySelector(".post-text").textContent = comment.text;

  if (comment.parentId) {
    const container = findRepliesContainer(comment.parentId);
    if (container) {
      container.appendChild(wrapper);
    }
  } else {
    document.getElementById("commentList").appendChild(wrapper);
  }

  if (persist) {
    addComment(comment);
  }
}

// --- イベント委譲 ---

function handleClick(event) {
  const followBtn = event.target.closest(".btn-follow[data-user-id]");
  if (followBtn) {
    const isFollowing = toggleFollow(followBtn.dataset.userId);
    document.querySelectorAll(`.btn-follow[data-user-id="${followBtn.dataset.userId}"]`).forEach((btn) => {
      applyFollowState(btn, isFollowing);
    });
    return;
  }

  const likeBtn = event.target.closest(".like-btn");
  if (likeBtn) {
    const countEl = likeBtn.querySelector(".like-count");
    let count = parseInt(countEl.textContent, 10) || 0;
    const liked = likeBtn.classList.toggle("is-liked");
    countEl.textContent = liked ? count + 1 : count - 1;
    return;
  }

  const replyToggleBtn = event.target.closest("[data-reply-toggle]");
  if (replyToggleBtn) {
    const form = document.getElementById(replyToggleBtn.dataset.replyToggle);
    if (form) form.classList.toggle("is-open");
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
    const user = {
      id: `user-${Date.now()}`,
      displayName,
      initial: displayName.charAt(0) || "?",
    };
    setCurrentUser(user);
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

  if (form.matches(".comment-compose, .reply-form")) {
    event.preventDefault();
    const textarea = form.querySelector("textarea");
    const text = textarea.value.trim();
    if (!text) return;

    const postId = document.querySelector("[data-post-id]")?.dataset.postId;
    const user = getCurrentUser();
    const comment = {
      id: `c-${Date.now()}`,
      postId,
      parentId: form.dataset.parentId || null,
      authorInitial: user.initial,
      authorName: user.displayName,
      text,
    };

    appendCommentToDom(comment, { persist: true });
    bumpCommentCount(1);
    textarea.value = "";
    if (form.classList.contains("reply-form")) {
      form.classList.remove("is-open");
    }
  }
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
  syncFollowButtons();
  renderStoredComments();
  initCharCounters();
  document.addEventListener("click", handleClick);
  document.addEventListener("submit", handleSubmit);
});
