package com.aimv.domain.chain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aimv.domain.shared.ChainType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StageCoordinatorTest {

    @Test
    void mergesImageVisualBriefByDocumentedPriorityAndRecordsConflicts() {
        StageCatalog.StageDefinition stage = stage(ChainType.IMAGE, "I10");
        StageCoordinationResult result = new StageCoordinator().coordinate(stage, List.of(
            new StagePartialOutput("SubjectAgent", Map.of(
                "subject", "侦探背影",
                "aspectRatio", "1:1"
            )),
            new StagePartialOutput("StyleAgent", Map.of(
                "palette", "neon",
                "aspectRatio", "16:9"
            )),
            new StagePartialOutput("ConstraintAgent", Map.of(
                "aspectRatio", "9:16",
                "forbiddenTerms", List.of("logo")
            ))
        ));

        assertThat(result.outputSchemaId()).isEqualTo("image-I10-output.v1");
        assertThat(result.mergedOutput())
            .containsEntry("subject", "侦探背影")
            .containsEntry("palette", "neon")
            .containsEntry("aspectRatio", "9:16");
        assertThat(result.conflictResolutions()).singleElement().satisfies(conflict -> {
            assertThat(conflict.fieldName()).isEqualTo("aspectRatio");
            assertThat(conflict.selectedAgentName()).isEqualTo("ConstraintAgent");
            assertThat(conflict.conflictingAgentNames()).containsExactly("SubjectAgent", "StyleAgent");
            assertThat(conflict.reason()).contains("ConstraintAgent", "I10");
        });
    }

    @Test
    void mergesStructuredSingleAgentGoalOutputWhenStageDeclaresPartialSchema() {
        StageCatalog.StageDefinition stage = stage(ChainType.VIDEO, "V00");
        StageCoordinationResult result = new StageCoordinator().coordinate(stage, List.of(
            new StagePartialOutput("GoalAgent", Map.of(
                "theme", "都市悬疑反转",
                "durationSeconds", 10,
                "aspectRatio", "9:16",
                "style", "neon suspense",
                "voiceoverRequirement", "HUMAN_VOICE_REQUIRED",
                "outputFormat", "complete_short_video",
                "goalClarityScore", 95,
                "safetyScore", 100
            ))
        ));

        assertThat(result.outputSchemaId()).isEqualTo("video-V00-output.v1");
        assertThat(result.mergedOutput())
            .containsEntry("durationSeconds", 10)
            .containsEntry("aspectRatio", "9:16")
            .containsEntry("voiceoverRequirement", "HUMAN_VOICE_REQUIRED");
        assertThat(result.conflictResolutions()).isEmpty();
    }

    @Test
    void rejectsMissingRequiredPartialForDivideAndMergeStage() {
        StageCatalog.StageDefinition stage = stage(ChainType.VIDEO, "V10");

        assertThatThrownBy(() -> new StageCoordinator().coordinate(stage, List.of(
            new StagePartialOutput("StoryAgent", Map.of("story", "反转")),
            new StagePartialOutput("ConstraintAgent", Map.of("aspectRatio", "9:16"))
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("V10")
            .hasMessageContaining("VisualAgent")
            .hasMessageContaining("MotionAgent");
    }

    @Test
    void rejectsPartialMissingRequiredSchemaField() {
        StageCatalog.StageDefinition stage = stage(ChainType.IMAGE, "I10");

        assertThatThrownBy(() -> new StageCoordinator().coordinate(stage, List.of(
            new StagePartialOutput("SubjectAgent", Map.of("aspectRatio", "1:1")),
            new StagePartialOutput("StyleAgent", Map.of("palette", "neon")),
            new StagePartialOutput("ConstraintAgent", Map.of(
                "aspectRatio", "9:16",
                "forbiddenTerms", List.of("logo")
            ))
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("image-I10-output.v1")
            .hasMessageContaining("SubjectAgent")
            .hasMessageContaining("subject");
    }

    @Test
    void rejectsPartialFieldOutsideSchema() {
        StageCatalog.StageDefinition stage = stage(ChainType.IMAGE, "I10");

        assertThatThrownBy(() -> new StageCoordinator().coordinate(stage, List.of(
            new StagePartialOutput("SubjectAgent", Map.of(
                "subject", "侦探背影",
                "markdownNarration", "### not json schema"
            )),
            new StagePartialOutput("StyleAgent", Map.of("palette", "neon")),
            new StagePartialOutput("ConstraintAgent", Map.of(
                "aspectRatio", "9:16",
                "forbiddenTerms", List.of("logo")
            ))
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SubjectAgent")
            .hasMessageContaining("markdownNarration");
    }

    private StageCatalog.StageDefinition stage(ChainType chainType, String stageCode) {
        return StageCatalog.stages(chainType).stream()
            .filter(definition -> stageCode.equals(definition.stageCode()))
            .findFirst()
            .orElseThrow();
    }
}
