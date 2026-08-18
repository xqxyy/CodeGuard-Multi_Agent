<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import {
  Activity,
  ClipboardList,
  Download,
  FileCode2,
  FolderKanban,
  GitPullRequest,
  History,
  Loader2,
  LogOut,
  Play,
  RefreshCw,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles
} from '@lucide/vue';
import {
  clearAuth,
  createProject,
  getMarkdown,
  getProgress,
  getReview,
  getSarif,
  getStoredAuth,
  listProjectReviews,
  listProjects,
  listReviews,
  listSamples,
  login,
  parseDiff,
  reviewSample,
  storeAuth,
  submitGithubPr,
  submitReview
} from './services/api';

// 登录状态。企业演示里先用内置用户换取 Bearer Token。
const storedAuth = getStoredAuth();
const token = ref(storedAuth.token);
const currentUser = ref(storedAuth.user);
const loginForm = ref({ username: 'admin', password: 'codeguard123' });

// 项目、样例、历史记录都是工作台左侧的上下文。
const projects = ref([]);
const samples = ref([]);
const history = ref([]);
const selectedProjectKey = ref('default');
const selectedSampleId = ref('');

// 中间编辑区：可以粘贴 diff，也可以从样例或 GitHub PR 填充。
const title = ref('手动粘贴的代码变更');
const repositoryName = ref('manual-diff');
const diffText = ref('');
const parsedPreview = ref(null);
const githubForm = ref({ repository: 'spring-projects/spring-petclinic', pullNumber: 1 });
const newProject = ref({ projectKey: '', name: '', description: '' });

// Agent 开关。前端传到后端后，后端会保存到 Review 任务里。
const options = ref({
  enableBugLogic: true,
  enableSecurity: true,
  enableCodeQuality: true,
  enableTestCoverage: true,
  enableLlmReview: true,
  failOnP0: true
});

// 右侧结果区：异步任务进度、最终结果、报告。
const currentJob = ref(null);
const progress = ref(null);
const currentReview = ref(null);
const markdownText = ref('');
const activeTab = ref('issues');

const loading = ref(false);
const loadingMessage = ref('');
const errorMessage = ref('');
let pollTimer = null;

const selectedSample = computed(() =>
  samples.value.find((sample) => sample.id === selectedSampleId.value)
);

const diffLineCount = computed(() =>
  diffText.value ? diffText.value.split(/\r?\n/).length : 0
);

const canRunReview = computed(() => diffText.value.trim().length >= 20 && !loading.value);

const sortedIssues = computed(() => {
  const severityOrder = { P0: 0, P1: 1, P2: 2, P3: 3 };
  return [...(currentReview.value?.issues ?? [])].sort((left, right) => {
    return (severityOrder[left.severity] ?? 99) - (severityOrder[right.severity] ?? 99);
  });
});

const issueCountBySeverity = computed(() => {
  const counter = { P0: 0, P1: 0, P2: 0, P3: 0 };
  for (const issue of currentReview.value?.issues ?? []) {
    counter[issue.severity] = (counter[issue.severity] ?? 0) + 1;
  }
  return counter;
});

const progressPercent = computed(() => {
  if (!progress.value?.totalAgents) {
    return 0;
  }

  const done = progress.value.completedAgents + progress.value.failedAgents + progress.value.skippedAgents;
  return Math.min(100, Math.round((done / progress.value.totalAgents) * 100));
});

onMounted(async () => {
  if (token.value) {
    await refreshInitialData();
  }
});

onBeforeUnmount(() => {
  stopPolling();
});

async function doLogin() {
  await withLoading('正在登录', async () => {
    const response = await login(loginForm.value.username, loginForm.value.password);
    storeAuth(response);
    token.value = response.token;
    currentUser.value = {
      username: response.username,
      displayName: response.displayName,
      role: response.role
    };
    await refreshInitialData();
  });
}

function doLogout() {
  clearAuth();
  token.value = null;
  currentUser.value = null;
  stopPolling();
}

