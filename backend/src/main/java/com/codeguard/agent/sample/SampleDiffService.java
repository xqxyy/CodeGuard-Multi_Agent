package com.codeguard.agent.sample;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 内置样例服务
 *
 * 这个服务负责保存和查询项目内置的 Git diff 样例
 * 当前先把样例直接写在 Java 代码里，后面可以改成读取 JSON 文件或数据库
 */
@Service
public class SampleDiffService {

    /**
     * 样例列表
     */
    private final List<SampleDiff> samples = new ArrayList<>();

    /**
     * 按样例 id 建立索引，方便快速查询
     */
    private Map<String, SampleDiff> samplesById = Map.of();

    /**
     * Spring 创建 Bean 后执行初始化
     *
     * 这里统一加载内置样例
     */
    @PostConstruct
    public void init() {
        samples.add(returnNullSample());
        samples.add(sqlInjectionSample());
        samples.add(controllerRepositorySample());
        samples.add(hardcodedSecretSample());
        samples.add(authRemovalSample());
        samples.add(unsafeUploadSample());
        samples.add(swallowedExceptionSample());

        samplesById = samples.stream()
                .collect(Collectors.toMap(SampleDiff::id, Function.identity()));
    }

    /**
     * 查询所有样例
     */
    public List<SampleDiff> list() {
        return List.copyOf(samples);
    }

    /**
     * 根据 id 查询单个样例
     */
    public SampleDiff get(String id) {
        SampleDiff sample = samplesById.get(id);

        if (sample == null) {
            throw new IllegalArgumentException("Sample diff not found: " + id);
        }

        return sample;
    }

    /**
     * return null 缺陷样例
     */
    private SampleDiff returnNullSample() {
        return new SampleDiff(
                "return-null",
                "新增 return null 风险",
                "bug",
                "模拟 Service 新增 return null，触发 BugLogicAgent",
                List.of("BUG", "TEST_GAP"),
                """
                diff --git a/src/main/java/com/example/UserService.java b/src/main/java/com/example/UserService.java
                --- a/src/main/java/com/example/UserService.java
                +++ b/src/main/java/com/example/UserService.java
                @@ -1,6 +1,6 @@
                 public class UserService {
                   public User findUser(Long id) {
                -    return userRepository.findById(id).orElseThrow();
                +    return null;
                   }
                 }
                """
        );
    }

    /**
     * SQL 注入安全样例
     */
    private SampleDiff sqlInjectionSample() {
        return new SampleDiff(
                "sql-injection",
                "SQL 字符串拼接风险",
                "security",
                "模拟新增 SQL 拼接代码，触发 SecurityAgent",
                List.of("SECURITY", "TEST_GAP"),
                """
                diff --git a/src/main/java/com/example/UserRepository.java b/src/main/java/com/example/UserRepository.java
                --- a/src/main/java/com/example/UserRepository.java
                +++ b/src/main/java/com/example/UserRepository.java
                @@ -1,6 +1,7 @@
                 public class UserRepository {
                   public List<User> search(String keyword) {
                +    String sql = "select * from users where name = '" + keyword + "'";
                     return jdbcTemplate.query(sql, userMapper);
                   }
                 }
                """
        );
    }

    /**
     * Controller 直接访问 Repository 的质量样例
     */
    private SampleDiff controllerRepositorySample() {
        return new SampleDiff(
                "controller-repository",
                "Controller 直接访问持久层",
                "quality",
                "模拟 Controller 里直接调用 Repository，触发 CodeQualityAgent",
                List.of("QUALITY", "TEST_GAP"),
                """
                diff --git a/src/main/java/com/example/UserController.java b/src/main/java/com/example/UserController.java
                --- a/src/main/java/com/example/UserController.java
                +++ b/src/main/java/com/example/UserController.java
                @@ -1,7 +1,8 @@
                 @RestController
                 public class UserController {
                +  private UserRepository userRepository;
                   public User getUser(Long id) {
                +    return userRepository.findById(id).get();
                   }
                 }
                """
        );
    }

    /**
     * 硬编码密钥安全样例
     */
    private SampleDiff hardcodedSecretSample() {
        return new SampleDiff(
                "hardcoded-secret",
                "配置中出现硬编码密钥",
                "security",
                "模拟把 Token 直接写进配置文件，触发 SecurityAgent",
                List.of("SECURITY"),
                """
                diff --git a/src/main/resources/application.yml b/src/main/resources/application.yml
                --- a/src/main/resources/application.yml
                +++ b/src/main/resources/application.yml
                @@ -1,5 +1,6 @@
                 payment:
                   endpoint: https://pay.example.com
                +  api-key: sk_live_abc123456
                 logging:
                   level: INFO
                """
        );
    }

    /**
     * 权限注解删除安全样例
     */
    private SampleDiff authRemovalSample() {
        return new SampleDiff(
                "auth-removal",
                "接口权限注解被删除",
                "security",
                "模拟删除 @PreAuthorize，触发 SecurityAgent 和 TestCoverageAgent",
                List.of("SECURITY", "TEST_GAP"),
                """
                diff --git a/src/main/java/com/example/AdminController.java b/src/main/java/com/example/AdminController.java
                --- a/src/main/java/com/example/AdminController.java
                +++ b/src/main/java/com/example/AdminController.java
                @@ -1,8 +1,7 @@
                 @RestController
                 public class AdminController {
                -  @PreAuthorize("hasRole('ADMIN')")
                   public void deleteUser(Long id) {
                     userService.deleteUser(id);
                   }
                 }
                """
        );
    }

    /**
     * 文件上传缺少校验样例
     */
    private SampleDiff unsafeUploadSample() {
        return new SampleDiff(
                "unsafe-upload",
                "文件上传缺少校验",
                "security",
                "模拟上传文件直接保存，触发安全和测试覆盖建议",
                List.of("SECURITY", "TEST_GAP"),
                """
                diff --git a/src/main/java/com/example/FileController.java b/src/main/java/com/example/FileController.java
                --- a/src/main/java/com/example/FileController.java
                +++ b/src/main/java/com/example/FileController.java
                @@ -1,7 +1,8 @@
                 @RestController
                 public class FileController {
                   public void upload(MultipartFile file) throws IOException {
                +    file.transferTo(new File("/tmp/" + file.getOriginalFilename()));
                   }
                 }
                """
        );
    }

    /**
     * 宽泛异常处理样例
     */
    private SampleDiff swallowedExceptionSample() {
        return new SampleDiff(
                "swallowed-exception",
                "捕获宽泛异常后继续返回成功",
                "bug",
                "模拟 catch Exception 后吞掉错误，触发 BugLogicAgent",
                List.of("BUG", "TEST_GAP"),
                """
                diff --git a/src/main/java/com/example/OrderService.java b/src/main/java/com/example/OrderService.java
                --- a/src/main/java/com/example/OrderService.java
                +++ b/src/main/java/com/example/OrderService.java
                @@ -1,8 +1,12 @@
                 public class OrderService {
                   public boolean submit(Order order) {
                +    try {
                +      orderRepository.save(order);
                +    } catch (Exception exception) {
                +      exception.printStackTrace();
                +    }
                     return true;
                   }
                 }
                """
        );
    }
}
