<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import {
  Activity,
  AlertTriangle,
  Bot,
  CheckCircle2,
  ClipboardList,
  Clock3,
  Database,
  Download,
  FileCode2,
  FileText,
  FolderKanban,
  GitPullRequest,
  History,
  KeyRound,
  LayoutDashboard,
  Loader2,
  Lock,
  LogOut,
  Play,
  RefreshCw,
  Save,
  Search,
  Settings,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  Users,
  XCircle
} from '@lucide/vue';
import {
  clearAuth,
  createProject,
  getCurrentOrganization,
  getDashboard,
  getMarkdown,
  getPolicy,
  getProgress,
  getReview,
  getSarif,
  getStoredAuth,
  listAgents,
  listAuditLogs,
  listPolicies,
  listProjectReviews,
  listProjects,
  listReviews,
  listSamples,
  login,
  parseDiff,
  reviewSample,
  savePolicy,
  storeAuth,
  submitGithubPr,
  submitReview
} from './services/api';

const storedAuth = getStoredAuth();

const isAuthenticated = ref(Boolean(storedAuth.token));
const currentUser = ref(storedAuth.user);
const organization = ref(null);
const activeView = ref('overview');
const activeResultTab = ref('issues');

const loginForm = ref({ username: 'admin', password: 'codeguard123' });
const loginLoading = ref(false);
const shellLoading = ref(false);
const submitting = ref(false);
const policySaving = ref(false);
const parsing = ref(false);
const error = ref('');
const success = ref('');

const dashboard = ref(null);
const projects = ref([]);
const samples = ref([]);
const agents = ref([]);
const auditLogs = ref([]);
const policies = ref([]);
const history = ref([]);
const parsedPreview = ref(null);

const selectedProjectKey = ref('default');
const selectedSampleId = ref('');
const title = ref('人工提交 Diff 审查');
const repositoryName = ref('manual-diff');
const diffText = ref(defaultDiff());

const githubForm = ref({
  owner: '',
  repo: '',
  pullNumber: '',
  projectKey: 'default'
});

const newProject = ref({
  projectKey: '',
  name: '',
  description: ''
});

const options = ref({
  enableBugLogic: true,
  enableSecurity: true,
  enableCodeQuality: true,
  enableTestCoverage: true,
  enableLlmReview: true,
  failOnP0: true
});

const policyForm = ref(defaultPolicyForm());
const currentJob = ref(null);
const progress = ref(null);
const currentReview = ref(null);
const markdownText = ref('');
let progressTimer = null;

const navItems = [
  { key: 'overview', label: '概览', icon: LayoutDashboard },
  { key: 'reviews', label: '审查', icon: FileCode2 },
  { key: 'policies', label: '策略', icon: SlidersHorizontal },
  { key: 'agents', label: 'Agent', icon: Bot },
  { key: 'audit', label: '审计', icon: History },
  { key: 'assets', label: '资产', icon: Database }
];

const projectChoices = computed(() => {
  if (projects.value.length === 0) {
    return [{ projectKey: 'default', name: '默认项目' }];
  }
  return projects.value;
});

const selectedProjectName = computed(() => {
  return projectChoices.value.find(project => project.projectKey === selectedProjectKey.value)?.name
    ?? selectedProjectKey.value;
});

const latestReviews = computed(() => dashboard.value?.latestReviews ?? history.value);
const issueList = computed(() => currentReview.value?.issues ?? []);
const traceList = computed(() => progress.value?.traces ?? currentReview.value?.traces ?? []);
const completedAgentCount = computed(() => progress.value?.completedAgents ?? 0);
const totalAgentCount = computed(() => progress.value?.totalAgents ?? 7);
const progressPercent = computed(() => {
  if (!progress.value) return 0;
  const done = progress.value.completedAgents + progress.value.failedAgents + progress.value.skippedAgents;
  return Math.min(100, Math.round((done / Math.max(1, progress.value.totalAgents)) * 100));
});