async function refreshInitialData() {
  await withLoading('正在加载工作台数据', async () => {
    const [projectList, sampleList, reviewList] = await Promise.all([
      listProjects(),
      listSamples(),
      listReviews()
    ]);

    projects.value = projectList;
    samples.value = sampleList;
    history.value = reviewList;

    if (!selectedProjectKey.value && projectList.length > 0) {
      selectedProjectKey.value = projectList[0].projectKey;
    }

    if (!diffText.value && sampleList.length > 0) {
      chooseSample(sampleList[0]);
    }
  });
}

async function refreshProjectHistory() {
  if (!selectedProjectKey.value) {
    history.value = await listReviews();
    return;
  }

  history.value = await listProjectReviews(selectedProjectKey.value);
}

async function createNewProject() {
  if (!newProject.value.projectKey.trim()) {
    errorMessage.value = '请先填写 projectKey';
    return;
  }

  await withLoading('正在创建项目', async () => {
    const created = await createProject(newProject.value);
    selectedProjectKey.value = created.projectKey;
    newProject.value = { projectKey: '', name: '', description: '' };
    await refreshInitialData();
  });
}

function chooseSample(sample) {
  selectedSampleId.value = sample.id;
  title.value = sample.title;
  repositoryName.value = 'sample-library';
  diffText.value = sample.diffText;
  parsedPreview.value = null;
  errorMessage.value = '';
}

async function runParsePreview() {
  await withLoading('正在解析 diff', async () => {
    parsedPreview.value = await parseDiff(title.value, diffText.value);
  });
}

async function runManualReview() {
  await withLoading('已提交审查任务', async () => {
    const job = await submitReview({
      title: title.value,
      diffText: diffText.value,
      projectKey: selectedProjectKey.value,
      repositoryName: repositoryName.value,
      sourceType: 'MANUAL',
      options: options.value
    });
    await startTracking(job);
  });
}

async function runSelectedSample() {
  if (!selectedSampleId.value) {
    return;
  }

  await withLoading('已提交样例审查任务', async () => {
    const job = await reviewSample(selectedSampleId.value);
    await startTracking(job);
  });
}

async function runGithubReview() {
  await withLoading('正在拉取 GitHub PR diff', async () => {
    const job = await submitGithubPr({
      repository: githubForm.value.repository,
      pullNumber: Number(githubForm.value.pullNumber),
      projectKey: selectedProjectKey.value,
      options: options.value
    });
    await startTracking(job);
  });
}

async function startTracking(job) {
  currentJob.value = job;
  currentReview.value = null;
  markdownText.value = '';
  progress.value = await getProgress(job.reviewId);
  activeTab.value = 'agents';
  stopPolling();

  pollTimer = window.setInterval(async () => {
    try {
      const nextProgress = await getProgress(job.reviewId);
      progress.value = nextProgress;

      if (['COMPLETED', 'FAILED', 'CANCELED'].includes(nextProgress.status)) {
        stopPolling();
        currentReview.value = await getReview(job.reviewId);
        markdownText.value = currentReview.value.markdown ?? '';
        await refreshProjectHistory();
        activeTab.value = nextProgress.status === 'COMPLETED' ? 'issues' : 'agents';
      }
    } catch (error) {
      stopPolling();
      errorMessage.value = error.message || '轮询进度失败';
    }
  }, 1500);
}

async function openReview(reviewId) {
  await withLoading('正在打开历史审查', async () => {
    currentJob.value = { reviewId };
    progress.value = await getProgress(reviewId);
    currentReview.value = await getReview(reviewId);
    const markdown = await getMarkdown(reviewId);
    markdownText.value = markdown.markdown;
    activeTab.value = 'issues';
  });
}

