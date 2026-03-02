FROM node:24-alpine
WORKDIR /app

# Copy manifests first — this layer is cached until deps change
COPY package.json package-lock.json ./
COPY prisma ./prisma/

# Install all deps (including devDependencies) and generate Prisma client
RUN npm ci && npx prisma generate

# Source code is provided via bind mounts (see docker-compose.yml)
# node_modules is preserved via an anonymous volume

EXPOSE 8080
CMD ["npm", "run", "dev"]
