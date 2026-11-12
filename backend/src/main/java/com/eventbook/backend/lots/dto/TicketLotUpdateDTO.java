package com.eventbook.backend.lots.dto;

import com.eventbook.backend.lots.LotStatus;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketLotUpdateDTO {

    @Size(max = 255)
    private String name;

    private BigDecimal unitPrice;

    private Integer capacity;

    private Instant salesStart;

    private Instant salesEnd;

    private LotStatus status;
}
