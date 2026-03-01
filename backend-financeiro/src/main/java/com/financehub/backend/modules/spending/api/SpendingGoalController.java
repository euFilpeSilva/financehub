package com.financehub.backend.modules.spending.api;

import com.financehub.backend.modules.spending.api.dto.SpendingGoalRequest;
import com.financehub.backend.modules.spending.api.dto.SpendingGoalResponse;
import com.financehub.backend.modules.spending.application.SpendingGoalService;
import com.financehub.backend.modules.spending.domain.SpendingGoal;
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
@RequestMapping("/api/v1/spending-goals")
public class SpendingGoalController {
  private final SpendingGoalService service;

  public SpendingGoalController(SpendingGoalService service) {
    this.service = service;
  }

  @GetMapping
  public List<SpendingGoalResponse> list() {
    return service.listAll().stream().map(this::toResponse).toList();
  }

  @PostMapping
  public SpendingGoalResponse create(@Valid @RequestBody SpendingGoalRequest request) {
    return toResponse(service.create(request));
  }

  @PutMapping("/{id}")
  public SpendingGoalResponse update(@PathVariable String id, @Valid @RequestBody SpendingGoalRequest request) {
    return toResponse(service.update(id, request));
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable String id) {
    service.delete(id);
  }

  private SpendingGoalResponse toResponse(SpendingGoal goal) {
    return new SpendingGoalResponse(
      goal.getId(),
      goal.getTitle(),
      goal.getLimitAmount(),
      goal.getCategory(),
      goal.getSchedule(),
      goal.getStartMonth(),
      goal.getStartDate(),
      goal.getEndDate(),
      goal.isActive()
    );
  }
}

