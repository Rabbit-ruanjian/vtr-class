package com.vtr.dto;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
@Data
public class AcademicClassImportResult {
    private int total;
    private int created;
    private int existing;
    private int skipped;
    private int unregistered;
    private List<String> createdNumbers = new ArrayList<>();
    private List<String> existingNumbers = new ArrayList<>();
    private List<String> failedNumbers = new ArrayList<>();
    private List<String> unregisteredNumbers = new ArrayList<>();
}
