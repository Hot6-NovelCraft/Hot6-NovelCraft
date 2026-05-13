// ========================
// NovelCraft 공통 유틸리티
// ========================

const API_BASE = '/api';

// ── 토큰 관리 ──
const Auth = {
  getToken: () => localStorage.getItem('accessToken'),
  setToken: (token) => localStorage.setItem('accessToken', token),
  removeToken: () => localStorage.removeItem('accessToken'),
  isLoggedIn: () => !!localStorage.getItem('accessToken'),
  getUser: () => {
    const u = localStorage.getItem('user');
    return u ? JSON.parse(u) : null;
  },
  setUser: (user) => localStorage.setItem('user', JSON.stringify(user)),
  removeUser: () => localStorage.removeItem('user'),
  logout: async () => {
    const token = Auth.getToken();
    if (token) {
      try {
        await api.post('/auth/logout', {}, true);
      } catch {}
    }
    Auth.removeToken();
    Auth.removeUser();
    window.location.href = '/login.html';
  }
};

// ── API 공통 fetch ──
const api = {
    request: async (method, path, body = null, auth = false) => {
        const headers = { 'Content-Type': 'application/json' };
        const token = Auth.getToken();
        if ((auth || Auth.isLoggedIn()) && token) {
            headers['Authorization'] = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
        }
        const opts = { method, headers };
        if (body) opts.body = JSON.stringify(body);
        const res = await fetch(API_BASE + path, opts);
        const data = await res.json();

        // ✅ AccessToken 만료 시 Refresh-Token으로 재시도
        if (res.status === 401 && data.message?.includes('만료')) {
            const refreshToken = localStorage.getItem('refreshToken');
            if (!refreshToken) {
                Auth.removeToken(); Auth.removeUser();
                window.location.href = `/login.html?redirect=${encodeURIComponent(location.pathname)}`;
                return;
            }
            // Refresh-Token 헤더로 재요청
            const currentToken = Auth.getToken();
            const retryHeaders = { 'Content-Type': 'application/json',
                ...(currentToken && { 'Authorization': `Bearer ${currentToken}` }),
                'Refresh-Token': `Bearer ${refreshToken}`
            };
            const retryOpts = { method, headers: retryHeaders };
            if (body) retryOpts.body = JSON.stringify(body);
            const retryRes = await fetch(API_BASE + path, retryOpts);

            // 새 AccessToken 응답 헤더에서 저장
            const newToken = retryRes.headers.get('Authorization');
            if (newToken) {
                Auth.setToken(newToken.replace('Bearer ', ''));
            }

            const retryData = await retryRes.json();
            if (!retryRes.ok) {
                // Refresh도 만료 → 로그아웃
                Auth.removeToken(); Auth.removeUser();
                window.location.href = `/login.html?redirect=${encodeURIComponent(location.pathname)}`;
                return;
            }
            return retryData.data;
        }

        if (!res.ok) throw new Error(data.message || '요청 실패');
        return data.data;
    },
  get: (path, auth = false) => api.request('GET', path, null, auth),
  post: (path, body, auth = false) => api.request('POST', path, body, auth),
  patch: (path, body, auth = false) => api.request('PATCH', path, body, auth),
  put: (path, body, auth = false) => api.request('PUT', path, body, auth),
  delete: (path, auth = false) => api.request('DELETE', path, null, auth),
};

// ── Toast 알림 ──
const Toast = {
  show: (msg, type = 'success') => {
    const el = document.createElement('div');
    el.className = `nc-toast nc-toast--${type}`;
    el.textContent = msg;
    document.body.appendChild(el);
    setTimeout(() => el.classList.add('show'), 10);
    setTimeout(() => { el.classList.remove('show'); setTimeout(() => el.remove(), 300); }, 3000);
  },
  success: (msg) => Toast.show(msg, 'success'),
  error: (msg) => Toast.show(msg, 'error'),
  info: (msg) => Toast.show(msg, 'info'),
};

// ── Modal 유틸 ──
const Modal = {
  open: (id) => {
    const el = document.getElementById(id);
    if (el) { el.classList.add('active'); document.body.style.overflow = 'hidden'; }
  },
  close: (id) => {
    const el = document.getElementById(id);
    if (el) { el.classList.remove('active'); document.body.style.overflow = ''; }
  },
  closeAll: () => {
    document.querySelectorAll('.nc-modal.active').forEach(m => m.classList.remove('active'));
    document.body.style.overflow = '';
  }
};

