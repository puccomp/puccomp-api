# STEP 1: install deps + generate prisma client
FROM node:24-alpine AS d
WORKDIR /app

COPY package.json package-lock.json ./
COPY prisma ./prisma/

RUN npm cache clean --force && npm ci

RUN npx prisma generate

# STEP 2: dev image
FROM node:24-alpine
WORKDIR /app

COPY --from=d /app/node_modules ./node_modules
COPY --from=d /app/prisma ./prisma

COPY . .

EXPOSE 8080
CMD ["npm", "run", "dev"]