const severityCounts = computed(() => {
  const counts = { P0: 0, P1: 0, P2: 0, P3: 0 };
  for (const issue of issueList.value) {
    counts[issue.severity] = (counts[issue.severity] ?? 0) + 1;
  }
  return counts;
});

const dashboardStatus = computed(() => dashboard.value?.statusCounts ?? {});
const readyAgents = computed(() => agents.value.filter(agent => agent.status === 'READY').length);
const hasRunningJob = computed(() => ['QUEUED', 'RUNNING'].includes(progress.value?.status));

onMounted(async () => {
  window.addEventListener('codeguard-auth-expired', handleAuthExpired);
  if (isAuthenticated.value) {
    await refreshAll();
  }
});

onUnmounted(() => {
  window.removeEventListener('codeguard-auth-expired', handleAuthExpired);
  stopPolling();
});

watch(selectedProjectKey, async projectKey => {
  githubForm.value.projectKey = projectKey;
  if (isAuthenticated.value) {
    await Promise.allSettled([
      loadHistory(),
      loadPolicy(projectKey)
    ]);
  }
});

async function handleLogin() {
  loginLoading.value = true;
  error.value = '';
  try {
    const response = await login(loginForm.value.username, loginForm.value.password);
    storeAuth(response);
    currentUser.value = {
      username: response.username,
      displayName: response.displayName,
      role: response.role,
      organizationKey: response.organizationKey
    };
    isAuthenticated.value = true;
    await refreshAll();
  } catch (exception) {
    error.value = exception.message;
  } finally {
    loginLoading.value = false;
  }
}

function logout() {
  stopPolling();
  clearAuth();
  isAuthenticated.value = false;
  currentUser.value = null;
  currentJob.value = null;
  progress.value = null;
  currentReview.value = null;
  markdownText.value = '';
}

function handleAuthExpired(event) {
  stopPolling();
  isAuthenticated.value = false;
  currentUser.value = null;
  currentJob.value = null;
  progress.value = null;
  currentReview.value = null;
  markdownText.value = '';
  error.value = event.detail?.message ?? '登录状态已失效，请重新登录';
}

async function refreshAll() {
  shellLoading.value = true;
  error.value = '';
  try {
    const [
      organizationResult,
      dashboardResult,
      projectResult,
      sampleResult,
      agentResult,
      auditResult,
      policyResult,
      reviewResult
    ] = await Promise.all([
      getCurrentOrganization(),
      getDashboard(),
      listProjects(),
      listSamples(),
      listAgents(),
      listAuditLogs(),
      listPolicies(),
      listReviews()
    ]);

    organization.value = organizationResult;
    dashboard.value = dashboardResult;
    projects.value = projectResult;
    samples.value = sampleResult;
    agents.value = agentResult;
    auditLogs.value = auditResult;
    policies.value = policyResult;
    history.value = reviewResult;

    if (!selectedSampleId.value && samples.value.length > 0) {
      selectedSampleId.value = samples.value[0].id;
    }
    if (projects.value.length > 0 && !projects.value.some(project => project.projectKey === selectedProjectKey.value)) {
      selectedProjectKey.value = projects.value[0].projectKey;
    }
    await loadPolicy(selectedProjectKey.value);
  } catch (exception) {
    error.value = exception.message;
  } finally {
    shellLoading.value = false;
  }
}

async function loadHistory() {
  history.value = await listProjectReviews(selectedProjectKey.value);
}

async function loadPolicy(projectKey) {
  policyForm.value = toPolicyForm(await getPolicy(projectKey));
}

async function handleCreateProject() {
  error.value = '';
  success.value = '';
  if (!newProject.value.projectKey.trim()) {
    error.value = '请填写项目 Key';
    return;
  }

  try {
    const created = await createProject(newProject.value);
    selectedProjectKey.value = created.projectKey;
    newProject.value = { projectKey: '', name: '', description: '' };
    success.value = `已创建项目 ${created.name}`;
    await refreshAll();
  } catch (exception) {
    error.value = exception.message;
  }
}

