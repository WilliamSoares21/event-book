# Estágio 1: Dependências

FROM node:22-bookworm-slim AS deps

WORKDIR /app

COPY package.json package-lock.json ./

RUN --mount=type=cache,target=/root/.npm \
  npm ci


# Estágio 2: Desenvolvimento

FROM node:22-bookworm-slim AS development

WORKDIR /app

COPY --from=deps --chown=node:node /app/node_modules ./node_modules

COPY --chown=node:node . .

# Prepara o diretório de cache do Angular
RUN install -d -o node -g node /app/.angular

ENV NG_ALLOWED_HOSTS=localhost,127.0.0.1,0.0.0.0

USER node

EXPOSE 4200

CMD ["npm", "start", "--", "--host", "0.0.0.0", "--port", "4200", "--poll", "2000"]

# Estágio 3: Build de produção

FROM node:22-bookworm-slim AS builder

WORKDIR /app

COPY --from=deps --chown=node:node /app/node_modules ./node_modules

COPY --chown=node:node . .

USER node

RUN npm run build -- --configuration production



# Estágio 4: Produção estática

FROM nginxinc/nginx-unprivileged:stable-alpine AS production

COPY --from=builder --chown=nginx:nginx \
  /app/dist/event-book-frontend/browser/ \
  /usr/share/nginx/html/

EXPOSE 8080

USER nginx

CMD ["nginx", "-g", "daemon off;"]
