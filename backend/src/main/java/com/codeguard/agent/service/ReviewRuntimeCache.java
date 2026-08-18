package com.codeguard.agent.service;

import com.codeguard.agent.domain.MergeRecommendation;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Review 运行态缓存
 *
 * Redis 只保存最近 Review 的轻量摘要，数据库仍然是完整 Review 数据的主存储
 */
@Component
public class ReviewRuntimeCache {

  /** Redis 字符串操作模板 */
  private final StringRedisTemplate redisTemplate;

  /**
   * 构造方法
   */
  public ReviewRuntimeCache(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * 缓存 Review 的合并建议和风险分
   *
   * Redis 不可用时直接跳过，不能因为缓存失败影响主审查链路
   */
  public void cacheReviewResult(
      UUID reviewId, MergeRecommendation recommendation, int riskScore) {
    try {
      String key = "codeguard:review:" + reviewId + ":summary";
      String value = recommendation + "|" + riskScore;
      redisTemplate.opsForValue().set(key, value, Duration.ofHours(6));
    } catch (RuntimeException ignored) {
      // 缓存是可选能力，失败时不影响数据库保存和接口返回
    }
  }
}
