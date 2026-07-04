package com.aimv.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DddLayerArchitectureTest {

    @Test
    void domainLayerDoesNotDependOnSpringOrHttpFrameworks() throws IOException {
        Path domainRoot = Path.of("src/main/java/com/aimv/domain");

        if (!Files.exists(domainRoot)) {
            throw new AssertionError("domain layer must exist");
        }

        List<Path> javaFiles;
        try (var stream = Files.walk(domainRoot)) {
            javaFiles = stream
                .filter(path -> path.toString().endsWith(".java"))
                .toList();
        }

        assertThat(javaFiles).isNotEmpty();

        for (Path javaFile : javaFiles) {
            String source = Files.readString(javaFile);
            assertThat(source)
                .as(javaFile.toString())
                .doesNotContain("org.springframework")
                .doesNotContain("jakarta.servlet")
                .doesNotContain("@RestController")
                .doesNotContain("@Service")
                .doesNotContain("@Repository");
        }
    }
}
