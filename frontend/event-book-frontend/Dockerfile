FROM node:22

WORKDIR /app

COPY . .

RUN npm install

ENV NG_ALLOWED_HOSTS=localhost,127.0.0.1,0.0.0.0

EXPOSE 4200

CMD ["npm", "start", "--", "--host", "0.0.0.0", "--port", "4200"]