// ── 날짜 포맷 ──
const formatDate = (dateStr) => {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  return `${d.getFullYear()}.${String(d.getMonth()+1).padStart(2,'0')}.${String(d.getDate()).padStart(2,'0')}`;
};

// ── 숫자 포맷 ──
const formatNum = (n) => n >= 10000 ? `${(n/10000).toFixed(1)}만` : n?.toLocaleString() ?? '0';

// ── 장르 뱃지 색상 ──
const GENRE_COLORS = {
  '판타지': '#7c3aed', '로맨스': '#db2777', '현대': '#0891b2',
  '무협': '#92400e', '공포': '#1f2937', '역사': '#065f46',
  'SF': '#1d4ed8', '일상': '#059669', '추리': '#7c2d12',
  '현대판타지': '#4f46e5', '로판': '#be185d',
};
const genreBadge = (genre) => {
  const c = GENRE_COLORS[genre] || '#6b7280';
  return `<span class="nc-badge" style="background:${c}20;color:${c};border:1px solid ${c}40">${genre}</span>`;
};

// ── 소설 상태 뱃지 ──
const STATUS_MAP = {
  ONGOING: { label: '연재중', color: '#10b981' },
  COMPLETED: { label: '완결', color: '#6366f1' },
  HIATUS: { label: '중단', color: '#f59e0b' },
  PENDING: { label: '보류', color: '#9ca3af' },
};
const statusBadge = (status) => {
  const s = STATUS_MAP[status] || { label: status, color: '#9ca3af' };
  return `<span class="nc-badge" style="background:${s.color}20;color:${s.color};border:1px solid ${s.color}40">${s.label}</span>`;
};

// ── 네비게이션 초기화 ──
function initNav() {
  const nav = document.getElementById('nc-nav');
  if (!nav) return;
  const user = Auth.getUser();
  const isLoggedIn = Auth.isLoggedIn();

  const pointBadge = isLoggedIn ? `<span class="nc-point-badge" id="nav-point">···P</span>` : '';

  nav.innerHTML = `
    <div class="nc-nav__inner">
      <a href="/index.html" class="nc-nav__logo">
        <span class="logo-icon">✦</span>NovelCraft
      </a>
      <div class="nc-nav__search-wrap" id="nav-search-wrap">
        <input type="text" class="nc-nav__search" id="nav-search" placeholder="소설, 작가, 태그 검색...">
        <div class="nc-search-dropdown" id="search-dropdown"></div>
      </div>
      <div class="nc-nav__right">
        ${pointBadge}
        ${isLoggedIn ? `
          <button class="nc-nav__btn" id="nav-alarm" onclick="window.location.href='/mypage.html?tab=notification'">
            🔔 <span class="nc-alarm-dot" id="alarm-dot" style="display:none"></span>
          </button>
          <button class="nc-nav__btn" onclick="window.location.href='/mypage.html?tab=library'">내 서재</button>
          ${user?.role === 'AUTHOR' || user?.role === 'MENTOR' ? `
            <button class="nc-nav__btn nc-nav__btn--active" onclick="window.location.href='/editor.html'">에디터</button>
          ` : ''}
          ${user?.role === 'MENTOR' ? `
            <button class="nc-nav__btn nc-nav__btn--mentor" onclick="window.location.href='/mentor-dashboard.html'">멘토</button>
          ` : ''}
          <button class="nc-nav__btn nc-nav__btn--mentee" onclick="window.location.href='/mentor-list.html'">멘티</button>
          <a href="/mypage.html" class="nc-nav__avatar">${user?.nickname?.charAt(0) || 'U'}</a>
        ` : `
          <button class="nc-nav__btn" onclick="window.location.href='/login.html'">로그인</button>
          <button class="nc-nav__btn nc-nav__btn--primary" onclick="window.location.href='/signup.html'">회원가입</button>
        `}
      </div>
    </div>
  `;

  // 포인트 로드
  if (isLoggedIn) {
    api.get('/points/balance', true).then(d => {
      const el = document.getElementById('nav-point');
      if (el) el.textContent = `${(d?.balance || 0).toLocaleString()}P`;
    }).catch(() => {});

    // 알림 읽지 않은 수
    api.get('/notifications/unread-count', true).then(d => {
      if (d?.count > 0) {
        const dot = document.getElementById('alarm-dot');
        if (dot) dot.style.display = 'block';
      }
    }).catch(() => {});
  }

  // 검색 기능
  initSearch();
}