async function handleParseDiff() {
  parsing.value = true;
  error.value = '';
  try {
    parsedPreview.value = await parseDiff(title.value, diffText.value);
  } catch (exception) {
    error.value = exception.message;
  } finally {
    parsing.value = false;
  }
}

async function handleSubmitReview() {
  submitting.value = true;
  error.value = '';
  success.value = '';
  try {
    const job = await submitReview({
      title: title.value,
      diffText: diffText.value,
      projectKey: selectedProjectKey.value,
      repositoryName: repositoryName.value,
      sourceType: 'MANUAL',
      options: options.value
    });
    success.value = '审查任务已提交';
    activeResultTab.value = 'agents';
    await startPolling(job.reviewId);
  } catch (exception) {
    error.value = exception.message;
  } finally {
    submitting.value = false;
  }
}

async function handleSampleReview() {
  if (!selectedSampleId.value) return;

  submitting.value = true;
  error.value = '';
  success.value = '';
  try {
    const job = await reviewSample(selectedSampleId.value);
    success.value = '样例审查任务已提交';
    activeView.value = 'reviews';
    activeResultTab.value = 'agents';
    await startPolling(job.reviewId);
  } catch (exception) {
    error.value = exception.message;
  } finally {
    submitting.value = false;
  }
}

async function handleGithubReview() {
  submitting.value = true;
  error.value = '';
  success.value = '';
  try {
    const repository = `${githubForm.value.owner.trim()}/${githubForm.value.repo.trim()}`;
    if (!githubForm.value.owner.trim() || !githubForm.value.repo.trim() || !githubForm.value.pullNumber) {
      error.value = '请填写 GitHub owner、repo 和 PR 编号';
      return;
    }

    const job = await submitGithubPr({
      repository,
      pullNumber: Number(githubForm.value.pullNumber),
      projectKey: selectedProjectKey.value
    });
    success.value = 'GitHub PR 审查任务已提交';
    activeResultTab.value = 'agents';
    await startPolling(job.reviewId);
  } catch (exception) {
    error.value = exception.message;
  } finally {
    submitting.value = false;
  }
}

async function startPolling(reviewId) {
  currentJob.value = { reviewId };
  stopPolling();
  await refreshProgress(reviewId);
  progressTimer = window.setInterval(() => refreshProgress(reviewId), 1500);
}

async function refreshProgress(reviewId = currentJob.value?.reviewId) {
  if (!reviewId) return;

  progress.value = await getProgress(reviewId);
  if (['COMPLETED', 'FAILED', 'CANCELED'].includes(progress.value.status)) {
    stopPolling();
    await loadReviewDetail(reviewId);
    await Promise.allSettled([
      getDashboard().then(value => { dashboard.value = value; }),
      loadHistory(),
      listAuditLogs().then(value => { auditLogs.value = value; })
    ]);
  }
}

function stopPolling() {
  if (progressTimer) {
    window.clearInterval(progressTimer);
    progressTimer = null;
  }
}

async function loadReviewDetail(reviewId) {
  error.value = '';
  try {
    currentReview.value = await getReview(reviewId);
    const markdown = await getMarkdown(reviewId);
    markdownText.value = markdown.markdown;
    currentJob.value = { reviewId };
    activeView.value = 'reviews';
  } catch (exception) {
    error.value = exception.message;
  }
}

async function handleSavePolicy() {
  policySaving.value = true;
  error.value = '';
  success.value = '';
  try {
    const saved = await savePolicy(selectedProjectKey.value, {
      ...policyForm.value,
      maxDiffChars: Number(policyForm.value.maxDiffChars)
    });
    policyForm.value = toPolicyForm(saved);
    success.value = '策略已保存';
    policies.value = await listPolicies();
  } catch (exception) {
    error.value = exception.message;
  } finally {
    policySaving.value = false;
  }
}

