// モックアップ用の見た目確認だけを目的とした簡易JS。API通信・永続化は行わない。

function initTabs() {
  document.querySelectorAll("[data-tabs]").forEach((tabGroup) => {
    const tabs = tabGroup.querySelectorAll(".tab");
    tabs.forEach((tab) => {
      tab.addEventListener("click", () => {
        tabs.forEach((t) => t.classList.remove("is-active"));
        tab.classList.add("is-active");

        const targetFeed = tab.dataset.feed;
        document.querySelectorAll("[data-feed-panel]").forEach((panel) => {
          panel.style.display = panel.dataset.feedPanel === targetFeed ? "" : "none";
        });
      });
    });
  });
}

function initLikeButtons() {
  document.querySelectorAll(".like-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      const countEl = btn.querySelector(".like-count");
      let count = parseInt(countEl.textContent, 10) || 0;
      const liked = btn.classList.toggle("is-liked");
      count = liked ? count + 1 : count - 1;
      countEl.textContent = count;
    });
  });
}

function initFollowButtons() {
  document.querySelectorAll(".btn-follow").forEach((btn) => {
    btn.addEventListener("click", () => {
      const following = btn.classList.toggle("is-following");
      btn.textContent = following ? "フォロー中" : "フォローする";
    });
  });
}

function initReplyToggles() {
  document.querySelectorAll("[data-reply-toggle]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const targetId = btn.dataset.replyToggle;
      const form = document.getElementById(targetId);
      if (form) {
        form.classList.toggle("is-open");
      }
    });
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
  initTabs();
  initLikeButtons();
  initFollowButtons();
  initReplyToggles();
  initCharCounters();
});