async function downloadSarif() {
  if (!currentReview.value?.id) {
    return;
  }

  const sarif = await getSarif(currentReview.value.id);
  const blob = new Blob([JSON.stringify(sarif, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `codeguard-${currentReview.value.id}.sarif.json`;
  link.click();
  URL.revokeObjectURL(url);
}

function stopPolling() {
  if (pollTimer) {
    window.clearInterval(pollTimer);
    pollTimer = null;
  }
}

async function withLoading(message, task) {
  loading.value = true;
  loadingMessage.value = message;
  errorMessage.value = '';

  try {
    await task();
  } catch (error) {
    errorMessage.value = error.message || '操作失败';
  } finally {
    loading.value = false;
    loadingMessage.value = '';
  }
}

function recommendationLabel(value) {
  const labels = {
    APPROVE: '可以合并',
    CAN_MERGE_WITH_NOTES: '可带备注合并',
    REQUEST_CHANGES: '需要修改',
    BLOCK: '阻止合并'
  };
  return labels[value] ?? value ?? '尚未审查';
}

function statusLabel(value) {
  const labels = {
    QUEUED: '排队中',
    RUNNING: '运行中',
    COMPLETED: '已完成',
    FAILED: '失败',
    CANCELED: '已取消',
    SKIPPED: '跳过'
  };
  return labels[value] ?? value ?? '-';
}

function agentLabel(value) {
  const labels = {
    ROUTER: 'Router 路由',
    BUG_LOGIC: 'Bug 逻辑',
    SECURITY: '安全',
    CODE_QUALITY: '代码质量',
    TEST_COVERAGE: '测试覆盖',
    LLM_REVIEW: 'LLM 复审',
    SUMMARY: '汇总'
  };
  return labels[value] ?? value;
}

function formatDate(value) {
  if (!value) {
    return '-';
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value));
}
</script>

<template>
  <main v-if="!token" class="login-shell">
    <section class="login-panel">
      <p class="eyebrow">Enterprise Demo</p>
      <h1>CodeGuard Agent</h1>
      <p class="login-copy">登录后进入多 Agent 代码审查工作台。</p>
      <label>
        <span>用户名</span>
        <input v-model="loginForm.username" type="text" />
      </label>
      <label>
        <span>密码</span>
        <input v-model="loginForm.password" type="password" @keyup.enter="doLogin" />
      </label>
      <button class="primary-button wide" type="button" @click="doLogin" :disabled="loading">
        <ShieldCheck :size="17" />
        登录
      </button>
      <p class="hint">演示账号：admin / codeguard123</p>
      <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>
    </section>
  </main>

  <main v-else class="app-shell">
    <section class="top-bar">
      <div>
        <p class="eyebrow">Multi Agent Java Backend</p>
        <h1>CodeGuard Agent 企业工作台</h1>
      </div>
      <div class="user-actions">
        <span>{{ currentUser?.displayName }} · {{ currentUser?.role }}</span>
        <button class="ghost-button dark" type="button" @click="refreshInitialData" :disabled="loading">
          <RefreshCw :size="17" />
          刷新
        </button>
        <button class="ghost-button dark" type="button" @click="doLogout">
          <LogOut :size="17" />
          退出
        </button>
      </div>
    </section>

    <section v-if="errorMessage" class="notice error-notice">{{ errorMessage }}</section>
    <section v-if="loading" class="notice loading-notice">
      <Loader2 class="spin" :size="18" />
      {{ loadingMessage }}
    </section>

    <section class="workspace-grid enterprise-grid">
      <aside class="side-rail">
        <div class="section-title">
          <FolderKanban :size="18" />
          项目
        </div>
        <select v-model="selectedProjectKey" class="select-field" @change="refreshProjectHistory">
          <option value="default">default</option>
          <option v-for="project in projects" :key="project.id" :value="project.projectKey">
            {{ project.projectKey }}
          </option>
        </select>

        <div class="new-project">
          <input v-model="newProject.projectKey" placeholder="projectKey" />
          <input v-model="newProject.name" placeholder="项目名" />
          <button class="ghost-button" type="button" @click="createNewProject">创建项目</button>
        </div>

        <div class="section-title history-title">
          <ClipboardList :size="18" />
          内置样例
        </div>
        <div class="sample-list">
          <button
            v-for="sample in samples"
            :key="sample.id"
            class="sample-item"
            :class="{ active: sample.id === selectedSampleId }"
            type="button"
            @click="chooseSample(sample)"
          >
            <span class="sample-title">{{ sample.title }}</span>
            <span class="sample-meta">{{ sample.category }}</span>
          </button>
        </div>

        <div class="section-title history-title">
          <History :size="18" />
          最近审查
        </div>
        <div class="history-list">
          <button
            v-for="item in history"
            :key="item.id"
            class="history-item"
            type="button"
            @click="openReview(item.id)"
          >
            <span class="history-name">{{ item.title }}</span>
            <span class="history-meta">
              {{ item.projectKey }} / {{ item.repositoryName }}
            </span>
            <span class="history-meta">
              {{ recommendationLabel(item.recommendation) }} · 风险 {{ item.riskScore }}
            </span>
            <span class="history-time">{{ formatDate(item.createdAt) }}</span>
          </button>
          <p v-if="history.length === 0" class="empty-text">还没有审查记录</p>
        </div>
      </aside>

      <section class="editor-area">
        <div class="editor-toolbar">
          <label class="title-field">
            <span>审查标题</span>
            <input v-model="title" type="text" />
          </label>
          <label class="title-field small-field">
            <span>仓库名</span>
            <input v-model="repositoryName" type="text" />
          </label>
          <div class="toolbar-actions">
            <button class="ghost-button" type="button" @click="runParsePreview" :disabled="!canRunReview">
              <FileCode2 :size="17" />
              只解析
            </button>
            <button class="ghost-button" type="button" @click="runSelectedSample" :disabled="!selectedSampleId || loading">
              <Sparkles :size="17" />
              跑样例
            </button>
            <button class="primary-button" type="button" @click="runManualReview" :disabled="!canRunReview">
              <Play :size="17" />
              提交审查
            </button>
          </div>
        </div>

        <div class="control-band">
          <div>
            <div class="section-title">
              <SlidersHorizontal :size="18" />
              Agent 开关
            </div>
            <div class="option-grid">
              <label><input v-model="options.enableBugLogic" type="checkbox" /> Bug 逻辑</label>
              <label><input v-model="options.enableSecurity" type="checkbox" /> 安全</label>
              <label><input v-model="options.enableCodeQuality" type="checkbox" /> 质量</label>
              <label><input v-model="options.enableTestCoverage" type="checkbox" /> 测试覆盖</label>
              <label><input v-model="options.enableLlmReview" type="checkbox" /> LLM 复审</label>
              <label><input v-model="options.failOnP0" type="checkbox" /> P0 阻断</label>
            </div>
          </div>

          <div>
            <div class="section-title">
              <GitPullRequest :size="18" />
              GitHub PR
            </div>
            <div class="github-row">
              <input v-model="githubForm.repository" placeholder="owner/repo" />
              <input v-model.number="githubForm.pullNumber" type="number" min="1" />
              <button class="ghost-button" type="button" @click="runGithubReview">
                拉取并审查
              </button>
            </div>
          </div>
        </div>

        <div class="diff-editor">
          <div class="diff-editor-head">
            <span>Git diff 输入</span>
            <span>{{ diffLineCount }} 行</span>
          </div>
          <textarea
            v-model="diffText"
            spellcheck="false"
            placeholder="把 git diff 粘贴到这里，或者从左侧选择一个样例"
          />
        </div>

        <div v-if="selectedSample" class="sample-context">
          <strong>{{ selectedSample.description }}</strong>
          <span>预期标签：{{ selectedSample.expectedTags.join(' / ') }}</span>
        </div>

        <div v-if="parsedPreview" class="parse-preview">
          <span>解析预览</span>
          <strong>{{ parsedPreview.summary.filesChanged }} 个文件</strong>
          <strong>+{{ parsedPreview.summary.additions }}</strong>
          <strong>-{{ parsedPreview.summary.deletions }}</strong>
        </div>
      </section>

      <section class="review-area">
        <div class="result-summary">
          <div>
            <span class="summary-label">任务状态</span>
            <strong>{{ statusLabel(progress?.status ?? currentReview?.status) }}</strong>
          </div>
          <div>
            <span class="summary-label">合并建议</span>
            <strong :class="['recommendation', currentReview?.recommendation?.toLowerCase()]">
              {{ recommendationLabel(currentReview?.recommendation) }}
            </strong>
          </div>
          <div>
            <span class="summary-label">风险分</span>
            <strong>{{ currentReview?.riskScore ?? progress?.riskScore ?? 0 }}</strong>
          </div>
          <div>
            <span class="summary-label">文件</span>
            <strong>{{ currentReview?.diffSummary?.filesChanged ?? 0 }}</strong>
          </div>
        </div>

        <div class="progress-box">
          <div class="progress-head">
            <span>Agent 进度</span>
            <strong>{{ progressPercent }}%</strong>
          </div>
          <div class="progress-track">
            <span :style="{ width: `${progressPercent}%` }"></span>
          </div>
        </div>

        <div class="severity-strip">
          <span class="severity p0">P0 {{ issueCountBySeverity.P0 }}</span>
          <span class="severity p1">P1 {{ issueCountBySeverity.P1 }}</span>
          <span class="severity p2">P2 {{ issueCountBySeverity.P2 }}</span>
          <span class="severity p3">P3 {{ issueCountBySeverity.P3 }}</span>
        </div>

        <div class="tabs">
          <button type="button" :class="{ active: activeTab === 'issues' }" @click="activeTab = 'issues'">
            <ShieldCheck :size="16" />
            问题
          </button>
          <button type="button" :class="{ active: activeTab === 'agents' }" @click="activeTab = 'agents'">
            <Activity :size="16" />
            Agent
          </button>
          <button type="button" :class="{ active: activeTab === 'markdown' }" @click="activeTab = 'markdown'">
            <FileCode2 :size="16" />
            报告
          </button>
        </div>

        <div v-if="activeTab === 'issues'" class="result-panel">
          <button
            v-if="currentReview"
            class="ghost-button export-button"
            type="button"
            @click="downloadSarif"
          >
            <Download :size="16" />
            导出 SARIF
          </button>
          <article v-for="issue in sortedIssues" :key="issue.id" class="issue-card">
            <div class="issue-head">
              <span :class="['severity-dot', issue.severity.toLowerCase()]">{{ issue.severity }}</span>
              <strong>{{ issue.title }}</strong>
            </div>
            <p>{{ issue.detail }}</p>
            <dl>
              <dt>位置</dt>
              <dd>{{ issue.filePath }}{{ issue.lineNumber ? `:${issue.lineNumber}` : '' }}</dd>
              <dt>Agent</dt>
              <dd>{{ agentLabel(issue.agentType) }} / {{ issue.tag }}</dd>
              <dt>建议</dt>
              <dd>{{ issue.suggestion }}</dd>
            </dl>
            <pre v-if="issue.evidence">{{ issue.evidence }}</pre>
          </article>
          <p v-if="!currentReview" class="empty-state">提交审查后，这里会显示最终问题列表。</p>
          <p v-else-if="sortedIssues.length === 0" class="empty-state">这次审查没有发现明确问题。</p>
        </div>

        <div v-if="activeTab === 'agents'" class="result-panel">
          <article v-for="trace in progress?.traces ?? currentReview?.traces ?? []" :key="trace.id" class="trace-row">
            <div class="trace-main">
              <strong>{{ agentLabel(trace.agentType) }}</strong>
              <span>{{ trace.outputSummary || trace.skipReason || trace.inputSummary }}</span>
              <small v-if="trace.provider">{{ trace.provider }} / {{ trace.modelName }}</small>
            </div>
            <div class="trace-meta">
              <span>{{ statusLabel(trace.status) }}</span>
              <span>{{ trace.durationMs }} ms</span>
            </div>
          </article>
          <p v-if="!progress && !currentReview" class="empty-state">审查开始后，这里会显示每个 Agent 的执行轨迹。</p>
        </div>

        <div v-if="activeTab === 'markdown'" class="result-panel">
          <pre class="markdown-report">{{ markdownText || '审查完成后，这里会显示 Markdown 报告。' }}</pre>
        </div>
      </section>
    </section>
  </main>
</template>