async function downloadMarkdown() {
  if (!markdownText.value) return;

  const blob = new Blob([markdownText.value], { type: 'text/markdown;charset=utf-8' });
  downloadBlob(blob, `codeguard-review-${currentJob.value?.reviewId ?? 'report'}.md`);
}

async function downloadSarif() {
  if (!currentJob.value?.reviewId) return;

  try {
    const sarif = await getSarif(currentJob.value.reviewId);
    const blob = new Blob([JSON.stringify(sarif, null, 2)], { type: 'application/json;charset=utf-8' });
    downloadBlob(blob, `codeguard-review-${currentJob.value.reviewId}.sarif`);
  } catch (exception) {
    error.value = exception.message;
  }
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

function recommendationLabel(value) {
  const labels = {
    APPROVE: '允许合并',
    CAN_MERGE_WITH_NOTES: '带备注合并',
    REQUEST_CHANGES: '需要修改',
    BLOCK: '阻断合并'
  };
  return labels[value] ?? value ?? '待生成';
}

function statusLabel(value) {
  const labels = {
    QUEUED: '排队中',
    RUNNING: '运行中',
    COMPLETED: '已完成',
    FAILED: '失败',
    CANCELED: '已取消'
  };
  return labels[value] ?? value ?? '未知';
}

function formatDate(value) {
  if (!value) return '-';
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value));
}

function toPolicyForm(policy) {
  return {
    name: policy.name,
    blockSeverity: policy.blockSeverity,
    failOnP0: policy.failOnP0,
    requireTestsForApiChanges: policy.requireTestsForApiChanges,
    enableBugLogic: policy.enableBugLogic,
    enableSecurity: policy.enableSecurity,
    enableCodeQuality: policy.enableCodeQuality,
    enableTestCoverage: policy.enableTestCoverage,
    enableLlmReview: policy.enableLlmReview,
    maxDiffChars: policy.maxDiffChars
  };
}

function defaultPolicyForm() {
  return {
    name: '企业默认审查策略',
    blockSeverity: 'P0',
    failOnP0: true,
    requireTestsForApiChanges: true,
    enableBugLogic: true,
    enableSecurity: true,
    enableCodeQuality: true,
    enableTestCoverage: true,
    enableLlmReview: true,
    maxDiffChars: 200000
  };
}

function defaultDiff() {
  return `diff --git a/src/main/java/UserService.java b/src/main/java/UserService.java
--- a/src/main/java/UserService.java
+++ b/src/main/java/UserService.java
@@ -1,6 +1,8 @@
 public User find(Long id) {
-    return repository.findById(id).orElseThrow();
+    System.out.println("debug token=" + apiKey);
+    return null;
 }
`;
}
</script>

