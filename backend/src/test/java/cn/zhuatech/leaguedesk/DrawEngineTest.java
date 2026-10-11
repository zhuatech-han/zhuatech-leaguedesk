// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.leaguedesk;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 验证奇偶参赛数量、不重复对阵、公平轮空、种子半区和时间边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class DrawEngineTest {
  @Test
  void everyPairOnceForOddAndEvenCounts() {
    for (int n = 2; n <= 16; n++) {
      var ids = new ArrayList<Long>();
      for (long i = 1; i <= n; i++) ids.add(i);
      var games = DrawEngine.roundRobin(ids);
      assertEquals(n * (n - 1) / 2, games.size());
      var pairs = new HashSet<String>();
      var rounds = new HashMap<Integer, Set<Long>>();
      for (var g : games) {
        assertNotEquals(g.home(), g.away());
        assertTrue(pairs.add(Math.min(g.home(), g.away()) + ":" + Math.max(g.home(), g.away())));
        var used = rounds.computeIfAbsent(g.round(), x -> new HashSet<>());
        assertTrue(used.add(g.home()));
        assertTrue(used.add(g.away()));
      }
    }
  }

  @Test
  void knockoutSeedsKeepTopTwoInOppositeHalves() {
    for (int n = 2; n <= 64; n++) {
      var s = DrawEngine.seeds(n);
      assertEquals(s.size(), new HashSet<>(s).size());
      assertEquals(1, s.getFirst());
      assertTrue(s.indexOf(2) >= s.size() / 2);
      for (int i = 0; i < s.size(); i += 2) assertEquals(s.size() + 1, s.get(i) + s.get(i + 1));
    }
  }

  @Test
  void snakeGroupsBalanced() {
    assertEquals(
        List.of(1, 2, 3, 3, 2, 1, 1, 2),
        java.util.stream.IntStream.range(0, 8).map(i -> DrawEngine.group(i, 3)).boxed().toList());
  }

  @Test
  void adjacentSlotsDoNotConflict() {
    var a = Instant.parse("2026-10-11T10:00:00Z");
    var b = a.plusSeconds(60);
    var c = b.plusSeconds(60);
    assertFalse(DrawEngine.overlap(a, b, b, c));
    assertTrue(DrawEngine.overlap(a, c, b, c));
  }

  @Test
  void badDrawSizeRejected() {
    assertThrows(Problem.class, () -> DrawEngine.seeds(1));
    assertThrows(Problem.class, () -> DrawEngine.seeds(65));
  }

  @Test
  void csvDoesNotExecuteFormula() {
    assertEquals("\"' =SUM(A1)\"", Rules.csv(" =SUM(A1)"));
    assertEquals("\"a\"\"b\"", Rules.csv("a\"b"));
  }
}
