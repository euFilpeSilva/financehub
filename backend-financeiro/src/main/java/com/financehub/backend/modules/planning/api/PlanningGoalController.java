package com.financehub.backend.modules.planning.api;

import com.financehub.backend.modules.planning.api.dto.PlanningGoalRequest;
import com.financehub.backend.modules.planning.api.dto.PlanningGoalResponse;
import com.financehub.backend.modules.planning.application.PlanningGoalService;
import com.financehub.backend.modules.planning.domain.PlanningGoal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/planning-goals")
public class PlanningGoalController {
  private final PlanningGoalService service;

  public PlanningGoalController(PlanningGoalService service) {
    this.service = service;
  }

  @GetMapping
  public List<PlanningGoalResponse> list() {
    return service.listAll().stream().map(this::toResponse).toList();
  }

  @PostMapping
  public PlanningGoalResponse create(@Valid @RequestBody PlanningGoalRequest request) {
    return toResponse(service.create(request));
  }

  @PutMapping("/{id}")
  public PlanningGoalResponse update(@PathVariable String id, @Valid @RequestBody PlanningGoalRequest request) {
    return toResponse(service.update(id, request));
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable String id) {
    service.delete(id);
  }

  private PlanningGoalResponse toResponse(PlanningGoal goal) {
    return new PlanningGoalResponse(
      goal.getId(),
      goal.getTitle(),
      goal.getTargetAmount(),
      goal.getCurrentAmount(),
      goal.getTargetDate(),
      goal.getNotes(),
      goal.isComplete()
    );
  }
}