<template>
  <main v-if="!isAuthenticated" class="login-page">
    <section class="login-visual">
      <div class="brand-mark">
        <ShieldCheck :size="34" />
      </div>
      <p class="eyebrow">CodeGuard Agent</p>
      <h1>企业级多 Agent 代码审查控制台</h1>
      <div class="login-metrics">
        <span>Router</span>
        <span>Security</span>
        <span>LLM Review</span>
      </div>
    </section>

    <form class="login-card" @submit.prevent="handleLogin">
      <div>
        <p class="eyebrow">Secure Sign In</p>
        <h2>登录工作台</h2>
      </div>

      <label>
        <span>用户名</span>
        <input v-model="loginForm.username" autocomplete="username" />
      </label>

      <label>
        <span>密码</span>
        <input v-model="loginForm.password" type="password" autocomplete="current-password" />
      </label>

      <button class="primary-button" type="submit" :disabled="loginLoading">
        <Loader2 v-if="loginLoading" class="spin" :size="18" />
        <KeyRound v-else :size="18" />
        登录
      </button>

      <p v-if="error" class="form-error">{{ error }}</p>
      <p class="demo-account">admin / codeguard123</p>
    </form>
  </main>

  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="sidebar-brand">
        <div class="brand-mark small">
          <ShieldCheck :size="24" />
        </div>
        <div>
          <strong>CodeGuard</strong>
          <span>Agent Control Plane</span>
        </div>
      </div>

      <div class="org-pill">
        <Users :size="16" />
        <span>{{ organization?.name ?? currentUser?.organizationKey }}</span>
      </div>

      <nav class="nav-list">
        <button
          v-for="item in navItems"
          :key="item.key"
          :class="{ active: activeView === item.key }"
          @click="activeView = item.key"
        >
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <div class="side-status">
        <div>
          <span>Agent Ready</span>
          <strong>{{ readyAgents }}/{{ agents.length || 7 }}</strong>
        </div>
        <div>
          <span>Active Job</span>
          <strong>{{ hasRunningJob ? 'Running' : 'Idle' }}</strong>
        </div>
      </div>
    </aside>

    <main class="main-area">
      <header class="topbar">
        <div>
          <p class="eyebrow">{{ selectedProjectName }}</p>
          <h1>{{ navItems.find(item => item.key === activeView)?.label }}</h1>
        </div>

        <div class="topbar-actions">
          <select v-model="selectedProjectKey" class="project-select">
            <option v-for="project in projectChoices" :key="project.projectKey" :value="project.projectKey">
              {{ project.name }} · {{ project.projectKey }}
            </option>
          </select>
          <button class="icon-button" title="刷新数据" @click="refreshAll">
            <RefreshCw :class="{ spin: shellLoading }" :size="18" />
          </button>
          <div class="user-chip">
            <span>{{ currentUser?.displayName }}</span>
            <small>{{ currentUser?.role }}</small>
          </div>
          <button class="icon-button" title="退出登录" @click="logout">
            <LogOut :size="18" />
          </button>
        </div>
      </header>

      <div v-if="error" class="toast error">
        <AlertTriangle :size="18" />
        {{ error }}
      </div>
      <div v-if="success" class="toast success">
        <CheckCircle2 :size="18" />
        {{ success }}
      </div>

      <section v-if="activeView === 'overview'" class="view-stack">
        <div class="kpi-grid">
          <article class="metric-card">
            <span>审查任务</span>
            <strong>{{ dashboard?.totalReviews ?? 0 }}</strong>
            <small>已完成 {{ dashboardStatus.COMPLETED ?? 0 }}</small>
          </article>
          <article class="metric-card accent-cyan">
            <span>平均风险</span>
            <strong>{{ dashboard?.averageRiskScore ?? 0 }}</strong>
            <small>高风险 {{ dashboard?.highRiskReviews ?? 0 }}</small>
          </article>
          <article class="metric-card accent-red">
            <span>P0 / P1</span>
            <strong>{{ dashboard?.p0Issues ?? 0 }} / {{ dashboard?.p1Issues ?? 0 }}</strong>
            <small>阻断率 {{ dashboard?.blockRate ?? 0 }}%</small>
          </article>
          <article class="metric-card accent-violet">
            <span>项目资产</span>
            <strong>{{ dashboard?.projectCount ?? 0 }}</strong>
            <small>仓库 {{ dashboard?.repositoryCount ?? 0 }}</small>
          </article>
        </div>

        <div class="overview-layout">
          <section class="panel">
            <div class="section-title">
              <div>
                <p class="eyebrow">Risk Operations</p>
                <h2>审查状态分布</h2>
              </div>
              <Activity :size="20" />
            </div>
            <div class="status-bars">
              <div v-for="status in ['QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELED']" :key="status" class="status-row">
                <span>{{ statusLabel(status) }}</span>
                <div class="bar-track">
                  <div
                    class="bar-fill"
                    :style="{ width: `${Math.min(100, ((dashboardStatus[status] ?? 0) / Math.max(1, dashboard?.totalReviews ?? 1)) * 100)}%` }"
                  />
                </div>
                <strong>{{ dashboardStatus[status] ?? 0 }}</strong>
              </div>
            </div>
          </section>

          <section class="panel">
            <div class="section-title">
              <div>
                <p class="eyebrow">Latest Reviews</p>
                <h2>最近审查</h2>
              </div>
              <ClipboardList :size="20" />
            </div>
            <div class="review-table compact">
              <button
                v-for="review in latestReviews.slice(0, 8)"
                :key="review.id"
                class="review-row"
                @click="loadReviewDetail(review.id)"
              >
                <span>{{ review.title }}</span>
                <small>{{ recommendationLabel(review.recommendation) }}</small>
                <strong>{{ review.riskScore }}</strong>
              </button>
              <p v-if="latestReviews.length === 0" class="empty-text">暂无审查记录</p>
            </div>
          </section>
        </div>
      </section>

      <section v-if="activeView === 'reviews'" class="review-workbench">
        <section class="panel input-panel">
          <div class="section-title">
            <div>
              <p class="eyebrow">Review Input</p>
              <h2>提交代码变更</h2>
            </div>
            <FileCode2 :size="20" />
          </div>

          <div class="form-grid">
            <label>
              <span>标题</span>
              <input v-model="title" />
            </label>
            <label>
              <span>仓库</span>
              <input v-model="repositoryName" />
            </label>
          </div>

          <div class="toggle-grid">
            <label v-for="(value, key) in options" :key="key" class="toggle-line">
              <input v-model="options[key]" type="checkbox" />
              <span>{{ key }}</span>
            </label>
          </div>

          <label class="diff-editor">
            <span>Git Diff</span>
            <textarea v-model="diffText" spellcheck="false" />
          </label>

          <div class="action-row">
            <button class="secondary-button" :disabled="parsing" @click="handleParseDiff">
              <Search :size="17" />
              解析预览
            </button>
            <button class="primary-button" :disabled="submitting" @click="handleSubmitReview">
              <Loader2 v-if="submitting" class="spin" :size="18" />
              <Play v-else :size="18" />
              提交审查
            </button>
          </div>

          <div v-if="parsedPreview" class="diff-preview">
            <span>文件 {{ parsedPreview.summary.filesChanged }}</span>
            <span>新增 {{ parsedPreview.summary.additions }}</span>
            <span>删除 {{ parsedPreview.summary.deletions }}</span>
          </div>

          <div class="source-tools">
            <div>
              <h3>样例库</h3>
              <select v-model="selectedSampleId">
                <option v-for="sample in samples" :key="sample.id" :value="sample.id">
                  {{ sample.title }}
                </option>
              </select>
              <button class="secondary-button" :disabled="submitting || !selectedSampleId" @click="handleSampleReview">
                <Sparkles :size="17" />
                运行样例
              </button>
            </div>

            <div>
              <h3>GitHub PR</h3>
              <div class="github-grid">
                <input v-model="githubForm.owner" placeholder="owner" />
                <input v-model="githubForm.repo" placeholder="repo" />
                <input v-model="githubForm.pullNumber" placeholder="PR #" />
              </div>
              <button class="secondary-button" :disabled="submitting" @click="handleGithubReview">
                <GitPullRequest :size="17" />
                审查 PR
              </button>
            </div>
          </div>
        </section>

        <section class="panel result-panel">
          <div class="section-title">
            <div>
              <p class="eyebrow">Review Result</p>
              <h2>{{ currentReview?.title ?? progress?.title ?? '等待审查任务' }}</h2>
            </div>
            <ShieldCheck :size="20" />
          </div>

          <div class="result-hero">
            <div class="risk-ring" :style="{ '--score': currentReview?.riskScore ?? progress?.riskScore ?? 0 }">
              <span>{{ currentReview?.riskScore ?? progress?.riskScore ?? 0 }}</span>
              <small>risk</small>
            </div>
            <div>
              <strong>{{ recommendationLabel(currentReview?.recommendation ?? progress?.recommendation) }}</strong>
              <span>{{ statusLabel(progress?.status ?? currentReview?.status) }}</span>
            </div>
          </div>

          <div class="progress-line">
            <span>{{ completedAgentCount }}/{{ totalAgentCount }} agents</span>
            <div class="bar-track">
              <div class="bar-fill cyan" :style="{ width: `${progressPercent}%` }" />
            </div>
            <span>{{ progressPercent }}%</span>
          </div>

          <div class="result-tabs">
            <button :class="{ active: activeResultTab === 'issues' }" @click="activeResultTab = 'issues'">问题</button>
            <button :class="{ active: activeResultTab === 'agents' }" @click="activeResultTab = 'agents'">Trace</button>
            <button :class="{ active: activeResultTab === 'markdown' }" @click="activeResultTab = 'markdown'">报告</button>
          </div>

          <div v-if="activeResultTab === 'issues'" class="issues-view">
            <div class="severity-strip">
              <span v-for="level in ['P0', 'P1', 'P2', 'P3']" :key="level" :class="`sev-${level}`">
                {{ level }} {{ severityCounts[level] }}
              </span>
            </div>
            <article v-for="issue in issueList" :key="issue.id" class="issue-card">
              <div>
                <span :class="`severity-badge sev-${issue.severity}`">{{ issue.severity }}</span>
                <strong>{{ issue.title }}</strong>
              </div>
              <p>{{ issue.detail }}</p>
              <small>{{ issue.filePath || 'unknown file' }}{{ issue.lineNumber ? `:${issue.lineNumber}` : '' }}</small>
            </article>
            <p v-if="issueList.length === 0" class="empty-text">暂无问题</p>
          </div>

          <div v-if="activeResultTab === 'agents'" class="trace-list">
            <article v-for="trace in traceList" :key="trace.id" class="trace-item">
              <div>
                <Bot :size="17" />
                <strong>{{ trace.agentType }}</strong>
                <span :class="`status-pill ${trace.status?.toLowerCase()}`">{{ trace.status }}</span>
              </div>
              <p>{{ trace.outputSummary || trace.skipReason || trace.inputSummary }}</p>
              <small>{{ trace.durationMs }}ms · {{ formatDate(trace.startedAt) }}</small>
            </article>
            <p v-if="traceList.length === 0" class="empty-text">暂无 Trace</p>
          </div>

          <div v-if="activeResultTab === 'markdown'" class="markdown-view">
            <div class="action-row right">
              <button class="secondary-button" :disabled="!markdownText" @click="downloadMarkdown">
                <Download :size="17" />
                Markdown
              </button>
              <button class="secondary-button" :disabled="!currentJob?.reviewId" @click="downloadSarif">
                <FileText :size="17" />
                SARIF
              </button>
            </div>
            <pre>{{ markdownText || '报告生成后显示在这里' }}</pre>
          </div>
        </section>
      </section>

      <section v-if="activeView === 'policies'" class="policy-layout">
        <section class="panel">
          <div class="section-title">
            <div>
              <p class="eyebrow">Policy Center</p>
              <h2>{{ selectedProjectName }} 策略</h2>
            </div>
            <Settings :size="20" />
          </div>

          <div class="form-grid">
            <label>
              <span>策略名称</span>
              <input v-model="policyForm.name" />
            </label>
            <label>
              <span>阻断级别</span>
              <select v-model="policyForm.blockSeverity">
                <option value="P0">P0</option>
                <option value="P1">P1</option>
                <option value="P2">P2</option>
              </select>
            </label>
            <label>
              <span>最大 Diff 字符数</span>
              <input v-model="policyForm.maxDiffChars" type="number" min="1000" max="1000000" />
            </label>
          </div>

          <div class="toggle-grid policy-toggles">
            <label v-for="key in ['failOnP0', 'requireTestsForApiChanges', 'enableBugLogic', 'enableSecurity', 'enableCodeQuality', 'enableTestCoverage', 'enableLlmReview']" :key="key" class="toggle-line">
              <input v-model="policyForm[key]" type="checkbox" />
              <span>{{ key }}</span>
            </label>
          </div>

          <button class="primary-button" :disabled="policySaving" @click="handleSavePolicy">
            <Loader2 v-if="policySaving" class="spin" :size="18" />
            <Save v-else :size="18" />
            保存策略
          </button>
        </section>

        <section class="panel">
          <div class="section-title">
            <div>
              <p class="eyebrow">Saved Policies</p>
              <h2>策略记录</h2>
            </div>
            <Lock :size="20" />
          </div>
          <div class="simple-table">
            <div v-for="policy in policies" :key="policy.id" class="table-row">
              <span>{{ policy.projectKey }}</span>
              <strong>{{ policy.name }}</strong>
              <small>{{ policy.blockSeverity }} · {{ policy.maxDiffChars }}</small>
            </div>
            <p v-if="policies.length === 0" class="empty-text">暂无策略记录</p>
          </div>
        </section>
      </section>

      <section v-if="activeView === 'agents'" class="agent-grid">
        <article v-for="agent in agents" :key="agent.type" class="agent-card">
          <div class="agent-head">
            <Bot :size="20" />
            <span :class="`status-dot ${agent.status === 'READY' ? 'ready' : 'warn'}`" />
          </div>
          <h2>{{ agent.name }}</h2>
          <p>{{ agent.description }}</p>
          <strong>{{ agent.engine }}</strong>
          <div class="signal-list">
            <span v-for="signal in agent.signals" :key="signal">{{ signal }}</span>
          </div>
          <small>{{ agent.enterpriseValue }}</small>
        </article>
      </section>

      <section v-if="activeView === 'audit'" class="panel">
        <div class="section-title">
          <div>
            <p class="eyebrow">Audit Trail</p>
            <h2>审计日志</h2>
          </div>
          <History :size="20" />
        </div>
        <div class="audit-table">
          <div v-for="log in auditLogs" :key="log.id" class="audit-row">
            <Clock3 :size="16" />
            <span>{{ formatDate(log.createdAt) }}</span>
            <strong>{{ log.action }}</strong>
            <small>{{ log.actorUsername }} · {{ log.summary }}</small>
          </div>
          <p v-if="auditLogs.length === 0" class="empty-text">暂无审计日志</p>
        </div>
      </section>

      <section v-if="activeView === 'assets'" class="assets-layout">
        <section class="panel">
          <div class="section-title">
            <div>
              <p class="eyebrow">Projects</p>
              <h2>项目资产</h2>
            </div>
            <FolderKanban :size="20" />
          </div>
          <div class="form-grid">
            <input v-model="newProject.projectKey" placeholder="project-key" />
            <input v-model="newProject.name" placeholder="项目名称" />
            <input v-model="newProject.description" placeholder="项目描述" />
          </div>
          <button class="primary-button" @click="handleCreateProject">
            <FolderKanban :size="18" />
            创建项目
          </button>
        </section>

        <section class="project-list">
          <article
            v-for="project in projectChoices"
            :key="project.projectKey"
            class="project-card"
            :class="{ active: selectedProjectKey === project.projectKey }"
            @click="selectedProjectKey = project.projectKey"
          >
            <FolderKanban :size="19" />
            <div>
              <strong>{{ project.name }}</strong>
              <span>{{ project.projectKey }}</span>
            </div>
          </article>
        </section>
      </section>
    </main>
  </div>
</template>
