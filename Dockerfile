# ==========================================
# STAGE 1: Build (Compilazione)
# Usiamo un'immagine che contiene Maven e Java 17.
# Questo ci evita di doverli installare sul Mac.
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build

# Modulo da compilare, passato da docker-compose per ogni servizio
ARG MODULE_NAME

# Copiamo tutto il monorepo nel container
COPY . .

# Compiliamo solo il modulo richiesto per evitare errori su moduli non presenti.
RUN mvn -f ${MODULE_NAME}/pom.xml clean package -DskipTests

# ==========================================
# STAGE 2: Runtime (Esecuzione)
# Usiamo un'immagine JRE multi-arch (senza Maven)
# per garantire compatibilita su Apple Silicon e AMD64.
# ==========================================
FROM eclipse-temurin:17-jre
WORKDIR /app

# Questo parametro verrà passato dal docker-compose per dirci quale .jar estrarre
ARG MODULE_NAME

# Copiamo SOLO il .jar del microservizio specifico dallo stage di build
COPY --from=builder /build/${MODULE_NAME}/target/*.jar app.jar

# Eseguiamo il microservizio
ENTRYPOINT ["java", "-jar", "app.jar"]