function initSearch() {
  const input = document.getElementById('nav-search');
  const dropdown = document.getElementById('search-dropdown');
  if (!input || !dropdown) return;

  let searchTimer;

  input.addEventListener('focus', async () => {
    dropdown.innerHTML = '<div class="nc-search-loading">로딩 중...</div>';
    dropdown.classList.add('active');
    try {
      const [popular, recent] = await Promise.allSettled([
        api.get('/search/keywords/popular'),
        Auth.isLoggedIn() ? api.get('/search/keywords/recent', true) : Promise.resolve([])
      ]);
      let html = '';
      if (recent.status === 'fulfilled' && recent.value?.length) {
        html += `<div class="nc-search-section">내 검색 결과</div>`;
        recent.value.slice(0,5).forEach(k => {
          html += `<div class="nc-search-item" onclick="doSearch('${k}')">${k}</div>`;
        });
      }
      if (popular.status === 'fulfilled' && popular.value?.length) {
        html += `<div class="nc-search-section">인기 검색어</div>`;
        popular.value.slice(0,5).forEach(k => {
          html += `<div class="nc-search-item" onclick="doSearch('${k}')">${k}</div>`;
        });
      }
      dropdown.innerHTML = html || '<div class="nc-search-empty">검색어를 입력하세요</div>';
    } catch {
      dropdown.innerHTML = '<div class="nc-search-empty">검색어를 입력하세요</div>';
    }
  });

  input.addEventListener('input', (e) => {
    clearTimeout(searchTimer);
    const kw = e.target.value.trim();
    if (!kw) return;
    searchTimer = setTimeout(async () => {
      try {
        const res = await api.get(`/search/v2/novels?keyword=${encodeURIComponent(kw)}`);
        let html = `<div class="nc-search-section">검색 결과</div>`;
        if (res?.content?.length) {
          res.content.slice(0,5).forEach(n => {
            html += `<div class="nc-search-item" onclick="window.location.href='/novel-detail.html?id=${n.novelId}'">
              <span>${n.title}</span><span class="nc-search-sub">${n.authorName}</span>
            </div>`;
          });
        } else {
          html += '<div class="nc-search-empty">검색 결과가 없습니다</div>';
        }
        dropdown.innerHTML = html;
      } catch {}
    }, 300);
  });

  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') doSearch(input.value.trim());
  });

  document.addEventListener('click', (e) => {
    if (!e.target.closest('#nav-search-wrap')) {
      dropdown.classList.remove('active');
    }
  });
}

function doSearch(keyword) {
  if (!keyword) return;
  window.location.href = `/search.html?keyword=${encodeURIComponent(keyword)}`;
}

