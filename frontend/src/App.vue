<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, onUnmounted } from "vue";
import {
  Trophy,
  CalendarDays,
  Users,
  ShieldCheck,
  Settings,
  LogOut,
  Menu,
  X,
  Plus,
  MapPin,
  BarChart3,
  Flag,
} from "@lucide/vue";
import { api, resetApi } from "./api.js";
import {
  t,
  language,
  stateName,
  dateTime,
  errorMessage,
  confirmation,
  answerConfirmation,
  ask,
  cloneRecord,
} from "./ui.js";
import AdminPanel from "./components/AdminPanel.vue";
import EventView from "./components/EventView.vue";
const user = ref(null),
  section = ref("events"),
  pending = ref(false),
  message = ref(""),
  success = ref(""),
  ready = ref(false),
  sidebar = ref(false),
  about = ref(false),
  password = ref(null),
  login = ref({ username: "", password: "" }),
  showPublic = ref(false),
  television = ref(false),
  selected = ref(null),
  eventRef = ref(null),
  adminRef = ref(null),
  options = ref({
    seasons: [],
    courts: [],
    accounts: [],
    departments: [],
    categories: [],
  }),
  events = ref({ items: [], total: 0 }),
  query = ref(""),
  filter = ref(""),
  sort = ref("newest"),
  page = ref(0),
  rows = ref([]),
  reports = ref(null),
  masterType = ref("seasons"),
  editing = ref(null),
  editingType = ref(""),
  publicEvents = ref([]),
  recordQuery = ref(""),
  recordPage = ref(0);
