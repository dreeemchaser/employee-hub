# Build stage
FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json ./
RUN npm install
COPY . .
ARG REACT_APP_API_URL=http://localhost:8080
ENV REACT_APP_API_URL=$REACT_APP_API_URL
# Skip the CRA ESLint plugin during the production build. The `react-app/jest`
# ESLint config trips an "Environment key jest/globals is unknown" error under
# the Node 20 toolchain here; linting still runs in `npm test` (CI test job).
ENV DISABLE_ESLINT_PLUGIN=true
RUN npm run build

# Serve stage
FROM nginx:alpine
COPY --from=builder /app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3001

HEALTHCHECK --interval=10s --timeout=5s --retries=5 \
    CMD wget --quiet --tries=1 --spider http://localhost:3001/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
