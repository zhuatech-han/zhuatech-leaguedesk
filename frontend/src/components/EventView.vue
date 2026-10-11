<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed } from "vue";
import { t, stateName, dateTime, ask } from "../ui.js";
import { download } from "../api.js";
const props = defineProps({
  detail: { type: Object, required: true },
  user: { type: Object, default: null },
  options: { type: Object, default: () => ({ accounts: [], courts: [] }) },
  pending: Boolean,
  publicView: Boolean,
  message: { type: String, default: "" },
  perform: { type: Function, default: async () => null },
});
const emit = defineEmits(["updated", "back", "error"]);
const tab = ref("games"),
  q = ref(""),
  stage = ref(""),
  status = ref(""),
  page = ref(1),
  dialog = ref(null),
  form = ref({});
const ev = computed(() => props.detail.tournament);
const staff = computed(
  () =>
    !props.publicView &&
    props.user?.permissions.includes("manage") &&
    ["ALL", "DEPARTMENT"].includes(props.user.scope),
);
const canEnter = computed(
  () =>
    !props.publicView &&
    ev.value.status === "OPEN" &&
    (staff.value || props.user?.scope === "PARTICIPANT"),
);
const name = (id) =>
  props.detail.entries.find((e) => e.id === id)?.name || t("待晋级", "TBD");
const court = (id) => props.detail.courts.find((c) => c.id === id)?.name || "—";
const games = computed(() =>
  props.detail.games.filter(
    (g) =>
      (!stage.value || g.stage === stage.value) &&
      (!status.value || g.status === status.value) &&
      (!q.value ||
        [name(g.homeId), name(g.awayId), court(g.courtId), String(g.id)]
          .join(" ")
          .toLowerCase()
          .includes(q.value.toLowerCase())),
  ),
);
const visible = computed(() =>
  games.value.slice((page.value - 1) * 15, page.value * 15),
);
const rounds = computed(() =>
  [
    ...new Set(
      props.detail.games
        .filter((g) => g.stage === "KNOCKOUT")
        .map((g) => g.roundNo),
    ),
  ].sort((a, b) => a - b),
);
const canScore = (g) =>
  !props.publicView &&
  props.user?.permissions.includes("referee") &&
  props.user.id === g.refereeId;