const recordRows = computed(() =>
  rows.value.filter((r) =>
    (
      JSON.stringify(r) +
      " " +
      stateName(r.game?.status || r.entry?.status || "")
    )
      .toLowerCase()
      .includes(recordQuery.value.toLowerCase()),
  ),
);
const rowWindow = computed(() =>
  recordRows.value.slice(recordPage.value * 15, (recordPage.value + 1) * 15),
);
const recordSection = computed(
  () =>
    !showPublic.value &&
    !selected.value &&
    ["masters", "mine", "referee", "audit"].includes(section.value),
);
const icons = {
  events: CalendarDays,
  mine: Flag,
  referee: Trophy,
  masters: MapPin,
  reports: BarChart3,
  audit: ShieldCheck,
  users: Users,
  roles: ShieldCheck,
  settings: Settings,
};
const has = (p) => user.value?.permissions.includes(p);
const staff = computed(
  () => has("manage") && ["ALL", "DEPARTMENT"].includes(user.value?.scope),
);
const menuName = computed(() => {
  const m = user.value?.menus.find((m) => m.code === section.value);
  return m
    ? language.value === "zh"
      ? m.name
      : m.nameEn
    : t("赛事", "Tournaments");
});
/** 写操作禁用重复提交，未知结果不重放；失败保留表单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function perform(path, method, body) {
  if (pending.value) return null;
  pending.value = true;
  message.value = "";
  success.value = "";
  try {
    const out = await api(path, { method, body });
    success.value = t("已保存", "Saved");
    return out;
  } catch (e) {
    handle(e);
    return null;
  } finally {
    pending.value = false;
  }
}
function handle(e) {
  message.value = errorMessage(e);
  if (e.status === 401) {
    user.value = null;
    resetApi();
    login.value.password = "";
  }
}
async function load() {
  ready.value = false;
  message.value = "";
  try {
    if (showPublic.value) {
      if (selected.value)
        selected.value = await api(
          "/public/events/" + selected.value.tournament.id,
        );
      else publicEvents.value = await api("/public/events?page=" + page.value);
      return;
    }
    if (!user.value) return;
    options.value = await api("/options");
    if (selected.value) {
      selected.value = await api("/events/" + selected.value.tournament.id);
      return;
    }
    if (section.value === "events")
      events.value = await api(
        `/events?search=${encodeURIComponent(query.value)}&status=${filter.value}&sort=${sort.value}&page=${page.value}`,
      );
    else if (section.value === "reports") reports.value = await api("/reports");
    else if (section.value === "masters")
      rows.value = await api("/masters/" + masterType.value);
    else if (["mine", "referee", "audit"].includes(section.value))
      rows.value = await api("/" + section.value);
  } catch (e) {
    if (showPublic.value && e.status === 404) selected.value = null;
    handle(e);
  } finally {
    ready.value = true;
  }
}
async function canLeave() {
  if (
    editing.value &&
    !(await ask(t("放弃未保存的表单？", "Discard unsaved form?")))
  )
    return false;
  if (eventRef.value && !(await eventRef.value.canLeave())) return false;
  if (adminRef.value && !(await adminRef.value.canLeave())) return false;
  editing.value = null;
  return true;
}
async function signIn() {
  const out = await perform("/auth/login", "POST", login.value);
  login.value.password = "";
  if (out) {
    resetApi();
    user.value = out;
    success.value = "";
    section.value = out.menus[0]?.code || "events";
    await load();
  }
}
async function signOut() {
  if (await canLeave()) {
    const ok = await perform("/auth/logout", "POST", {});
    if (ok) {
      user.value = null;
      selected.value = null;
      resetApi();
    }
  }
}
async function navigate(code) {
  if (pending.value || !(await canLeave())) return;
  section.value = code;
  selected.value = null;
  page.value = 0;
  sidebar.value = false;
  query.value = "";
  filter.value = "";
  rows.value = [];
  recordQuery.value = "";
  recordPage.value = 0;
  success.value = "";
  await load();
}
async function openEvent(id) {
  if (!(await canLeave())) return;
  message.value = "";
  try {
    selected.value = await api(
      (showPublic.value ? "/public/events/" : "/events/") + id,
    );
    if (showPublic.value) history.replaceState(null, "", `#public/${id}`);
  } catch (e) {
    handle(e);
  }
}
async function back() {
  if (!(await canLeave())) return;
  selected.value = null;
  if (showPublic.value) history.replaceState(null, "", "#public");
  await load();
}
function localInput(v) {
  const d = new Date(v);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);
}
function editEvent(row = null) {
  const start = new Date();
  start.setMinutes(0, 0, 0);
  start.setHours(start.getHours() + 1);
  editingType.value = "event";
  editing.value = row
    ? {
        ...cloneRecord(row),
        startsAt: localInput(row.startsAt),
        endsAt: localInput(row.endsAt),
      }
    : {
        name: "",
        seasonId: options.value.seasons[0]?.id || "",
        category: options.value.categories[0]?.code || "",
        description: "",
        format: "GROUP_KO",
        groups: 2,
        advance: 2,
        bestOf: 3,
        capacity: 16,
        startsAt: localInput(start),
        endsAt: localInput(new Date(start.getTime() + 86400000)),
      };
}
function editMaster(row = null) {
  editingType.value = "master";
  editing.value = row
    ? cloneRecord(row)
    : {
        name: "",
        departmentId: options.value.departments[0]?.id || "",
        enabled: true,
      };
}
async function saveEditing() {
  const b = { ...editing.value };
  if (editingType.value === "event") {
    b.startsAt = new Date(b.startsAt).toISOString();
    b.endsAt = new Date(b.endsAt).toISOString();
  }
  const root =
    editingType.value === "event" ? "/events" : "/masters/" + masterType.value;
  const out = await perform(
    root + (b.id ? "/" + b.id : ""),
    b.id ? "PUT" : "POST",
    b,
  );
  if (out) {
    editing.value = null;
    if (editingType.value === "event") await openEvent(out.id);
    else await load();
  }
}
async function remove(row, master = false) {
  if (
    await ask(t("删除未被业务引用的记录？", "Delete this unreferenced record?"))
  ) {
    const ok = await perform(
      `${master ? "/masters/" + masterType.value : "/events"}/${row.id}?version=${row.version}`,
      "DELETE",
    );
    if (ok) await load();
  }
}
async function changeMaster(k) {
  if (!(await canLeave())) return;
  masterType.value = k;
  recordQuery.value = "";
  recordPage.value = 0;
  await load();
}
async function savePassword() {
  const ok = await perform("/auth/password", "POST", password.value);
  if (ok) {
    password.value = null;
    user.value = null;
    resetApi();
  }
}
async function publicMode() {
  if (!(await canLeave())) return;
  showPublic.value = true;
  selected.value = null;
  page.value = 0;
  history.replaceState(null, "", "#public");
  await load();
}
async function workspaceMode() {
  showPublic.value = false;
  television.value = false;
  selected.value = null;
  history.replaceState(null, "", location.pathname);
  await load();
}
async function route() {
  if (location.hash.startsWith("#public")) {
    showPublic.value = true;
    const id = Number(location.hash.split("/")[1]);
    selected.value = id > 0 ? { tournament: { id } } : null;
    await load();
  } else if (showPublic.value) await workspaceMode();
}
let timer;
onMounted(async () => {
  try {
    user.value = await api("/auth/me");
  } catch (e) {
    if (e.status !== 401) handle(e);
  }
  if (user.value) section.value = user.value.menus[0]?.code || "events";
  if (location.hash.startsWith("#public")) await route();
  else await load();
  window.addEventListener("hashchange", route);
  timer = setInterval(async () => {
    if (showPublic.value && selected.value && !pending.value) {
      try {
        selected.value = await api(
          "/public/events/" + selected.value.tournament.id,
        );
      } catch (e) {
        if (e.status === 404) selected.value = null;
        handle(e);
      }
    }
  }, 15000);
  ready.value = true;
});
onUnmounted(() => {
  window.removeEventListener("hashchange", route);
  clearInterval(timer);
});
</script>
<template>
  <div v-if="!user && !showPublic" class="login-page">
    <div class="login-box">
      <header class="login-brand">
        <img src="/brand/logo.jpg" alt="知华科技" />
        <div>
          <b>LeagueDesk</b
          ><span>{{
            t("知华赛事与联赛管理", "ZhiHua tournament management")
          }}</span>
        </div>
      </header>
      <h1>{{ t("登录工作台", "Sign in") }}</h1>
      <form @submit.prevent="signIn">
        <label
          >{{ t("账号", "Username")
          }}<input
            v-model="login.username"
            autocomplete="username"
            required
            maxlength="60"
            :disabled="pending" /></label
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="login.password"
            type="password"
            autocomplete="current-password"
            required
            :disabled="pending"
        /></label>
        <p v-if="message" class="form-error" role="alert">{{ message }}</p>
        <button class="primary" type="submit" :disabled="pending">
          {{ pending ? t("登录中…", "Signing in…") : t("登录", "Sign in") }}
        </button>
      </form>
      <div class="login-links">
        <button class="link" @click="publicMode">
          {{ t("查看公开赛事", "Public tournaments") }}</button
        ><button
          class="link"
          @click="language = language === 'zh' ? 'en' : 'zh'"
        >
          {{ language === "zh" ? "English" : "中文" }}
        </button>
      </div>
      <p class="login-foot">
        {{
          t(
            "公开源码学习版 · 非商业使用",
            "Public source · Non-commercial use",
          )
        }}<br /><a
          href="https://www.zhuatech.cn/"
          target="_blank"
          rel="noopener"
          >{{ t("知华科技官网", "ZhiHua Technology") }}</a
        >
        ·
        <button class="link" @click="about = true">
          {{ t("授权咨询", "Licensing") }}
        </button>
      </p>
    </div>
  </div>
  <div v-else :class="showPublic ? 'public-layout' : 'app-shell'">
    <aside v-if="!showPublic" class="sidebar" :class="{ open: sidebar }">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" />
        <div>
          <b>LeagueDesk</b
          ><small>{{ t("赛事与联赛", "Tournaments & leagues") }}</small>
        </div>
        <button class="mobile-close" @click="sidebar = false">
          <X :size="20" />
        </button>
      </div>
      <nav>
        <button
          v-for="m in user.menus"
          :key="m.code"
          :class="{ active: section === m.code }"
          :disabled="pending"
          @click="navigate(m.code)"
        >
          <component :is="icons[m.code] || CalendarDays" :size="18" /><span>{{
            language === "zh" ? m.name : m.nameEn
          }}</span>
        </button>
      </nav>
      <footer class="sidebar-footer">
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener">{{
          t("知华科技官网", "ZhiHua Technology")
        }}</a
        ><button @click="about = true">
          {{ t("关于与商业授权", "About & licensing") }}</button
        ><small
          >{{ t("公开源码学习版", "Non-commercial source") }} · 1.0.0</small
        >
      </footer>
    </aside>
    <div
      :class="showPublic ? 'public-content' : 'workspace'"
      :data-display="television"
    >
      <header :class="showPublic ? 'public-header' : 'topbar'">
        <template v-if="showPublic"
          ><a href="#public" @click.prevent="back"
            ><img src="/brand/logo.jpg" alt="知华科技" /><b>LeagueDesk</b></a
          ></template
        ><template v-else
          ><button
            class="icon-button mobile-menu"
            :aria-label="t('展开菜单', 'Open menu')"
            @click="sidebar = true"
          >
            <Menu :size="20" /></button
          ><span>{{ menuName }}</span></template
        ><span class="grow"></span
        ><button v-if="!showPublic" @click="publicMode">
          {{ t("公开赛事", "Public events") }}</button
        ><button @click="language = language === 'zh' ? 'en' : 'zh'">
          {{ language === "zh" ? "English" : "中文" }}</button
        ><template v-if="showPublic"
          ><button v-if="selected" @click="television = !television">
            {{
              television
                ? t("退出大屏", "Exit display")
                : t("大屏模式", "Display mode")
            }}</button
          ><button @click="workspaceMode">
            {{ t("工作台登录", "Staff sign-in") }}
          </button></template
        ><template v-else
          ><span class="user-name">{{ user.displayName }}</span
          ><button
            class="link"
            @click="password = { oldPassword: '', newPassword: '' }"
          >
            {{ t("改密", "Password") }}</button
          ><button
            class="icon-button"
            :aria-label="t('退出登录', 'Sign out')"
            :disabled="pending"
            @click="signOut"
          >
            <LogOut :size="17" /></button
        ></template>
      </header>
      <main :class="showPublic ? 'public-main' : 'content'">
        <p v-if="message" class="notice error" role="alert">
          {{ message }}<button @click="message = ''">×</button>
        </p>
        <p
          v-else-if="success && !showPublic"
          class="notice success"
          role="status"
        >
          {{ success }}<button @click="success = ''">×</button>
        </p>
        <form
          v-if="recordSection"
          class="toolbar"
          @submit.prevent="recordPage = 0"
        >
          <label
            >{{ t("检索记录", "Search records")
            }}<input
              v-model="recordQuery"
              @input="recordPage = 0"
              :placeholder="
                t('名称、赛事、场地或状态', 'Name, event, court or status')
              "
          /></label>
          <span class="muted"
            >{{ recordRows.length }} {{ t("条记录", "records") }}</span
          >
        </form>
        <EventView
          v-if="selected?.entries"
          ref="eventRef"
          :detail="selected"
          :public-view="showPublic"
          :user="user"
          :options="options"
          :pending="pending"
          :message="message"
          :perform="perform"
          @updated="selected = $event"
          @back="back"
          @error="handle"
        />
        <section v-else-if="showPublic">
          <header class="page-heading">
            <div>
              <h1>{{ t("公开赛事", "Public tournaments") }}</h1>
              <p class="muted">
                {{ t("赛程、结果和对阵", "Schedules, results and brackets") }}
              </p>
            </div>
            <button @click="load">{{ t("刷新", "Refresh") }}</button>
          </header>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("赛事", "Tournament") }}</th>
                  <th>{{ t("日期", "Dates") }}</th>
                  <th>{{ t("状态", "Status") }}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="e in publicEvents" :key="e.id">
                  <td>{{ e.name }}</td>
                  <td>{{ dateTime(e.startsAt) }}</td>
                  <td>
                    <span class="badge" :class="e.status">{{
                      stateName(e.status)
                    }}</span>
                  </td>
                  <td>
                    <button @click="openEvent(e.id)">
                      {{ t("查看赛程", "View schedule") }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-if="!publicEvents.length" class="empty">
              {{
                ready
                  ? t("暂无公开赛事", "No published tournaments")
                  : t("读取中…", "Loading…")
              }}
            </div>
          </div>
          <footer class="pagination">
            <button
              :disabled="page === 0"
              @click="
                page--;
                load();
              "
            >
              {{ t("上一页", "Previous") }}</button
            ><span>{{ page + 1 }}</span
            ><button
              :disabled="publicEvents.length < 20"
              @click="
                page++;
                load();
              "
            >
              {{ t("下一页", "Next") }}
            </button>
          </footer>
        </section>
        <AdminPanel
          v-else-if="['users', 'roles', 'settings'].includes(section)"
          ref="adminRef"
          :key="section"
          :section="section"
          :pending="pending"
          :message="message"
          :perform="perform"
          @error="handle"
        />
        <section v-else-if="section === 'events'">
          <header class="page-heading">
            <div>
              <h1>{{ t("赛事管理", "Tournaments") }}</h1>
              <p class="muted">
                {{
                  t("报名、排赛与结果", "Registration, scheduling and results")
                }}
              </p>
            </div>
            <div class="actions">
              <button :disabled="pending" @click="load">
                {{ t("刷新", "Refresh") }}</button
              ><button
                v-if="staff"
                class="primary"
                :disabled="pending"
                @click="editEvent()"
              >
                <Plus :size="16" />{{ t("新建赛事", "New tournament") }}
              </button>
            </div>
          </header>
          <form
            class="toolbar"
            @submit.prevent="
              page = 0;
              load();
            "
          >
            <label class="search-label"
              >{{ t("赛事名称", "Tournament name")
              }}<input
                v-model="query"
                :placeholder="t('搜索赛事', 'Search events')" /></label
            ><label
              >{{ t("状态", "Status")
              }}<select v-model="filter">
                <option value="">{{ t("全部", "All") }}</option>
                <option
                  v-for="s in [
                    'DRAFT',
                    'OPEN',
                    'GROUP',
                    'KNOCKOUT',
                    'FINISHED',
                    'CANCELLED',
                  ]"
                  :key="s"
                  :value="s"
                >
                  {{ stateName(s) }}
                </option>
              </select></label
            ><label
              >{{ t("排序", "Sort")
              }}<select v-model="sort">
                <option value="newest">{{ t("最新创建", "Newest") }}</option>
                <option value="time">{{ t("开始时间", "Start time") }}</option>
                <option value="name">{{ t("名称", "Name") }}</option>
              </select></label
            ><button type="submit">{{ t("查询", "Search") }}</button>
          </form>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("赛事", "Tournament") }}</th>
                  <th>{{ t("赛制", "Format") }}</th>
                  <th>{{ t("开始时间", "Starts") }}</th>
                  <th>{{ t("状态", "Status") }}</th>
                  <th>{{ t("公开", "Public") }}</th>
                  <th>{{ t("操作", "Actions") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="e in events.items" :key="e.id">
                  <td>
                    <button class="link" @click="openEvent(e.id)">
                      <b>{{ e.name }}</b></button
                    ><small>#{{ e.id }}</small>
                  </td>
                  <td>
                    {{
                      e.format === "GROUP_KO"
                        ? t("小组＋淘汰", "Groups + knockout")
                        : t("直接淘汰", "Single elimination")
                    }}<small>{{ t("胜局制", "Best of") }} {{ e.bestOf }}</small>
                  </td>
                  <td>{{ dateTime(e.startsAt) }}</td>
                  <td>
                    <span class="badge" :class="e.status">{{
                      stateName(e.status)
                    }}</span>
                  </td>
                  <td>{{ e.published ? t("已发布", "Published") : "—" }}</td>
                  <td>
                    <div class="actions">
                      <button @click="openEvent(e.id)">
                        {{ t("进入", "Open") }}</button
                      ><button
                        v-if="staff && e.status === 'DRAFT'"
                        :disabled="pending"
                        @click="editEvent(e)"
                      >
                        {{ t("编辑", "Edit") }}</button
                      ><button
                        v-if="staff && e.status === 'DRAFT'"
                        :disabled="pending"
                        @click="remove(e)"
                      >
                        {{ t("删除", "Delete") }}
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-if="!events.items.length" class="empty">
              {{
                ready
                  ? t("暂无赛事", "No tournaments")
                  : t("读取中…", "Loading…")
              }}
              <p v-if="staff && ready">
                {{
                  t(
                    "先建立赛季、场地和参赛账号，再新建赛事。",
                    "Create a season, courts and entrant accounts before an event.",
                  )
                }}
              </p>
              <button
                v-if="staff && has('masters') && ready"
                @click="navigate('masters')"
              >
                {{ t("建立赛季与场地", "Set up seasons & courts") }}
              </button>
            </div>
          </div>
          <footer class="pagination">
            <span
              >{{ events.total }} {{ t("条", "events") }} · {{ page + 1 }} /
              {{ Math.max(1, Math.ceil(events.total / 20)) }}</span
            ><button
              :disabled="page === 0"
              @click="
                page--;
                load();
              "
            >
              {{ t("上一页", "Previous") }}</button
            ><button
              :disabled="(page + 1) * 20 >= events.total"
              @click="
                page++;
                load();
              "
            >
              {{ t("下一页", "Next") }}
            </button>
          </footer>
        </section>
        <section v-else-if="section === 'masters'">
          <header class="page-heading">
            <h1>{{ t("赛季与场地", "Seasons & courts") }}</h1>
            <button class="primary" :disabled="pending" @click="editMaster()">
              <Plus :size="16" />{{ t("新增", "Add") }}
            </button>
          </header>
          <nav class="sub-tabs">
            <button
              :class="{ active: masterType === 'seasons' }"
              @click="changeMaster('seasons')"
            >
              {{ t("赛季", "Seasons") }}</button
            ><button
              :class="{ active: masterType === 'courts' }"
              @click="changeMaster('courts')"
            >
              {{ t("场地", "Courts") }}
            </button>
          </nav>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("名称", "Name") }}</th>
                  <th>{{ t("组织", "Organization") }}</th>
                  <th>{{ t("状态", "Status") }}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in rowWindow" :key="r.id">
                  <td>{{ r.name }}</td>
                  <td>
                    {{
                      options.departments.find((d) => d.id === r.departmentId)
                        ?.name || "#" + r.departmentId
                    }}
                  </td>
                  <td>
                    {{
                      r.enabled ? t("启用", "Active") : t("停用", "Disabled")
                    }}
                  </td>
                  <td>
                    <div class="actions">
                      <button :disabled="pending" @click="editMaster(r)">
                        {{ t("编辑", "Edit") }}</button
                      ><button :disabled="pending" @click="remove(r, true)">
                        {{ t("删除", "Delete") }}
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-if="!rows.length" class="empty">
              {{ t("暂无记录", "No records") }}
            </div>
          </div>
        </section>
        <section v-else-if="section === 'mine'">
          <header class="page-heading">
            <h1>{{ t("我的报名", "My entries") }}</h1>
            <button @click="load">{{ t("刷新", "Refresh") }}</button>
          </header>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("赛事", "Tournament") }}</th>
                  <th>{{ t("显示名", "Display name") }}</th>
                  <th>{{ t("审核", "Review") }}</th>
                  <th>{{ t("签到", "Check-in") }}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in rowWindow" :key="r.entry.id">
                  <td>{{ r.tournament.name }}</td>
                  <td>{{ r.entry.name }}</td>
                  <td>
                    <span class="badge" :class="r.entry.status">{{
                      stateName(r.entry.status)
                    }}</span>
                  </td>
                  <td>
                    {{ r.entry.checkedIn ? t("已签到", "Checked in") : "—" }}
                  </td>
                  <td>
                    <button @click="openEvent(r.tournament.id)">
                      {{ t("进入赛事", "Open event") }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-if="!rows.length" class="empty">
              {{
                t(
                  "暂无报名；到赛事页选择开放报名的赛事。",
                  "No entries. Choose an open tournament on the events page.",
                )
              }}
            </div>
          </div>
        </section>
        <section v-else-if="section === 'referee'">
          <header class="page-heading">
            <div>
              <h1>{{ t("裁判工作台", "Referee desk") }}</h1>
              <p class="muted">
                {{
                  t(
                    "仅显示指派给本账号的比赛",
                    "Matches assigned to this account",
                  )
                }}
              </p>
            </div>
            <button @click="load">{{ t("刷新", "Refresh") }}</button>
          </header>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("赛事／场次", "Event / match") }}</th>
                  <th>{{ t("双方", "Entrants") }}</th>
                  <th>{{ t("计划时间", "Scheduled") }}</th>
                  <th>{{ t("场地", "Court") }}</th>
                  <th>{{ t("状态", "Status") }}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in rowWindow" :key="r.game.id">
                  <td>
                    {{ r.tournament.name }}<small>#{{ r.game.id }}</small>
                  </td>
                  <td>
                    {{ r.home }}<small>vs {{ r.away }}</small>
                  </td>
                  <td>{{ dateTime(r.game.startsAt) }}</td>
                  <td>{{ r.court }}</td>
                  <td>
                    <span class="badge" :class="r.game.status">{{
                      stateName(r.game.status)
                    }}</span>
                  </td>
                  <td>
                    <button @click="openEvent(r.tournament.id)">
                      {{ t("办理比赛", "Open match") }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-if="!rows.length" class="empty">
              {{ t("暂无指派比赛", "No assigned matches") }}
            </div>
          </div>
        </section>
        <section v-else-if="section === 'reports' && reports">
          <header class="page-heading">
            <div>
              <h1>{{ t("赛季积分", "Season standings") }}</h1>
              <p class="muted">
                {{
                  t(
                    "已完成赛事：冠军5分、亚军3分、其他入签者1分。",
                    "Finished events: winner 5, runner-up 3, other drawn entrants 1.",
                  )
                }}
              </p>
            </div>
            <button @click="load">{{ t("刷新", "Refresh") }}</button>
          </header>
          <div class="stats-strip">
            <div
              v-for="k in ['events', 'active', 'finished', 'entries']"
              :key="k"
            >
              <span>{{
                {
                  events: t("赛事总数", "Events"),
                  active: t("正在办赛", "Active"),
                  finished: t("已完成", "Finished"),
                  entries: t("报名记录", "Entries"),
                }[k]
              }}</span
              ><strong>{{ reports[k] }}</strong>
            </div>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("赛季", "Season") }}</th>
                  <th>{{ t("参赛单位", "Entrant") }}</th>
                  <th>{{ t("参赛次数", "Events") }}</th>
                  <th>{{ t("冠军次数", "Titles") }}</th>
                  <th>{{ t("积分", "Points") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(r, i) in reports.standings" :key="i">
                  <td>{{ r.season }}</td>
                  <td>{{ r.name }}</td>
                  <td>{{ r.events }}</td>
                  <td>{{ r.wins }}</td>
                  <td class="score">{{ r.points }}</td>
                </tr>
              </tbody>
            </table>
            <div v-if="!reports.standings.length" class="empty">
              {{
                t("完成赛事后产生积分", "Finish an event to generate points")
              }}
            </div>
          </div>
        </section>
        <section v-else-if="section === 'audit'">
          <header class="page-heading">
            <h1>{{ t("操作审计", "Audit") }}</h1>
            <button @click="load">{{ t("刷新", "Refresh") }}</button>
          </header>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("时间", "Time") }}</th>
                  <th>{{ t("账号", "Account") }}</th>
                  <th>{{ t("动作", "Action") }}</th>
                  <th>{{ t("对象", "Object") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in rowWindow" :key="r.id">
                  <td>{{ dateTime(r.createdAt) }}</td>
                  <td>{{ r.actor }}</td>
                  <td>{{ r.action }}</td>
                  <td>{{ r.objectId }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <div v-if="recordSection" class="pagination">
          <span
            >{{ recordRows.length }} {{ t("条", "records") }} ·
            {{ recordPage + 1 }} /
            {{ Math.max(1, Math.ceil(recordRows.length / 15)) }}</span
          >
          <button :disabled="recordPage === 0" @click="recordPage--">
            {{ t("上一页", "Previous") }}
          </button>
          <button
            :disabled="(recordPage + 1) * 15 >= recordRows.length"
            @click="recordPage++"
          >
            {{ t("下一页", "Next") }}
          </button>
        </div>
      </main>
      <footer v-if="showPublic" class="public-footer">
        {{
          t(
            "知华科技 · 公开源码学习版",
            "ZhiHua Technology · Non-commercial source",
          )
        }}
        ·
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >zhuatech.cn</a
        >
      </footer>
    </div>
  </div>
  <div v-if="editing" class="modal-backdrop">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('编辑赛事目录', 'Edit event directory')"
    >
      <header>
        <h2>
          {{
            editingType === "event"
              ? t("赛事草稿", "Tournament draft")
              : masterType === "seasons"
                ? t("赛季", "Season")
                : t("场地", "Court")
          }}
        </h2>
        <button :disabled="pending" @click="canLeave">×</button>
      </header>
      <p v-if="message" class="form-error" role="alert">{{ message }}</p>
      <form @submit.prevent="saveEditing">
        <label
          >{{ t("名称", "Name")
          }}<input
            v-model="editing.name"
            required
            maxlength="120"
            :disabled="pending" /></label
        ><template v-if="editingType === 'event'"
          ><div class="form-grid">
            <label
              >{{ t("赛季", "Season")
              }}<select v-model="editing.seasonId" required :disabled="pending">
                <option value="">{{ t("请选择", "Choose") }}</option>
                <option v-for="s in options.seasons" :key="s.id" :value="s.id">
                  {{ s.name }}
                </option>
              </select></label
            ><label
              >{{ t("分类", "Category")
              }}<select v-model="editing.category" required :disabled="pending">
                <option
                  v-for="c in options.categories"
                  :key="c.id"
                  :value="c.code"
                >
                  {{ language === "zh" ? c.name : c.nameEn }}
                </option>
              </select></label
            ><label
              >{{ t("赛制", "Format")
              }}<select v-model="editing.format" :disabled="pending">
                <option value="GROUP_KO">
                  {{ t("小组循环＋淘汰", "Groups + knockout") }}
                </option>
                <option value="KNOCKOUT">
                  {{ t("直接淘汰", "Single elimination") }}
                </option>
              </select></label
            ><label
              >{{ t("名额上限", "Capacity")
              }}<input
                v-model.number="editing.capacity"
                type="number"
                min="2"
                max="64"
                required
                :disabled="pending" /></label
            ><label v-if="editing.format === 'GROUP_KO'"
              >{{ t("分组数", "Groups")
              }}<input
                v-model.number="editing.groups"
                type="number"
                min="1"
                max="8"
                required
                :disabled="pending" /></label
            ><label v-if="editing.format === 'GROUP_KO'"
              >{{ t("每组晋级人数", "Qualifiers per group")
              }}<input
                v-model.number="editing.advance"
                type="number"
                min="1"
                max="4"
                required
                :disabled="pending" /></label
            ><label
              >{{ t("胜局制", "Best of")
              }}<select v-model.number="editing.bestOf" :disabled="pending">
                <option :value="3">{{ t("三局两胜", "Best of 3") }}</option>
                <option :value="5">{{ t("五局三胜", "Best of 5") }}</option>
                <option :value="7">{{ t("七局四胜", "Best of 7") }}</option>
              </select></label
            ><label
              >{{ t("开始时间", "Event start")
              }}<input
                v-model="editing.startsAt"
                type="datetime-local"
                required
                :disabled="pending" /></label
            ><label
              >{{ t("结束时间", "Event end")
              }}<input
                v-model="editing.endsAt"
                type="datetime-local"
                required
                :disabled="pending"
            /></label>
          </div>
          <p class="hint">
            {{
              t(
                "按浏览器时区；开放报名后赛制不可修改。",
                "Browser timezone. Format locks when registration opens.",
              )
            }}
            {{ Intl.DateTimeFormat().resolvedOptions().timeZone }}
          </p>
          <label
            >{{ t("赛事说明（公开）", "Public event description")
            }}<textarea
              v-model="editing.description"
              maxlength="2000"
              :disabled="pending"
            /></label></template
        ><template v-else
          ><label
            >{{ t("组织", "Organization")
            }}<select
              v-model="editing.departmentId"
              required
              :disabled="pending || !!editing.id"
            >
              <option
                v-for="d in options.departments"
                :key="d.id"
                :value="d.id"
              >
                {{ d.name }}
              </option>
            </select></label
          ><label class="check"
            ><input
              v-model="editing.enabled"
              type="checkbox"
              :disabled="pending"
            />{{ t("启用", "Enabled") }}</label
          ></template
        >
        <div class="dialog-actions">
          <button type="button" :disabled="pending" @click="canLeave">
            {{ t("返回", "Back") }}</button
          ><button type="submit" class="primary" :disabled="pending">
            {{ pending ? t("保存中…", "Saving…") : t("保存", "Save") }}
          </button>
        </div>
      </form>
    </section>
  </div>
  <div v-if="confirmation" class="modal-backdrop">
    <section
      class="dialog small-dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="t('确认操作', 'Confirm action')"
    >
      <h2>{{ t("确认操作", "Confirm action") }}</h2>
      <p>{{ confirmation.message }}</p>
      <div class="dialog-actions">
        <button @click="answerConfirmation(false)">
          {{ t("返回", "Back") }}</button
        ><button class="primary" @click="answerConfirmation(true)">
          {{ t("确认", "Confirm") }}
        </button>
      </div>
    </section>
  </div>
  <div v-if="password" class="modal-backdrop">
    <section
      class="modal small-dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="t('修改密码', 'Change password')"
    >
      <header>
        <h2>{{ t("修改密码", "Change password") }}</h2>
        <button :disabled="pending" @click="password = null">×</button>
      </header>
      <p v-if="message" class="form-error">{{ message }}</p>
      <form @submit.prevent="savePassword">
        <label
          >{{ t("旧密码", "Current password")
          }}<input
            v-model="password.oldPassword"
            type="password"
            autocomplete="current-password"
            required
            :disabled="pending" /></label
        ><label
          >{{ t("新密码", "New password")
          }}<input
            v-model="password.newPassword"
            type="password"
            autocomplete="new-password"
            required
            :disabled="pending"
        /></label>
        <p class="hint">
          {{
            t(
              "12–72字节，含大小写字母和数字；保存后重新登录。",
              "12–72 bytes, upper/lowercase and a digit. Sign in again after saving.",
            )
          }}
        </p>
        <div class="dialog-actions">
          <button class="primary" :disabled="pending">
            {{ t("保存密码", "Save password") }}
          </button>
        </div>
      </form>
    </section>
  </div>
  <div v-if="about" class="modal-backdrop">
    <section
      class="modal about-dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="t('关于与商业授权', 'About & licensing')"
    >
      <header>
        <h2>LeagueDesk 1.0.0</h2>
        <button @click="about = false">×</button>
      </header>
      <p>
        {{
          t(
            "知华科技（上海如静知华信息科技有限公司）",
            "ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)",
          )
        }}
      </p>
      <p>
        {{
          t(
            "公开源码学习版，仅限个人学习交流，未经书面授权不得商用。",
            "Public source for personal learning and non-commercial research. Commercial use requires written authorization.",
          )
        }}
      </p>
      <p>
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >https://www.zhuatech.cn/</a
        >
      </p>
      <template v-if="language === 'zh'"
        ><p>商业授权或深度定制开发请联系知华科技：zhuatech、zhuatech2。</p>
        <div class="contact-grid">
          <figure>
            <img src="/brand/wechat-zhuatech.png" alt="微信 zhuatech" />
            <figcaption>zhuatech</figcaption>
          </figure>
          <figure>
            <img src="/brand/wechat-zhuatech2.png" alt="微信 zhuatech2" />
            <figcaption>zhuatech2</figcaption>
          </figure>
        </div></template
      >
      <p v-else>
        <a href="mailto:han@zhuatech.cn">han@zhuatech.cn</a><br /><a
          href="mailto:jack@zhuatech.cn"
          >jack@zhuatech.cn</a
        ><br /><a
          href="https://wa.me/8617521234993"
          target="_blank"
          rel="noopener"
          >WhatsApp +86 17521234993</a
        >
      </p>
    </section>
  </div>
</template>
