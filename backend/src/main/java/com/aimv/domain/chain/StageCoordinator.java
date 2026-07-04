package com.aimv.domain.chain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class StageCoordinator {

    private static final String DIVIDE_AND_MERGE = "DIVIDE_AND_MERGE";

    public StageCoordinationResult coordinate(StageCatalog.StageDefinition stage,
            List<StagePartialOutput> partialOutputs) {
        if (stage == null) {
            throw new IllegalArgumentException("stage must not be null");
        }
        if (!DIVIDE_AND_MERGE.equals(stage.collaborationMode()) && stage.partialSchemas().isEmpty()) {
            return StageCoordinationResult.empty(stage.outputSchemaId());
        }
        List<StagePartialOutput> safePartialOutputs = partialOutputs == null ? List.of() : List.copyOf(partialOutputs);
        Map<String, StagePartialOutput> partialByAgentName = partialByAgentName(stage, safePartialOutputs);
        validatePartialSchemas(stage, partialByAgentName);
        if (!DIVIDE_AND_MERGE.equals(stage.collaborationMode())) {
            return coordinateStructuredSingleAgentStage(stage, partialByAgentName);
        }
        List<String> mergePriorityAgentNames = mergePriorityAgentNames(stage);
        Set<String> fieldNames = fieldNames(stage.agentNames(), partialByAgentName);
        Map<String, Object> mergedOutput = new LinkedHashMap<>();
        List<StageConflictResolution> conflictResolutions = new ArrayList<>();
        for (String fieldName : fieldNames) {
            List<AgentFieldValue> fieldValues = fieldValues(stage.agentNames(), partialByAgentName, fieldName);
            AgentFieldValue selectedValue = selectedValue(mergePriorityAgentNames, fieldValues);
            mergedOutput.put(fieldName, selectedValue.value());
            if (hasConflict(fieldValues)) {
                conflictResolutions.add(conflictResolution(stage, fieldName, selectedValue, fieldValues,
                    mergePriorityAgentNames));
            }
        }
        return new StageCoordinationResult(stage.outputSchemaId(), mergedOutput, conflictResolutions);
    }

    private StageCoordinationResult coordinateStructuredSingleAgentStage(StageCatalog.StageDefinition stage,
            Map<String, StagePartialOutput> partialByAgentName) {
        Map<String, Object> mergedOutput = new LinkedHashMap<>();
        for (StagePartialSchema partialSchema : stage.partialSchemas()) {
            mergedOutput.putAll(partialByAgentName.get(partialSchema.agentName()).fields());
        }
        return new StageCoordinationResult(stage.outputSchemaId(), mergedOutput, List.of());
    }

    private Map<String, StagePartialOutput> partialByAgentName(StageCatalog.StageDefinition stage,
            List<StagePartialOutput> partialOutputs) {
        Map<String, StagePartialOutput> partialByAgentName = new LinkedHashMap<>();
        for (StagePartialOutput partialOutput : partialOutputs) {
            partialByAgentName.put(partialOutput.agentName(), partialOutput);
        }
        List<String> missingAgentNames = stage.agentNames().stream()
            .filter(agentName -> !partialByAgentName.containsKey(agentName))
            .toList();
        if (!missingAgentNames.isEmpty()) {
            throw new IllegalArgumentException(stage.stageCode() + " missing partial output from "
                + String.join(", ", missingAgentNames));
        }
        return partialByAgentName;
    }

    private void validatePartialSchemas(StageCatalog.StageDefinition stage,
            Map<String, StagePartialOutput> partialByAgentName) {
        for (StagePartialSchema partialSchema : stage.partialSchemas()) {
            StagePartialOutput partialOutput = partialByAgentName.get(partialSchema.agentName());
            if (partialOutput == null) {
                throw new IllegalArgumentException(stage.stageCode() + " missing partial schema input from "
                    + partialSchema.agentName());
            }
            validateRequiredFields(stage, partialSchema, partialOutput);
            validateAllowedFields(stage, partialSchema, partialOutput);
        }
    }

    private void validateRequiredFields(StageCatalog.StageDefinition stage, StagePartialSchema partialSchema,
            StagePartialOutput partialOutput) {
        List<String> missingFieldNames = partialSchema.requiredFieldNames().stream()
            .filter(fieldName -> !partialOutput.fields().containsKey(fieldName))
            .toList();
        if (!missingFieldNames.isEmpty()) {
            throw new IllegalArgumentException(stage.outputSchemaId() + " partial schema violation in "
                + partialSchema.agentName() + ": missing " + String.join(", ", missingFieldNames));
        }
    }

    private void validateAllowedFields(StageCatalog.StageDefinition stage, StagePartialSchema partialSchema,
            StagePartialOutput partialOutput) {
        List<String> unexpectedFieldNames = partialOutput.fields().keySet().stream()
            .filter(fieldName -> !partialSchema.allowedFieldNames().contains(fieldName))
            .toList();
        if (!unexpectedFieldNames.isEmpty()) {
            throw new IllegalArgumentException(stage.outputSchemaId() + " partial schema violation in "
                + partialSchema.agentName() + ": unexpected " + String.join(", ", unexpectedFieldNames));
        }
    }

    private List<String> mergePriorityAgentNames(StageCatalog.StageDefinition stage) {
        if (stage.mergePriorityAgentNames().isEmpty()) {
            return stage.agentNames();
        }
        return stage.mergePriorityAgentNames();
    }

    private Set<String> fieldNames(List<String> agentNames, Map<String, StagePartialOutput> partialByAgentName) {
        Set<String> fieldNames = new LinkedHashSet<>();
        for (String agentName : agentNames) {
            fieldNames.addAll(partialByAgentName.get(agentName).fields().keySet());
        }
        return fieldNames;
    }

    private List<AgentFieldValue> fieldValues(List<String> agentNames,
            Map<String, StagePartialOutput> partialByAgentName, String fieldName) {
        List<AgentFieldValue> fieldValues = new ArrayList<>();
        for (String agentName : agentNames) {
            Map<String, Object> fields = partialByAgentName.get(agentName).fields();
            if (fields.containsKey(fieldName)) {
                fieldValues.add(new AgentFieldValue(agentName, fields.get(fieldName)));
            }
        }
        return List.copyOf(fieldValues);
    }

    private AgentFieldValue selectedValue(List<String> mergePriorityAgentNames, List<AgentFieldValue> fieldValues) {
        for (String agentName : mergePriorityAgentNames) {
            for (AgentFieldValue fieldValue : fieldValues) {
                if (agentName.equals(fieldValue.agentName())) {
                    return fieldValue;
                }
            }
        }
        throw new IllegalArgumentException("No field value matches merge priority");
    }

    private boolean hasConflict(List<AgentFieldValue> fieldValues) {
        if (fieldValues.size() < 2) {
            return false;
        }
        Object firstValue = fieldValues.get(0).value();
        return fieldValues.stream().anyMatch(fieldValue -> !Objects.equals(firstValue, fieldValue.value()));
    }

    private StageConflictResolution conflictResolution(StageCatalog.StageDefinition stage, String fieldName,
            AgentFieldValue selectedValue, List<AgentFieldValue> fieldValues, List<String> mergePriorityAgentNames) {
        List<String> conflictingAgentNames = fieldValues.stream()
            .filter(fieldValue -> !selectedValue.agentName().equals(fieldValue.agentName()))
            .filter(fieldValue -> !Objects.equals(selectedValue.value(), fieldValue.value()))
            .map(AgentFieldValue::agentName)
            .collect(Collectors.toList());
        return new StageConflictResolution(fieldName, selectedValue.agentName(), selectedValue.value(),
            conflictingAgentNames, stage.stageCode() + " resolved by " + selectedValue.agentName()
                + " according to merge priority " + mergePriorityAgentNames);
    }

    private record AgentFieldValue(
            String agentName,
            Object value
    ) {
    }
}
