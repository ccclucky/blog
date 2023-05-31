// api/proxy.js
// 该服务为 vercel serve跨域解决
const { createProxyMiddleware } = require('http-proxy-middleware')

module.exports = (req, res) => {
    let target = ''
    // 代理指标地址
    // 这里应用 backend 次要用于辨别 vercel serverless 的 api 门路
    // target 替换为你跨域申请的服务器 如： http://baidu.com
    if (req.url.startsWith('/api')) {
        if (req.url != "/api") {
            target = 'http://114.132.162.245'
        }
    }
    // 创立代理对象并转发申请
    createProxyMiddleware({
        target,
        changeOrigin: true,
        pathRewrite: {
            // 通过门路重写，去除申请门路中的 `/backend`
            // 例如 /backend/user/login 将被转发到 https://fanyi-api.baidu.com/user/login
            '^/api/': '/',
        },
    })(req, res)
}