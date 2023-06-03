const { createProxyMiddleware } = require('http-proxy-middleware');

module.exports = (req, res) => {
  // 添加跨域响应头
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  
  // 创建代理对象并转发请求
  createProxyMiddleware({
    target: 'http://114.132.162.245:8080',
    changeOrigin: true,
    pathRewrite: {
      '^/api/': '/', // 通过路径重写，去除请求路径中的 `/api`
    },
  })(req, res);
};