// ── 공통 CSS 인젝션 ──
function injectCommonStyles() {
  const style = document.createElement('style');
  style.textContent = `
    @import url('https://fonts.googleapis.com/css2?family=Noto+Serif+KR:wght@400;700;900&family=Noto+Sans+KR:wght@300;400;500;700&display=swap');

    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

    :root {
      --bg: #0f0f13;
      --bg2: #16161d;
      --bg3: #1e1e28;
      --surface: #1e1e2a;
      --surface2: #252535;
      --border: #2a2a3a;
      --text: #e8e8f0;
      --text2: #a0a0b8;
      --text3: #6a6a80;
      --primary: #7c5af0;
      --primary-light: #9b7ef8;
      --primary-dark: #5b3dd0;
      --accent: #f0c842;
      --accent2: #f06842;
      --success: #10b981;
      --error: #ef4444;
      --warning: #f59e0b;
      --radius: 12px;
      --radius-sm: 8px;
      --shadow: 0 4px 24px rgba(0,0,0,0.4);
      --shadow-lg: 0 8px 48px rgba(0,0,0,0.6);
    }

    html { font-size: 16px; scroll-behavior: smooth; }
    body {
      background: var(--bg);
      color: var(--text);
      font-family: 'Noto Sans KR', sans-serif;
      line-height: 1.6;
      min-height: 100vh;
    }

    /* 네비게이션 */
    #nc-nav {
      position: sticky; top: 0; z-index: 100;
      background: rgba(15,15,19,0.92);
      backdrop-filter: blur(12px);
      border-bottom: 1px solid var(--border);
    }
    .nc-nav__inner {
      max-width: 1280px; margin: 0 auto;
      display: flex; align-items: center; gap: 16px;
      padding: 0 24px; height: 60px;
    }
    .nc-nav__logo {
      font-family: 'Noto Serif KR', serif;
      font-size: 1.25rem; font-weight: 900;
      color: var(--primary-light); text-decoration: none;
      display: flex; align-items: center; gap: 6px;
      white-space: nowrap;
    }
    .logo-icon { color: var(--accent); }
    .nc-nav__search-wrap { flex: 1; max-width: 480px; position: relative; }
    .nc-nav__search {
      width: 100%; padding: 8px 16px;
      background: var(--surface); border: 1px solid var(--border);
      border-radius: 24px; color: var(--text);
      font-size: 0.875rem; outline: none;
      transition: border-color 0.2s;
    }
    .nc-nav__search:focus { border-color: var(--primary); }
    .nc-nav__right { display: flex; align-items: center; gap: 8px; margin-left: auto; }
    .nc-nav__btn {
      padding: 6px 14px; border-radius: 8px;
      border: 1px solid var(--border); background: transparent;
      color: var(--text2); font-size: 0.8rem; cursor: pointer;
      transition: all 0.2s; white-space: nowrap;
    }
    .nc-nav__btn:hover { background: var(--surface); color: var(--text); }
    .nc-nav__btn--primary { background: var(--primary); border-color: var(--primary); color: white; }
    .nc-nav__btn--primary:hover { background: var(--primary-dark); }
    .nc-nav__btn--active { border-color: var(--primary); color: var(--primary-light); }
    .nc-nav__btn--mentor { border-color: var(--accent); color: var(--accent); }
    .nc-nav__btn--mentee { border-color: var(--accent2); color: var(--accent2); }
    .nc-point-badge {
      padding: 4px 12px; border-radius: 20px;
      background: linear-gradient(135deg, var(--accent)20, var(--accent)10);
      border: 1px solid var(--accent)40; color: var(--accent);
      font-size: 0.8rem; font-weight: 700;
    }
    .nc-nav__avatar {
      width: 34px; height: 34px; border-radius: 50%;
      background: linear-gradient(135deg, var(--primary), var(--accent2));
      display: flex; align-items: center; justify-content: center;
      font-weight: 700; font-size: 0.9rem; color: white;
      cursor: pointer; text-decoration: none;
    }
    .nc-alarm-dot {
      width: 8px; height: 8px; border-radius: 50%;
      background: var(--error); position: absolute;
      top: -2px; right: -2px;
    }
    #nav-alarm { position: relative; }

    /* 검색 드롭다운 */
    .nc-search-dropdown {
      position: absolute; top: calc(100% + 8px); left: 0; right: 0;
      background: var(--surface2); border: 1px solid var(--border);
      border-radius: var(--radius); box-shadow: var(--shadow-lg);
      z-index: 200; display: none; max-height: 400px; overflow-y: auto;
    }
    .nc-search-dropdown.active { display: block; }
    .nc-search-section {
      padding: 10px 16px 6px; font-size: 0.75rem;
      color: var(--text3); font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
    }
    .nc-search-item {
      padding: 10px 16px; cursor: pointer; display: flex; justify-content: space-between;
      align-items: center; transition: background 0.15s; font-size: 0.875rem;
    }
    .nc-search-item:hover { background: var(--surface); }
    .nc-search-sub { color: var(--text3); font-size: 0.75rem; }
    .nc-search-loading, .nc-search-empty { padding: 16px; text-align: center; color: var(--text3); font-size: 0.875rem; }

    /* 공통 컴포넌트 */
    .nc-container { max-width: 1280px; margin: 0 auto; padding: 0 24px; }
    .nc-badge {
      display: inline-block; padding: 2px 10px; border-radius: 20px;
      font-size: 0.75rem; font-weight: 600;
    }
    .nc-btn {
      display: inline-flex; align-items: center; justify-content: center; gap: 6px;
      padding: 10px 20px; border-radius: var(--radius-sm);
      border: none; cursor: pointer; font-weight: 600; font-size: 0.9rem;
      transition: all 0.2s; text-decoration: none;
    }
    .nc-btn--primary { background: var(--primary); color: white; }
    .nc-btn--primary:hover { background: var(--primary-dark); transform: translateY(-1px); }
    .nc-btn--outline { background: transparent; border: 1px solid var(--border); color: var(--text2); }
    .nc-btn--outline:hover { border-color: var(--primary); color: var(--primary-light); }
    .nc-btn--ghost { background: transparent; color: var(--text2); }
    .nc-btn--ghost:hover { color: var(--text); }
    .nc-btn--danger { background: var(--error); color: white; }
    .nc-btn--sm { padding: 6px 14px; font-size: 0.8rem; }
    .nc-btn--lg { padding: 14px 28px; font-size: 1rem; }
    .nc-btn--full { width: 100%; }
    .nc-btn:disabled { opacity: 0.5; cursor: not-allowed; }

    /* 카드 */
    .nc-card {
      background: var(--surface); border: 1px solid var(--border);
      border-radius: var(--radius); overflow: hidden;
      transition: all 0.25s;
    }
    .nc-card:hover { border-color: var(--primary)50; box-shadow: 0 0 0 1px var(--primary)20, var(--shadow); transform: translateY(-2px); }

    /* 폼 */
    .nc-form-group { margin-bottom: 20px; }
    .nc-label { display: block; margin-bottom: 6px; font-size: 0.875rem; font-weight: 600; color: var(--text2); }
    .nc-input, .nc-select, .nc-textarea {
      width: 100%; padding: 10px 14px;
      background: var(--surface); border: 1px solid var(--border);
      border-radius: var(--radius-sm); color: var(--text);
      font-size: 0.9rem; font-family: inherit; outline: none;
      transition: border-color 0.2s;
    }
    .nc-input:focus, .nc-select:focus, .nc-textarea:focus { border-color: var(--primary); }
    .nc-textarea { resize: vertical; min-height: 120px; }
    .nc-select option { background: var(--surface2); }
    .nc-input-hint { margin-top: 4px; font-size: 0.75rem; color: var(--text3); }
    .nc-input-error { border-color: var(--error) !important; }
    .nc-error-msg { color: var(--error); font-size: 0.75rem; margin-top: 4px; }

    /* 모달 */
    .nc-modal {
      position: fixed; inset: 0; z-index: 1000;
      display: flex; align-items: center; justify-content: center;
      opacity: 0; pointer-events: none; transition: opacity 0.25s;
    }
    .nc-modal.active { opacity: 1; pointer-events: all; }
    .nc-modal__backdrop {
      position: absolute; inset: 0;
      background: rgba(0,0,0,0.7); backdrop-filter: blur(4px);
    }
    .nc-modal__box {
      position: relative; background: var(--surface2);
      border: 1px solid var(--border); border-radius: var(--radius);
      padding: 28px; max-width: 480px; width: 90%;
      max-height: 90vh; overflow-y: auto;
      box-shadow: var(--shadow-lg);
      transform: scale(0.95) translateY(10px);
      transition: transform 0.25s;
    }
    .nc-modal.active .nc-modal__box { transform: scale(1) translateY(0); }
    .nc-modal__title {
      font-family: 'Noto Serif KR', serif;
      font-size: 1.2rem; font-weight: 700; margin-bottom: 20px;
      padding-bottom: 16px; border-bottom: 1px solid var(--border);
    }
    .nc-modal__close {
      position: absolute; top: 16px; right: 16px;
      background: none; border: none; color: var(--text3);
      font-size: 1.25rem; cursor: pointer; padding: 4px;
    }
    .nc-modal__close:hover { color: var(--text); }

    /* 사이드바 */
    .nc-sidebar {
      position: fixed; top: 0; right: -400px; width: 380px; height: 100vh;
      background: var(--surface2); border-left: 1px solid var(--border);
      z-index: 500; transition: right 0.3s ease;
      display: flex; flex-direction: column;
    }
    .nc-sidebar.active { right: 0; }
    .nc-sidebar__header {
      padding: 20px 24px; border-bottom: 1px solid var(--border);
      display: flex; align-items: center; justify-content: space-between;
    }
    .nc-sidebar__title { font-weight: 700; font-size: 1rem; }
    .nc-sidebar__close { background: none; border: none; color: var(--text3); font-size: 1.25rem; cursor: pointer; }
    .nc-sidebar__body { flex: 1; overflow-y: auto; padding: 20px 24px; }

    /* Toast */
    .nc-toast {
      position: fixed; bottom: 24px; right: 24px; z-index: 9999;
      padding: 12px 20px; border-radius: var(--radius-sm);
      font-size: 0.875rem; font-weight: 500;
      box-shadow: var(--shadow-lg); max-width: 320px;
      transform: translateY(20px); opacity: 0;
      transition: all 0.3s; pointer-events: none;
    }
    .nc-toast.show { transform: translateY(0); opacity: 1; }
    .nc-toast--success { background: var(--success); color: white; }
    .nc-toast--error { background: var(--error); color: white; }
    .nc-toast--info { background: var(--primary); color: white; }

    /* 로딩 */
    .nc-loading {
      display: flex; align-items: center; justify-content: center;
      padding: 60px; color: var(--text3);
    }
    .nc-spinner {
      width: 32px; height: 32px; border-radius: 50%;
      border: 3px solid var(--border); border-top-color: var(--primary);
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }

    /* 소설 커버 플레이스홀더 */
    .nc-cover {
      background: linear-gradient(135deg, var(--surface2), var(--bg3));
      display: flex; align-items: center; justify-content: center;
      overflow: hidden; border-radius: var(--radius-sm);
    }
    .nc-cover img { width: 100%; height: 100%; object-fit: cover; }
    .nc-cover-placeholder { color: var(--text3); font-size: 2rem; }

    /* 페이지네이션 */
    .nc-pagination {
      display: flex; align-items: center; justify-content: center;
      gap: 8px; padding: 24px 0;
    }
    .nc-page-btn {
      width: 36px; height: 36px; border-radius: 8px;
      border: 1px solid var(--border); background: transparent;
      color: var(--text2); cursor: pointer; transition: all 0.2s;
      display: flex; align-items: center; justify-content: center;
      font-size: 0.875rem;
    }
    .nc-page-btn:hover, .nc-page-btn.active { background: var(--primary); border-color: var(--primary); color: white; }

    /* 탭 */
    .nc-tabs { display: flex; border-bottom: 1px solid var(--border); gap: 4px; }
    .nc-tab {
      padding: 10px 20px; background: none; border: none;
      color: var(--text3); cursor: pointer; font-size: 0.9rem; font-weight: 500;
      border-bottom: 2px solid transparent; transition: all 0.2s;
      font-family: inherit;
    }
    .nc-tab:hover { color: var(--text2); }
    .nc-tab.active { color: var(--primary-light); border-bottom-color: var(--primary); }

    /* 섹션 헤더 */
    .nc-section-header {
      display: flex; align-items: center; justify-content: space-between;
      margin-bottom: 20px;
    }
    .nc-section-title {
      font-family: 'Noto Serif KR', serif;
      font-size: 1.2rem; font-weight: 700;
    }

    /* 빈 상태 */
    .nc-empty {
      padding: 60px 20px; text-align: center; color: var(--text3);
    }
    .nc-empty__icon { font-size: 3rem; margin-bottom: 12px; }
    .nc-empty__text { font-size: 0.95rem; }

    /* 반응형 */
    @media (max-width: 768px) {
      .nc-nav__inner { padding: 0 16px; }
      .nc-nav__search-wrap { max-width: 200px; }
      .nc-container { padding: 0 16px; }
    }
  `;
  document.head.appendChild(style);
}

// 페이지 로드시 자동 실행
document.addEventListener('DOMContentLoaded', () => {
  injectCommonStyles();
  initNav();
});
