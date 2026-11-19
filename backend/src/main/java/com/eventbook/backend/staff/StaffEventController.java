package com.eventbook.backend.staff;

import com.eventbook.backend.security.AuthenticatedUser;
import com.eventbook.backend.staff.dto.StaffEventResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffEventController {

    private final StaffService staffService;

    @GetMapping("/events")
    @PreAuthorize("hasRole('STAFF')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Lista os eventos associados ao staff autenticado")
    public List<StaffEventResponse> listAssignedEvents(@AuthenticationPrincipal AuthenticatedUser user) {
        return staffService.listAssignedEvents(user.id());
    }
}
