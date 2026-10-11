// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { ref } from "vue";
export const language = ref("zh");
/** 双语显示，业务名称保持原文。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function t(zh, en) {
  return language.value === "zh" ? zh : en;
}
export const states = {
  DRAFT: ["草稿", "Draft"],
  OPEN: ["报名中", "Registration open"],
  GROUP: ["小组赛", "Group stage"],
  KNOCKOUT: ["淘汰赛", "Knockout"],
  FINISHED: ["已完成", "Finished"],
  CANCELLED: ["已取消", "Cancelled"],
  PENDING: ["待审核", "Pending"],
  APPROVED: ["已通过", "Approved"],
  REJECTED: ["未通过", "Rejected"],
  WITHDRAWN: ["已撤回", "Withdrawn"],
  WAITING: ["待排赛", "Unscheduled"],
  SCHEDULED: ["已排赛", "Scheduled"],
  LIVE: ["进行中", "In progress"],
  DONE: ["已结束", "Completed"],
  BYE: ["轮空晋级", "Bye"],
};
/** 显示服务端真实状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function stateName(s) {
  return states[s] ? t(...states[s]) : s;
}
const errors = {
  UNAUTHENTICATED: [
    "登录已失效，请重新登录。",
    "Session expired. Sign in again.",
  ],
  LOGIN_FAILED: [
    "账号、密码错误或账号停用。",
    "Invalid credentials or disabled account.",
  ],
  LOGIN_THROTTLED: [
    "尝试过多，请五分钟后重试。",
    "Too many attempts. Retry in five minutes.",
  ],
  FORBIDDEN: ["没有操作权限。", "Permission denied."],
  OUT_OF_SCOPE: [
    "不在当前账号的赛事范围内。",
    "Event outside your account scope.",
  ],
  VERSION_CONFLICT: [
    "记录已更新，请刷新核对后再操作。",
    "Record changed. Refresh and verify.",
  ],
  STATE_INVALID: [
    "当前状态不能执行此操作。",
    "Action not allowed in this state.",
  ],
  NOT_FOUND: ["记录不存在或已取消公开。", "Record unavailable or unpublished."],
  INVALID_INPUT: [
    "请核对必填字段与格式。",
    "Check required fields and formats.",
  ],
  CONFLICT: [
    "名称重复或记录仍被引用。",
    "Duplicate name or referenced record.",
  ],
  PASSWORD_WEAK: [
    "密码需12–72字节，含大写、小写字母及数字。",
    "Password: 12–72 bytes, upper/lowercase and a digit.",
  ],
  LAST_ADMIN: [
    "必须保留至少一个启用的完整管理员。",
    "Keep at least one active full administrator.",
  ],
  PARTICIPANT_ROLE_INVALID: [
    "参赛角色只能有赛事查阅和本人报名权限。",
    "Participant roles need only event and registration permissions.",
  ],
  PARTICIPANT_INVALID: [
    "请选择同组织启用的参赛负责人。",
    "Choose an enabled participant in this organization.",
  ],
  REGISTRATION_CLOSED: ["报名已关闭。", "Registration closed."],
  EVENT_FULL: ["报名名额已满。", "Registration capacity reached."],
  DUPLICATE_ENTRY: [
    "此账号已报名本赛事。",
    "Account already entered this event.",
  ],
  PUBLIC_NAME_CONSENT: [
    "请确认参赛显示名可以公开。",
    "Confirm the public display name consent.",
  ],
  DRAW_INVALID: [
    "至少两名已审核并签到人员；晋级人数至少两名。",
    "Need two checked-in entries and at least two qualifiers.",
  ],
  GROUP_SIZE_INVALID: [
    "各组至少两人，且不少于晋级人数，每组最多16人。",
    "Each group needs 2–16 entries, at least the qualifier count.",
  ],
  SEED_DUPLICATE: [
    "参赛种子序号重复，请调整。",
    "Seed numbers must be unique.",
  ],
  GROUP_INCOMPLETE: [
    "小组赛仍有未结束场次。",
    "Complete all group matches first.",
  ],
  MATCHES_INCOMPLETE: [
    "淘汰赛尚未全部结束。",
    "Complete all knockout matches first.",
  ],
  SCORE_INVALID: [
    "胜方须达到所需胜局数，双方不能同时达标；弃权比分为0:0。",
    "Winner must reach the target, loser below target. Walkover scores are 0:0.",
  ],
  REFEREE_ONLY: [
    "只能操作指派给本账号的比赛。",
    "Only the assigned referee can start or score.",
  ],
  SCHEDULE_CONFLICT: [
    "场地、裁判或参赛负责人时间冲突。",
    "Court, referee or entrant has a schedule conflict.",
  ],
  REFEREE_CONFLICT: [
    "裁判不能同时是本场参赛负责人。",
    "Referee cannot be an entrant in this match.",
  ],
  REFEREE_INVALID: [
    "裁判须为同组织启用、具有裁判权限的工作人员。",
    "Referee must be active staff in this organization.",
  ],
  COURT_INVALID: [
    "场地已停用或不属于本组织。",
    "Court disabled or outside this organization.",
  ],
  COURT_IN_USE: [
    "该场地仍有待赛或进行中比赛，不能停用。",
    "Court has scheduled or live matches.",
  ],
  TIME_INVALID: [
    "比赛须在赛事时段内，结束晚于开始，单场不超过4小时。",
    "Match must fit event dates, end after start, and last at most 4 hours.",
  ],
  MATCH_TOO_EARLY: [
    "开赛不能早于计划时间前30分钟。",
    "Start at most 30 minutes before the scheduled time.",
  ],
  MATCH_NOT_READY: [
    "双方尚未确定或比赛状态不可排赛。",
    "Both entrants must be known before scheduling.",
  ],
  DOWNSTREAM_LOCKED: [
    "下游阶段已生成或已有比赛开赛，不能更正此结果。",
    "Downstream stage generated or matches started; correction blocked.",
  ],
  MATCH_IN_PROGRESS: [
    "仍有进行中比赛，不能取消赛事。",
    "Finish live matches before cancellation.",
  ],
  REQUEST_REUSED: [
    "请求编号已用于另一项操作，请刷新。",
    "Request ID already used for different input.",
  ],
  REQUEST_ID_INVALID: [
    "请求编号无效，请刷新重试。",
    "Invalid request ID. Refresh first.",
  ],
  ENTRY_INVALID: [
    "报名不属于当前赛事。",
    "Entry belongs to another tournament.",
  ],
  MATCH_INVALID: [
    "比赛不属于当前赛事。",
    "Match belongs to another tournament.",
  ],
  RESOURCE_LIMIT: ["达到数据上限。", "Data limit reached."],
  IDENTITY_LOCKED: [
    "组织或身份字段不能修改。",
    "Organization or identity field is immutable.",
  ],
  DEPARTMENT_DISABLED: ["组织已停用。", "Organization disabled."],
  SEASON_DISABLED: ["赛季已停用。", "Season disabled."],
  CATEGORY_INVALID: ["请选择启用的赛事分类。", "Choose an active category."],
  SYSTEM_DIRECTORY_FIXED: [
    "系统目录只允许修改现有记录。",
    "Edit existing system directories only.",
  ],
  RESULT_UNKNOWN: [
    "网络中断，结果未知。请刷新核对，不要直接重复提交。",
    "Connection lost; result unknown. Refresh and verify before submitting again.",
  ],
  NETWORK_ERROR: [
    "无法连接服务器，请检查网络。",
    "Server unavailable. Check the connection.",
  ],
};
/** 明确业务错误，不显示服务器配置。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function errorMessage(e) {
  return errors[e.message]
    ? t(...errors[e.message])
    : t("操作未完成", "Action failed") + " (" + e.message + ")";
}
export const confirmation = ref(null);
/** 页内明确确认，不自动重放未知结果操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function ask(message) {
  if (confirmation.value) return Promise.resolve(false);
  return new Promise((resolve) => (confirmation.value = { message, resolve }));
}
/** 结束当前确认。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function answerConfirmation(ok) {
  const p = confirmation.value;
  confirmation.value = null;
  p?.resolve(ok);
}
/** 编辑副本与列表分离。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function cloneRecord(r) {
  return JSON.parse(JSON.stringify(r));
}
/** 时间按浏览器时区展示，服务端统一UTC。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function dateTime(v) {
  return v
    ? new Intl.DateTimeFormat(language.value === "zh" ? "zh-CN" : "en", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(v))
    : "—";
}
