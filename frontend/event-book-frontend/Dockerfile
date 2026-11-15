# Estágio 1: Dependências
FROM node:24.18.0-bookworm-slim AS deps

WORKDIR /app

COPY package.json package-lock.json ./

RUN --mount=type=cache,target=/root/.npm \
    npm ci


# Estágio 2: Build de produção
FROM node:24.18.0-bookworm-slim AS build

WORKDIR /app

COPY --from=deps --chown=node:node /app/node_modules ./node_modules

COPY --chown=node:node . .

USER node

RUN npm run build -- --configuration production


# Estágio 3: Desenvolvimento
FROM node:24.18.0-bookworm-slim AS development

WORKDIR /app

COPY --from=deps --chown=node:node /app/node_modules ./node_modules

COPY --chown=node:node . .

RUN install -d -o node -g node /app/.angular

ENV NG_ALLOWED_HOSTS=localhost,127.0.0.1,0.0.0.0

USER node

EXPOSE 4200

CMD ["npm", "start", "--", "--host", "0.0.0.0", "--port", "4200", "--poll", "2000"]
