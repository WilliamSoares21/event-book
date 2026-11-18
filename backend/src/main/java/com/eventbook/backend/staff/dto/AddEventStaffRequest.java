package com.eventbook.backend.staff.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddEventStaffRequest {

    @NotNull(message = "O ID do usuário é obrigatório")
    private UUID userId;
}