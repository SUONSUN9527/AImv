package com.aimv.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StoryboardPlannerTest {

    @Test
    void splitsStoryIntoExactShotCountWithNonBlankScenes() {
        String story = "天地初开，混沌未分。少年负剑，立于云海之巅。剑锋出鞘，寒光乍现。魔影升起。";
        List<String> shots = StoryboardPlanner.shots(story, 12);

        assertThat(shots).hasSize(12);
        assertThat(shots).allSatisfy(shot -> assertThat(shot).isNotBlank());
        assertThat(shots.get(0)).contains("天地初开");
    }

    @Test
    void reusesScenesWhenStoryHasFewerSentencesThanShots() {
        List<String> shots = StoryboardPlanner.shots("少年御剑。", 5);

        assertThat(shots).hasSize(5);
        assertThat(shots).allSatisfy(shot -> assertThat(shot).contains("少年御剑"));
    }

    @Test
    void fallsBackWhenStoryIsBlank() {
        List<String> shots = StoryboardPlanner.shots("", 3);

        assertThat(shots).hasSize(3);
        assertThat(shots).allSatisfy(shot -> assertThat(shot).isNotBlank());
    }

    @Test
    void characterAnchorCarriesConsistencyConstraintAndGoal() {
        String anchor = StoryboardPlanner.characterAnchor("雨夜霓虹街头的年轻侦探");

        assertThat(anchor)
            .contains("consistent same main character")
            .contains("9:16")
            .contains("雨夜霓虹街头的年轻侦探");
    }
}
