# 这个镜像只负责运行已经打包好的 Spring Boot jar。
# 打包动作放在 scripts/start-codeguard.ps1 里执行，这样本地学习时启动更快，也更容易看到 Maven 错误。
FROM eclipse-temurin:21-jre

WORKDIR /app

ENV JAVA_OPTS=""

COPY target/codeguard-agent-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