/** 赛事命令带状态版本和独立幂等键；服务端成功后替换详情。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function command(action, extra = {}) {
  const d = await props.perform(
    `/events/${ev.value.id}/commands/${action}`,
    "POST",
    { version: ev.value.version, requestId: crypto.randomUUID(), ...extra },
  );
  if (d) emit("updated", d);
  return d;
}
async function confirmAction(action, extra = {}, question = "") {
  if (await ask(question || t("确认执行此操作？", "Confirm this action?")))
    await command(action, extra);
}
function localInput(v) {
  const d = new Date(v);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);
}
function open(kind, row) {
  dialog.value = kind;
  form.value =
    kind === "register"
      ? {
          name: "",
          note: "",
          accountId:
            props.options.accounts.find(
              (a) => a.participant && a.departmentId === ev.value.departmentId,
            )?.id || "",
          publicConsent: false,
        }
      : kind === "review"
        ? {
            entryId: row.id,
            entryVersion: row.version,
            seed: row.seed || 1,
            approved: true,
          }
        : kind === "schedule"
          ? {
              gameId: row.id,
              gameVersion: row.version,
              courtId:
                row.courtId ||
                props.options.courts.find(
                  (c) => c.departmentId === ev.value.departmentId,
                )?.id ||
                "",
              refereeId:
                row.refereeId ||
                props.options.accounts.find(
                  (a) => a.referee && a.departmentId === ev.value.departmentId,
                )?.id ||
                "",
              startsAt: localInput(row.startsAt || ev.value.startsAt),
              endsAt: localInput(
                row.endsAt ||
                  new Date(new Date(ev.value.startsAt).getTime() + 1800000),
              ),
              reason: "",
            }
          : kind === "cancel"
            ? { reason: "" }
            : {
                gameId: row.id,
                gameVersion: row.version,
                homeId: row.homeId,
                awayId: row.awayId,
                homeScore: row.homeScore,
                awayScore: row.awayScore,
                resultType: row.resultType || "PLAYED",
                winnerId: row.winnerId || row.homeId,
                reason: "",
              };
}
/** 先完成必填校验和时间转换，失败时保留表单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function save() {
  let d;
  const b = { ...form.value };
  if (dialog.value === "register")
    d = await props.perform(`/events/${ev.value.id}/entries`, "POST", b);
  else if (dialog.value === "schedule") {
    b.startsAt = new Date(b.startsAt).toISOString();
    b.endsAt = new Date(b.endsAt).toISOString();
    d = await command("SCHEDULE", b);
  } else
    d = await command(
      {
        review: "REVIEW",
        score: "RESULT",
        correct: "CORRECT",
        cancel: "CANCEL",
      }[dialog.value],
      b,
    );
  if (d) {
    dialog.value = null;
    emit("updated", d);
  }
}
async function withdraw(e) {
  if (await ask(t("撤回此报名？", "Withdraw this entry?"))) {
    const d = await props.perform(
      `/events/${ev.value.id}/entries/${e.id}/withdraw`,
      "POST",
      { version: e.version },
    );
    if (d) emit("updated", d);
  }
}
async function exportCsv() {
  try {
    await download(
      `/events/${ev.value.id}/report.csv`,
      `tournament-${ev.value.id}.csv`,
    );
  } catch (e) {
    emit("error", e);
  }
}
async function canLeave() {
  if (
    dialog.value &&
    !(await ask(t("关闭未保存的表单？", "Close the unsaved form?")))
  )
    return false;
  dialog.value = null;
  return true;
}
defineExpose({ canLeave });
</script>
<template>
  <section>
    <header class="page-heading">
      <div>
        <button class="link" @click="emit('back')">
          ← {{ t("返回赛事", "Back to events") }}
        </button>
        <h1>{{ ev.name }}</h1>
        <p class="muted">
          {{ dateTime(ev.startsAt) }} — {{ dateTime(ev.endsAt) }}
        </p>
      </div>
      <div class="actions">
        <span class="badge" :class="ev.status">{{ stateName(ev.status) }}</span
        ><button v-if="publicView" @click="tab = 'bracket'">
          {{ t("对阵图", "Bracket") }}</button
        ><button
          v-if="!publicView && user?.permissions.includes('export')"
          :disabled="pending"
          @click="exportCsv"
        >
          {{ t("导出赛程", "Export schedule") }}
        </button>
      </div>
    </header>
    <div class="event-summary">
      <span>{{
        ev.format === "GROUP_KO"
          ? t("小组循环＋淘汰", "Groups + knockout")
          : t("直接淘汰", "Single elimination")
      }}</span
      ><span>{{ t("胜局制", "Best of") }} {{ ev.bestOf }}</span
      ><span v-if="ev.format === 'GROUP_KO'"
        >{{ ev.groups }} {{ t("组，每组前", "groups, top") }} {{ ev.advance }}
        {{ t("晋级", "advance") }}</span
      ><span v-if="!publicView">{{
        ev.published ? t("已公开", "Published") : t("未公开", "Unpublished")
      }}</span>
    </div>
    <div v-if="ev.description" class="event-description">
      {{ ev.description }}
    </div>
    <div v-if="!publicView" class="order-actions">
      <button
        v-if="staff && ev.status === 'DRAFT'"
        class="primary"
        :disabled="pending"
        @click="
          confirmAction(
            'OPEN',
            {},
            t(
              '开放报名后不能修改赛制，确认开放？',
              'Opening locks the format. Open registration?',
            ),
          )
        "
      >
        {{ t("开放报名", "Open registration") }}</button
      ><button
        v-if="canEnter"
        class="primary"
        :disabled="pending"
        @click="open('register')"
      >
        {{ t("报名参赛", "Register entry") }}</button
      ><button
        v-if="staff && ev.status === 'OPEN'"
        :disabled="pending"
        @click="
          confirmAction(
            'DRAW',
            {},
            t(
              '仅审核通过且已签到者入签；分组与种子将冻结，确认生成？',
              'Only approved, checked-in entrants enter the draw. Freeze and generate?',
            ),
          )
        "
      >
        {{ t("关闭报名并生成对阵", "Close registration & draw") }}</button
      ><button
        v-if="staff && ev.status === 'GROUP'"
        :disabled="pending"
        @click="
          confirmAction(
            'ADVANCE',
            {},
            t(
              '确认小组成绩无误后生成淘汰赛？生成后不能更正小组结果。',
              'Confirm group results and generate knockout? Group corrections will be locked.',
            ),
          )
        "
      >
        {{ t("生成淘汰赛", "Generate knockout") }}</button
      ><button
        v-if="staff && ev.status === 'KNOCKOUT'"
        :disabled="pending"
        @click="
          confirmAction(
            'FINISH',
            {},
            t(
              '完成赛事后成绩冻结并计入赛季积分，确认？',
              'Finish, freeze results and add season points?',
            ),
          )
        "
      >
        {{ t("完成赛事", "Finish event") }}</button
      ><button
        v-if="staff && ev.status !== 'DRAFT'"
        :disabled="pending"
        @click="
          confirmAction(
            'PUBLISH',
            { published: !ev.published },
            ev.published
              ? t('取消公开赛程？', 'Unpublish this event?')
              : t(
                  '公开显示名、赛程与成绩？不会公开报名备注。',
                  'Publish display names, schedule and results? Private notes stay hidden.',
                ),
          )
        "
      >
        {{
          ev.published
            ? t("取消公开", "Unpublish")
            : t("发布公开赛程", "Publish schedule")
        }}</button
      ><a
        v-if="ev.published"
        :href="`#public/${ev.id}`"
        target="_blank"
        rel="noopener"
        >{{ t("查看公开页 ↗", "View public page ↗") }}</a
      ><button
        v-if="staff && !['FINISHED', 'CANCELLED'].includes(ev.status)"
        class="danger"
        :disabled="pending"
        @click="open('cancel')"
      >
        {{ t("取消赛事", "Cancel event") }}
      </button>
    </div>
    <nav class="tabs">
      <button
        v-for="x in [
          'games',
          'bracket',
          'standings',
          'entries',
          ...(staff ? ['history'] : []),
        ]"
        :key="x"
        :class="{ active: tab === x }"
        @click="tab = x"
      >
        {{
          {
            games: t("赛程与比分", "Schedule & scores"),
            bracket: t("淘汰对阵", "Knockout bracket"),
            standings: t("小组排名", "Group standings"),
            entries: t("报名名单", "Entries"),
            history: t("操作历史", "History"),
          }[x]
        }}
      </button>
    </nav>
    <div v-if="tab === 'games'" class="panel">
      <div class="toolbar">
        <label class="search-label"
          >{{ t("搜索选手、场地", "Search entrant or court")
          }}<input v-model="q" @input="page = 1" /></label
        ><label
          >{{ t("阶段", "Stage")
          }}<select v-model="stage" @change="page = 1">
            <option value="">{{ t("全部", "All") }}</option>
            <option value="GROUP">{{ t("小组赛", "Groups") }}</option>
            <option value="KNOCKOUT">{{ t("淘汰赛", "Knockout") }}</option>
          </select></label
        ><label
          >{{ t("状态", "Status")
          }}<select v-model="status" @change="page = 1">
            <option value="">{{ t("全部", "All") }}</option>
            <option
              v-for="s in [
                'WAITING',
                'SCHEDULED',
                'LIVE',
                'DONE',
                'BYE',
                'CANCELLED',
              ]"
              :key="s"
              :value="s"
            >
              {{ stateName(s) }}
            </option>
          </select></label
        ><span class="muted">{{ games.length }} {{ t("场", "matches") }}</span>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ t("场次／轮次", "Match / round") }}</th>
              <th>{{ t("比赛双方", "Entrants") }}</th>
              <th>{{ t("比分", "Sets") }}</th>
              <th>{{ t("时间／场地", "Time / court") }}</th>
              <th>{{ t("状态", "Status") }}</th>
              <th v-if="!publicView">{{ t("操作", "Actions") }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="g in visible" :key="g.id">
              <td>
                <b>#{{ g.id }}</b
                ><small
                  >{{
                    g.stage === "GROUP"
                      ? `${t("小组", "Group")} ${g.groupNo}`
                      : t("淘汰", "Knockout")
                  }}
                  · R{{ g.roundNo }}</small
                >
              </td>
              <td>
                <div :class="{ winner: g.winnerId === g.homeId && g.homeId }">
                  {{
                    g.homeId
                      ? name(g.homeId)
                      : g.sourceHomeId
                        ? `${t("场", "Match")} #${g.sourceHomeId} ${t("胜者", "winner")}`
                        : t("轮空", "Bye")
                  }}
                </div>
                <div class="versus">vs</div>
                <div :class="{ winner: g.winnerId === g.awayId && g.awayId }">
                  {{
                    g.awayId
                      ? name(g.awayId)
                      : g.sourceAwayId
                        ? `${t("场", "Match")} #${g.sourceAwayId} ${t("胜者", "winner")}`
                        : t("轮空", "Bye")
                  }}
                </div>
              </td>
              <td class="score">
                {{
                  g.status === "DONE" ? `${g.homeScore} : ${g.awayScore}` : "—"
                }}<small v-if="g.resultType === 'WALKOVER'">{{
                  t("弃权胜", "Walkover")
                }}</small>
              </td>
              <td>
                {{
                  g.startsAt
                    ? dateTime(g.startsAt)
                    : t("待排赛", "Unscheduled")
                }}<small
                  >{{ court(g.courtId)
                  }}<template v-if="!publicView && g.refereeId">
                    ·
                    {{
                      options.accounts.find((a) => a.id === g.refereeId)
                        ?.name || t("已指派裁判", "Assigned referee")
                    }}</template
                  ></small
                >
              </td>
              <td>
                <span class="badge" :class="g.status">{{
                  stateName(g.status)
                }}</span>
              </td>
              <td v-if="!publicView">
                <div class="actions wrap">
                  <button
                    v-if="
                      staff &&
                      ['WAITING', 'SCHEDULED'].includes(g.status) &&
                      g.homeId &&
                      g.awayId &&
                      ['GROUP', 'KNOCKOUT'].includes(ev.status)
                    "
                    :disabled="pending"
                    @click="open('schedule', g)"
                  >
                    {{ t("排赛", "Schedule") }}</button
                  ><button
                    v-if="
                      canScore(g) &&
                      g.status === 'SCHEDULED' &&
                      ['GROUP', 'KNOCKOUT'].includes(ev.status)
                    "
                    :disabled="pending"
                    @click="
                      confirmAction('START', {
                        gameId: g.id,
                        gameVersion: g.version,
                      })
                    "
                  >
                    {{ t("开赛", "Start") }}</button
                  ><button
                    v-if="canScore(g) && g.status === 'LIVE'"
                    class="primary"
                    :disabled="pending"
                    @click="open('score', g)"
                  >
                    {{ t("录入结果", "Enter result") }}</button
                  ><button
                    v-if="
                      staff &&
                      g.status === 'DONE' &&
                      ['GROUP', 'KNOCKOUT'].includes(ev.status)
                    "
                    :disabled="pending"
                    @click="open('correct', g)"
                  >
                    {{ t("更正", "Correct") }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!games.length" class="empty">
          {{ t("暂无符合条件的场次", "No matching matches") }}
        </div>
      </div>
      <footer class="pagination">
        <span>{{ page }} / {{ Math.max(1, Math.ceil(games.length / 15)) }}</span
        ><button :disabled="page === 1" @click="page--">
          {{ t("上一页", "Previous") }}</button
        ><button :disabled="page * 15 >= games.length" @click="page++">
          {{ t("下一页", "Next") }}
        </button>
      </footer>
    </div>
    <div v-else-if="tab === 'bracket'" class="panel">
      <div v-if="rounds.length" class="bracket-scroll">
        <div v-for="r in rounds" :key="r" class="bracket-round">
          <h2>
            {{
              r === rounds.at(-1)
                ? t("决赛", "Final")
                : t("第 " + r + " 轮", "Round " + r)
            }}
          </h2>
          <div
            v-for="g in detail.games.filter(
              (g) => g.stage === 'KNOCKOUT' && g.roundNo === r,
            )"
            :key="g.id"
            class="bracket-match"
          >
            <div class="bracket-caption">
              #{{ g.id }} · {{ stateName(g.status)
              }}<span v-if="g.resultType === 'WALKOVER'">
                · {{ t("弃权判胜", "Walkover") }}</span
              >
            </div>
            <div :class="{ winner: g.homeId && g.homeId === g.winnerId }">
              <span>{{
                g.homeId
                  ? name(g.homeId)
                  : g.sourceHomeId
                    ? `#${g.sourceHomeId} ${t("胜者", "winner")}`
                    : t("轮空", "Bye")
              }}</span
              ><b>{{ g.status === "DONE" ? g.homeScore : "—" }}</b>
            </div>
            <div :class="{ winner: g.awayId && g.awayId === g.winnerId }">
              <span>{{
                g.awayId
                  ? name(g.awayId)
                  : g.sourceAwayId
                    ? `#${g.sourceAwayId} ${t("胜者", "winner")}`
                    : t("轮空", "Bye")
              }}</span
              ><b>{{ g.status === "DONE" ? g.awayScore : "—" }}</b>
            </div>
            <small
              >{{
                g.status === "BYE"
                  ? t("自动晋级，无需排赛", "Automatic advance; no slot needed")
                  : g.startsAt
                    ? dateTime(g.startsAt)
                    : t("待排赛", "Unscheduled")
              }}
              · {{ court(g.courtId) }}</small
            >
          </div>
        </div>
      </div>
      <div v-else class="empty">
        {{ t("淘汰对阵尚未生成", "Knockout draw not generated") }}
      </div>
    </div>
    <div v-else-if="tab === 'standings'" class="panel">
      <p class="muted">
        {{
          t(
            "依次按胜场、净胜局、胜局和种子序排名；弃权仅计胜场。",
            "Ranked by wins, set difference, sets won, then seed. Walkovers count a win only.",
          )
        }}
      </p>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ t("组", "Group") }}</th>
              <th>{{ t("排名", "Rank") }}</th>
              <th>{{ t("参赛单位", "Entrant") }}</th>
              <th>{{ t("已赛", "Played") }}</th>
              <th>{{ t("胜场", "Wins") }}</th>
              <th>{{ t("胜局／负局", "Sets for / against") }}</th>
              <th>{{ t("净胜局", "Difference") }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in detail.standings" :key="r.entryId">
              <td>{{ r.groupNo }}</td>
              <td>{{ r.rank }}</td>
              <td>{{ r.name }}</td>
              <td>{{ r.played }}</td>
              <td>{{ r.wins }}</td>
              <td>{{ r.setsFor }} / {{ r.setsAgainst }}</td>
              <td>{{ r.difference }}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="!detail.standings.length" class="empty">
          {{ t("暂无小组排名", "No group standings") }}
        </div>
      </div>
    </div>
    <div v-else-if="tab === 'entries'" class="panel">
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ t("参赛显示名", "Public entrant name") }}</th>
              <th>{{ t("审核", "Review") }}</th>
              <th>{{ t("签到", "Check-in") }}</th>
              <th>{{ t("种子／组别", "Seed / group") }}</th>
              <th v-if="staff">{{ t("报名备注", "Private notes") }}</th>
              <th v-if="!publicView">{{ t("操作", "Actions") }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="e in detail.entries" :key="e.id">
              <td>
                <b>{{ e.name }}</b>
              </td>
              <td>
                <span class="badge" :class="e.status">{{
                  stateName(e.status)
                }}</span>
              </td>
              <td>
                {{
                  e.checkedIn
                    ? t("已签到", "Checked in")
                    : t("未签到", "Not checked in")
                }}
              </td>
              <td>{{ e.seed || "—" }} / {{ e.groupNo || "—" }}</td>
              <td v-if="staff">{{ e.note || "—" }}</td>
              <td v-if="!publicView">
                <div class="actions wrap">
                  <button
                    v-if="
                      staff &&
                      ev.status === 'OPEN' &&
                      ['PENDING', 'APPROVED', 'REJECTED'].includes(e.status)
                    "
                    :disabled="pending"
                    @click="open('review', e)"
                  >
                    {{ t("审核／种子", "Review / seed") }}</button
                  ><button
                    v-if="
                      staff && ev.status === 'OPEN' && e.status === 'APPROVED'
                    "
                    :disabled="pending"
                    @click="
                      command('CHECKIN', {
                        entryId: e.id,
                        entryVersion: e.version,
                        checkedIn: !e.checkedIn,
                      })
                    "
                  >
                    {{
                      e.checkedIn
                        ? t("撤销签到", "Undo check-in")
                        : t("签到", "Check in")
                    }}</button
                  ><button
                    v-if="
                      ev.status === 'OPEN' &&
                      ['PENDING', 'APPROVED'].includes(e.status) &&
                      (staff || e.accountId === user?.id)
                    "
                    :disabled="pending"
                    @click="withdraw(e)"
                  >
                    {{ t("撤回", "Withdraw") }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!detail.entries.length" class="empty">
          {{ t("暂无可见报名", "No visible entries") }}
        </div>
      </div>
    </div>
    <div v-else class="panel">
      <ul class="timeline">
        <li v-for="e in detail.history" :key="e.id">
          <time>{{ dateTime(e.createdAt) }}</time
          ><strong>{{ e.action }}</strong
          ><span>#{{ e.actorId }}</span>
          <p v-if="e.details">{{ e.details }}</p>
        </li>
      </ul>
    </div>
    <div v-if="dialog" class="modal-backdrop">
      <section
        class="modal"
        role="dialog"
        aria-modal="true"
        :aria-label="t('赛事操作', 'Tournament action')"
      >
        <header>
          <h2>
            {{
              {
                register: t("报名参赛", "Register entry"),
                review: t("审核与种子", "Review & seeding"),
                schedule: t("安排比赛", "Schedule match"),
                score: t("录入结果", "Enter result"),
                correct: t("更正结果", "Correct result"),
                cancel: t("取消赛事", "Cancel event"),
              }[dialog]
            }}
          </h2>
          <button :disabled="pending" @click="canLeave">×</button>
        </header>
        <p v-if="message" class="form-error" role="alert">{{ message }}</p>
        <form @submit.prevent="save">
          <template v-if="dialog === 'register'"
            ><label v-if="staff"
              >{{ t("报名负责人账号", "Entrant account")
              }}<select v-model="form.accountId" required :disabled="pending">
                <option value="">{{ t("选择账号", "Choose account") }}</option>
                <option
                  v-for="a in options.accounts.filter(
                    (a) => a.participant && a.departmentId === ev.departmentId,
                  )"
                  :key="a.id"
                  :value="a.id"
                >
                  {{ a.name }}
                </option>
              </select></label
            ><label
              >{{ t("参赛显示名", "Public entrant name")
              }}<input
                v-model="form.name"
                required
                maxlength="120"
                :disabled="pending" /></label
            ><label
              >{{ t("报名备注（不公开）", "Private registration notes")
              }}<textarea
                v-model="form.note"
                maxlength="600"
                :disabled="pending"
              /></label
            ><label class="check"
              ><input
                v-model="form.publicConsent"
                type="checkbox"
                required
                :disabled="pending"
              />{{
                t(
                  "同意显示名出现在公开赛程和成绩中",
                  "Allow this name in public schedules and results",
                )
              }}</label
            ></template
          ><template v-else-if="dialog === 'review'"
            ><label
              >{{ t("审核结果", "Decision")
              }}<select v-model="form.approved" :disabled="pending">
                <option :value="true">{{ t("通过", "Approve") }}</option>
                <option :value="false">{{ t("不通过", "Reject") }}</option>
              </select></label
            ><label
              >{{
                t(
                  "种子序号（入签人员不可重复）",
                  "Seed number (unique in draw)",
                )
              }}<input
                v-model.number="form.seed"
                type="number"
                min="1"
                max="10000"
                required
                :disabled="pending" /></label></template
          ><template v-else-if="dialog === 'schedule'"
            ><label
              >{{ t("场地", "Court")
              }}<select v-model="form.courtId" required :disabled="pending">
                <option value="">{{ t("选择场地", "Choose court") }}</option>
                <option
                  v-for="c in options.courts.filter(
                    (c) => c.departmentId === ev.departmentId,
                  )"
                  :key="c.id"
                  :value="c.id"
                >
                  {{ c.name }}
                </option>
              </select></label
            ><label
              >{{ t("裁判", "Referee")
              }}<select v-model="form.refereeId" required :disabled="pending">
                <option value="">{{ t("选择裁判", "Choose referee") }}</option>
                <option
                  v-for="a in options.accounts.filter(
                    (a) => a.referee && a.departmentId === ev.departmentId,
                  )"
                  :key="a.id"
                  :value="a.id"
                >
                  {{ a.name }}
                </option>
              </select></label
            >
            <div class="form-grid">
              <label
                >{{ t("开始时间", "Start")
                }}<input
                  v-model="form.startsAt"
                  type="datetime-local"
                  required
                  :disabled="pending" /></label
              ><label
                >{{ t("结束时间", "End")
                }}<input
                  v-model="form.endsAt"
                  type="datetime-local"
                  required
                  :disabled="pending"
              /></label>
            </div>
            <p class="hint">
              {{ t("时间按当前浏览器时区：", "Times use browser timezone: ")
              }}{{ Intl.DateTimeFormat().resolvedOptions().timeZone }}
            </p></template
          ><template v-else-if="['score', 'correct'].includes(dialog)"
            ><label
              >{{ t("结果类型", "Result type")
              }}<select v-model="form.resultType" :disabled="pending">
                <option value="PLAYED">{{ t("正常完赛", "Played") }}</option>
                <option value="WALKOVER">
                  {{ t("一方弃权", "Walkover") }}
                </option>
              </select></label
            >
            <div class="form-grid">
              <label
                >{{ name(form.homeId)
                }}<input
                  v-model.number="form.homeScore"
                  type="number"
                  min="0"
                  :max="Math.floor(ev.bestOf / 2) + 1"
                  required
                  :disabled="pending" /></label
              ><label
                >{{ name(form.awayId)
                }}<input
                  v-model.number="form.awayScore"
                  type="number"
                  min="0"
                  :max="Math.floor(ev.bestOf / 2) + 1"
                  required
                  :disabled="pending"
              /></label>
            </div>
            <label v-if="form.resultType === 'WALKOVER'"
              >{{
                t("弃权胜方（双方比分填0）", "Walkover winner (both scores 0)")
              }}<select v-model="form.winnerId" :disabled="pending">
                <option :value="form.homeId">{{ name(form.homeId) }}</option>
                <option :value="form.awayId">{{ name(form.awayId) }}</option>
              </select></label
            >
            <p class="hint">
              {{ t("胜方需取得", "Winner needs") }}
              {{ Math.floor(ev.bestOf / 2) + 1 }}
              {{ t("局；比分以胜局数记录。", "sets. Scores record sets won.") }}
            </p>
            <label
              >{{ t("说明／更正理由", "Note / correction reason")
              }}<textarea
                v-model="form.reason"
                maxlength="600"
                :required="dialog === 'correct'"
                :disabled="pending"
              /></label></template
          ><label v-else
            >{{ t("取消理由", "Cancellation reason")
            }}<textarea
              v-model="form.reason"
              required
              maxlength="600"
              :disabled="pending"
            />
          </label>
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
  </section>
</template>
