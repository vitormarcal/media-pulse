FROM node:22-alpine AS build

WORKDIR /workspace/frontend

ARG NUXT_PUBLIC_API_BASE=/api
ENV NUXT_PUBLIC_API_BASE=$NUXT_PUBLIC_API_BASE

RUN npm install -g npm@11.6.2

COPY ./frontend/package.json ./package.json
COPY ./frontend/package-lock.json ./package-lock.json
RUN npm ci

COPY ./frontend ./
RUN npm run build

FROM nginx:1.29-alpine

COPY ./frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/frontend/.output/public /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
