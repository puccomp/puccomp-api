# SETP 1: install dependencies and generate prisma client
FROM node:22-alpine AS d
WORKDIR /app

COPY package*.json .

COPY prisma ./prisma/

RUN npm install

# read schema.prisma, generate ts code based on models and generate prisma client
RUN npx prisma generate

# STEP 2: generate dev image
FROM node:22-alpine
WORKDIR /app

COPY --from=d /app/node_modules ./node_modules
COPY --from=d /app/prisma ./prisma

COPY . .

EXPOSE 8080

# run with nodemon/hot-reload
CMD [ "npm", "run", "dev" ]