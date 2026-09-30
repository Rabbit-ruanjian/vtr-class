# 手机号/邮箱验证码登录配置

## 当前实现

- `POST /api/users/send-code`：发送登录验证码。
- `POST /api/users/code-login`：验证码登录；联系方式不存在时自动创建账号。
- 注册页提交验证码时还会设置密码，新创建的账号可继续使用“学号/工号 + 密码”登录。
- `POST /api/users/me/identity-binding`：提交单位、学号/工号绑定申请。
- `PUT /api/users/{id}/identity-binding/review?approved=true`：管理员审核绑定申请。
- 验证码有效期 5 分钟，60 秒内不能重复发送，单个 IP 每小时最多发送 30 次，错误 5 次后验证码失效。

## 本地开发

默认配置为：

```yaml
verification:
  provider: console
```

此时验证码不会真的发短信或邮件，而是打印在后端日志中。搜索：

```text
[开发环境] 手机验证码
[开发环境] 邮箱验证码
```

## 邮箱发送

设置以下环境变量，然后将 `VERIFICATION_PROVIDER` 设置为 `smtp`：

```text
VERIFICATION_PROVIDER=smtp
MAIL_HOST=smtp.example.com
MAIL_PORT=465
MAIL_USERNAME=your-mail@example.com
MAIL_PASSWORD=邮箱 SMTP 授权码
MAIL_SSL=true
```

邮箱服务商通常要求先开启 SMTP，并使用“授权码”，不能直接使用网页登录密码。
`MAIL_USERNAME` 是系统的固定发件邮箱，验证码接口中的 `target` 是用户填写的收件邮箱，
因此验证码可以发送给不同用户的 QQ、163、企业邮箱等地址，不会只发送给 `MAIL_USERNAME` 本身。

以 QQ 邮箱为例：在 QQ 邮箱“设置 > 账户”中开启 SMTP 并生成授权码，然后配置：

```text
VERIFICATION_PROVIDER=smtp
MAIL_HOST=smtp.qq.com
MAIL_PORT=465
MAIL_USERNAME=your-mail@qq.com
MAIL_PASSWORD=QQ邮箱SMTP授权码
MAIL_SSL=true
```

PowerShell 中要在同一个窗口设置并启动后端；如果后端已经运行过，必须重启使新配置生效：

```powershell
$env:VERIFICATION_PROVIDER = "smtp"
$env:MAIL_HOST = "smtp.qq.com"
$env:MAIL_PORT = "465"
$env:MAIL_USERNAME = "你的QQ邮箱@qq.com"
$env:MAIL_PASSWORD = "QQ邮箱SMTP授权码"
$env:MAIL_SSL = "true"
& "E:\新的\源代码\start-backend.cmd" restart
```

启动日志应出现“邮箱验证码 SMTP 模式已启用”。如果仍出现“开发环境”字样，说明旧进程没有重启或环境变量没有传入。

也可以使用 587 端口：将 `MAIL_PORT` 设置为 `587`、`MAIL_SSL` 设置为 `false`，并增加
`$env:MAIL_STARTTLS = "true"`。

测试请求：

```json
POST /api/users/send-code
{
  "channel": "EMAIL",
  "target": "任意收件人的邮箱@example.com",
  "scene": "LOGIN"
}
```

不要把邮箱密码或授权码提交到 Git，也不要放进前端代码。

## 手机短信发送

短信服务商的签名、模板和鉴权方式各不相同，因此当前代码保留了
`VerificationCodeSender.sendSms` 适配入口。生产环境需要在
`DefaultVerificationCodeSender.sendSms` 中接入已购买的短信服务商 SDK 或 HTTPS API，
密钥只放在后端环境变量中，不能放到 Vue 前端。

短信模板建议：

```text
您的登录验证码为 ${code}，5分钟内有效。如非本人操作请忽略。
```

## 数据库迁移

生产环境执行：

```text
vtr-backend/sql/migration-auth-identity.sql
```

老用户如果原来的 `username` 就是学号/工号，可以确认数据后执行脚本底部的回填 SQL。
