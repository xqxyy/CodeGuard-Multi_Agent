// 这一层专门封装后端请求。
// 页面组件不用关心 fetch、Authorization、错误解析这些细节。
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';
const TOKEN_KEY = 'codeguard_token';
const USER_KEY = 'codeguard_user';

export function getStoredAuth() {
  const token = localStorage.getItem(TOKEN_KEY);
  const userJson = localStorage.getItem(USER_KEY);
  return {
    token,
    user: userJson ? JSON.parse(userJson) : null
  };
}

export function storeAuth(loginResponse) {
  localStorage.setItem(TOKEN_KEY, loginResponse.token);
  localStorage.setItem(USER_KEY, JSON.stringify({
    username: loginResponse.username,
    displayName: loginResponse.displayName,
    role: loginResponse.role
  }));
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

async function request(path, options = {}) {
  const token = localStorage.getItem(TOKEN_KEY);
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers ?? {})
  };

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers
  });

  const contentType = response.headers.get('content-type') ?? '';
  const body = contentType.includes('application/json')
    ? await response.json()
    : await response.text();

  if (!response.ok) {
    const message = typeof body === 'object' && body !== null
      ? body.message ?? body.error ?? '请求失败'
      : body || '请求失败';
    throw new Error(message);
  }

  return body;
}

export function login(username, password) {
  return request('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password })
  });
}

export function health() {
  return request('/health');
}

export function listProjects() {
  return request('/projects');
}

export function createProject(project) {
  return request('/projects', {
    method: 'POST',
    body: JSON.stringify(project)
  });
}

export function listSamples() {
  return request('/samples');
}

export function parseDiff(title, diffText) {
  return request('/diff/parse', {
    method: 'POST',
    body: JSON.stringify({ title, diffText })
  });
}

export function submitReview(payload) {
  return request('/reviews', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function reviewDiffSync(title, diffText) {
  return request('/reviews/sync', {
    method: 'POST',
    body: JSON.stringify({ title, diffText })
  });
}

export function reviewSample(sampleId) {
  return request(`/samples/${sampleId}/reviews`, {
    method: 'POST'
  });
}

export function submitGithubPr(payload) {
  return request('/integrations/github/pr-review', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function listReviews() {
  return request('/reviews');
}

export function listProjectReviews(projectKey) {
  return request(`/projects/${projectKey}/reviews`);
}

export function getReview(reviewId) {
  return request(`/reviews/${reviewId}`);
}

export function getProgress(reviewId) {
  return request(`/reviews/${reviewId}/progress`);
}

export function getMarkdown(reviewId) {
  return request(`/reviews/${reviewId}/markdown`);
}

export function getSarif(reviewId) {
  return request(`/reviews/${reviewId}/sarif`);
}
