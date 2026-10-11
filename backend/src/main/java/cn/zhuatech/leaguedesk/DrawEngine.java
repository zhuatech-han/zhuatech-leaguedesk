// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leaguedesk;

import java.util.*;

/** 独立、确定性的蛇形分组、循环对阵与淘汰种子位置。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class DrawEngine {
  /** 蛇形分配种子，组人数差最多1。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static int group(int index, int count) {
    Rules.check(index >= 0 && count > 0, "DRAW_INVALID");
    int block = index / count;
    return block % 2 == 0 ? index % count + 1 : count - index % count;
  }

  /** 圆桌轮转生成每对一次、奇数人数自动轮空的循环赛。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static List<Pair> roundRobin(List<Long> ids) {
    var slots = new ArrayList<>(ids);
    if (slots.size() % 2 == 1) slots.add(null);
    var result = new ArrayList<Pair>();
    for (int r = 1; r < slots.size(); r++) {
      for (int i = 0; i < slots.size() / 2; i++) {
        Long a = slots.get(i), b = slots.get(slots.size() - 1 - i);
        if (a != null && b != null) result.add(new Pair(r, a, b));
      }
      slots.add(1, slots.removeLast());
    }
    return result;
  }

  /** 种子位置保证头两号种子分别位于上下半区。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static List<Integer> seeds(int players) {
    Rules.check(players >= 2 && players <= 64, "DRAW_INVALID");
    int size = 2;
    while (size < players) size *= 2;
    var seed = new ArrayList<>(List.of(1, 2));
    for (int n = 4; n <= size; n *= 2) {
      var next = new ArrayList<Integer>();
      for (int s : seed) {
        next.add(s);
        next.add(n + 1 - s);
      }
      seed = next;
    }
    return seed;
  }

  /** 赛程采用半开区间，相邻比赛不算重叠。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static boolean overlap(
      java.time.Instant a, java.time.Instant b, java.time.Instant c, java.time.Instant d) {
    return a.isBefore(d) && c.isBefore(b);
  }

  /** 一场循环对阵。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Pair(int round, Long home, Long away) {}

  private DrawEngine() {}
}
