FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/templates/default.conf.template
ENV ATLAS_NETWORK_GATEWAY=172.30.80.1 NGINX_ENVSUBST_FILTER=^ATLAS_
COPY --from=build /app/dist /usr/share/nginx/html
# A private installer checkout can give copied public files mode 0640.
# Nginx workers must be able to read every deliberately published static asset.
RUN chmod -R a+rX /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
