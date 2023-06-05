FROM node:lts-alpine
# ENV NODE_ENV=production
WORKDIR /mydata/frontend-app/blog-adm
COPY ["package.json", "package-lock.json*", "npm-shrinkwrap.json*", "./"]
RUN npm install --production --silent && mv node_modules ../
COPY . .
EXPOSE 3000
RUN npm install -g @vue/cli-service
RUN npm install babel-plugin-import -D
RUN chown -R node /mydata/frontend-app/blog-adm
USER node
CMD ["npm", "run", "serve"]