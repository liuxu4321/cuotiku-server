# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai PORT=80 SPRING_PROFILES_ACTIVE=prod
COPY --from=build /build/target/yycuotiku-server.jar app.jar
RUN mkdir -p /app/data
VOLUME ["/app/data"]
EXPOSE 80
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
