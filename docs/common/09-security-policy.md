# Security Policy

- API key 明文只允许后端接收一次。
- 响应、日志、RAG、测试报告、ReviewReport 均不得出现 API key 明文。
- key 存储必须使用加密或不可逆本地替代；删除时清除密文字段或写入不可恢复墓碑。
- 所有输入必须在接口边界校验，并限制 provider、label、apiKey、model 等字符串长度。
- 带有 key hash、密文或凭据定位信息的领域对象不得使用默认全字段 `toString()` 泄漏敏感材料。
- 错误响应只返回业务错误码和用户可理解信息，不透出 provider 原始敏感报文。
- 免费额度不足、provider 需要付费或自动升级付费风险存在时必须阻塞。
