# Chain Map

## IMAGE

| Stage | Name | Output |
| --- | --- | --- |
| I00 | 目标锁定 | `ImageGoalSpec` |
| I10 | 视觉方案 | `VisualBrief` |
| I20 | 图片提示词包 | `ImagePromptPack` |
| I30 | 图片能力预检 | `ImageGenerationPlan` |
| I40 | 图片生成 | `ImageCandidateAssets` |
| I50 | 图片质量评审 | `ImageReviewReport` |
| I60 | 图片验收交付 | `FinalImageArtifact` |

## VIDEO

| Stage | Name | Output |
| --- | --- | --- |
| V00 | 目标锁定 | `VideoGoalSpec` |
| V10 | 短片方案 | `ShortFilmBrief` |
| V20 | 完整视频提示词包 | `FullVideoPromptPack` |
| V30 | 视频能力预检 | `FullVideoGenerationPlan` |
| V40 | 完整短片生成 | `VideoCandidateAssets` |
| V50 | 视频质量评审 | `VideoReviewReport` |
| V60 | 视频验收交付 | `FinalVideoArtifact` |

