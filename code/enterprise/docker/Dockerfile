# 多阶段构建：构建指定模块 → 运行镜像
# 用法（由 docker-compose 传入 MODULE）：docker build --build-arg MODULE=examforge-user ..
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
ARG MODULE
COPY pom.xml .
# 全部模块 pom 先进层（父 pom 校验 modules 必须齐全）+ 依赖预热（层缓存跨镜像共享）
COPY examforge-common/pom.xml examforge-common/
COPY examforge-api/pom.xml examforge-api/
COPY examforge-gateway/pom.xml examforge-gateway/
COPY examforge-user/pom.xml examforge-user/
COPY examforge-question/pom.xml examforge-question/
COPY examforge-paper/pom.xml examforge-paper/
COPY examforge-admin/pom.xml examforge-admin/
COPY examforge-trade/pom.xml examforge-trade/
COPY examforge-practice/pom.xml examforge-practice/
COPY examforge-ai/pom.xml examforge-ai/
COPY examforge-resource/pom.xml examforge-resource/
COPY examforge-school/pom.xml examforge-school/
COPY docker/maven-settings.xml /root/.m2/settings.xml
# question 模块依赖 shardingsphere-test-util（Central 无此包，G2 已在其 pom 排除）——go-offline 跳过它，由其 package 层现场解析
RUN mvn -q dependency:go-offline -pl '!examforge-question'
COPY examforge-common examforge-common
COPY examforge-api examforge-api
COPY examforge-gateway examforge-gateway
COPY examforge-user examforge-user
COPY examforge-question examforge-question
COPY examforge-paper examforge-paper
COPY examforge-admin examforge-admin
COPY examforge-trade examforge-trade
COPY examforge-practice examforge-practice
COPY examforge-ai examforge-ai
COPY examforge-resource examforge-resource
COPY examforge-school examforge-school
RUN mvn -q -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
ARG MODULE
COPY --from=build /src/${MODULE}/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
