# Tool Registry

当前登记能力只允许云端 HTTP adapter 或确定性工程工具。

| Capability | Chain | Adapter Kind | Free Gate Required |
| --- | --- | --- | --- |
| `llm.text.free` | IMAGE, VIDEO | HTTP_ADAPTER | yes |
| `rag.embedding.free` | IMAGE, VIDEO | HTTP_ADAPTER | yes |
| `rag.rerank.free` | IMAGE, VIDEO | HTTP_ADAPTER | yes |
| `image.generate.free` | IMAGE | HTTP_ADAPTER | yes |
| `video.generate.full_with_voice.free` | VIDEO | HTTP_ADAPTER | yes |

禁止登记本地大模型权重下载、本地模型推理、剪辑导出、独立音频和口型同步能力。

