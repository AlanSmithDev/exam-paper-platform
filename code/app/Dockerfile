FROM node:18-alpine
WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev --registry=https://registry.npmmirror.com
COPY server ./server
COPY admin ./admin
COPY tests ./tests
ENV PORT=3000
EXPOSE 3000
CMD ["node", "server/server.js"]
