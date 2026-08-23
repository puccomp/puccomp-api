# --- Estágio 1: Build (Compilação do código) ---
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app

# Copia os arquivos do Gradle primeiro (melhora o cache do Docker)
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# Dá permissão e baixa as dependências
RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon || true

# Copia o código fonte da API e compila
COPY src src
RUN ./gradlew bootJar --no-daemon

# --- Estágio 2: Runtime (Imagem enxuta para Produção) ---
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Pega o .jar compilado do Estágio 1 e coloca na imagem final
COPY --from=builder /app/build/libs/*.jar app.jar

# Informa a porta e roda a aplicação
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]